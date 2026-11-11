package com.clothingplatform.fulfilment;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.NotificationService;
import com.clothingplatform.order.OrderService;
import com.clothingplatform.order.OrderStatus;
import com.clothingplatform.storage.StorageService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * The tailor's production steps. A tailor may only move their own order one step at a time:
 * FABRIC_APPROVED -> cutting -> stitching -> quality check -> ready for dispatch (photos of the finished garment required);
 * quality check may go back to stitching for rework. Starting to cut is impossible before the customer approved the fabric,
 * because FABRIC_APPROVED is the only status cutting can follow (the order state machine enforces it too).
 */
@Service
public class ProgressService {
    public record ProgressRequest(@NotBlank String status, @Size(max = 500) String note, @Size(max = 6) List<String> photoUrls) {}

    private static final Map<OrderStatus, Set<OrderStatus>> TAILOR_MOVES = Map.of(
            OrderStatus.FABRIC_APPROVED, Set.of(OrderStatus.CUTTING_IN_PROGRESS),
            OrderStatus.CUTTING_IN_PROGRESS, Set.of(OrderStatus.STITCHING_IN_PROGRESS),
            OrderStatus.STITCHING_IN_PROGRESS, Set.of(OrderStatus.QUALITY_CHECK),
            OrderStatus.QUALITY_CHECK, Set.of(OrderStatus.READY_FOR_DISPATCH, OrderStatus.STITCHING_IN_PROGRESS));
    public static boolean tailorMayMove(OrderStatus from, OrderStatus to) { return TAILOR_MOVES.getOrDefault(from, Set.of()).contains(to); }

    private static final Pattern PHOTO = Pattern.compile("^/media/completed/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|gif|webp)$");
    public static boolean validPhotoUrl(String u) { return u != null && PHOTO.matcher(u).matches(); }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OrderService orders;
    private final NotificationService notifications;
    private final AuditService audit;
    private final StorageService storage;
    public ProgressService(JdbcTemplate jdbc, TransactionTemplate tx, OrderService orders, NotificationService notifications, AuditService audit, StorageService storage) {
        this.jdbc = jdbc; this.tx = tx; this.orders = orders; this.notifications = notifications; this.audit = audit; this.storage = storage;
    }

    /** Photos of the finished garment: only for the tailor's own order, only at the quality-check step. */
    public String uploadPhoto(long tailorId, long orderId, MultipartFile file) {
        Map<String, Object> o = jdbc.queryForList("SELECT status, tailor_id FROM orders WHERE id = ?", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
        if (o.get("tailor_id") == null || ((Number) o.get("tailor_id")).longValue() != tailorId) throw notFound("Order");
        if (!OrderStatus.QUALITY_CHECK.name().equals(o.get("status"))) throw new BusinessException("NOT_AT_QUALITY_CHECK", "Photos of the finished garment are added when you mark it ready for dispatch.", HttpStatus.CONFLICT);
        return storage.storeImage("completed", file);
    }

    public void advance(long tailorId, long orderId, ProgressRequest r) {
        OrderStatus to;
        try { to = OrderStatus.valueOf(r.status()); } catch (IllegalArgumentException e) { throw bad("Unknown step."); }
        List<String> photos = r.photoUrls() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(r.photoUrls()));
        for (String u : photos) if (!validPhotoUrl(u)) throw bad("Use the photo upload button; only photos uploaded here can be attached.");
        tx.executeWithoutResult(s -> {
            Map<String, Object> o = jdbc.queryForList("SELECT status, tailor_id, user_id, order_number FROM orders WHERE id = ? FOR UPDATE", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
            if (o.get("tailor_id") == null || ((Number) o.get("tailor_id")).longValue() != tailorId) throw notFound("Order");
            OrderStatus from = OrderStatus.valueOf((String) o.get("status"));
            if (!tailorMayMove(from, to)) throw new BusinessException("STEP_NOT_ALLOWED", "This order cannot move from \"" + label(from) + "\" to \"" + label(to) + "\" right now.", HttpStatus.CONFLICT);
            if (to == OrderStatus.READY_FOR_DISPATCH && photos.isEmpty()) throw bad("Add at least one photo of the finished garment.");
            if (to != OrderStatus.READY_FOR_DISPATCH && !photos.isEmpty()) throw bad("Photos are only attached when marking the garment ready for dispatch.");
            String note = r.note() == null || r.note().isBlank() ? defaultNote(from, to) : (from == OrderStatus.QUALITY_CHECK && to == OrderStatus.STITCHING_IN_PROGRESS ? "Back to stitching for rework: " : "") + r.note().trim();
            orders.transition(orderId, to, note, tailorId, photos);
            long customer = ((Number) o.get("user_id")).longValue();
            String number = (String) o.get("order_number");
            notifications.notifyUser(customer, "ORDER_PROGRESS", customerTitle(to), "Order " + number + ": " + customerBody(to), "/account/orders/" + orderId);
            if (to == OrderStatus.READY_FOR_DISPATCH)
                notifications.notifyPermissionHolders("DELIVERY_MANAGE", "READY_FOR_DISPATCH", "Order " + number + " is ready to ship", "The tailor finished the garment. Arrange dispatch.", "/admin/orders/" + orderId);
            audit.audit(tailorId, "ORDER_PROGRESS_" + to.name(), "ORDER", orderId, from.name(), to.name(), r.note());
        });
    }

    private static String label(OrderStatus s) { return s.name().replace('_', ' ').toLowerCase(); }
    private static String defaultNote(OrderStatus from, OrderStatus to) {
        return switch (to) {
            case CUTTING_IN_PROGRESS -> "Cutting has started";
            case STITCHING_IN_PROGRESS -> from == OrderStatus.QUALITY_CHECK ? "Back to stitching for rework" : "Stitching has started";
            case QUALITY_CHECK -> "Stitching finished. Quality check";
            case READY_FOR_DISPATCH -> "Finished and ready for dispatch";
            default -> label(to);
        };
    }
    private static String customerTitle(OrderStatus to) {
        return switch (to) {
            case CUTTING_IN_PROGRESS -> "Cutting has started";
            case STITCHING_IN_PROGRESS -> "Stitching in progress";
            case QUALITY_CHECK -> "Quality check";
            case READY_FOR_DISPATCH -> "Your garment is finished";
            default -> "Order update";
        };
    }
    private static String customerBody(OrderStatus to) {
        return switch (to) {
            case CUTTING_IN_PROGRESS -> "your tailor has started cutting the approved fabric.";
            case STITCHING_IN_PROGRESS -> "your tailor is stitching your garment.";
            case QUALITY_CHECK -> "stitching is done and your garment is being checked.";
            case READY_FOR_DISPATCH -> "it is ready for dispatch. See the photos on your order page.";
            default -> "status updated.";
        };
    }
}
