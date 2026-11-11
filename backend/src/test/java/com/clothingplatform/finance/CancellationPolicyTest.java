package com.clothingplatform.finance;

import static com.clothingplatform.order.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CancellationPolicyTest {
    private static final BigDecimal FEE = new BigDecimal("10");

    @Test
    void fullRefundBeforeATailorIsAssigned() {
        for (var s : new com.clothingplatform.order.OrderStatus[]{ORDER_PLACED, PAYMENT_PENDING, PAYMENT_CONFIRMED, TAILOR_ASSIGNMENT_PENDING}) {
            var d = CancellationPolicy.forCustomer(s, FEE);
            assertTrue(d.customerMayCancel(), "" + s);
            assertEquals(0, new BigDecimal("100").compareTo(d.refundPercent()));
        }
    }

    @Test
    void feeAppliesAfterAssignmentUntilCuttingStarts() {
        for (var s : new com.clothingplatform.order.OrderStatus[]{TAILOR_ASSIGNED, FABRIC_SAMPLE_PENDING, FABRIC_SAMPLE_SUBMITTED, CUSTOMER_APPROVAL_PENDING, FABRIC_REJECTED, FABRIC_APPROVED}) {
            var d = CancellationPolicy.forCustomer(s, FEE);
            assertTrue(d.customerMayCancel(), "" + s);
            assertEquals(0, new BigDecimal("90").compareTo(d.refundPercent()));
        }
        assertEquals(0, new BigDecimal("1350.00").compareTo(CancellationPolicy.refundAmount(new BigDecimal("1500.00"), new BigDecimal("90"))));
    }

    @Test
    void noOnlineCancellationOnceFabricIsCut() {
        for (var s : new com.clothingplatform.order.OrderStatus[]{CUTTING_IN_PROGRESS, STITCHING_IN_PROGRESS, QUALITY_CHECK, READY_FOR_DISPATCH, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, REFUNDED})
            assertFalse(CancellationPolicy.forCustomer(s, FEE).customerMayCancel(), "" + s);
    }

    @Test
    void refundIsNeverMoreThanWasPaid() {
        assertEquals(0, new BigDecimal("100.00").compareTo(CancellationPolicy.refundAmount(new BigDecimal("100.00"), new BigDecimal("100"))));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(CancellationPolicy.refundAmount(new BigDecimal("100.00"), BigDecimal.ZERO)));
    }
}
