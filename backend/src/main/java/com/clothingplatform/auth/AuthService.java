package com.clothingplatform.auth;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.auth.AuthDtos.*;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.notification.NotificationService;
import com.clothingplatform.notification.SmsSender;
import com.clothingplatform.security.AccountRestrictedException;
import com.clothingplatform.security.AccountStatusChecker;
import com.clothingplatform.security.AppUserDetails;
import com.clothingplatform.security.AppUserDetailsService;
import com.clothingplatform.user.AccountStatus;
import com.clothingplatform.user.User;
import com.clothingplatform.user.UserRepository;
import com.clothingplatform.user.UserType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository users;
    private final AuthenticationManager authManager;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final VerificationService verifications;
    private final RefreshTokenService refreshTokens;
    private final AppUserDetailsService details;
    private final AuditService audit;
    private final EmailSender email;
    private final SmsSender sms;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final String frontendUrl;

    public AuthService(UserRepository users, AuthenticationManager authManager, PasswordEncoder encoder, JwtService jwt,
                       VerificationService verifications, RefreshTokenService refreshTokens, AppUserDetailsService details,
                       AuditService audit, EmailSender email, SmsSender sms, JdbcTemplate jdbc, NotificationService notifications,
                       @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users; this.authManager = authManager; this.encoder = encoder; this.jwt = jwt;
        this.verifications = verifications; this.refreshTokens = refreshTokens; this.details = details;
        this.audit = audit; this.email = email; this.sms = sms; this.jdbc = jdbc; this.notifications = notifications; this.frontendUrl = frontendUrl;
    }

    // ---------- registration ----------

    @Transactional
    public void registerCustomer(RegisterCustomerRequest r, String ip) {
        User u = createUser(r.fullName(), r.email(), r.phone(), r.password(), UserType.CUSTOMER);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = 'CUSTOMER'", u.getId());
        sendEmailVerification(u);
        sendPhoneOtp(u);
        audit.activity(u.getId(), "REGISTER_CUSTOMER", "USER", u.getId(), ip);
    }

    /** Creates the account and the application. The TAILOR role is only granted when an admin approves (Phase 3). */
    @Transactional
    public void registerTailor(RegisterTailorRequest r, String ip) {
        User u = createUser(r.fullName(), r.email(), r.phone(), r.password(), UserType.TAILOR);
        jdbc.update("""
            INSERT INTO tailor_applications (user_id, address, experience_years, previous_experience, preferred_category, skills, status)
            VALUES (?,?,?,?,?,?,'PENDING_EMAIL_VERIFICATION')""",
            u.getId(), r.address(), r.experienceYears(), r.previousExperience(), r.preferredCategory(), r.skills());
        sendEmailVerification(u);
        sendPhoneOtp(u);
        audit.activity(u.getId(), "REGISTER_TAILOR", "USER", u.getId(), ip);
    }

    private User createUser(String fullName, String emailAddr, String phone, String password, UserType type) {
        String normalized = emailAddr.trim().toLowerCase();
        if (users.existsByEmailIgnoreCaseAndDeletedAtIsNull(normalized))
            throw new BusinessException("EMAIL_TAKEN", "An account with this email already exists.", HttpStatus.CONFLICT);
        if (users.existsByPhoneAndDeletedAtIsNull(phone))
            throw new BusinessException("PHONE_TAKEN", "An account with this phone number already exists.", HttpStatus.CONFLICT);
        User u = new User();
        u.setEmail(normalized); u.setPhone(phone); u.setFullName(fullName.trim());
        u.setPasswordHash(encoder.encode(password));
        u.setUserType(type);
        u.setAccountStatus(AccountStatus.PENDING_EMAIL_VERIFICATION);
        return users.save(u);
    }

    // ---------- verification ----------

    private void sendEmailVerification(User u) {
        String token = verifications.issueLinkToken(u.getId(), VerificationChannel.EMAIL, Duration.ofHours(24));
        email.send(u.getEmail(), "Verify your email",
                "Hi " + u.getFullName() + ",\n\nConfirm your email address:\n" + frontendUrl + "/verify-email?token=" + token
                        + "\n\nThis link expires in 24 hours.");
    }

    private void sendPhoneOtp(User u) {
        String otp = verifications.issueOtp(u.getId());
        sms.send(u.getPhone(), "Your verification code is " + otp + ". It expires in 10 minutes.");
    }

    /** Always succeeds from the caller's point of view, so it cannot be used to discover registered emails. */
    @Transactional
    public void resendEmailVerification(String emailAddr) {
        find(emailAddr).filter(u -> !u.isEmailVerified() && !verifications.issuedRecently(u.getId(), VerificationChannel.EMAIL))
                .ifPresent(this::sendEmailVerification);
    }

    @Transactional
    public void resendPhoneOtp(String emailAddr) {
        find(emailAddr).filter(u -> u.getPhone() != null && !u.isPhoneVerified() && !verifications.issuedRecently(u.getId(), VerificationChannel.PHONE))
                .ifPresent(this::sendPhoneOtp);
    }

    @Transactional
    public void verifyEmail(String token) {
        User u = verifications.consumeLinkToken(token, VerificationChannel.EMAIL)
                .orElseThrow(() -> new BusinessException("INVALID_TOKEN", "This link is invalid or has expired. Request a new one.", HttpStatus.BAD_REQUEST));
        u.setEmailVerified(true);
        advanceStatus(u);
        audit.activity(u.getId(), "EMAIL_VERIFIED", "USER", u.getId(), null);
    }

    @Transactional
    public void verifyPhone(String emailAddr, String otp) {
        User u = find(emailAddr).orElseThrow(this::badOtp);
        if (!verifications.consumeOtp(u.getId(), otp)) throw badOtp();
        u.setPhoneVerified(true);
        advanceStatus(u);
        audit.activity(u.getId(), "PHONE_VERIFIED", "USER", u.getId(), null);
    }

    private BusinessException badOtp() {
        return new BusinessException("INVALID_OTP", "That code is incorrect or has expired.", HttpStatus.BAD_REQUEST);
    }

    /** Moves an account through the verification states; never touches blocked/rejected/etc. */
    public void advanceStatus(User u) {
        AccountStatus s = u.getAccountStatus();
        if (s != AccountStatus.PENDING_EMAIL_VERIFICATION && s != AccountStatus.PENDING_PHONE_VERIFICATION) return;
        AccountStatus next;
        if (!u.isEmailVerified()) next = AccountStatus.PENDING_EMAIL_VERIFICATION;
        else if (!u.isPhoneVerified()) next = AccountStatus.PENDING_PHONE_VERIFICATION;
        else next = u.getUserType() == UserType.TAILOR ? AccountStatus.PENDING_APPROVAL : AccountStatus.ACTIVE;
        u.setAccountStatus(next);
        users.save(u);
        if (u.getUserType() == UserType.TAILOR)
            jdbc.update("UPDATE tailor_applications SET status = ?, updated_at = now() WHERE user_id = ? AND status LIKE 'PENDING%'", next.name(), u.getId());
        if (next == AccountStatus.PENDING_APPROVAL)
            notifications.notifyPermissionHolders("TAILOR_APPLICATION_REVIEW", "TAILOR_APPLICATION_SUBMITTED", "New tailor application",
                    u.getFullName() + " has submitted a tailor application for review.", "/admin/tailors/pending");
    }

    // ---------- login / refresh / logout ----------

    public AuthResult login(LoginRequest r, String ip, String ua) {
        String emailAddr = r.email().trim().toLowerCase();
        Optional<User> existing = users.findByEmailIgnoreCaseAndDeletedAtIsNull(emailAddr);
        if (existing.isPresent() && existing.get().getLockedUntil() != null && existing.get().getLockedUntil().isAfter(Instant.now())) {
            audit.login(existing.get().getId(), emailAddr, false, "LOCKED", ip, ua);
            throw new BusinessException("ACCOUNT_LOCKED", "Too many failed attempts. Try again in a few minutes.", HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            var auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(emailAddr, r.password()));
            AppUserDetails principal = (AppUserDetails) auth.getPrincipal();
            User u = users.findById(principal.getId()).orElseThrow();
            u.setFailedLoginCount(0); u.setLockedUntil(null); u.setLastLoginAt(Instant.now());
            users.save(u);
            audit.login(u.getId(), emailAddr, true, null, ip, ua);
            return issueSession(principal.getId(), ip, ua);
        } catch (AccountRestrictedException e) {
            audit.login(existing.map(User::getId).orElse(null), emailAddr, false, e.getCode(), ip, ua);
            throw new BusinessException(e.getCode(), e.getMessage(), HttpStatus.FORBIDDEN);
        } catch (BadCredentialsException e) {
            existing.ifPresent(u -> registerFailure(u));
            audit.login(existing.map(User::getId).orElse(null), emailAddr, false, "BAD_CREDENTIALS", ip, ua);
            throw new BusinessException("INVALID_CREDENTIALS", "Incorrect email or password.", HttpStatus.UNAUTHORIZED);
        } catch (AuthenticationException e) {
            throw new BusinessException("INVALID_CREDENTIALS", "Incorrect email or password.", HttpStatus.UNAUTHORIZED);
        }
    }

    private void registerFailure(User u) {
        int n = u.getFailedLoginCount() + 1;
        u.setFailedLoginCount(n);
        if (n >= MAX_FAILED_LOGINS) { u.setLockedUntil(Instant.now().plus(LOCK_DURATION)); u.setFailedLoginCount(0); }
        users.save(u);
    }

    public AuthResult refresh(String rawRefreshToken, String ip, String ua) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank())
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Your session has expired. Please sign in again.", HttpStatus.UNAUTHORIZED);
        var rotated = refreshTokens.rotate(rawRefreshToken, ip, ua);
        AppUserDetails d = details.loadById(rotated.userId())
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "Your session has expired. Please sign in again.", HttpStatus.UNAUTHORIZED));
        try {
            new AccountStatusChecker().check(d);
        } catch (AccountRestrictedException e) {
            refreshTokens.revokeAllForUser(d.getId());
            throw new BusinessException(e.getCode(), e.getMessage(), HttpStatus.FORBIDDEN);
        }
        return new AuthResult(new AuthResponse(jwt.createAccessToken(d.getId()), jwt.accessTtlSeconds(), summary(d)), rotated.newToken());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) refreshTokens.revokeByRaw(rawRefreshToken);
    }

    private AuthResult issueSession(long userId, String ip, String ua) {
        AppUserDetails d = details.loadById(userId).orElseThrow();
        String refresh = refreshTokens.issueNewFamily(userId, ip, ua);
        return new AuthResult(new AuthResponse(jwt.createAccessToken(userId), jwt.accessTtlSeconds(), summary(d)), refresh);
    }

    public UserSummary summary(AppUserDetails d) {
        List<String> perms = d.getPermissions().stream().sorted().toList();
        return new UserSummary(d.getId(), d.getFullName(), d.getUsername(), d.getUserType(), perms);
    }

    // ---------- passwords ----------

    @Transactional
    public void forgotPassword(String emailAddr) {
        find(emailAddr).filter(u -> !verifications.issuedRecently(u.getId(), VerificationChannel.PASSWORD_RESET)).ifPresent(u -> {
            String token = verifications.issueLinkToken(u.getId(), VerificationChannel.PASSWORD_RESET, Duration.ofHours(1));
            email.send(u.getEmail(), "Reset your password",
                    "Use this link to choose a new password:\n" + frontendUrl + "/reset-password?token=" + token
                            + "\n\nIt expires in 1 hour. If you did not ask for this, ignore this email.");
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest r) {
        User u = verifications.consumeLinkToken(r.token(), VerificationChannel.PASSWORD_RESET)
                .orElseThrow(() -> new BusinessException("INVALID_TOKEN", "This link is invalid or has expired. Request a new one.", HttpStatus.BAD_REQUEST));
        u.setPasswordHash(encoder.encode(r.newPassword()));
        u.setFailedLoginCount(0); u.setLockedUntil(null);
        users.save(u);
        refreshTokens.revokeAllForUser(u.getId());   // sign out everywhere
        audit.audit(u.getId(), "PASSWORD_RESET", "USER", u.getId(), null, null, null);
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest r) {
        User u = users.findById(userId).orElseThrow();
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash()))
            throw new BusinessException("INVALID_CREDENTIALS", "Your current password is incorrect.", HttpStatus.BAD_REQUEST);
        u.setPasswordHash(encoder.encode(r.newPassword()));
        users.save(u);
        refreshTokens.revokeAllForUser(userId);
        audit.audit(userId, "PASSWORD_CHANGED", "USER", userId, null, null, null);
    }

    private Optional<User> find(String emailAddr) {
        return users.findByEmailIgnoreCaseAndDeletedAtIsNull(emailAddr.trim().toLowerCase());
    }
}
