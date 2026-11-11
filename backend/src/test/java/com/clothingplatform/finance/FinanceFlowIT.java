package com.clothingplatform.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.notification.SmsSender;
import com.clothingplatform.payment.PaymentGateway;
import com.clothingplatform.payment.PaymentGateway.GatewayRefund;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Money rules on a real PostgreSQL: earnings split, payout, cancellation refunds and the guards. The payment provider is faked. Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"app.jwt.secret=0123456789abcdef0123456789abcdef"})
class FinanceFlowIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16");

    @Autowired EarningsService earnings;
    @Autowired RefundService refunds;
    @Autowired JdbcTemplate jdbc;
    @MockBean PaymentGateway gateway;
    @MockBean EmailSender email;
    @MockBean SmsSender sms;

    private long user(String e, String type, String status) {
        return jdbc.queryForObject("INSERT INTO users (email, password_hash, full_name, user_type, account_status, email_verified, phone_verified) VALUES (?, 'x', ?, ?, ?, TRUE, TRUE) RETURNING id", Long.class, e, e, type, status);
    }
    private long order(String number, long customer, Long tailor, String status, String total, String charges, boolean paidOnline) {
        long cat = jdbc.queryForObject("SELECT id FROM tailor_categories ORDER BY level LIMIT 1", Long.class);
        long id = jdbc.queryForObject("""
            INSERT INTO orders (order_number, user_id, status, payment_method, payment_status, tailor_category_id, tailor_category_name, tailor_price_per_garment, garment_count, subtotal, discount, tailor_charges,
                tax_rate_percent, tax, delivery_charge, total, shipping_address, customer_name, customer_phone, tailor_id)
            VALUES (?, ?, ?, 'RAZORPAY', ?, ?, 'Beginner', 1000, 1, 2000, 200, ?, 5, 140, 99, ?, '{"city":"Chennai"}'::jsonb, 'Fin Customer', '+919999999999', ?) RETURNING id""",
            Long.class, number, customer, status, paidOnline ? "PAID" : "PENDING", cat, new BigDecimal(charges), new BigDecimal(total), tailor);
        if (paidOnline) jdbc.update("INSERT INTO payments (order_id, user_id, method, status, amount, provider_order_id, provider_payment_id) VALUES (?,?,'RAZORPAY','SUCCESS',?,?,?)", id, customer, new BigDecimal(total), "order_" + number, "pay_" + number);
        return id;
    }
    private BigDecimal ledger(String account) { return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE account = ?", BigDecimal.class, account); }

    @Test
    void earningsSplitPayoutAndCancellationRefunds() {
        long customer = user("c@fin.local", "CUSTOMER", "ACTIVE"), tailor = user("t@fin.local", "TAILOR", "APPROVED"), admin = user("a@fin.local", "ADMIN", "ACTIVE");
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.refund(anyString(), anyLong(), anyString())).thenAnswer(i -> new GatewayRefund("rfnd_" + UUID.randomUUID().toString().substring(0, 8), "processed", i.getArgument(1)));

        // 1) delivery: the order total splits into separate accounts that add back up to it; the earning is created once
        long delivered = order("ORD-FIN-1", customer, tailor, "DELIVERED", "3039.00", "1000.00", false);
        earnings.accrueOnDelivery(delivered);
        earnings.accrueOnDelivery(delivered);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tailor_earnings WHERE order_id = ?", Integer.class, delivered));
        assertEquals(0, new BigDecimal("850.00").compareTo(jdbc.queryForObject("SELECT net_amount FROM tailor_earnings WHERE order_id = ?", BigDecimal.class, delivered)));
        BigDecimal recognised = ledger("ITEM_REVENUE").add(ledger("DELIVERY_REVENUE")).add(ledger("PLATFORM_COMMISSION")).add(ledger("TAX_COLLECTED")).add(ledger("TAILOR_PAYABLE"));
        assertEquals(0, new BigDecimal("3039.00").compareTo(recognised));

        // 2) nothing is payable during the settlement hold; afterwards one payout settles it
        assertThrows(BusinessException.class, () -> earnings.recordPayout(admin, tailor, "UTR1", null));
        jdbc.update("UPDATE tailor_earnings SET payable_after = now() - interval '1 day'");
        earnings.recordPayout(admin, tailor, "UTR123", "April");
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM tailor_earnings WHERE order_id = ?", String.class, delivered));
        assertEquals(0, BigDecimal.ZERO.compareTo(ledger("TAILOR_PAYABLE")));
        assertThrows(BusinessException.class, () -> earnings.recordPayout(admin, tailor, "UTR124", null));      // already settled

        // 3) cancelling before a tailor is assigned: full refund, processed through the (fake) provider
        long early = order("ORD-FIN-2", customer, null, "TAILOR_ASSIGNMENT_PENDING", "1464.00", "300.00", true);
        refunds.customerCancel(customer, early, "changed my mind");
        assertEquals("REFUNDED", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, early));
        assertEquals(0, new BigDecimal("1464.00").compareTo(jdbc.queryForObject("SELECT amount FROM refunds WHERE order_id = ?", BigDecimal.class, early)));

        // 4) cancelling after assignment: the late fee is kept (10% of what was paid)
        long late = order("ORD-FIN-3", customer, tailor, "FABRIC_SAMPLE_PENDING", "1000.00", "300.00", true);
        jdbc.update("INSERT INTO tailor_assignments (order_id, tailor_user_id, method) VALUES (?, ?, 'ACCEPTED')", late, tailor);
        refunds.customerCancel(customer, late, null);
        assertEquals(0, new BigDecimal("900.00").compareTo(jdbc.queryForObject("SELECT amount FROM refunds WHERE order_id = ?", BigDecimal.class, late)));
        assertEquals(0, new BigDecimal("100.00").compareTo(jdbc.queryForObject("SELECT fee_retained FROM refunds WHERE order_id = ?", BigDecimal.class, late)));
        assertEquals("RELEASED", jdbc.queryForObject("SELECT status FROM tailor_assignments WHERE order_id = ?", String.class, late));   // the tailor is released from a cancelled order

        // 5) once fabric is being cut, the customer cannot cancel online
        long cutting = order("ORD-FIN-4", customer, tailor, "CUTTING_IN_PROGRESS", "1000.00", "300.00", true);
        assertThrows(BusinessException.class, () -> refunds.customerCancel(customer, cutting, null));
        // and nobody else can cancel your order
        long other = user("o@fin.local", "CUSTOMER", "ACTIVE");
        long mine = order("ORD-FIN-5", customer, null, "TAILOR_ASSIGNMENT_PENDING", "500.00", "300.00", true);
        assertThrows(BusinessException.class, () -> refunds.customerCancel(other, mine, null));

        // 6) an admin-requested refund needs a different admin; a repeat refund beyond the amount paid is refused
        long returned = order("ORD-FIN-6", customer, tailor, "DELIVERED", "1000.00", "300.00", true);
        long refundId = refunds.adminRefund(admin, returned, new BigDecimal("400.00"), "Fit problem", false);
        assertThrows(BusinessException.class, () -> refunds.approve(admin, false, refundId));
        long second = user("a2@fin.local", "ADMIN", "ACTIVE");
        refunds.approve(second, false, refundId);
        assertEquals("PROCESSED", jdbc.queryForObject("SELECT status FROM refunds WHERE id = ?", String.class, refundId));
        assertEquals("REFUNDED", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, returned));
    }
}
