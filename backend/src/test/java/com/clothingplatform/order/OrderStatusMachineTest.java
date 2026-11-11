package com.clothingplatform.order;

import static com.clothingplatform.order.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrderStatusMachineTest {
    @Test
    void cuttingRequiresApprovedFabric() {
        for (OrderStatus s : OrderStatus.values())
            assertEquals(s == FABRIC_APPROVED, OrderStatusMachine.allowed(s, CUTTING_IN_PROGRESS), "from " + s);
    }

    @Test
    void happyPathIsContinuous() {
        OrderStatus[] path = {ORDER_PLACED, PAYMENT_PENDING, PAYMENT_CONFIRMED, TAILOR_ASSIGNMENT_PENDING, TAILOR_ASSIGNED, FABRIC_SAMPLE_PENDING, FABRIC_SAMPLE_SUBMITTED,
            CUSTOMER_APPROVAL_PENDING, FABRIC_APPROVED, CUTTING_IN_PROGRESS, STITCHING_IN_PROGRESS, QUALITY_CHECK, READY_FOR_DISPATCH, SHIPPED, OUT_FOR_DELIVERY, DELIVERED};
        for (int i = 0; i + 1 < path.length; i++) assertTrue(OrderStatusMachine.allowed(path[i], path[i + 1]), path[i] + " -> " + path[i + 1]);
    }

    @Test
    void codSkipsPaymentSteps() { assertTrue(OrderStatusMachine.allowed(ORDER_PLACED, TAILOR_ASSIGNMENT_PENDING)); }

    @Test
    void rejectedFabricNeedsARevisedSampleAndNoStatusSkipsAhead() {
        assertTrue(OrderStatusMachine.allowed(FABRIC_REJECTED, FABRIC_SAMPLE_SUBMITTED));
        assertFalse(OrderStatusMachine.allowed(FABRIC_REJECTED, CUTTING_IN_PROGRESS));
        assertFalse(OrderStatusMachine.allowed(PAYMENT_PENDING, TAILOR_ASSIGNED));
        assertFalse(OrderStatusMachine.allowed(DELIVERED, SHIPPED));
    }

    @Test
    void finalStatesHaveNoExits() {
        assertTrue(OrderStatusMachine.next(REFUNDED).isEmpty());
        assertFalse(OrderStatusMachine.allowed(CANCELLED, ORDER_PLACED));
    }

    @Test
    void reassignmentIsPossibleOnlyBeforeASampleExists() {
        assertTrue(OrderStatusMachine.allowed(TAILOR_ASSIGNED, TAILOR_ASSIGNMENT_PENDING));
        assertTrue(OrderStatusMachine.allowed(FABRIC_SAMPLE_PENDING, TAILOR_ASSIGNMENT_PENDING));
        assertFalse(OrderStatusMachine.allowed(FABRIC_SAMPLE_SUBMITTED, TAILOR_ASSIGNMENT_PENDING));
        assertFalse(OrderStatusMachine.allowed(CUTTING_IN_PROGRESS, TAILOR_ASSIGNMENT_PENDING));
    }

    @Test
    void cancellationReachesIntoProductionButNeverPastShipping() {
        for (OrderStatus s : new OrderStatus[]{ORDER_PLACED, PAYMENT_PENDING, PAYMENT_CONFIRMED, TAILOR_ASSIGNMENT_PENDING, TAILOR_ASSIGNED, FABRIC_SAMPLE_PENDING, FABRIC_SAMPLE_SUBMITTED,
                CUSTOMER_APPROVAL_PENDING, FABRIC_REJECTED, FABRIC_APPROVED, CUTTING_IN_PROGRESS, STITCHING_IN_PROGRESS, QUALITY_CHECK, READY_FOR_DISPATCH})
            assertTrue(OrderStatusMachine.allowed(s, CANCELLED), "from " + s);
        for (OrderStatus s : new OrderStatus[]{SHIPPED, OUT_FOR_DELIVERY, DELIVERED, REFUNDED}) assertFalse(OrderStatusMachine.allowed(s, CANCELLED), "from " + s);
    }

    @Test
    void rejectedRefundReturnsTheOrderToWhereItWas() {
        assertTrue(OrderStatusMachine.allowed(REFUND_PENDING, DELIVERED));
        assertTrue(OrderStatusMachine.allowed(REFUND_PENDING, CANCELLED));
        assertTrue(OrderStatusMachine.allowed(REFUND_PENDING, REFUNDED));
        assertFalse(OrderStatusMachine.allowed(REFUND_PENDING, SHIPPED));
    }
}
