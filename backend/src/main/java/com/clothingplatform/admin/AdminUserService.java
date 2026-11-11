package com.clothingplatform.admin;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.auth.AuthService;
import com.clothingplatform.auth.RefreshTokenService;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.common.Sql;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.security.AppUserDetails;
import com.clothingplatform.security.CurrentUser;
import com.clothingplatform.user.AccountStatus;
import com.clothingplatform.user.User;
import com.clothingplatform.user.UserRepository;
import com.clothingplatform.user.UserType;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    public record UserRow(long id, String fullName, String email, String phone, String userType, String accountStatus,
                          boolean emailVerified, boolean phoneVerified, Instant createdAt, Instant lastLoginAt) {}
    public record LoginEntry(boolean success, String failureReason, String ipAddress, Instant at) {}
    public record ActivityEntry(String action, String entityType, String entityId, Instant at) {}
    public record UserDetail(UserRow user, String statusReason, List<String> roles, List<LoginEntry> logins,
                             List<ActivityEntry> activity, Long latestApplicationId) {}

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AuditService audit;
    private final RefreshTokenService refreshTokens;
    private final AuthService authService;

    public AdminUserService(JdbcTemplate jdbc, UserRepository users, AuditService audit, RefreshTokenService refreshTokens, AuthService authService) {
        this.jdbc = jdbc; this.users = users; this.audit = audit; this.refreshTokens = refreshTokens; this.authService = authService;
    }

    private static UserRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserRow(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
                rs.getString("user_type"), rs.getString("account_status"), rs.getBoolean("email_verified"),
                rs.getBoolean("phone_verified"), Sql.ts(rs, "created_at"), Sql.ts(rs, "last_login_at"));
    }

    private static final String COLS = "id, full_name, email, phone, user_type, account_status, email_verified, phone_verified, created_at, last_login_at";

    private boolean canSeeStaff() {
        AppUserDetails a = CurrentUser.require();
        return a.isSuperAdmin() || a.hasPermission("ADMIN_MANAGE");
    }

    public PageResult<UserRow> list(String type, String status, String q, int page, int size) {
        List<String> allowed = canSeeStaff() ? List.of("CUSTOMER", "TAILOR", "ADMIN", "SUPER_ADMIN") : List.of("CUSTOMER", "TAILOR");
        if (type != null && !type.isBlank()) {
            try { UserType.valueOf(type); } catch (IllegalArgumentException e) { throw bad("Unknown user type."); }
            if (!allowed.contains(type)) throw new BusinessException("ACCESS_DENIED", "You do not have permission to view these accounts.", HttpStatus.FORBIDDEN);
            allowed = List.of(type);
        }
        StringBuilder where = new StringBuilder("WHERE deleted_at IS NULL AND user_type IN (")
                .append(String.join(",", Collections.nCopies(allowed.size(), "?"))).append(")");
        List<Object> args = new ArrayList<>(allowed);
        if (status != null && !status.isBlank()) {
            try { AccountStatus.valueOf(status); } catch (IllegalArgumentException e) { throw bad("Unknown status."); }
            where.append(" AND account_status = ?"); args.add(status);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (full_name ILIKE ? OR email ILIKE ? OR phone ILIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        int p = Sql.clampPage(page), s = Sql.clampSize(size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM users " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s); pageArgs.add(p * s);
        List<UserRow> items = jdbc.query("SELECT " + COLS + " FROM users " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> row(rs), pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, p, s);
    }

    public UserDetail detail(long id) {
        List<UserRow> found = jdbc.query("SELECT " + COLS + " FROM users WHERE id = ? AND deleted_at IS NULL", (rs, i) -> row(rs), id);
        if (found.isEmpty()) throw notFound();
        UserRow u = found.get(0);
        requireViewable(u.userType());
        String reason = jdbc.queryForObject("SELECT status_reason FROM users WHERE id = ?", String.class, id);
        List<String> roles = jdbc.queryForList("SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ? ORDER BY r.code", String.class, id);
        List<LoginEntry> logins = jdbc.query("SELECT success, failure_reason, ip_address, created_at FROM login_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT 20",
                (rs, i) -> new LoginEntry(rs.getBoolean(1), rs.getString(2), rs.getString(3), Sql.ts(rs, "created_at")), id);
        List<ActivityEntry> activity = jdbc.query("SELECT action, entity_type, entity_id, created_at FROM activity_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT 20",
                (rs, i) -> new ActivityEntry(rs.getString(1), rs.getString(2), rs.getString(3), Sql.ts(rs, "created_at")), id);
        List<Long> apps = jdbc.queryForList("SELECT id FROM tailor_applications WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, id);
        return new UserDetail(u, reason, roles, logins, activity, apps.isEmpty() ? null : apps.get(0));
    }

    public record UpdateUserRequest(String fullName, String phone, Boolean emailVerified, Boolean phoneVerified) {}

    @Transactional
    public void update(long id, UpdateUserRequest r) {
        User u = target(id);
        Map<String, Object> before = Map.of("fullName", u.getFullName(), "phone", String.valueOf(u.getPhone()),
                "emailVerified", u.isEmailVerified(), "phoneVerified", u.isPhoneVerified());
        if (r.fullName() != null) {
            if (r.fullName().isBlank() || r.fullName().length() > 160) throw bad("Enter a name up to 160 characters.");
            u.setFullName(r.fullName().trim());
        }
        if (r.phone() != null && !r.phone().equals(u.getPhone())) {
            if (!r.phone().matches("^\\+[1-9]\\d{7,14}$")) throw bad("Enter the phone number with country code, for example +919876543210.");
            if (users.existsByPhoneAndDeletedAtIsNull(r.phone())) throw new BusinessException("PHONE_TAKEN", "Another account uses this phone number.", HttpStatus.CONFLICT);
            u.setPhone(r.phone());
            u.setPhoneVerified(false);
        }
        if (r.emailVerified() != null) u.setEmailVerified(r.emailVerified());
        if (r.phoneVerified() != null) u.setPhoneVerified(r.phoneVerified());
        users.save(u);
        authService.advanceStatus(u);   // moves pending-verification accounts forward if both are now verified
        audit.audit(CurrentUser.require().getId(), "USER_UPDATED", "USER", id, before, r, null);
    }

    public record StatusChangeRequest(String action, String reason) {}

    /** BLOCK, SUSPEND or REACTIVATE. Blocking ends every session immediately. */
    @Transactional
    public void changeStatus(long id, StatusChangeRequest r) {
        AppUserDetails actor = CurrentUser.require();
        if (actor.getId() == id) throw bad("You cannot change your own account status.");
        User u = target(id);
        String action = r.action() == null ? "" : r.action().toUpperCase();
        AccountStatus before = u.getAccountStatus();
        AccountStatus after;
        switch (action) {
            case "BLOCK" -> {
                requireReason(r.reason());
                if (before == AccountStatus.BLOCKED) throw bad("This account is already blocked.");
                after = AccountStatus.BLOCKED;
            }
            case "SUSPEND" -> {
                requireReason(r.reason());
                if (!before.canAuthenticate()) throw bad("Only active accounts can be suspended.");
                after = AccountStatus.SUSPENDED;
            }
            case "REACTIVATE" -> {
                if (before != AccountStatus.BLOCKED && before != AccountStatus.SUSPENDED && before != AccountStatus.DEACTIVATED)
                    throw bad("This account is not blocked, suspended or deactivated.");
                after = restoredStatus(u);
            }
            default -> throw bad("Action must be BLOCK, SUSPEND or REACTIVATE.");
        }
        u.setAccountStatus(after);
        u.setStatusReason("REACTIVATE".equals(action) ? null : r.reason().trim());
        users.save(u);
        if (after == AccountStatus.BLOCKED || after == AccountStatus.SUSPENDED) refreshTokens.revokeAllForUser(id);
        audit.audit(actor.getId(), "USER_" + action, "USER", id, before.name(), after.name(), r.reason());
    }

    /** Where a reactivated account returns to, without skipping verification or tailor approval. */
    private AccountStatus restoredStatus(User u) {
        if (u.getUserType() == UserType.ADMIN || u.getUserType() == UserType.SUPER_ADMIN) return AccountStatus.ACTIVE;
        if (!u.isEmailVerified()) return AccountStatus.PENDING_EMAIL_VERIFICATION;
        if (!u.isPhoneVerified()) return AccountStatus.PENDING_PHONE_VERIFICATION;
        if (u.getUserType() == UserType.CUSTOMER) return AccountStatus.ACTIVE;
        List<String> app = jdbc.queryForList("SELECT status FROM tailor_applications WHERE user_id = ? ORDER BY id DESC LIMIT 1", String.class, u.getId());
        String s = app.isEmpty() ? "PENDING_APPROVAL" : app.get(0);
        return switch (s) { case "APPROVED" -> AccountStatus.APPROVED; case "REJECTED" -> AccountStatus.REJECTED; default -> AccountStatus.PENDING_APPROVAL; };
    }

    // ----- guards -----

    private User target(long id) {
        User u = users.findById(id).filter(x -> x.getDeletedAt() == null).orElseThrow(this::notFound);
        if (u.getUserType() == UserType.SUPER_ADMIN)
            throw new BusinessException("ACCESS_DENIED", "The Super Admin account cannot be modified here.", HttpStatus.FORBIDDEN);
        requireViewable(u.getUserType().name());
        return u;
    }

    private void requireViewable(String type) {
        if (("ADMIN".equals(type) || "SUPER_ADMIN".equals(type)) && !canSeeStaff())
            throw new BusinessException("ACCESS_DENIED", "You do not have permission to manage admin accounts.", HttpStatus.FORBIDDEN);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.trim().length() < 3) throw bad("Please give a reason.");
        if (reason.trim().length() > 500) throw bad("Keep the reason under 500 characters.");
    }
    private BusinessException bad(String m) { return new BusinessException("BAD_REQUEST", m, HttpStatus.BAD_REQUEST); }
    private BusinessException notFound() { return new BusinessException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND); }
}
