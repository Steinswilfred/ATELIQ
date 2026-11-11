package com.clothingplatform.approval;

import static org.junit.jupiter.api.Assertions.*;

import com.clothingplatform.approval.FabricSampleService.RejectRequest;
import com.clothingplatform.approval.FabricSampleService.SubmitRequest;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.notification.SmsSender;
import com.clothingplatform.order.OrderService;
import com.clothingplatform.order.OrderStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Walks the whole approval loop on a real PostgreSQL and checks the guards. Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"app.jwt.secret=0123456789abcdef0123456789abcdef"})
class FabricApprovalFlowIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16");

    @Autowired FabricSampleService samples;
    @Autowired OrderService orders;
    @Autowired JdbcTemplate jdbc;
    @MockBean EmailSender email;
    @MockBean SmsSender sms;

    private static final String PHOTO1 = "/media/samples/123e4567-e89b-12d3-a456-426614174001.jpg";
    private static final String PHOTO2 = "/media/samples/123e4567-e89b-12d3-a456-426614174002.jpg";

    private long user(String e, String type, String status) {
        return jdbc.queryForObject("INSERT INTO users (email, password_hash, full_name, user_type, account_status, email_verified, phone_verified) VALUES (?, 'x', ?, ?, ?, TRUE, TRUE) RETURNING id", Long.class, e, e, type, status);
    }
    private String status(long orderId) { return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId); }

    @Test
    void sampleLoopAndCuttingGuard() {
        long cat = jdbc.queryForObject("SELECT id FROM tailor_categories ORDER BY level LIMIT 1", Long.class);
        long customer = user("c@flow.local", "CUSTOMER", "ACTIVE"), other = user("c2@flow.local", "CUSTOMER", "ACTIVE");
        long tailor = user("t@flow.local", "TAILOR", "APPROVED"), otherTailor = user("t2@flow.local", "TAILOR", "APPROVED");
        long orderId = jdbc.queryForObject("""
            INSERT INTO orders (order_number, user_id, status, payment_method, payment_status, tailor_category_id, tailor_category_name, tailor_price_per_garment, garment_count, subtotal, tailor_charges,
                tax_rate_percent, tax, delivery_charge, total, shipping_address, customer_name, customer_phone, tailor_id)
            VALUES ('ORD-FLOW-1', ?, 'FABRIC_SAMPLE_PENDING', 'COD', 'PENDING_COLLECTION', ?, 'Beginner', 300, 1, 1000, 300, 5, 65, 99, 1464, '{"city":"Chennai"}'::jsonb, 'Flow Customer', '+919999999999', ?) RETURNING id""", Long.class, customer, cat, tailor);

        // only the assigned tailor can send; a sample needs photos from our own storage
        assertThrows(BusinessException.class, () -> samples.submit(otherTailor, orderId, new SubmitRequest(List.of(PHOTO1), null)));
        assertThrows(BusinessException.class, () -> samples.submit(tailor, orderId, new SubmitRequest(List.of("https://evil.example/a.jpg"), null)));

        samples.submit(tailor, orderId, new SubmitRequest(List.of(PHOTO1), "Navy cotton"));
        assertEquals("CUSTOMER_APPROVAL_PENDING", status(orderId));
        long first = jdbc.queryForObject("SELECT id FROM fabric_samples WHERE order_id = ? AND revision = 1", Long.class, orderId);

        // cannot send another while one is waiting; a stranger cannot decide; cutting is impossible before approval
        assertThrows(BusinessException.class, () -> samples.submit(tailor, orderId, new SubmitRequest(List.of(PHOTO2), null)));
        assertThrows(BusinessException.class, () -> samples.approve(other, orderId, first));
        assertThrows(BusinessException.class, () -> orders.transition(orderId, OrderStatus.CUTTING_IN_PROGRESS, "too early", tailor));

        samples.reject(customer, orderId, first, "The colour looks too dark");
        assertEquals("FABRIC_REJECTED", status(orderId));
        assertThrows(BusinessException.class, () -> orders.transition(orderId, OrderStatus.CUTTING_IN_PROGRESS, "still rejected", tailor));
        assertThrows(BusinessException.class, () -> samples.approve(customer, orderId, first));          // already decided

        samples.submit(tailor, orderId, new SubmitRequest(List.of(PHOTO2), "Lighter navy"));
        long second = jdbc.queryForObject("SELECT id FROM fabric_samples WHERE order_id = ? AND revision = 2", Long.class, orderId);
        samples.approve(customer, orderId, second);
        assertEquals("FABRIC_APPROVED", status(orderId));
        orders.transition(orderId, OrderStatus.CUTTING_IN_PROGRESS, "start", tailor);                   // now allowed
        assertEquals("CUTTING_IN_PROGRESS", status(orderId));

        // full history with identities
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM fabric_approvals a JOIN fabric_samples s ON s.id = a.sample_id WHERE s.order_id = ?", Integer.class, orderId));
        assertEquals(2, samples.forCustomer(customer, orderId).size());
    }
}
