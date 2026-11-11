package com.clothingplatform.fulfilment;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.Sql;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.notification.NotificationService;
import com.clothingplatform.finance.EarningsService;
import com.clothingplatform.order.OrderService;
import com.clothingplatform.order.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shipping and delivery. Without a courier integration these updates are typed in by an admin, and customers are told that.
 * Nothing here invents location data: locations are free text an admin entered, and there is no map or GPS.
 */
@Service
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final Pattern HTTPS = Pattern.compile("^https://\\S{4,480}$");

    public record ShipRequest(@NotBlank @Size(max = 80) String courierName, @Size(max = 80) String trackingNumber, @Size(max = 480) String trackingUrl, LocalDate estimatedDelivery, @Size(max = 500) String note) {}
    public record StatusRequest(@NotBlank String status, @Size(max = 500) String note, @Size(max = 120) String location) {}
    public record EventRequest(@NotBlank @Size(max = 500) String note, @Size(max = 120) String location) {}
    public record EventView(String status, String note, String location, Instant at, String source) {}
    public record DeliveryView(boolean shipped, String courierName, String trackingNumber, String trackingUrl, LocalDate estimatedDelivery, Instant shippedAt, Instant deliveredAt, boolean courierIntegrated, List<EventView> events) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OrderService orders;
    private final NotificationService notifications;
    private final EmailSender email;
    private final AuditService audit;
    private final List<CourierTrackingProvider> providers;
    private final EarningsService earnings;

    public DeliveryService(JdbcTemplate jdbc, TransactionTemplate tx, OrderService orders, NotificationService notifications, EmailSender email, AuditService audit, List<CourierTrackingProvider> providers, EarningsService earnings) {
        this.earnings = earnings;
        this.jdbc = jdbc; this.tx = tx; this.orders = orders; this.notifications = notifications; this.email = email; this.audit = audit; this.providers = providers;
    }

    public void ship(long adminId, long orderId, ShipRequest r) {
        String url = r.trackingUrl() == null || r.trackingUrl().isBlank() ? null : r.trackingUrl().trim();
        if (url != null && !HTTPS.matcher(url).matches()) throw bad("The tracking link must start with https://.");
        if (r.estimatedDelivery() != null && r.estimatedDelivery().isBefore(LocalDate.now())) throw bad("The estimated delivery date cannot be in the past.");
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = lock(orderId);
            if (!OrderStatus.READY_FOR_DISPATCH.name().equals(o.get("status"))) throw new BusinessException("NOT_READY", "Only an order that is ready for dispatch can be shipped.", HttpStatus.CONFLICT);
            jdbc.update("INSERT INTO delivery_tracking (order_id, courier_name, tracking_number, tracking_url, estimated_delivery, created_by) VALUES (?,?,?,?,?,?)",
                    orderId, r.courierName().trim(), blank(r.trackingNumber()), url, r.estimatedDelivery(), adminId);
            event(orderId, "SHIPPED", blank(r.note()) == null ? "Shipped with " + r.courierName().trim() : r.note().trim(), null, adminId);
            orders.transition(orderId, OrderStatus.SHIPPED, "Shipped with " + r.courierName().trim() + (blank(r.trackingNumber()) != null ? " (tracking " + r.trackingNumber().trim() + ")" : ""), adminId);
            String number = (String) o.get("order_number");
            long customer = ((Number) o.get("user_id")).longValue();
            notifications.notifyUser(customer, "ORDER_SHIPPED", "Your order has shipped", "Order " + number + " is on its way with " + r.courierName().trim() + ".", "/account/orders/" + orderId);
            email.send(emailOf(customer), "Your order " + number + " has shipped", "Your order is on its way with " + r.courierName().trim() + "." + (blank(r.trackingNumber()) != null ? "\nTracking number: " + r.trackingNumber().trim() : "")
                    + (url != null ? "\nTrack it here: " + url : "") + (r.estimatedDelivery() != null ? "\nEstimated delivery: " + r.estimatedDelivery() : "") + "\nYou can also follow it under My orders.");
            audit.audit(adminId, "ORDER_SHIPPED", "ORDER", orderId, null, Map.of("courier", r.courierName().trim()), null);
        });
    }

    /** OUT_FOR_DELIVERY (from shipped) or DELIVERED (from out for delivery). */
    public void update(long adminId, long orderId, StatusRequest r) {
        OrderStatus to;
        try { to = OrderStatus.valueOf(r.status()); } catch (IllegalArgumentException e) { throw bad("Unknown status."); }
        if (to != OrderStatus.OUT_FOR_DELIVERY && to != OrderStatus.DELIVERED) throw bad("Choose OUT_FOR_DELIVERY or DELIVERED.");
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = lock(orderId);
            requireTracked(orderId);
            String note = blank(r.note()) == null ? (to == OrderStatus.DELIVERED ? "Delivered" : "Out for delivery") : r.note().trim();
            orders.transition(orderId, to, note, adminId);      // the state machine rejects any other jump with 409
            event(orderId, to.name(), note, blank(r.location()), adminId);
            if (to == OrderStatus.DELIVERED) {
                jdbc.update("UPDATE delivery_tracking SET delivered_at = now() WHERE order_id = ?", orderId);
                earnings.accrueOnDelivery(orderId);     // revenue is recognised and the tailor's earning starts its settlement hold
            }
            String number = (String) o.get("order_number");
            long customer = ((Number) o.get("user_id")).longValue();
            boolean cashDue = "COD".equals(o.get("payment_method")) && !"PAID".equals(o.get("payment_status"));
            if (to == OrderStatus.OUT_FOR_DELIVERY) {
                notifications.notifyUser(customer, "ORDER_OUT_FOR_DELIVERY", "Out for delivery", "Order " + number + " arrives today." + (cashDue ? " Please keep ₹" + o.get("total") + " ready in cash." : ""), "/account/orders/" + orderId);
            } else {
                notifications.notifyUser(customer, "ORDER_DELIVERED", "Delivered", "Order " + number + " has been delivered. We hope you love it.", "/account/orders/" + orderId);
                email.send(emailOf(customer), "Your order " + number + " was delivered", "Your order has been delivered. Thank you for shopping with us.");
                if (o.get("tailor_id") != null) notifications.notifyUser(((Number) o.get("tailor_id")).longValue(), "ORDER_DELIVERED", "Order " + number + " delivered", "Great work. The order has been delivered to the customer.", "/tailor/assigned/" + orderId);
                if (cashDue) notifications.notifyPermissionHolders("PAYMENT_COD_COLLECT", "COD_TO_COLLECT", "Collect cash for " + number, "Delivered. Record the cash collection once the courier hands it over.", "/admin/payments");
            }
            audit.audit(adminId, "ORDER_DELIVERY_" + to.name(), "ORDER", orderId, null, to.name(), r.note());
        });
    }

    /** A progress note (for example "Reached the Chennai hub") without changing the status. */
    public void addEvent(long adminId, long orderId, EventRequest r) {
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = lock(orderId);
            String st = (String) o.get("status");
            if (!OrderStatus.SHIPPED.name().equals(st) && !OrderStatus.OUT_FOR_DELIVERY.name().equals(st)) throw new BusinessException("NOT_IN_TRANSIT", "Updates can be added while the order is shipped or out for delivery.", HttpStatus.CONFLICT);
            event(orderId, "UPDATE", r.note().trim(), blank(r.location()), adminId);
        });
    }

    // ---------------- views ----------------

    public DeliveryView forCustomer(long customerId, long orderId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM orders WHERE id = ? AND user_id = ?", Integer.class, orderId, customerId);
        if (n == null || n == 0) throw notFound("Order");
        return view(orderId);
    }

    public DeliveryView forAdmin(long orderId) {
        if (jdbc.queryForObject("SELECT count(*) FROM orders WHERE id = ?", Integer.class, orderId) == 0) throw notFound("Order");
        return view(orderId);
    }

    private DeliveryView view(long orderId) {
        List<Map<String, Object>> t = jdbc.queryForList("SELECT courier_name, tracking_number, tracking_url, estimated_delivery, shipped_at, delivered_at FROM delivery_tracking WHERE order_id = ?", orderId);
        if (t.isEmpty()) return new DeliveryView(false, null, null, null, null, null, null, !providers.isEmpty(), List.of());
        Map<String, Object> d = t.get(0);
        List<EventView> events = new ArrayList<>(jdbc.query("SELECT status, note, location, occurred_at FROM delivery_events WHERE order_id = ? ORDER BY occurred_at, id",
                (rs, i) -> new EventView(rs.getString(1), rs.getString(2), rs.getString(3), Sql.ts(rs, "occurred_at"), "TEAM"), orderId));
        String courier = (String) d.get("courier_name"), number = (String) d.get("tracking_number");
        boolean integrated = false;
        if (number != null) for (CourierTrackingProvider p : providers) {
            if (!p.supports(courier)) continue;
            integrated = true;
            try { for (var e : p.fetch(number)) events.add(new EventView(e.status(), e.note(), e.location(), e.at(), "COURIER")); }
            catch (RuntimeException ex) { log.warn("Courier lookup failed for {}: {}", courier, ex.getMessage()); }   // fall back to what the team recorded
        }
        events.sort(Comparator.comparing(EventView::at));
        java.sql.Date eta = (java.sql.Date) d.get("estimated_delivery");
        return new DeliveryView(true, courier, number, (String) d.get("tracking_url"), eta == null ? null : eta.toLocalDate(), ((java.sql.Timestamp) d.get("shipped_at")).toInstant(),
                d.get("delivered_at") == null ? null : ((java.sql.Timestamp) d.get("delivered_at")).toInstant(), integrated, events);
    }

    // ---------------- helpers ----------------

    private Map<String, Object> lock(long orderId) {
        return jdbc.queryForList("SELECT status, user_id, tailor_id, order_number, payment_method, payment_status, total FROM orders WHERE id = ? FOR UPDATE", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
    }
    private void requireTracked(long orderId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM delivery_tracking WHERE order_id = ?", Integer.class, orderId);
        if (n == null || n == 0) throw new BusinessException("NOT_SHIPPED", "Ship the order first.", HttpStatus.CONFLICT);
    }
    private void event(long orderId, String status, String note, String location, long by) {
        jdbc.update("INSERT INTO delivery_events (order_id, status, note, location, recorded_by) VALUES (?,?,?,?,?)", orderId, status, note, location, by);
    }
    private String emailOf(long userId) { return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId); }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
