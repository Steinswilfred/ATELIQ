package com.clothingplatform.approval;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.Sql;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.notification.NotificationService;
import com.clothingplatform.order.OrderService;
import com.clothingplatform.order.OrderStatus;
import com.clothingplatform.storage.StorageService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * The mandatory fabric approval step. Rules:
 *  - only the tailor assigned to the order can send a sample, and only while the order is waiting for one (first sample or after a rejection);
 *  - only the customer who owns the order can approve or reject, and only the sample currently waiting for them;
 *  - cutting can start only from FABRIC_APPROVED (enforced by the order state machine, which this service drives);
 *  - every submission and decision is stored with the person and time (fabric_approvals is append-only).
 */
@Service
public class FabricSampleService {
    public record EventView(String action, String actorName, String role, String reason, Instant at) {}
    public record SampleView(long id, int revision, String status, String note, String submittedBy, Instant submittedAt, Instant decidedAt, String decisionReason, List<String> photos, List<EventView> events) {}
    public record SubmitRequest(@NotNull @Size(min = 1, max = 6, message = "Add between 1 and 6 photos.") List<String> photoUrls, @Size(max = 500) String note) {}
    public record RejectRequest(@NotBlank @Size(min = 5, max = 500, message = "Tell the tailor what is wrong (5 to 500 characters).") String reason) {}

    /** Photos must be files this server stored in the samples folder (the uploader names them with a random UUID). */
    private static final Pattern PHOTO = Pattern.compile("^/media/samples/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|gif|webp)$");
    public static boolean validPhotoUrl(String u) { return u != null && PHOTO.matcher(u).matches(); }
    private static final int REPEATED_REJECTIONS = 3;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OrderService orders;
    private final NotificationService notifications;
    private final EmailSender email;
    private final AuditService audit;
    private final StorageService storage;

    public FabricSampleService(JdbcTemplate jdbc, TransactionTemplate tx, OrderService orders, NotificationService notifications, EmailSender email, AuditService audit, StorageService storage) {
        this.jdbc = jdbc; this.tx = tx; this.orders = orders; this.notifications = notifications; this.email = email; this.audit = audit; this.storage = storage;
    }

    // ---------------- tailor ----------------

    /** Photo upload for the tailor's own order, only while a sample is wanted. Returns the stored address to include when submitting. */
    public String uploadPhoto(long tailorUserId, long orderId, MultipartFile file) {
        Map<String, Object> o = jdbc.queryForList("SELECT status, tailor_id FROM orders WHERE id = ?", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
        if (o.get("tailor_id") == null || ((Number) o.get("tailor_id")).longValue() != tailorUserId) throw notFound("Order");
        requireSampleWanted((String) o.get("status"));
        return storage.storeImage("samples", file);
    }

    public long submit(long tailorUserId, long orderId, SubmitRequest r) {
        List<String> urls = new ArrayList<>(new LinkedHashSet<>(r.photoUrls()));
        for (String u : urls) if (!validPhotoUrl(u)) throw bad("Use the photo upload button; only photos uploaded here can be sent.");
        long[] sampleId = new long[1];
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = lockOrder(orderId);
            if (o.get("tailor_id") == null || ((Number) o.get("tailor_id")).longValue() != tailorUserId) throw notFound("Order");
            requireSampleWanted((String) o.get("status"));
            Integer rev = jdbc.queryForObject("SELECT COALESCE(MAX(revision), 0) + 1 FROM fabric_samples WHERE order_id = ?", Integer.class, orderId);
            sampleId[0] = jdbc.queryForObject("INSERT INTO fabric_samples (order_id, revision, submitted_by, note) VALUES (?,?,?,?) RETURNING id", Long.class, orderId, rev, tailorUserId, r.note() == null || r.note().isBlank() ? null : r.note().trim());
            int n = 0;
            for (String u : urls) jdbc.update("INSERT INTO fabric_sample_photos (sample_id, url, sort_order) VALUES (?,?,?)", sampleId[0], u, n++);
            event(sampleId[0], "SUBMITTED", tailorUserId, "TAILOR", null);
            orders.transition(orderId, OrderStatus.FABRIC_SAMPLE_SUBMITTED, "Fabric sample " + (rev > 1 ? "(revision " + rev + ") " : "") + "sent by the tailor", tailorUserId);
            orders.transition(orderId, OrderStatus.CUSTOMER_APPROVAL_PENDING, "Waiting for the customer to approve the fabric", null);
            long customer = ((Number) o.get("user_id")).longValue();
            String number = (String) o.get("order_number");
            notifications.notifyUser(customer, "FABRIC_SAMPLE_SUBMITTED", "Approve your fabric sample", "Your tailor sent photos of the fabric for order " + number + ". Nothing is cut until you approve.", "/account/orders/" + orderId);
            email.send(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, customer), "Your fabric sample is ready for approval",
                    "Your tailor has sent photos of the fabric for order " + number + ". Please review and approve it, or ask for a change, from My orders. No cutting starts until you approve.");
            audit.audit(tailorUserId, "FABRIC_SAMPLE_SUBMITTED", "ORDER", orderId, null, Map.of("revision", rev, "photos", urls.size()), null);
        });
        return sampleId[0];
    }

    // ---------------- customer ----------------

    public void approve(long customerId, long orderId, long sampleId) { decide(customerId, orderId, sampleId, true, null); }
    public void reject(long customerId, long orderId, long sampleId, String reason) { decide(customerId, orderId, sampleId, false, reason.trim()); }

    private void decide(long customerId, long orderId, long sampleId, boolean approve, String reason) {
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = lockOrder(orderId);
            if (((Number) o.get("user_id")).longValue() != customerId) throw notFound("Order");
            if (!OrderStatus.CUSTOMER_APPROVAL_PENDING.name().equals(o.get("status")))
                throw new BusinessException("NOT_WAITING_FOR_YOU", "There is no fabric sample waiting for your decision on this order.", HttpStatus.CONFLICT);
            String st = jdbc.query("SELECT status FROM fabric_samples WHERE id = ? AND order_id = ? FOR UPDATE", rs -> rs.next() ? rs.getString(1) : null, sampleId, orderId);
            if (st == null) throw notFound("Fabric sample");
            if (!"PENDING".equals(st)) throw new BusinessException("ALREADY_DECIDED", "This sample has already been " + st.toLowerCase() + ".", HttpStatus.CONFLICT);
            jdbc.update("UPDATE fabric_samples SET status = ?, decided_at = now(), decided_by = ?, decision_reason = ? WHERE id = ?", approve ? "APPROVED" : "REJECTED", customerId, reason, sampleId);
            event(sampleId, approve ? "APPROVED" : "REJECTED", customerId, "CUSTOMER", reason);
            long tailor = ((Number) o.get("tailor_id")).longValue();
            String number = (String) o.get("order_number");
            if (approve) {
                orders.transition(orderId, OrderStatus.FABRIC_APPROVED, "Fabric approved by the customer. Cutting may begin", customerId);
                notifications.notifyUser(tailor, "FABRIC_APPROVED", "Fabric approved for " + number, "The customer approved your sample. You can start cutting.", "/tailor/assigned/" + orderId);
            } else {
                orders.transition(orderId, OrderStatus.FABRIC_REJECTED, "Fabric sample rejected: " + reason, customerId);
                notifications.notifyUser(tailor, "FABRIC_REJECTED", "Fabric sample rejected for " + number, reason + " Please send a revised sample.", "/tailor/assigned/" + orderId);
                Integer rejections = jdbc.queryForObject("SELECT count(*) FROM fabric_samples WHERE order_id = ? AND status = 'REJECTED'", Integer.class, orderId);
                if (rejections != null && rejections >= REPEATED_REJECTIONS)
                    notifications.notifyPermissionHolders("ORDER_VIEW", "FABRIC_REPEATED_REJECTION", "Fabric rejected " + rejections + " times on " + number, "Consider contacting the customer and tailor.", "/admin/orders/" + orderId);
            }
            audit.audit(customerId, approve ? "FABRIC_APPROVED" : "FABRIC_REJECTED", "ORDER", orderId, null, Map.of("sampleId", sampleId), reason);
        });
    }

    // ---------------- history (role-scoped) ----------------

    public List<SampleView> forCustomer(long customerId, long orderId) { requireOwner("user_id", customerId, orderId); return history(orderId); }
    public List<SampleView> forTailor(long tailorId, long orderId) { requireOwner("tailor_id", tailorId, orderId); return history(orderId); }
    public List<SampleView> forAdmin(long orderId) {
        if (jdbc.queryForObject("SELECT count(*) FROM orders WHERE id = ?", Integer.class, orderId) == 0) throw notFound("Order");
        return history(orderId);
    }

    private List<SampleView> history(long orderId) {
        Map<Long, List<String>> photos = new HashMap<>();
        jdbc.query("SELECT p.sample_id, p.url FROM fabric_sample_photos p JOIN fabric_samples s ON s.id = p.sample_id WHERE s.order_id = ? ORDER BY p.sort_order", rs -> { photos.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2)); }, orderId);
        Map<Long, List<EventView>> events = new HashMap<>();
        jdbc.query("SELECT a.sample_id, a.action, u.full_name, a.actor_role, a.reason, a.created_at FROM fabric_approvals a JOIN fabric_samples s ON s.id = a.sample_id JOIN users u ON u.id = a.actor_id WHERE s.order_id = ? ORDER BY a.created_at, a.id", rs -> {
            events.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new EventView(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), Sql.ts(rs, "created_at")));
        }, orderId);
        return jdbc.query("""
            SELECT s.id, s.revision, s.status, s.note, split_part(t.full_name, ' ', 1) AS tname, s.submitted_at, s.decided_at, s.decision_reason
            FROM fabric_samples s JOIN users t ON t.id = s.submitted_by WHERE s.order_id = ? ORDER BY s.revision""",
            (rs, i) -> new SampleView(rs.getLong("id"), rs.getInt("revision"), rs.getString("status"), rs.getString("note"), rs.getString("tname"), Sql.ts(rs, "submitted_at"), Sql.ts(rs, "decided_at"), rs.getString("decision_reason"),
                    photos.getOrDefault(rs.getLong("id"), List.of()), events.getOrDefault(rs.getLong("id"), List.of())), orderId);
    }

    // ---------------- helpers ----------------

    private Map<String, Object> lockOrder(long orderId) {
        return jdbc.queryForList("SELECT status, tailor_id, user_id, order_number FROM orders WHERE id = ? FOR UPDATE", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
    }

    private void requireSampleWanted(String status) {
        if (!OrderStatus.FABRIC_SAMPLE_PENDING.name().equals(status) && !OrderStatus.FABRIC_REJECTED.name().equals(status))
            throw new BusinessException("SAMPLE_NOT_WANTED", "A fabric sample can be sent only while the order is waiting for one.", HttpStatus.CONFLICT);
    }

    /** @param column an internal constant: user_id or tailor_id */
    private void requireOwner(String column, long userId, long orderId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM orders WHERE id = ? AND " + column + " = ?", Integer.class, orderId, userId);
        if (n == null || n == 0) throw notFound("Order");
    }

    private void event(long sampleId, String action, long actor, String role, String reason) {
        jdbc.update("INSERT INTO fabric_approvals (sample_id, action, actor_id, actor_role, reason) VALUES (?,?,?,?,?)", sampleId, action, actor, role, reason);
    }
}
