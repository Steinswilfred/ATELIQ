package com.clothingplatform.auth;

import com.clothingplatform.user.AccountStatus;
import com.clothingplatform.user.User;
import com.clothingplatform.user.UserRepository;
import com.clothingplatform.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first Super Admin from SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD when none exists. Credentials never live in SQL or source. */
@Component
public class SuperAdminBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final String email;
    private final String password;

    public SuperAdminBootstrap(UserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc,
                               @Value("${app.bootstrap.super-admin-email}") String email,
                               @Value("${app.bootstrap.super-admin-password}") String password) {
        this.users = users; this.encoder = encoder; this.jdbc = jdbc; this.email = email; this.password = password;
    }

    @Override
    public void run(String... args) {
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM users WHERE user_type = 'SUPER_ADMIN' AND deleted_at IS NULL", Integer.class);
        if (existing != null && existing > 0) return;
        if (email.isBlank() || password.isBlank()) { log.warn("No Super Admin exists. Set SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD and restart."); return; }
        if (password.length() < 12) { log.error("SUPER_ADMIN_PASSWORD must be at least 12 characters. Super Admin not created."); return; }
        User u = new User();
        u.setEmail(email.trim().toLowerCase()); u.setFullName("Super Admin");
        u.setPasswordHash(encoder.encode(password));
        u.setUserType(UserType.SUPER_ADMIN); u.setAccountStatus(AccountStatus.ACTIVE);
        u.setEmailVerified(true); u.setPhoneVerified(true);
        u = users.save(u);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = 'SUPER_ADMIN'", u.getId());
        log.info("Super Admin created for {}", u.getEmail());
    }
}
