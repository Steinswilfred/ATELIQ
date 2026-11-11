package com.clothingplatform.finance;

import com.clothingplatform.order.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * What a customer may do and what they get back.
 *  - before a tailor is assigned: cancel freely, full refund of what was paid;
 *  - after assignment but before cutting: cancel, refund minus the late-cancellation fee (a percentage of what was paid);
 *  - once cutting has begun: no online cancellation (the fabric is cut); an administrator decides case by case.
 */
public final class CancellationPolicy {
    private CancellationPolicy() {}

    public record Decision(boolean customerMayCancel, BigDecimal refundPercent, String stage) {}

    private static final Set<OrderStatus> BEFORE_TAILOR = Set.of(OrderStatus.ORDER_PLACED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_CONFIRMED, OrderStatus.TAILOR_ASSIGNMENT_PENDING);
    private static final Set<OrderStatus> BEFORE_CUTTING = Set.of(OrderStatus.TAILOR_ASSIGNED, OrderStatus.FABRIC_SAMPLE_PENDING, OrderStatus.FABRIC_SAMPLE_SUBMITTED,
            OrderStatus.CUSTOMER_APPROVAL_PENDING, OrderStatus.FABRIC_REJECTED, OrderStatus.FABRIC_APPROVED);

    public static Decision forCustomer(OrderStatus status, BigDecimal lateFeePercent) {
        if (BEFORE_TAILOR.contains(status)) return new Decision(true, BigDecimal.valueOf(100), "BEFORE_TAILOR");
        if (BEFORE_CUTTING.contains(status)) return new Decision(true, BigDecimal.valueOf(100).subtract(lateFeePercent), "BEFORE_CUTTING");
        return new Decision(false, BigDecimal.ZERO, "IN_PRODUCTION_OR_LATER");
    }

    public static BigDecimal refundAmount(BigDecimal paid, BigDecimal refundPercent) {
        return paid.multiply(refundPercent).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP).min(paid);
    }
}
