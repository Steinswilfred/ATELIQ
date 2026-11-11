package com.clothingplatform.payment;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.common.Sql;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.NotificationService;
import com.clothingplatform.finance.LedgerService;
import com.clothingplatform.finance.RefundService;
import com.clothingplatform.order.OrderService;
import com.clothingplatform.order.OrderStatus;
import com.clothingplatform.order.PricingCalculator;
import com.clothingplatform.payment.PaymentGateway.GatewayOrder;
import com.clothingplatform.payment.PaymentGateway.GatewayPayment;
import com.clothingplatform.security.CurrentUser;
import com.clothingplatform.settings.SettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Payment rules. Hard rules enforced here:
 *  - the amount comes from the stored order, never from the browser;
 *  - a payment is SUCCESS only after the HMAC signature is valid AND Razorpay itself reports the payment captured for that order and amount;
 *  - confirmation is idempotent (row lock + unique gateway ids), so the browser callback and the webhook can both arrive in any order and repeat safely.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public record RazorpayCheckout(String keyId, String razorpayOrderId, long amountPaise, String currency, String merchantName, String description, String prefillName, String prefillEmail, String prefillPhone) {}
    public record VerifyRequest(@NotBlank String razorpayOrderId, @NotBlank String razorpayPaymentId, @NotBlank String razorpaySignature) {}
    public record VerifyResult(String status, long orderId) {}
    public record PaymentRow(long id, long orderId, String orderNumber, String customerName, String method, String status, BigDecimal amount, String providerOrderId, String providerPaymentId, String instrument, String failureReason, Instant paidAt, Instant createdAt) {}
    public record TxEntry(String type, BigDecimal amount, String providerRef, String detail, Instant at) {}
    public record PaymentDetail(PaymentRow payment, List<TxEntry> history) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PaymentGateway gateway;
    private final OrderService orders;
    private final NotificationService notifications;
    private final AuditService audit;
    private final SettingsService settings;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final LedgerService ledger;
    private final RefundService refunds;
    private final String keySecret;
    private final String webhookSecret;

    public PaymentService(JdbcTemplate jdbc, TransactionTemplate tx, PaymentGateway gateway, OrderService orders, NotificationService notifications, AuditService audit, SettingsService settings, ObjectMapper mapper, ApplicationEventPublisher events, LedgerService ledger, RefundService refunds,
                          @Value("${app.razorpay.key-secret}") String keySecret, @Value("${app.razorpay.webhook-secret}") String webhookSecret) {
        this.jdbc = jdbc; this.tx = tx; this.gateway = gateway; this.orders = orders; this.notifications = notifications; this.audit = audit; this.settings = settings; this.mapper = mapper; this.events = events; this.ledger = ledger; this.refunds = refunds;
        this.keySecret = keySecret; this.webhookSecret = webhookSecret;
    }

    public boolean onlinePaymentsEnabled() { return gateway.isConfigured(); }

    // ---------------- starting an online payment ----------------

    /** Creates (or re-creates, for a retry) the Razorpay order for an unpaid online order and returns what the browser needs to open Checkout. */
    public RazorpayCheckout createGatewayOrder(long orderId, long userId) {
        if (!gateway.isConfigured()) throw new BusinessException("PAYMENTS_UNAVAILABLE", "Online payment is not available right now. Choose Cash on Delivery or try later.", HttpStatus.SERVICE_UNAVAILABLE);
        Map<String, Object> o = jdbc.queryForList("""
            SELECT o.order_number, o.total, o.status, o.payment_method, o.payment_status, o.customer_name, o.customer_phone, u.email
            FROM orders o JOIN users u ON u.id = o.user_id WHERE o.id = ? AND o.user_id = ?""", orderId, userId).stream().findFirst().orElseThrow(() -> notFound("Order"));
        if (!"RAZORPAY".equals(o.get("payment_method"))) throw bad("This order is not an online-payment order.");
        if (!OrderStatus.PAYMENT_PENDING.name().equals(o.get("status")) || "PAID".equals(o.get("payment_status"))) throw new BusinessException("ORDER_NOT_PAYABLE", "This order does not need a payment.", HttpStatus.CONFLICT);
        BigDecimal total = (BigDecimal) o.get("total");
        String number = (String) o.get("order_number");

        GatewayOrder g = gateway.createOrder(PricingCalculator.toPaise(total), "INR", number, Map.of("order_id", String.valueOf(orderId), "order_number", number));
        tx.executeWithoutResult(s -> {
            jdbc.update("UPDATE payments SET status = 'SUPERSEDED', updated_at = now() WHERE order_id = ? AND status = 'PENDING'", orderId);
            long pid = jdbc.queryForObject("INSERT INTO payments (order_id, user_id, method, status, amount, provider_order_id) VALUES (?,?,'RAZORPAY','PENDING',?,?) RETURNING id", Long.class, orderId, userId, total, g.id());
            logTx(pid, "ORDER_CREATED", total, g.id(), "Razorpay order created");
            jdbc.update("UPDATE orders SET payment_status = 'PENDING', updated_at = now() WHERE id = ?", orderId);
        });
        String merchant = Optional.ofNullable(settings.publicSettings().get("site.name")).map(n -> n.asText("Atelier")).orElse("Atelier");
        return new RazorpayCheckout(gateway.keyId(), g.id(), g.amountPaise(), g.currency(), merchant, "Order " + number, (String) o.get("customer_name"), (String) o.get("email"), (String) o.get("customer_phone"));
    }

    // ---------------- browser callback ----------------

    public VerifyResult verify(long userId, VerifyRequest r) {
        Map<String, Object> p = jdbc.queryForList("SELECT id, order_id, status FROM payments WHERE provider_order_id = ? AND user_id = ?", r.razorpayOrderId(), userId).stream().findFirst().orElseThrow(() -> notFound("Payment"));
        long paymentId = ((Number) p.get("id")).longValue(), orderId = ((Number) p.get("order_id")).longValue();
        if (!RazorpaySignature.verifyPayment(r.razorpayOrderId(), r.razorpayPaymentId(), r.razorpaySignature(), keySecret)) {
            tx.executeWithoutResult(s -> logTx(paymentId, "SIGNATURE_INVALID", null, r.razorpayPaymentId(), "Checkout signature did not match"));
            log.warn("Invalid Razorpay signature for order {}", r.razorpayOrderId());
            throw new BusinessException("PAYMENT_VERIFICATION_FAILED", "We could not verify this payment. If money was deducted, it will be refunded automatically or confirmed shortly.", HttpStatus.BAD_REQUEST);
        }
        if ("SUCCESS".equals(p.get("status"))) return new VerifyResult("SUCCESS", orderId);

        // The signature proves the callback is genuine; Razorpay's own record decides whether money was actually captured.
        GatewayPayment gp = gateway.fetchPayment(r.razorpayPaymentId());
        if (!r.razorpayOrderId().equals(gp.orderId())) throw new BusinessException("PAYMENT_VERIFICATION_FAILED", "This payment does not belong to this order.", HttpStatus.BAD_REQUEST);
        if ("authorized".equals(gp.status())) {
            gateway.capture(gp.id(), gp.amountPaise(), "INR");
            gp = gateway.fetchPayment(gp.id());
        }
        if ("captured".equals(gp.status())) {
            confirmSuccess(gp.orderId(), gp.id(), gp.method(), gp.amountPaise());
            return new VerifyResult("SUCCESS", orderId);
        }
        if ("failed".equals(gp.status())) {
            markFailed(gp.orderId(), gp.errorDescription());
            return new VerifyResult("FAILED", orderId);
        }
        return new VerifyResult("PENDING", orderId);   // still processing; the webhook will confirm
    }

    // ---------------- idempotent state changes (shared by browser callback and webhook) ----------------

    public void confirmSuccess(String razorpayOrderId, String razorpayPaymentId, String instrument, long amountPaise) {
        tx.executeWithoutResult(s -> {
            Map<String, Object> p = jdbc.queryForList("SELECT id, order_id, user_id, amount, status FROM payments WHERE provider_order_id = ? FOR UPDATE", razorpayOrderId).stream().findFirst()
                    .orElseThrow(() -> notFound("Payment"));
            long pid = ((Number) p.get("id")).longValue(), orderId = ((Number) p.get("order_id")).longValue(), userId = ((Number) p.get("user_id")).longValue();
            BigDecimal amount = (BigDecimal) p.get("amount");
            if ("SUCCESS".equals(p.get("status"))) return;                                  // already confirmed: repeats change nothing
            if (PricingCalculator.toPaise(amount) != amountPaise) {                          // never confirm an amount we did not ask for
                logTx(pid, "AMOUNT_MISMATCH", amount, razorpayPaymentId, "Gateway amount " + amountPaise + " paise, expected " + PricingCalculator.toPaise(amount));
                notifications.notifyPermissionHolders("PAYMENT_VIEW", "PAYMENT_MISMATCH", "Payment amount mismatch", "Order payment " + razorpayPaymentId + " does not match the order total. Review it.", "/admin/payments/" + pid);
                log.error("Amount mismatch for payment {}", pid);
                return;
            }
            jdbc.update("UPDATE payments SET status = 'SUCCESS', provider_payment_id = ?, payment_instrument = ?, paid_at = now(), failure_reason = NULL, updated_at = now() WHERE id = ?", razorpayPaymentId, instrument, pid);
            logTx(pid, "CAPTURED", amount, razorpayPaymentId, "Payment captured");
            ledger.record(orderId, LedgerService.CUSTOMER_PAYMENTS, amount, "PAYMENT", pid, "Online payment received");
            String status = jdbc.queryForObject("SELECT status FROM orders WHERE id = ? FOR UPDATE", String.class, orderId);
            if (OrderStatus.PAYMENT_PENDING.name().equals(status)) {
                jdbc.update("UPDATE orders SET payment_status = 'PAID', updated_at = now() WHERE id = ?", orderId);
                orders.transition(orderId, OrderStatus.PAYMENT_CONFIRMED, "Online payment verified", null);
                orders.transition(orderId, OrderStatus.TAILOR_ASSIGNMENT_PENDING, "Waiting for a tailor", null);
                orders.ensureInvoice(orderId);
                String number = jdbc.queryForObject("SELECT order_number FROM orders WHERE id = ?", String.class, orderId);
                orders.notifyPlaced(userId, orderId, number, amount, false);
                events.publishEvent(new OrderService.OrderReadyForAssignment(orderId));
            } else {
                // money arrived for an order that is no longer waiting for it (for example cancelled): flag for a refund decision
                logTx(pid, "PAYMENT_ON_CLOSED_ORDER", amount, razorpayPaymentId, "Order status was " + status);
                notifications.notifyPermissionHolders("PAYMENT_VIEW", "PAYMENT_NEEDS_REVIEW", "Payment received for a closed order", "Order status " + status + ". Consider a refund.", "/admin/payments/" + pid);
            }
        });
    }

    public void markFailed(String razorpayOrderId, String reason) {
        tx.executeWithoutResult(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("UPDATE payments SET status = 'FAILED', failure_reason = ?, updated_at = now() WHERE provider_order_id = ? AND status = 'PENDING' RETURNING id, order_id", truncate(reason), razorpayOrderId);
            for (Map<String, Object> row : rows) {
                long pid = ((Number) row.get("id")).longValue(), orderId = ((Number) row.get("order_id")).longValue();
                logTx(pid, "FAILED", null, razorpayOrderId, truncate(reason));
                jdbc.update("UPDATE orders SET payment_status = 'FAILED', updated_at = now() WHERE id = ? AND payment_status = 'PENDING'", orderId);
                notifications.notifyPermissionHolders("PAYMENT_VIEW", "PAYMENT_FAILED", "Payment failed", truncate(reason == null ? "The customer's payment did not go through." : reason), "/admin/payments/" + pid);
            }
        });
    }

    // ---------------- webhook ----------------

    /** @return false when the signature is invalid (caller answers 400); true when handled or safely ignored as a duplicate */
    public boolean handleWebhook(String rawBody, String signature, String eventIdHeader) {
        if (webhookSecret.isBlank()) throw new BusinessException("WEBHOOK_NOT_CONFIGURED", "Webhook secret is not configured.", HttpStatus.SERVICE_UNAVAILABLE);
        if (!RazorpaySignature.verifyWebhook(rawBody, signature, webhookSecret)) { log.warn("Rejected Razorpay webhook with invalid signature"); return false; }
        JsonNode event;
        try { event = mapper.readTree(rawBody); } catch (JsonProcessingException e) { return false; }
        String type = event.path("event").asText("");
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank() ? eventIdHeader : "body-" + sha256(rawBody);

        // One transaction: if processing fails the event row is rolled back too, so Razorpay's retry is processed again, not skipped as a duplicate.
        tx.executeWithoutResult(s -> {
            int inserted = jdbc.update("INSERT INTO payment_webhook_events (event_id, event_type, payload) VALUES (?, ?, ?::jsonb) ON CONFLICT (event_id) DO NOTHING", eventId, type, rawBody);
            if (inserted == 0) { log.info("Duplicate webhook {} ignored", eventId); return; }
            JsonNode pay = event.path("payload").path("payment").path("entity");
            JsonNode ord = event.path("payload").path("order").path("entity");
            String razorpayOrderId = !pay.path("order_id").isMissingNode() && !pay.path("order_id").isNull() ? pay.path("order_id").asText() : ord.path("id").asText(null);
            switch (type) {
                case "payment.captured", "order.paid" -> { if (razorpayOrderId != null && pay.hasNonNull("id")) confirmSuccess(razorpayOrderId, pay.get("id").asText(), pay.path("method").asText(null), pay.path("amount").asLong(-1)); }
                case "payment.failed" -> { if (razorpayOrderId != null) markFailed(razorpayOrderId, pay.path("error_description").asText(null)); }
                case "refund.created", "refund.processed", "refund.failed" -> refunds.onWebhook(type, event.path("payload").path("refund").path("entity"));
                default -> { /* other events are stored only */ }
            }
        });
        return true;
    }

    // ---------------- cash on delivery ----------------

    /** Only after the order is DELIVERED can the cash be recorded as collected. */
    public void collectCod(long paymentId) {
        long actor = CurrentUser.require().getId();
        tx.executeWithoutResult(s -> {
            Map<String, Object> p = jdbc.queryForList("SELECT order_id, method, status, amount FROM payments WHERE id = ? FOR UPDATE", paymentId).stream().findFirst().orElseThrow(() -> notFound("Payment"));
            if (!"COD".equals(p.get("method")) || !"PENDING_COLLECTION".equals(p.get("status"))) throw new BusinessException("INVALID_STATE", "This payment is not waiting for cash collection.", HttpStatus.CONFLICT);
            long orderId = ((Number) p.get("order_id")).longValue();
            String status = jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
            if (!OrderStatus.DELIVERED.name().equals(status)) throw new BusinessException("ORDER_NOT_DELIVERED", "Cash can be marked as collected only after the order is delivered.", HttpStatus.CONFLICT);
            jdbc.update("UPDATE payments SET status = 'SUCCESS', paid_at = now(), collected_by = ?, updated_at = now() WHERE id = ?", actor, paymentId);
            jdbc.update("UPDATE orders SET payment_status = 'PAID', updated_at = now() WHERE id = ?", orderId);
            logTx(paymentId, "COD_COLLECTED", (BigDecimal) p.get("amount"), null, "Collected, recorded by admin " + actor);
            ledger.record(orderId, LedgerService.CUSTOMER_PAYMENTS, (BigDecimal) p.get("amount"), "PAYMENT", paymentId, "Cash on delivery collected");
            audit.audit(actor, "COD_COLLECTED", "PAYMENT", paymentId, "PENDING_COLLECTION", "SUCCESS", null);
        });
    }

    // ---------------- admin views ----------------

    private static final String ROW = """
        SELECT p.id, p.order_id, o.order_number, o.customer_name, p.method, p.status, p.amount, p.provider_order_id, p.provider_payment_id, p.payment_instrument, p.failure_reason, p.paid_at, p.created_at
        FROM payments p JOIN orders o ON o.id = p.order_id WHERE 1 = 1""";
    private static PaymentRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PaymentRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getBigDecimal(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), Sql.ts(rs, "paid_at"), Sql.ts(rs, "created_at"));
    }

    public PageResult<PaymentRow> list(String status, String method, String q, int page, int size) {
        StringBuilder where = new StringBuilder(); List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) { if (!Set.of("PENDING", "PENDING_COLLECTION", "SUCCESS", "FAILED", "SUPERSEDED").contains(status)) throw bad("Unknown status."); where.append(" AND p.status = ?"); args.add(status); }
        if (method != null && !method.isBlank()) { if (!Set.of("RAZORPAY", "COD").contains(method)) throw bad("Unknown method."); where.append(" AND p.method = ?"); args.add(method); }
        if (q != null && !q.isBlank()) { where.append(" AND (o.order_number ILIKE ? OR p.provider_order_id ILIKE ? OR p.provider_payment_id ILIKE ?)"); String like = "%" + q.trim() + "%"; args.add(like); args.add(like); args.add(like); }
        int pg = Sql.clampPage(page), sz = Sql.clampSize(size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM payments p JOIN orders o ON o.id = p.order_id WHERE 1 = 1" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(sz); pageArgs.add(pg * sz);
        return new PageResult<>(jdbc.query(ROW + where + " ORDER BY p.created_at DESC LIMIT ? OFFSET ?", (rs, i) -> row(rs), pageArgs.toArray()), total == null ? 0 : total, pg, sz);
    }

    public PaymentDetail detail(long id) {
        List<PaymentRow> r = jdbc.query(ROW + " AND p.id = ?", (rs, i) -> row(rs), id);
        if (r.isEmpty()) throw notFound("Payment");
        List<TxEntry> h = jdbc.query("SELECT type, amount, provider_ref, detail, created_at FROM payment_transactions WHERE payment_id = ? ORDER BY created_at, id",
                (rs, i) -> new TxEntry(rs.getString(1), rs.getBigDecimal(2), rs.getString(3), rs.getString(4), Sql.ts(rs, "created_at")), id);
        return new PaymentDetail(r.get(0), h);
    }

    private void logTx(long paymentId, String type, BigDecimal amount, String ref, String detail) {
        jdbc.update("INSERT INTO payment_transactions (payment_id, type, amount, provider_ref, detail) VALUES (?,?,?,?,?)", paymentId, type, amount, ref, truncate(detail));
    }
    private static String truncate(String s) { return s == null ? null : s.substring(0, Math.min(300, s.length())); }
    private static String sha256(String s) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
