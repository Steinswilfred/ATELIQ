package com.clothingplatform.fulfilment;

import static org.junit.jupiter.api.Assertions.*;

import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.fulfilment.DeliveryService.EventRequest;
import com.clothingplatform.fulfilment.DeliveryService.ShipRequest;
import com.clothingplatform.fulfilment.DeliveryService.StatusRequest;
import com.clothingplatform.fulfilment.ProgressService.ProgressRequest;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.notification.SmsSender;
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

/** Production steps and delivery on a real PostgreSQL: right order, right person, photos required. Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"app.jwt.secret=0123456789abcdef0123456789abcdef"})
class FulfilmentFlowIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16");

    @Autowired ProgressService progress;
    @Autowired DeliveryService delivery;
    @Autowired JdbcTemplate jdbc;
    @MockBean EmailSender email;
    @MockBean SmsSender sms;

    private long user(String e, String type, String status) {
        return jdbc.queryForObject("INSERT INTO users (email, password_hash, full_name, user_type, account_status, email_verified, phone_verified) VALUES (?, 'x', ?, ?, ?, TRUE, TRUE) RETURNING id", Long.class, e, e, type, status);
    }
    private String status(long id) { return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, id); }
    private ProgressRequest step(String s) { return new ProgressRequest(s, null, null); }

    @Test
    void cutStitchCheckDispatchShipDeliver() {
        long cat = jdbc.queryForObject("SELECT id FROM tailor_categories ORDER BY level LIMIT 1", Long.class);
        long customer = user("c@ful.local", "CUSTOMER", "ACTIVE"), tailor = user("t@ful.local", "TAILOR", "APPROVED"), other = user("t2@ful.local", "TAILOR", "APPROVED"), admin = user("a@ful.local", "ADMIN", "ACTIVE");
        long orderId = jdbc.queryForObject("""
            INSERT INTO orders (order_number, user_id, status, payment_method, payment_status, tailor_category_id, tailor_category_name, tailor_price_per_garment, garment_count, subtotal, tailor_charges,
                tax_rate_percent, tax, delivery_charge, total, shipping_address, customer_name, customer_phone, tailor_id)
            VALUES ('ORD-FUL-1', ?, 'CUSTOMER_APPROVAL_PENDING', 'COD', 'PENDING_COLLECTION', ?, 'Beginner', 300, 1, 1000, 300, 5, 65, 99, 1464, '{"city":"Chennai"}'::jsonb, 'Ful Customer', '+919999999999', ?) RETURNING id""", Long.class, customer, cat, tailor);

        // the fabric has not been approved yet: no production step is possible
        assertThrows(BusinessException.class, () -> progress.advance(tailor, orderId, step("CUTTING_IN_PROGRESS")));
        jdbc.update("UPDATE orders SET status = 'FABRIC_APPROVED' WHERE id = ?", orderId);

        assertThrows(BusinessException.class, () -> progress.advance(other, orderId, step("CUTTING_IN_PROGRESS")));     // not their order
        assertThrows(BusinessException.class, () -> progress.advance(tailor, orderId, step("STITCHING_IN_PROGRESS")));   // cannot skip cutting
        progress.advance(tailor, orderId, step("CUTTING_IN_PROGRESS"));
        progress.advance(tailor, orderId, step("STITCHING_IN_PROGRESS"));
        progress.advance(tailor, orderId, step("QUALITY_CHECK"));
        assertThrows(BusinessException.class, () -> progress.advance(tailor, orderId, step("READY_FOR_DISPATCH")));      // photos required
        String photo = "/media/completed/123e4567-e89b-12d3-a456-426614174111.jpg";
        progress.advance(tailor, orderId, new ProgressRequest("READY_FOR_DISPATCH", "Pressed and packed", List.of(photo)));
        assertEquals("READY_FOR_DISPATCH", status(orderId));
        assertEquals(1, jdbc.queryForObject("SELECT jsonb_array_length(photos) FROM order_status_history WHERE order_id = ? AND status = 'READY_FOR_DISPATCH'", Integer.class, orderId));

        // delivery is the admin's job, in order
        assertThrows(BusinessException.class, () -> delivery.update(admin, orderId, new StatusRequest("DELIVERED", null, null)));       // not shipped
        assertThrows(BusinessException.class, () -> delivery.ship(admin, orderId, new ShipRequest("DTDC", "X1", "http://insecure.example", null, null)));  // https only
        delivery.ship(admin, orderId, new ShipRequest("DTDC", "X123", "https://track.example/X123", null, null));
        assertEquals("SHIPPED", status(orderId));
        assertThrows(BusinessException.class, () -> delivery.update(admin, orderId, new StatusRequest("DELIVERED", null, null)));       // must go out for delivery first
        delivery.addEvent(admin, orderId, new EventRequest("Reached the Chennai hub", "Chennai"));
        delivery.update(admin, orderId, new StatusRequest("OUT_FOR_DELIVERY", null, "Chennai"));
        delivery.update(admin, orderId, new StatusRequest("DELIVERED", "Handed to customer", null));
        assertEquals("DELIVERED", status(orderId));
        var view = delivery.forCustomer(customer, orderId);
        assertTrue(view.shipped());
        assertNotNull(view.deliveredAt());
        assertEquals(4, view.events().size());                                                       // shipped, hub update, out for delivery, delivered
        assertThrows(BusinessException.class, () -> delivery.forCustomer(other, orderId));          // someone else's order
    }
}
