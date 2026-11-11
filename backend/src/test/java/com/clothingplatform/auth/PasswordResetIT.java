package com.clothingplatform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.clothingplatform.auth.AuthDtos.RegisterCustomerRequest;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.notification.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the real migrations against PostgreSQL, so a column that is too small for a value fails here and not in production. Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"app.jwt.secret=0123456789abcdef0123456789abcdef"})
class PasswordResetIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16");

    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @MockBean EmailSender email;
    @MockBean SmsSender sms;

    @Test
    void forgotPasswordStoresATokenAndSendsTheEmail() {
        auth.registerCustomer(new RegisterCustomerRequest("Test User", "reset@test.local", "+919876543210", "Passw0rdOK1"), "127.0.0.1");
        auth.forgotPassword("reset@test.local");
        Integer n = jdbc.queryForObject("SELECT count(*) FROM user_verifications v JOIN users u ON u.id = v.user_id WHERE u.email = 'reset@test.local' AND v.channel = 'PASSWORD_RESET'", Integer.class);
        assertEquals(1, n);
        verify(email).send(eq("reset@test.local"), eq("Reset your password"), anyString());
    }
}
