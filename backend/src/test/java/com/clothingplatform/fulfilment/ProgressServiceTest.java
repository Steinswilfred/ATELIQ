package com.clothingplatform.fulfilment;

import static com.clothingplatform.order.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProgressServiceTest {
    @Test
    void tailorMovesOneStepAtATimeAndNeverSkipsFabricApproval() {
        assertTrue(ProgressService.tailorMayMove(FABRIC_APPROVED, CUTTING_IN_PROGRESS));
        assertTrue(ProgressService.tailorMayMove(CUTTING_IN_PROGRESS, STITCHING_IN_PROGRESS));
        assertTrue(ProgressService.tailorMayMove(STITCHING_IN_PROGRESS, QUALITY_CHECK));
        assertTrue(ProgressService.tailorMayMove(QUALITY_CHECK, READY_FOR_DISPATCH));
        assertTrue(ProgressService.tailorMayMove(QUALITY_CHECK, STITCHING_IN_PROGRESS));
        for (var from : new com.clothingplatform.order.OrderStatus[]{FABRIC_SAMPLE_PENDING, CUSTOMER_APPROVAL_PENDING, FABRIC_REJECTED, TAILOR_ASSIGNED})
            assertFalse(ProgressService.tailorMayMove(from, CUTTING_IN_PROGRESS), "from " + from);
        assertFalse(ProgressService.tailorMayMove(CUTTING_IN_PROGRESS, QUALITY_CHECK));
        assertFalse(ProgressService.tailorMayMove(READY_FOR_DISPATCH, SHIPPED));           // shipping is the admin's step
        assertFalse(ProgressService.tailorMayMove(SHIPPED, DELIVERED));
    }

    @Test
    void completedPhotosMustBeServerStoredImages() {
        assertTrue(ProgressService.validPhotoUrl("/media/completed/123e4567-e89b-12d3-a456-426614174000.png"));
        assertFalse(ProgressService.validPhotoUrl("/media/samples/123e4567-e89b-12d3-a456-426614174000.png"));
        assertFalse(ProgressService.validPhotoUrl("https://x.example/a.png"));
        assertFalse(ProgressService.validPhotoUrl(null));
    }
}
