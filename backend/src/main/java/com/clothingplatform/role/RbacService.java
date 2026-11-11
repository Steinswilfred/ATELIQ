package com.clothingplatform.role;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.auth.VerificationChannel;
import com.clothingplatform.auth.VerificationService;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.role.RbacDtos.*;
import com.clothingplatform.security.AppUserDetails;
import com.clothingplatform.security.CurrentUser;
import com.clothingplatform.user.AccountStatus;
import com.clothingplatform.user.User;
import com.clothingplatform.user.UserRepository;
import com.clothingplatform.user.UserType;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization rules (enforced here, not just in controllers):
 *  - SUPER_ADMIN role is immutable and can never be assigned through the API.
 *  - A non-super-admin can only hand out permissions they hold themselves (no privilege escalation).
 *  - Only a holder of ADMIN_CREATE can create admins, and the Super Admin grants that explicitly.
 */
@Service
public class RbacService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final VerificationService verifications;
    private final EmailSender email;
    private final String frontendUrl;
    private static final SecureRandom RANDOM = new SecureRandom();

    public RbacService(JdbcTemplate jdbc, AuditService audit, UserRepository users, PasswordEncoder encoder,
                       VerificationService verifications, EmailSender email, @Value("${app.frontend-url}") String frontendUrl) {
        this.jdbc = jdbc; this.audit = audit; this.users = users; this.encoder = encoder;
        this.verifications = verifications; this.email = email; this.frontendUrl = frontendUrl;
    }

    public List<RoleView> listRoles() {
        Map<Long, List<String>> perms = new HashMap<>();
        jdbc.query("SELECT rp.role_id, p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.enabled ORDER BY p.code",
                rs -> { perms.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2)); });
        return jdbc.query("SELECT id, code, name, description, is_system FROM roles ORDER BY id",
                (rs, i) -> new RoleView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5),
                        perms.getOrDefault(rs.getLong(1), List.of())));
    }

    public List<PermissionView> listPermissions() {
        return jdbc.query("SELECT id, code, module, description FROM permissions ORDER BY module, code",
                (rs, i) -> new PermissionView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    @Transactional
    public RoleView createRole(CreateRoleRequest r) {
        if (exists("SELECT count(*) FROM roles WHERE code = ?", r.code())) throw conflict("ROLE_EXISTS", "A role with this code already exists.");
        long id = jdbc.queryForObject("INSERT INTO roles (code, name, description) VALUES (?,?,?) RETURNING id", Long.class, r.code(), r.name(), r.description());
        audit.audit(CurrentUser.require().getId(), "ROLE_CREATED", "ROLE", id, null, r, null);
        return new RoleView(id, r.code(), r.name(), r.description(), false, List.of());
    }

    @Transactional
    public void updateRole(long id, UpdateRoleRequest r) {
        requireMutableRole(id);
        jdbc.update("UPDATE roles SET name = ?, description = ?, updated_at = now() WHERE id = ?", r.name(), r.description(), id);
        audit.audit(CurrentUser.require().getId(), "ROLE_UPDATED", "ROLE", id, null, r, null);
    }

    @Transactional
    public void deleteRole(long id) {
        requireMutableRole(id);
        if (exists("SELECT count(*) FROM user_roles WHERE role_id = ?", id))
            throw conflict("ROLE_IN_USE", "This role is assigned to users. Remove it from them first.");
        jdbc.update("DELETE FROM roles WHERE id = ?", id);
        audit.audit(CurrentUser.require().getId(), "ROLE_DELETED", "ROLE", id, null, null, null);
    }

    @Transactional
    public void setRolePermissions(long roleId, List<String> codes) {
        requireMutableRole(roleId);
        Set<String> wanted = new TreeSet<>(codes);
        requireKnownPermissions(wanted);
        requireHolds(wanted);
        List<String> before = jdbc.queryForList("SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = ? ORDER BY p.code", String.class, roleId);
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
        for (String c : wanted) jdbc.update("INSERT INTO role_permissions (role_id, permission_id) SELECT ?, id FROM permissions WHERE code = ?", roleId, c);
        audit.audit(CurrentUser.require().getId(), "ROLE_PERMISSIONS_SET", "ROLE", roleId, before, wanted, null);
    }

    @Transactional
    public PermissionView createPermission(CreatePermissionRequest r) {
        if (exists("SELECT count(*) FROM permissions WHERE code = ?", r.code())) throw conflict("PERMISSION_EXISTS", "A permission with this code already exists.");
        long id = jdbc.queryForObject("INSERT INTO permissions (code, module, description) VALUES (?,?,?) RETURNING id", Long.class, r.code(), r.module(), r.description());
        audit.audit(CurrentUser.require().getId(), "PERMISSION_CREATED", "PERMISSION", id, null, r, null);
        return new PermissionView(id, r.code(), r.module(), r.description());
    }

    /** Replaces the roles of an ADMIN user. Roles granting permissions the actor lacks are refused. */
    @Transactional
    public void assignRoles(long userId, List<String> roleCodes) {
        User target = users.findById(userId).filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
        if (target.getUserType() != UserType.ADMIN)
            throw new BusinessException("NOT_AN_ADMIN", "Roles can only be changed for admin accounts here.", HttpStatus.BAD_REQUEST);
        List<Long> roleIds = resolveRoleIds(roleCodes);
        for (long rid : roleIds) requireHolds(new HashSet<>(jdbc.queryForList(
                "SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.enabled AND rp.role_id = ?", String.class, rid)));
        List<String> before = jdbc.queryForList("SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?", String.class, userId);
        long actor = CurrentUser.require().getId();
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        for (long rid : roleIds) jdbc.update("INSERT INTO user_roles (user_id, role_id, assigned_by) VALUES (?,?,?)", userId, rid, actor);
        audit.audit(actor, "USER_ROLES_SET", "USER", userId, before, roleCodes, null);
    }

    /** Only Super Admin, or an admin explicitly granted ADMIN_CREATE. The new admin sets their own password via emailed link. */
    @Transactional
    public long createAdmin(CreateAdminRequest r) {
        AppUserDetails actor = CurrentUser.require();
        if (!actor.isSuperAdmin() && !actor.hasPermission("ADMIN_CREATE"))
            throw new BusinessException("ACCESS_DENIED", "You do not have permission to create admins.", HttpStatus.FORBIDDEN);
        String emailAddr = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCaseAndDeletedAtIsNull(emailAddr)) throw conflict("EMAIL_TAKEN", "An account with this email already exists.");
        if (r.phone() != null && users.existsByPhoneAndDeletedAtIsNull(r.phone())) throw conflict("PHONE_TAKEN", "An account with this phone number already exists.");
        List<Long> roleIds = resolveRoleIds(r.roleCodes());
        for (long rid : roleIds) requireHolds(new HashSet<>(jdbc.queryForList(
                "SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.enabled AND rp.role_id = ?", String.class, rid)));

        User u = new User();
        u.setEmail(emailAddr); u.setPhone(r.phone()); u.setFullName(r.fullName().trim());
        byte[] pw = new byte[32];
        RANDOM.nextBytes(pw);
        u.setPasswordHash(encoder.encode(Base64.getEncoder().encodeToString(pw)));   // unknown to everyone until reset
        u.setUserType(UserType.ADMIN);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setEmailVerified(true);
        u.setPhoneVerified(r.phone() != null);
        u.setCreatedBy(actor.getId());
        u = users.save(u);
        for (long rid : roleIds) jdbc.update("INSERT INTO user_roles (user_id, role_id, assigned_by) VALUES (?,?,?)", u.getId(), rid, actor.getId());

        String token = verifications.issueLinkToken(u.getId(), VerificationChannel.PASSWORD_RESET, Duration.ofHours(48));
        email.send(emailAddr, "Your admin account", "An admin account was created for you.\nSet your password here (valid 48 hours):\n"
                + frontendUrl + "/reset-password?token=" + token);
        audit.audit(actor.getId(), "ADMIN_CREATED", "USER", u.getId(), null, Map.of("email", emailAddr, "roles", r.roleCodes()), null);
        return u.getId();
    }

    // ---------- guards ----------

    private void requireMutableRole(long id) {
        String code = jdbc.query("SELECT code, is_system FROM roles WHERE id = ?", rs -> rs.next() ? rs.getString(1) + "|" + rs.getBoolean(2) : null, id);
        if (code == null) throw new BusinessException("ROLE_NOT_FOUND", "Role not found.", HttpStatus.NOT_FOUND);
        if (code.startsWith("SUPER_ADMIN|")) throw new BusinessException("ROLE_IMMUTABLE", "The Super Admin role cannot be changed.", HttpStatus.FORBIDDEN);
        if (code.endsWith("|true") && !CurrentUser.require().isSuperAdmin() )
            throw new BusinessException("ROLE_IMMUTABLE", "Only the Super Admin can change system roles.", HttpStatus.FORBIDDEN);
    }

    private void requireKnownPermissions(Set<String> codes) {
        if (codes.isEmpty()) return;
        Set<String> known = new HashSet<>(jdbc.queryForList("SELECT code FROM permissions", String.class));
        Set<String> unknown = codes.stream().filter(c -> !known.contains(c)).collect(Collectors.toSet());
        if (!unknown.isEmpty()) throw new BusinessException("UNKNOWN_PERMISSION", "Unknown permission: " + unknown.iterator().next(), HttpStatus.BAD_REQUEST);
    }

    private void requireHolds(Set<String> codes) {
        AppUserDetails actor = CurrentUser.require();
        if (actor.isSuperAdmin()) return;
        for (String c : codes) if (!actor.hasPermission(c))
            throw new BusinessException("ESCALATION_DENIED", "You cannot grant a permission you do not hold: " + c, HttpStatus.FORBIDDEN);
    }

    private List<Long> resolveRoleIds(List<String> roleCodes) {
        List<Long> ids = new ArrayList<>();
        for (String code : new LinkedHashSet<>(roleCodes)) {
            if ("SUPER_ADMIN".equals(code) || "CUSTOMER".equals(code) || "TAILOR".equals(code))
                throw new BusinessException("ROLE_NOT_ASSIGNABLE", "The role " + code + " cannot be assigned to an admin.", HttpStatus.BAD_REQUEST);
            List<Long> found = jdbc.queryForList("SELECT id FROM roles WHERE code = ?", Long.class, code);
            if (found.isEmpty()) throw new BusinessException("ROLE_NOT_FOUND", "Unknown role: " + code, HttpStatus.BAD_REQUEST);
            ids.add(found.get(0));
        }
        return ids;
    }

    private boolean exists(String sql, Object arg) { Integer n = jdbc.queryForObject(sql, Integer.class, arg); return n != null && n > 0; }
    private BusinessException conflict(String code, String msg) { return new BusinessException(code, msg, HttpStatus.CONFLICT); }
}
