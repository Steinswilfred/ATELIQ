package com.clothingplatform.assignment;

import static org.junit.jupiter.api.Assertions.*;

import com.clothingplatform.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the "first tailor wins" guarantee against a real PostgreSQL: many tailors accept the same order at the same moment,
 * exactly one succeeds, the rest get ORDER_TAKEN, and only one active assignment row exists. Skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"app.jwt.secret=0123456789abcdef0123456789abcdef", "spring.mail.host=localhost"})
class AcceptConcurrencyIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16");

    @Autowired AssignmentService service;
    @Autowired JdbcTemplate jdbc;

    private long user(String email, String type, String status) {
        return jdbc.queryForObject("INSERT INTO users (email, password_hash, full_name, user_type, account_status, email_verified, phone_verified) VALUES (?, 'x', ?, ?, ?, TRUE, TRUE) RETURNING id", Long.class, email, email, type, status);
    }

    @Test
    void exactlyOneTailorWinsTheOrder() throws Exception {
        long categoryId = jdbc.queryForObject("SELECT id FROM tailor_categories ORDER BY level LIMIT 1", Long.class);
        long customer = user("cust@test.local", "CUSTOMER", "ACTIVE");
        int tailors = 8;
        List<Long> tailorIds = new ArrayList<>();
        for (int i = 0; i < tailors; i++) {
            long t = user("tailor" + i + "@test.local", "TAILOR", "APPROVED");
            long profile = jdbc.queryForObject("INSERT INTO tailor_profiles (user_id, category_id) VALUES (?, ?) RETURNING id", Long.class, t, categoryId);
            jdbc.update("INSERT INTO tailor_availability (tailor_id) VALUES (?)", profile);
            tailorIds.add(t);
        }
        long orderId = jdbc.queryForObject("""
            INSERT INTO orders (order_number, user_id, status, payment_method, payment_status, tailor_category_id, tailor_category_name, tailor_price_per_garment, garment_count, subtotal, tailor_charges,
                tax_rate_percent, tax, delivery_charge, total, shipping_address, customer_name, customer_phone)
            VALUES ('ORD-TEST-1', ?, 'TAILOR_ASSIGNMENT_PENDING', 'COD', 'PENDING_COLLECTION', ?, 'Beginner', 300, 1, 1000, 300, 5, 65, 99, 1464, '{"city":"Chennai"}'::jsonb, 'Test Customer', '+919999999999') RETURNING id""", Long.class, customer, categoryId);

        ExecutorService pool = Executors.newFixedThreadPool(tailors);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (long t : tailorIds) results.add(pool.submit(() -> {
            go.await();
            try { service.accept(t, orderId); return "WON"; }
            catch (BusinessException e) { return e.getCode(); }
        }));
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) outcomes.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, outcomes.stream().filter("WON"::equals).count(), outcomes.toString());
        assertEquals(tailors - 1, outcomes.stream().filter("ORDER_TAKEN"::equals).count(), outcomes.toString());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tailor_assignments WHERE order_id = ? AND status = 'ACTIVE'", Integer.class, orderId));
        assertEquals("FABRIC_SAMPLE_PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId));
        assertNotNull(jdbc.queryForObject("SELECT tailor_id FROM orders WHERE id = ?", Long.class, orderId));
    }
}
