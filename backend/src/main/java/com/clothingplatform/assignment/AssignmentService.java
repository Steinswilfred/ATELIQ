package com.clothingplatform.assignment;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.Sql;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.NotificationService;
import com.clothingplatform.order.OrderService;
import com.clothingplatform.order.OrderService.OrderReadyForAssignment;
import com.clothingplatform.order.OrderStatus;
import com.clothingplatform.order.OrderStatusMachine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Who stitches an order. Concurrency design:
 *  1. a tailor's own accepts are serialised by a row lock on their availability record, so the capacity check cannot be raced;
 *  2. the order is claimed with ONE conditional UPDATE (still pending, still unassigned, right category). Exactly one concurrent
 *     caller can change that row, the others see 0 rows and get a clear "already taken" answer;
 *  3. a partial unique index on active assignments is a final backstop in the database itself.
 */
@Service
public class AssignmentService {
    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    public record ItemSummary(String name, String imageUrl, int quantity, String fabric, String color, List<JsonNode> selections, String sizeCode, boolean customMeasurements) {}
    public record AvailableOrder(long id, String orderNumber, Instant placedAt, int garmentCount, BigDecimal stitchingCharge, List<ItemSummary> items) {}
    public record TailorOrderRow(long id, String orderNumber, String status, Instant placedAt, int garmentCount, String customerFirstName, String city, String firstItem, BigDecimal stitchingCharge) {}
    public record TailorItem(String name, String imageUrl, int quantity, String sizeCode, String measurementProfile, JsonNode measurements, JsonNode details) {}
    public record TimelineEntry(String status, String note, Instant at, List<String> photos) {}
    public record TailorOrderDetail(TailorOrderRow order, List<TailorItem> items, List<TimelineEntry> timeline, String category) {}
    public record HistoryEntry(String tailorName, String method, String status, String assignedBy, String reason, Instant assignedAt, Instant releasedAt, String releaseReason) {}
    public record EligibleTailor(long userId, String name, String category, String availability, int activeOrders, int maxActiveOrders, boolean inOrderCategory, boolean eligible) {}
    public record AssignmentInfo(String status, Long currentTailorId, String currentTailorName, List<HistoryEntry> history, List<EligibleTailor> tailors, boolean canChange) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OrderService orders;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AssignmentRules.Loader rulesLoader;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;

    public AssignmentService(JdbcTemplate jdbc, TransactionTemplate tx, OrderService orders, NotificationService notifications, AuditService audit, AssignmentRules.Loader rulesLoader, ApplicationEventPublisher events, ObjectMapper mapper) {
        this.jdbc = jdbc; this.tx = tx; this.orders = orders; this.notifications = notifications; this.audit = audit; this.rulesLoader = rulesLoader; this.events = events; this.mapper = mapper;
    }

    private static final String ACTIVE_COUNT = "(SELECT count(*) FROM orders ao WHERE ao.tailor_id = u.id AND ao.status NOT IN " + OrderStatusMachine.INACTIVE_SQL + ")";

    // ---------------- telling eligible tailors ----------------

    /** Runs after the order's transaction commits, so tailors are never told about an order that then rolls back. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderReady(OrderReadyForAssignment e) {
        try { notifyEligible(e.orderId()); } catch (RuntimeException ex) { log.error("Could not notify tailors for order {}", e.orderId(), ex); }
    }

    public int notifyEligible(long orderId) {
        Map<String, Object> o = jdbc.queryForList("SELECT order_number, tailor_category_id, garment_count, tailor_charges, status FROM orders WHERE id = ?", orderId).stream().findFirst().orElse(null);
        if (o == null || !OrderStatus.TAILOR_ASSIGNMENT_PENDING.name().equals(o.get("status"))) return 0;
        List<Long> ids = eligibleIds(orderId, ((Number) o.get("tailor_category_id")).longValue(), rulesLoader.load());
        for (long id : ids)
            notifications.notifyUser(id, "NEW_ORDER_AVAILABLE", "New order available", o.get("garment_count") + " garment(s) · stitching ₹" + o.get("tailor_charges") + ". The first tailor to accept gets it.", "/tailor/available");
        return ids.size();
    }

    private List<Long> eligibleIds(long orderId, long categoryId, AssignmentRules rules) {
        StringBuilder sql = new StringBuilder("""
            SELECT u.id FROM tailor_profiles p JOIN users u ON u.id = p.user_id JOIN tailor_availability a ON a.tailor_id = p.id
            WHERE u.account_status = 'APPROVED' AND u.deleted_at IS NULL AND p.category_id = ?
              AND NOT EXISTS (SELECT 1 FROM tailor_assignments ta WHERE ta.order_id = ? AND ta.tailor_user_id = u.id AND ta.status = 'RELEASED')""");
        if (rules.requireAvailable()) sql.append(" AND a.status = 'AVAILABLE'");
        if (rules.respectCapacity()) sql.append(" AND ").append(ACTIVE_COUNT).append(" < a.max_active_orders");
        return jdbc.queryForList(sql.toString(), Long.class, categoryId, orderId);
    }

    // ---------------- tailor: browse and accept ----------------

    /** Summary only: what to stitch, never who the customer is or their measurements. Those are revealed after accepting. */
    public List<AvailableOrder> available(long tailorUserId) {
        Long category = jdbc.query("SELECT category_id FROM tailor_profiles WHERE user_id = ?", rs -> rs.next() ? rs.getLong(1) : null, tailorUserId);
        if (category == null) return List.of();
        record Row(long id, String number, Instant placedAt, int garments, BigDecimal charge) {}
        List<Row> rows = jdbc.query("""
            SELECT o.id, o.order_number, o.placed_at, o.garment_count, o.tailor_charges FROM orders o
            WHERE o.status = 'TAILOR_ASSIGNMENT_PENDING' AND o.tailor_id IS NULL AND o.tailor_category_id = ?
              AND NOT EXISTS (SELECT 1 FROM tailor_assignments ta WHERE ta.order_id = o.id AND ta.tailor_user_id = ? AND ta.status = 'RELEASED')
            ORDER BY o.placed_at LIMIT 50""", (rs, i) -> new Row(rs.getLong(1), rs.getString(2), Sql.ts(rs, "placed_at"), rs.getInt(4), rs.getBigDecimal(5)), category, tailorUserId);
        List<AvailableOrder> out = new ArrayList<>();
        for (Row r : rows) {
            List<ItemSummary> items = jdbc.query("SELECT product_name, image_url, quantity, size_code, measurement_profile_name, details::text AS d FROM order_items WHERE order_id = ? ORDER BY id", (rs, i) -> {
                JsonNode d = parse(rs.getString("d"));
                List<JsonNode> sel = new ArrayList<>(); if (d != null) d.path("selections").forEach(sel::add);
                return new ItemSummary(rs.getString(1), rs.getString(2), rs.getInt(3), d == null ? null : d.path("fabric").asText(null), d == null ? null : d.path("color").asText(null), sel, rs.getString(4), rs.getString(5) != null);
            }, r.id());
            out.add(new AvailableOrder(r.id(), r.number(), r.placedAt(), r.garments(), r.charge(), items));
        }
        return out;
    }

    /** First eligible tailor wins. Everyone else gets 409 ORDER_TAKEN and the order disappears from their list. */
    public void accept(long tailorUserId, long orderId) {
        tx.executeWithoutResult(s -> {
            Map<String, Object> a = jdbc.queryForList("""
                SELECT a.status, a.max_active_orders, p.category_id FROM tailor_availability a JOIN tailor_profiles p ON p.id = a.tailor_id WHERE p.user_id = ? FOR UPDATE OF a""", tailorUserId)
                .stream().findFirst().orElseThrow(() -> notFound("Tailor profile"));
            AssignmentRules rules = rulesLoader.load();
            if (rules.requireAvailable() && !"AVAILABLE".equals(a.get("status")))
                throw new BusinessException("NOT_AVAILABLE", "Set your status to Available on your dashboard to accept orders.", HttpStatus.CONFLICT);
            if (rules.respectCapacity()) {
                Integer active = jdbc.queryForObject("SELECT count(*) FROM orders WHERE tailor_id = ? AND status NOT IN " + OrderStatusMachine.INACTIVE_SQL, Integer.class, tailorUserId);
                if (active != null && active >= ((Number) a.get("max_active_orders")).intValue())
                    throw new BusinessException("AT_CAPACITY", "You are at your limit of " + a.get("max_active_orders") + " active orders. Finish one to accept another.", HttpStatus.CONFLICT);
            }
            int claimed = jdbc.update("""
                UPDATE orders SET tailor_id = ?, updated_at = now()
                WHERE id = ? AND status = 'TAILOR_ASSIGNMENT_PENDING' AND tailor_id IS NULL AND tailor_category_id = ?
                  AND NOT EXISTS (SELECT 1 FROM tailor_assignments ta WHERE ta.order_id = orders.id AND ta.tailor_user_id = ? AND ta.status = 'RELEASED')""",
                tailorUserId, orderId, ((Number) a.get("category_id")).longValue(), tailorUserId);
            if (claimed == 0) throw new BusinessException("ORDER_TAKEN", "This order is no longer available. Another tailor accepted it first, or it was withdrawn.", HttpStatus.CONFLICT);
            finish(orderId, tailorUserId, "ACCEPTED", null, null, tailorUserId);
        });
    }

    /** Records the assignment, advances the order to "fabric sample needed", and tells the tailor and the customer. Runs inside the caller's transaction. */
    private void finish(long orderId, long tailorUserId, String method, Long adminId, String reason, long actorId) {
        jdbc.update("INSERT INTO tailor_assignments (order_id, tailor_user_id, method, assigned_by, assign_reason) VALUES (?,?,?,?,?)", orderId, tailorUserId, method, adminId, reason);
        orders.transition(orderId, OrderStatus.TAILOR_ASSIGNED, "ACCEPTED".equals(method) ? "Accepted by the tailor" : "Assigned by an administrator", actorId);
        orders.transition(orderId, OrderStatus.FABRIC_SAMPLE_PENDING, "The tailor will prepare a fabric sample for your approval", null);
        jdbc.update("UPDATE orders SET escalated_at = NULL WHERE id = ?", orderId);
        Map<String, Object> o = jdbc.queryForMap("SELECT user_id, order_number FROM orders WHERE id = ?", orderId);
        String first = jdbc.queryForObject("SELECT split_part(full_name, ' ', 1) FROM users WHERE id = ?", String.class, tailorUserId);
        notifications.notifyUser(((Number) o.get("user_id")).longValue(), "TAILOR_ASSIGNED", "A tailor is on your order", first + " will stitch order " + o.get("order_number") + ". You will be asked to approve the fabric before any cutting.", "/account/orders/" + orderId);
        notifications.notifyUser(tailorUserId, "ORDER_ASSIGNED", "Order " + o.get("order_number") + " is yours", "Next step: prepare the fabric sample for the customer's approval.", "/tailor/assigned/" + orderId);
        audit.audit(actorId, "ORDER_" + ("ACCEPTED".equals(method) ? "ACCEPTED" : "ASSIGNED"), "ORDER", orderId, null, Map.of("tailorUserId", tailorUserId, "method", method), reason);
    }

    // ---------------- tailor: my orders ----------------

    public List<TailorOrderRow> mine(long tailorUserId, boolean activeOnly) {
        return jdbc.query("""
            SELECT o.id, o.order_number, o.status, o.placed_at, o.garment_count, split_part(o.customer_name, ' ', 1) AS fname, o.shipping_address->>'city' AS city, o.tailor_charges,
                   (SELECT product_name FROM order_items i WHERE i.order_id = o.id ORDER BY i.id LIMIT 1) AS first_item
            FROM orders o WHERE o.tailor_id = ? AND o.status """ + (activeOnly ? " NOT IN " : " IN ") + (activeOnly ? OrderStatusMachine.INACTIVE_SQL : "('DELIVERED')") + " ORDER BY o.placed_at DESC LIMIT 100",
            (rs, i) -> new TailorOrderRow(rs.getLong(1), rs.getString(2), rs.getString(3), Sql.ts(rs, "placed_at"), rs.getInt(5), rs.getString("fname"), rs.getString("city"), rs.getString("first_item"), rs.getBigDecimal("tailor_charges")), tailorUserId);
    }

    /** Full job details, but only for the tailor the order is assigned to. The customer's phone and street address are not included. */
    public TailorOrderDetail tailorDetail(long tailorUserId, long orderId) {
        List<TailorOrderRow> rows = jdbc.query("""
            SELECT o.id, o.order_number, o.status, o.placed_at, o.garment_count, split_part(o.customer_name, ' ', 1) AS fname, o.shipping_address->>'city' AS city, o.tailor_charges,
                   (SELECT product_name FROM order_items i WHERE i.order_id = o.id ORDER BY i.id LIMIT 1) AS first_item
            FROM orders o WHERE o.id = ? AND o.tailor_id = ?""", (rs, i) -> new TailorOrderRow(rs.getLong(1), rs.getString(2), rs.getString(3), Sql.ts(rs, "placed_at"), rs.getInt(5), rs.getString("fname"), rs.getString("city"), rs.getString("first_item"), rs.getBigDecimal("tailor_charges")), orderId, tailorUserId);
        if (rows.isEmpty()) throw notFound("Order");
        List<TailorItem> items = jdbc.query("SELECT product_name, image_url, quantity, size_code, measurement_profile_name, measurements::text AS m, details::text AS d FROM order_items WHERE order_id = ? ORDER BY id",
                (rs, i) -> new TailorItem(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5), parse(rs.getString("m")), parse(rs.getString("d"))), orderId);
        List<TimelineEntry> tl = jdbc.query("SELECT status, note, created_at, photos::text AS p FROM order_status_history WHERE order_id = ? ORDER BY created_at, id", (rs, i) -> {
            JsonNode pn = parse(rs.getString("p")); List<String> ph = new ArrayList<>(); if (pn != null && pn.isArray()) pn.forEach(x -> ph.add(x.asText()));
            return new TimelineEntry(rs.getString(1), rs.getString(2), Sql.ts(rs, "created_at"), ph);
        }, orderId);
        String category = jdbc.queryForObject("SELECT tailor_category_name FROM orders WHERE id = ?", String.class, orderId);
        return new TailorOrderDetail(rows.get(0), items, tl, category);
    }

    // ---------------- admin ----------------

    public AssignmentInfo info(long orderId) {
        Map<String, Object> o = jdbc.queryForList("SELECT status, tailor_id, tailor_category_id FROM orders WHERE id = ?", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
        String status = (String) o.get("status");
        Long current = o.get("tailor_id") == null ? null : ((Number) o.get("tailor_id")).longValue();
        long categoryId = ((Number) o.get("tailor_category_id")).longValue();
        String currentName = current == null ? null : jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class, current);
        List<HistoryEntry> history = jdbc.query("""
            SELECT t.full_name, a.method, a.status, ab.full_name AS by_name, a.assign_reason, a.assigned_at, a.released_at, a.release_reason
            FROM tailor_assignments a JOIN users t ON t.id = a.tailor_user_id LEFT JOIN users ab ON ab.id = a.assigned_by WHERE a.order_id = ? ORDER BY a.assigned_at""",
            (rs, i) -> new HistoryEntry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), Sql.ts(rs, "assigned_at"), Sql.ts(rs, "released_at"), rs.getString(8)), orderId);
        AssignmentRules rules = rulesLoader.load();
        Set<Long> eligible = new HashSet<>(eligibleIds(orderId, categoryId, rules));
        List<EligibleTailor> tailors = jdbc.query("SELECT u.id, u.full_name, c.name AS cat, p.category_id, a.status, a.max_active_orders, " + ACTIVE_COUNT + " AS active FROM tailor_profiles p JOIN users u ON u.id = p.user_id JOIN tailor_categories c ON c.id = p.category_id JOIN tailor_availability a ON a.tailor_id = p.id WHERE u.account_status = 'APPROVED' AND u.deleted_at IS NULL ORDER BY u.full_name LIMIT 300",
                (rs, i) -> new EligibleTailor(rs.getLong(1), rs.getString(2), rs.getString("cat"), rs.getString("status"), rs.getInt("active"), rs.getInt("max_active_orders"), rs.getLong("category_id") == categoryId, eligible.contains(rs.getLong(1))), new Object[0]);
        boolean canChange = Set.of("TAILOR_ASSIGNMENT_PENDING", "TAILOR_ASSIGNED", "FABRIC_SAMPLE_PENDING").contains(status);
        return new AssignmentInfo(status, current, currentName, history, tailors, canChange);
    }

    /**
     * Assign (pending order), reassign (before the tailor submits a sample) or return to the pool (tailorUserId null).
     * An admin may pick any approved tailor even outside the usual rules; the reason is mandatory and recorded.
     */
    public void adminAssign(long adminId, long orderId, Long tailorUserId, String reason) {
        if (reason == null || reason.trim().length() < 5) throw bad("Give a reason (at least 5 characters). It is recorded in the order history.");
        if (reason.trim().length() > 250) throw bad("Keep the reason under 250 characters.");
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = jdbc.queryForList("SELECT status, tailor_id, order_number FROM orders WHERE id = ? FOR UPDATE", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
            OrderStatus status = OrderStatus.valueOf((String) o.get("status"));
            boolean assigned = status == OrderStatus.TAILOR_ASSIGNED || status == OrderStatus.FABRIC_SAMPLE_PENDING;
            if (status != OrderStatus.TAILOR_ASSIGNMENT_PENDING && !assigned)
                throw new BusinessException("ASSIGNMENT_LOCKED", "The tailor can only be changed before they submit the fabric sample.", HttpStatus.CONFLICT);
            if (status == OrderStatus.TAILOR_ASSIGNMENT_PENDING && tailorUserId == null) throw bad("Choose a tailor to assign.");
            if (tailorUserId != null) {
                Integer ok = jdbc.queryForObject("SELECT count(*) FROM tailor_profiles p JOIN users u ON u.id = p.user_id WHERE u.id = ? AND u.account_status = 'APPROVED' AND u.deleted_at IS NULL", Integer.class, tailorUserId);
                if (ok == null || ok == 0) throw bad("That tailor is not an approved, active tailor.");
            }
            if (assigned) {
                List<Long> released = jdbc.queryForList("UPDATE tailor_assignments SET status = 'RELEASED', released_at = now(), released_by = ?, release_reason = ? WHERE order_id = ? AND status = 'ACTIVE' RETURNING tailor_user_id", Long.class, adminId, reason.trim(), orderId);
                jdbc.update("UPDATE orders SET tailor_id = NULL, updated_at = now() WHERE id = ?", orderId);
                orders.transition(orderId, OrderStatus.TAILOR_ASSIGNMENT_PENDING, "Tailor changed by an administrator: " + reason.trim(), adminId);
                for (Long old : released) notifications.notifyUser(old, "ORDER_REASSIGNED", "Order " + o.get("order_number") + " was reassigned", "An administrator moved this order to another tailor.", "/tailor/assigned");
                audit.audit(adminId, "ORDER_TAILOR_RELEASED", "ORDER", orderId, released, null, reason.trim());
            }
            if (tailorUserId == null) { events.publishEvent(new OrderReadyForAssignment(orderId)); return; }   // back to the pool; eligible tailors are told after commit
            int n = jdbc.update("UPDATE orders SET tailor_id = ?, updated_at = now() WHERE id = ? AND status = 'TAILOR_ASSIGNMENT_PENDING' AND tailor_id IS NULL", tailorUserId, orderId);
            if (n == 0) throw new BusinessException("ORDER_TAKEN", "A tailor accepted this order a moment ago. Refresh and review before changing it.", HttpStatus.CONFLICT);
            finish(orderId, tailorUserId, "ADMIN_ASSIGNED", adminId, reason.trim(), adminId);
        });
    }

    // ---------------- escalation ----------------

    /** Every 15 minutes: orders nobody accepted within the configured time are flagged to admins, once. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void escalateStaleOrders() {
        int hours = rulesLoader.load().escalationHours();
        if (hours <= 0) return;
        List<Map<String, Object>> stale = jdbc.queryForList("""
            UPDATE orders SET escalated_at = now() WHERE status = 'TAILOR_ASSIGNMENT_PENDING' AND escalated_at IS NULL AND updated_at < now() - make_interval(hours => ?)
            RETURNING id, order_number""", hours);
        for (Map<String, Object> o : stale)
            notifications.notifyPermissionHolders("ORDER_VIEW", "ORDER_UNASSIGNED", "Order " + o.get("order_number") + " has no tailor", "Nobody accepted it within " + hours + " hours. Consider assigning a tailor.", "/admin/orders/" + o.get("id"));
    }

    private JsonNode parse(String s) { if (s == null) return null; try { return mapper.readTree(s); } catch (JsonProcessingException e) { return null; } }
}
