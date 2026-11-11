package com.clothingplatform.order;

import static com.clothingplatform.order.OrderStatus.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The only definition of which order status may follow which. Every status change goes through {@link #allowed}.
 * Key business rule: cutting can only start from FABRIC_APPROVED, so a tailor cannot cut before the customer approves the fabric.
 */
public final class OrderStatusMachine {
    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = new EnumMap<>(OrderStatus.class);
    static {
        NEXT.put(ORDER_PLACED, Set.of(PAYMENT_PENDING, TAILOR_ASSIGNMENT_PENDING /* cash on delivery */, CANCELLED));
        NEXT.put(PAYMENT_PENDING, Set.of(PAYMENT_CONFIRMED, CANCELLED));
        NEXT.put(PAYMENT_CONFIRMED, Set.of(TAILOR_ASSIGNMENT_PENDING, CANCELLED));
        NEXT.put(TAILOR_ASSIGNMENT_PENDING, Set.of(TAILOR_ASSIGNED, CANCELLED));
        NEXT.put(TAILOR_ASSIGNED, Set.of(FABRIC_SAMPLE_PENDING, TAILOR_ASSIGNMENT_PENDING /* admin reassignment */, CANCELLED));
        NEXT.put(FABRIC_SAMPLE_PENDING, Set.of(FABRIC_SAMPLE_SUBMITTED, TAILOR_ASSIGNMENT_PENDING /* admin reassignment before any sample exists */, CANCELLED));
        NEXT.put(FABRIC_SAMPLE_SUBMITTED, Set.of(CUSTOMER_APPROVAL_PENDING, CANCELLED));
        NEXT.put(CUSTOMER_APPROVAL_PENDING, Set.of(FABRIC_APPROVED, FABRIC_REJECTED, CANCELLED));
        NEXT.put(FABRIC_REJECTED, Set.of(FABRIC_SAMPLE_SUBMITTED /* revised sample */, CANCELLED));
        NEXT.put(FABRIC_APPROVED, Set.of(CUTTING_IN_PROGRESS, CANCELLED));
        NEXT.put(CUTTING_IN_PROGRESS, Set.of(STITCHING_IN_PROGRESS, CANCELLED /* admin only */));
        NEXT.put(STITCHING_IN_PROGRESS, Set.of(QUALITY_CHECK, CANCELLED /* admin only */));
        NEXT.put(QUALITY_CHECK, Set.of(READY_FOR_DISPATCH, STITCHING_IN_PROGRESS /* rework */, CANCELLED /* admin only */));
        NEXT.put(READY_FOR_DISPATCH, Set.of(SHIPPED, CANCELLED /* admin only */));
        NEXT.put(SHIPPED, Set.of(OUT_FOR_DELIVERY));
        NEXT.put(OUT_FOR_DELIVERY, Set.of(DELIVERED));
        NEXT.put(DELIVERED, Set.of(REFUND_PENDING));
        NEXT.put(CANCELLED, Set.of(REFUND_PENDING));
        NEXT.put(REFUND_PENDING, Set.of(REFUNDED, DELIVERED /* refund rejected, order had been delivered */, CANCELLED /* refund rejected, order had been cancelled */));
        NEXT.put(REFUNDED, Set.of());
    }

    /** SQL list of statuses in which an order no longer counts as work in progress for a tailor. */
    public static final String INACTIVE_SQL = "('DELIVERED','CANCELLED','REFUND_PENDING','REFUNDED')";

    private OrderStatusMachine() {}

    public static boolean allowed(OrderStatus from, OrderStatus to) { return NEXT.getOrDefault(from, Set.of()).contains(to); }
    public static Set<OrderStatus> next(OrderStatus from) { return NEXT.getOrDefault(from, Set.of()); }
}
