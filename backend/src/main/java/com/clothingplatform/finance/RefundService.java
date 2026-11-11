package com.clothingplatform.finance;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.common.Sql;
import com.clothingplatform.coupon.CouponService;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.NotificationService;
import com.clothingplatform.order.OrderService;
import com.clothingplatform.order.OrderStatus;
import com.clothingplatform.order.PricingCalculator;
import com.clothingplatform.payment.PaymentGateway;
import com.clothingplatform.payment.PaymentGateway.GatewayRefund;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancellations and refunds. Rules:
 *  - customers cancel under {@link CancellationPolicy}; their refunds can be auto-approved (a setting);
 *  - admin-initiated refunds are REQUESTED and need approval from a DIFFERENT administrator (a Super Admin may approve their own);
 *  - the gateway is called outside any database transaction, after the refund is marked PROCESSING with a conditional update, so it cannot be sent twice;
 *  - Razorpay's refund webhooks finish pending refunds; cash-on-delivery refunds are paid by hand and recorded with a reference;
 *  - one open refund per order at a time (database index).
 */
@Service
public class RefundService {
    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    public record CancelPreview(boolean allowed, String message, BigDecimal paid, BigDecimal refund, BigDecimal fee, String stage) {}
    public record RefundView(long id, long orderId, String orderNumber, String kind, BigDecimal amount, BigDecimal feeRetained, String status, String method, String reason, String requestedBy, String approvedBy,
                             String failureReason, boolean reverseTailorEarning, Instant createdAt, Instant processedAt) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OrderService orders;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CouponService coupons;
    private final LedgerService ledger;
    private final PaymentGateway gateway;
    private final FinanceSettings.Loader settings;

    public RefundService(JdbcTemplate jdbc, TransactionTemplate tx, OrderService orders, NotificationService notifications, AuditService audit, CouponService coupons, LedgerService ledger, PaymentGateway gateway, FinanceSettings.Loader settings) {
        this.jdbc = jdbc; this.tx = tx; this.orders = orders; this.notifications = notifications; this.audit = audit; this.coupons = coupons; this.ledger = ledger; this.gateway = gateway; this.settings = settings;
    }

    // ---------------- customer ----------------

    public CancelPreview preview(long customerId, long orderId) {
        Map<String, Object> o = jdbc.queryForList("SELECT status FROM orders WHERE id = ? AND user_id = ?", orderId, customerId).stream().findFirst().orElseThrow(() -> notFound("Order"));
        var d = CancellationPolicy.forCustomer(OrderStatus.valueOf((String) o.get("status")), settings.load().lateCancellationFeePercent());
        BigDecimal paid = paidAmount(orderId);
        BigDecimal refund = d.customerMayCancel() && paid.signum() > 0 ? CancellationPolicy.refundAmount(paid, d.refundPercent()) : BigDecimal.ZERO.setScale(2);
        String msg = !d.customerMayCancel() ? "Your garment is already being made, so it can no longer be cancelled online. Please contact us if you need help."
                : paid.signum() == 0 ? "This order has not been paid, so nothing will be charged or refunded."
                : refund.compareTo(paid) == 0 ? "You will be refunded the full amount you paid." : "A late-cancellation fee applies because a tailor has already been assigned.";
        return new CancelPreview(d.customerMayCancel(), msg, paid, refund, paid.subtract(refund), d.stage());
    }

    public void customerCancel(long customerId, long orderId, String reason) {
        long[] refundId = {0};
        boolean[] auto = {false};
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = jdbc.queryForList("SELECT status, tailor_id, order_number FROM orders WHERE id = ? AND user_id = ? FOR UPDATE", orderId, customerId).stream().findFirst().orElseThrow(() -> notFound("Order"));
            FinanceSettings f = settings.load();
            var d = CancellationPolicy.forCustomer(OrderStatus.valueOf((String) o.get("status")), f.lateCancellationFeePercent());
            if (!d.customerMayCancel()) throw new BusinessException("CANCEL_NOT_ALLOWED", "This order can no longer be cancelled online. Please contact us.", HttpStatus.CONFLICT);
            BigDecimal paid = paidAmount(orderId);
            String note = "Cancelled by the customer" + (reason == null || reason.isBlank() ? "" : ": " + reason.trim());
            closeOrder(orderId, o, note, customerId, paid.signum() > 0);
            BigDecimal amount = paid.signum() > 0 ? CancellationPolicy.refundAmount(paid, d.refundPercent()) : BigDecimal.ZERO;
            if (amount.signum() > 0) {
                auto[0] = f.autoApproveCustomerRefunds();
                refundId[0] = createRefund(orderId, "CANCELLATION", amount, paid.subtract(amount), auto[0] ? "APPROVED" : "REQUESTED", note, customerId, false);
                orders.transition(orderId, OrderStatus.REFUND_PENDING, "Refund of ₹" + amount + " started", null);
                if (!auto[0]) notifications.notifyPermissionHolders("REFUND_APPROVE", "REFUND_TO_APPROVE", "Refund to approve", "Order " + o.get("order_number") + ": ₹" + amount + " after a customer cancellation.", "/admin/refunds");
            }
            notifications.notifyUser(customerId, "ORDER_CANCELLED", "Order " + o.get("order_number") + " cancelled", amount.signum() > 0 ? "Your refund of ₹" + amount + " is being processed." : "Nothing was charged.", "/account/orders/" + orderId);
            audit.audit(customerId, "ORDER_CANCELLED_BY_CUSTOMER", "ORDER", orderId, o.get("status"), "CANCELLED", reason);
        });
        if (refundId[0] > 0 && auto[0]) process(refundId[0]);
    }

    public List<RefundView> forCustomer(long customerId, long orderId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM orders WHERE id = ? AND user_id = ?", Integer.class, orderId, customerId);
        if (n == null || n == 0) throw notFound("Order");
        return query(" WHERE r.order_id = ?", orderId);
    }

    // ---------------- admin ----------------

    /** Cancels an order that has not shipped. refundAmount null means the full amount paid; a refund still needs a second admin's approval. */
    public void adminCancel(long adminId, long orderId, String reason, BigDecimal refundAmount, boolean mayInitiateRefund) {
        if (reason == null || reason.trim().length() < 5 || reason.trim().length() > 400) throw bad("Give a reason (5 to 400 characters).");
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = jdbc.queryForList("SELECT status, tailor_id, order_number, user_id FROM orders WHERE id = ? FOR UPDATE", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
            BigDecimal paid = paidAmount(orderId);
            BigDecimal amount = refundAmount == null ? paid : refundAmount;
            if (amount.signum() < 0 || amount.compareTo(available(orderId)) > 0) throw bad("The refund cannot be more than the amount paid.");
            if (amount.signum() > 0 && !mayInitiateRefund) throw new BusinessException("ACCESS_DENIED", "You do not have permission to initiate refunds.", HttpStatus.FORBIDDEN);
            closeOrder(orderId, o, "Cancelled by an administrator: " + reason.trim(), adminId, paid.signum() > 0);
            if (amount.signum() > 0) {
                createRefund(orderId, "ADMIN", amount, paid.subtract(amount), "REQUESTED", reason.trim(), adminId, false);
                orders.transition(orderId, OrderStatus.REFUND_PENDING, "Refund of ₹" + amount + " awaiting approval", adminId);
                notifications.notifyPermissionHolders("REFUND_APPROVE", "REFUND_TO_APPROVE", "Refund to approve", "Order " + o.get("order_number") + ": ₹" + amount + ".", "/admin/refunds");
            }
            notifications.notifyUser(((Number) o.get("user_id")).longValue(), "ORDER_CANCELLED", "Order " + o.get("order_number") + " cancelled", "Your order was cancelled by our team." + (amount.signum() > 0 ? " A refund of ₹" + amount + " is being arranged." : ""), "/account/orders/" + orderId);
            audit.audit(adminId, "ORDER_CANCELLED_BY_ADMIN", "ORDER", orderId, o.get("status"), "CANCELLED", reason.trim());
        });
    }

    /** A refund for a delivered (or already cancelled and paid) order, for example a return. */
    public long adminRefund(long adminId, long orderId, BigDecimal amount, String reason, boolean reverseTailorEarning) {
        if (reason == null || reason.trim().length() < 5 || reason.trim().length() > 400) throw bad("Give a reason (5 to 400 characters).");
        long[] id = new long[1];
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = jdbc.queryForList("SELECT status, order_number, user_id FROM orders WHERE id = ? FOR UPDATE", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
            String st = (String) o.get("status");
            if (!"DELIVERED".equals(st) && !"CANCELLED".equals(st)) throw new BusinessException("REFUND_NOT_ALLOWED", "Refunds are issued for delivered or cancelled orders. Cancel the order first if it has not shipped.", HttpStatus.CONFLICT);
            BigDecimal avail = available(orderId);
            if (amount == null || amount.signum() <= 0 || amount.compareTo(avail) > 0) throw bad("Enter an amount between 0.01 and ₹" + avail + ".");
            id[0] = createRefund(orderId, "ADMIN", amount, BigDecimal.ZERO, "REQUESTED", reason.trim(), adminId, reverseTailorEarning);
            orders.transition(orderId, OrderStatus.REFUND_PENDING, "Refund of ₹" + amount + " awaiting approval", adminId);
            notifications.notifyPermissionHolders("REFUND_APPROVE", "REFUND_TO_APPROVE", "Refund to approve", "Order " + o.get("order_number") + ": ₹" + amount + ".", "/admin/refunds");
            audit.audit(adminId, "REFUND_REQUESTED", "REFUND", id[0], null, Map.of("orderId", orderId, "amount", amount), reason.trim());
        });
        return id[0];
    }

    public void approve(long adminId, boolean isSuperAdmin, long refundId) {
        tx.executeWithoutResult(s -> {
            Map<String, Object> r = lock(refundId);
            if (!"REQUESTED".equals(r.get("status"))) throw new BusinessException("INVALID_STATE", "Only a requested refund can be approved.", HttpStatus.CONFLICT);
            if (((Number) r.get("requested_by")).longValue() == adminId && !isSuperAdmin)
                throw new BusinessException("SELF_APPROVAL", "A different administrator must approve a refund you requested.", HttpStatus.FORBIDDEN);
            jdbc.update("UPDATE refunds SET status = 'APPROVED', approved_by = ?, updated_at = now() WHERE id = ?", adminId, refundId);
            audit.audit(adminId, "REFUND_APPROVED", "REFUND", refundId, "REQUESTED", "APPROVED", null);
        });
        process(refundId);
    }

    public void reject(long adminId, long refundId, String reason) {
        if (reason == null || reason.trim().length() < 5 || reason.trim().length() > 300) throw bad("Give a reason (5 to 300 characters).");
        tx.executeWithoutResult(s -> {
            Map<String, Object> r = lock(refundId);
            String st = (String) r.get("status");
            if (!Set.of("REQUESTED", "APPROVED", "FAILED").contains(st)) throw new BusinessException("INVALID_STATE", "This refund can no longer be rejected.", HttpStatus.CONFLICT);
            long orderId = ((Number) r.get("order_id")).longValue();
            jdbc.update("UPDATE refunds SET status = 'REJECTED', failure_reason = ?, approved_by = ?, updated_at = now() WHERE id = ?", reason.trim(), adminId, refundId);
            String os = jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
            if ("REFUND_PENDING".equals(os)) orders.transition(orderId, wasDelivered(orderId) ? OrderStatus.DELIVERED : OrderStatus.CANCELLED, "Refund declined: " + reason.trim(), adminId);
            long customer = jdbc.queryForObject("SELECT user_id FROM orders WHERE id = ?", Long.class, orderId);
            notifications.notifyUser(customer, "REFUND_DECLINED", "Refund not approved", reason.trim() + " Please contact us if you have questions.", "/account/orders/" + orderId);
            audit.audit(adminId, "REFUND_REJECTED", "REFUND", refundId, st, "REJECTED", reason.trim());
        });
    }

    public void retry(long adminId, long refundId) {
        tx.executeWithoutResult(s -> {
            Map<String, Object> r = lock(refundId);
            if (!"FAILED".equals(r.get("status"))) throw new BusinessException("INVALID_STATE", "Only a failed refund can be retried.", HttpStatus.CONFLICT);
            jdbc.update("UPDATE refunds SET status = 'APPROVED', failure_reason = NULL, provider_refund_id = NULL, updated_at = now() WHERE id = ?", refundId);
            audit.audit(adminId, "REFUND_RETRIED", "REFUND", refundId, "FAILED", "APPROVED", null);
        });
        process(refundId);
    }

    /** For refunds paid by hand (cash-on-delivery orders): record the bank transfer reference. */
    public void manualProcessed(long adminId, long refundId, String reference) {
        if (reference == null || reference.trim().length() < 3 || reference.trim().length() > 100) throw bad("Enter the payment reference (3 to 100 characters).");
        tx.executeWithoutResult(s -> {
            Map<String, Object> r = lock(refundId);
            if (!"APPROVED".equals(r.get("status")) || !"MANUAL".equals(r.get("method"))) throw new BusinessException("INVALID_STATE", "Only an approved manual refund can be recorded as paid.", HttpStatus.CONFLICT);
            jdbc.update("UPDATE refunds SET manual_reference = ? WHERE id = ?", reference.trim(), refundId);
        });
        finalizeRefund(refundId);
        audit.audit(adminId, "REFUND_MANUALLY_PAID", "REFUND", refundId, "APPROVED", "PROCESSED", reference.trim());
    }

    public PageResult<RefundView> list(String status, int page, int size) {
        if (status != null && !status.isBlank() && !Set.of("REQUESTED", "APPROVED", "PROCESSING", "PROCESSED", "FAILED", "REJECTED").contains(status)) throw bad("Unknown status.");
        boolean f = status != null && !status.isBlank();
        int p = Sql.clampPage(page), sz = Sql.clampSize(size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM refunds r" + (f ? " WHERE r.status = ?" : ""), Long.class, f ? new Object[]{status} : new Object[0]);
        List<RefundView> items = f ? query(" WHERE r.status = ? ORDER BY r.created_at DESC LIMIT " + sz + " OFFSET " + (p * sz), status) : query(" ORDER BY r.created_at DESC LIMIT " + sz + " OFFSET " + (p * sz));
        return new PageResult<>(items, total == null ? 0 : total, p, sz);
    }

    // ---------------- processing ----------------

    /** Sends an approved refund to the gateway (outside any transaction) or leaves a manual refund waiting for the admin. */
    public void process(long refundId) {
        Map<String, Object> r = jdbc.queryForList("SELECT r.order_id, r.amount, r.method, r.status, p.provider_payment_id FROM refunds r LEFT JOIN payments p ON p.id = r.payment_id WHERE r.id = ?", refundId).stream().findFirst().orElse(null);
        if (r == null || !"APPROVED".equals(r.get("status"))) return;
        if ("MANUAL".equals(r.get("method"))) {
            notifications.notifyPermissionHolders("REFUND_APPROVE", "MANUAL_REFUND_DUE", "Manual refund to pay", "Pay ₹" + r.get("amount") + " to the customer, then record the reference.", "/admin/refunds");
            return;
        }
        if (!gateway.isConfigured() || r.get("provider_payment_id") == null) { fail(refundId, "The payment provider is not configured or the payment id is missing."); return; }
        int claimed = jdbc.update("UPDATE refunds SET status = 'PROCESSING', updated_at = now() WHERE id = ? AND status = 'APPROVED'", refundId);
        if (claimed == 0) return;                                   // someone else is already sending it
        try {
            GatewayRefund g = gateway.refund((String) r.get("provider_payment_id"), PricingCalculator.toPaise((BigDecimal) r.get("amount")), "refund-" + refundId);
            jdbc.update("UPDATE refunds SET provider_refund_id = ?, updated_at = now() WHERE id = ?", g.id(), refundId);
            if ("processed".equals(g.status())) finalizeRefund(refundId);          // otherwise the refund.processed webhook finishes it
        } catch (BusinessException e) {
            fail(refundId, "The payment provider could not be reached or refused the refund. You can retry.");
        }
    }

    private void fail(long refundId, String reason) {
        jdbc.update("UPDATE refunds SET status = 'FAILED', failure_reason = ?, updated_at = now() WHERE id = ?", reason, refundId);
        notifications.notifyPermissionHolders("REFUND_APPROVE", "REFUND_FAILED", "A refund failed", reason, "/admin/refunds");
        log.warn("Refund {} failed: {}", refundId, reason);
    }

    /** Idempotent: money effects and order status change exactly once, however many times this is reached. */
    public void finalizeRefund(long refundId) {
        tx.executeWithoutResult(s -> {
            Map<String, Object> r = lock(refundId);
            String st = (String) r.get("status");
            if ("PROCESSED".equals(st)) return;
            if (!Set.of("PROCESSING", "APPROVED", "FAILED").contains(st)) { log.warn("Refund {} in status {} cannot be finalised", refundId, st); return; }
            long orderId = ((Number) r.get("order_id")).longValue();
            BigDecimal amount = (BigDecimal) r.get("amount");
            jdbc.update("UPDATE refunds SET status = 'PROCESSED', processed_at = now(), failure_reason = NULL, updated_at = now() WHERE id = ?", refundId);
            ledger.record(orderId, LedgerService.CUSTOMER_PAYMENTS, amount.negate(), "REFUND", refundId, "Refund paid to the customer");
            if (wasDelivered(orderId)) ledger.record(orderId, LedgerService.REVENUE_REFUNDED, amount, "REFUND", refundId, "Refund after delivery (deducted from revenue)");
            if (Boolean.TRUE.equals(r.get("reverse_tailor_earning"))) reverseEarning(orderId, refundId);
            jdbc.update("UPDATE orders SET payment_status = 'REFUNDED', updated_at = now() WHERE id = ?", orderId);
            String os = jdbc.queryForObject("SELECT status FROM orders WHERE id = ? FOR UPDATE", String.class, orderId);
            if ("REFUND_PENDING".equals(os)) orders.transition(orderId, OrderStatus.REFUNDED, "Refund of ₹" + amount + " processed", null);
            long customer = jdbc.queryForObject("SELECT user_id FROM orders WHERE id = ?", Long.class, orderId);
            notifications.notifyUser(customer, "REFUND_PROCESSED", "Refund of ₹" + amount + " processed", "It can take a few days to show in your account.", "/account/orders/" + orderId);
        });
    }

    private void reverseEarning(long orderId, long refundId) {
        List<Map<String, Object>> e = jdbc.queryForList("SELECT id, status, net_amount FROM tailor_earnings WHERE order_id = ? FOR UPDATE", orderId);
        if (e.isEmpty()) return;
        Map<String, Object> row = e.get(0);
        if ("ACCRUED".equals(row.get("status"))) {
            jdbc.update("UPDATE tailor_earnings SET status = 'REVERSED', reversed_at = now(), reversal_reason = 'Order refunded' WHERE id = ?", row.get("id"));
            ledger.record(orderId, LedgerService.TAILOR_PAYABLE, ((BigDecimal) row.get("net_amount")).negate(), "REFUND", refundId, "Tailor earning reversed");
        } else if ("PAID".equals(row.get("status"))) {
            jdbc.update("UPDATE tailor_earnings SET needs_recovery = TRUE WHERE id = ?", row.get("id"));
            notifications.notifyPermissionHolders("EARNINGS_MANAGE", "EARNING_RECOVERY", "Tailor already paid for a refunded order", "Recover ₹" + row.get("net_amount") + " from the tailor outside the system.", "/admin/earnings");
        }
    }

    /** Razorpay refund.created / refund.processed / refund.failed. Runs inside the webhook's transaction. */
    public void onWebhook(String type, JsonNode refund) {
        if (refund == null || refund.isMissingNode() || !refund.hasNonNull("id")) return;
        String providerId = refund.get("id").asText();
        List<Long> found = jdbc.queryForList("SELECT id FROM refunds WHERE provider_refund_id = ?", Long.class, providerId);
        if (found.isEmpty() && refund.hasNonNull("payment_id")) {      // the webhook can beat our own bookkeeping: match on the payment and amount instead
            found = jdbc.queryForList("SELECT r.id FROM refunds r JOIN payments p ON p.id = r.payment_id WHERE p.provider_payment_id = ? AND r.status = 'PROCESSING' AND r.provider_refund_id IS NULL AND (r.amount * 100)::bigint = ?",
                    Long.class, refund.get("payment_id").asText(), refund.path("amount").asLong(-1));
            if (!found.isEmpty()) jdbc.update("UPDATE refunds SET provider_refund_id = ? WHERE id = ?", providerId, found.get(0));
        }
        if (found.isEmpty()) { log.info("Webhook for unknown refund {} ignored", providerId); return; }
        long id = found.get(0);
        switch (type) {
            case "refund.processed" -> finalizeRefund(id);
            case "refund.failed" -> { jdbc.update("UPDATE refunds SET status = 'FAILED', failure_reason = 'The provider reported the refund failed', updated_at = now() WHERE id = ? AND status IN ('PROCESSING','APPROVED')", id);
                notifications.notifyPermissionHolders("REFUND_APPROVE", "REFUND_FAILED", "A refund failed at the provider", "Review it under Refunds and retry.", "/admin/refunds"); }
            default -> { }
        }
    }

    // ---------------- helpers ----------------

    private void closeOrder(long orderId, Map<String, Object> o, String note, long actor, boolean paid) {
        List<Long> released = jdbc.queryForList("UPDATE tailor_assignments SET status = 'RELEASED', released_at = now(), release_reason = ? WHERE order_id = ? AND status = 'ACTIVE' RETURNING tailor_user_id", Long.class, note.substring(0, Math.min(300, note.length())), orderId);
        jdbc.update("UPDATE payments SET status = 'SUPERSEDED', updated_at = now() WHERE order_id = ? AND status IN ('PENDING','PENDING_COLLECTION')", orderId);
        orders.transition(orderId, OrderStatus.CANCELLED, note.substring(0, Math.min(500, note.length())), actor);
        if (!paid) coupons.release(orderId);
        for (Long t : released) notifications.notifyUser(t, "ORDER_CANCELLED", "Order " + o.get("order_number") + " was cancelled", "You do not need to continue with this order.", "/tailor/assigned");
    }

    private long createRefund(long orderId, String kind, BigDecimal amount, BigDecimal fee, String status, String reason, long requestedBy, boolean reverse) {
        Map<String, Object> p = jdbc.queryForList("SELECT id, method FROM payments WHERE order_id = ? AND status = 'SUCCESS' ORDER BY id LIMIT 1", orderId).stream().findFirst().orElseThrow(() -> bad("There is no successful payment to refund."));
        return jdbc.queryForObject("""
            INSERT INTO refunds (order_id, payment_id, kind, amount, fee_retained, status, method, reason, requested_by, approved_by, reverse_tailor_earning)
            VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id""", Long.class, orderId, p.get("id"), kind, amount, fee, status, "COD".equals(p.get("method")) ? "MANUAL" : "RAZORPAY",
                reason == null ? null : reason.substring(0, Math.min(500, reason.length())), requestedBy, "APPROVED".equals(status) ? requestedBy : null, reverse);
    }

    private BigDecimal paidAmount(long orderId) { return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE order_id = ? AND status = 'SUCCESS'", BigDecimal.class, orderId); }
    private BigDecimal available(long orderId) {
        BigDecimal refunded = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE order_id = ? AND status IN ('REQUESTED','APPROVED','PROCESSING','PROCESSED','FAILED')", BigDecimal.class, orderId);
        return paidAmount(orderId).subtract(refunded);
    }
    private boolean wasDelivered(long orderId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM delivery_tracking WHERE order_id = ? AND delivered_at IS NOT NULL", Integer.class, orderId);
        return n != null && n > 0;
    }
    private Map<String, Object> lock(long refundId) {
        return jdbc.queryForList("SELECT order_id, amount, status, method, requested_by, reverse_tailor_earning FROM refunds WHERE id = ? FOR UPDATE", refundId).stream().findFirst().orElseThrow(() -> notFound("Refund"));
    }

    private List<RefundView> query(String tail, Object... args) {
        return jdbc.query("""
            SELECT r.id, r.order_id, o.order_number, r.kind, r.amount, r.fee_retained, r.status, r.method, r.reason, rb.full_name AS req, ab.full_name AS app, r.failure_reason, r.reverse_tailor_earning, r.created_at, r.processed_at
            FROM refunds r JOIN orders o ON o.id = r.order_id JOIN users rb ON rb.id = r.requested_by LEFT JOIN users ab ON ab.id = r.approved_by""" + tail,
            (rs, i) -> new RefundView(rs.getLong("id"), rs.getLong("order_id"), rs.getString("order_number"), rs.getString("kind"), rs.getBigDecimal("amount"), rs.getBigDecimal("fee_retained"), rs.getString("status"), rs.getString("method"),
                    rs.getString("reason"), rs.getString("req"), rs.getString("app"), rs.getString("failure_reason"), rs.getBoolean("reverse_tailor_earning"), Sql.ts(rs, "created_at"), Sql.ts(rs, "processed_at")), args);
    }
}
