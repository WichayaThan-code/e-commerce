package com.thaishopfun.order;

import java.util.Set;

/** refunds.status (V26 ck_refunds_status). PENDING / SUCCEEDED / FAILED are V1 gateway-refund values, kept for compatibility. */
public enum RefundStatus {
    REQUESTED, APPROVED, PROOF_UPLOADED, COMPLETED, REJECTED, DISPUTED, PENDING, SUCCEEDED, FAILED;

    /** Waiting for the shop (blocks a second refund request on the same order). */
    public static final Set<RefundStatus> OPEN = Set.of(REQUESTED, APPROVED);
    /** Money has been sent back by the shop (proof uploaded); counts against the refundable amount. */
    public static final Set<RefundStatus> REFUNDED = Set.of(PROOF_UPLOADED, COMPLETED, DISPUTED, SUCCEEDED);
    /** Counts against the refundable amount (open or refunded). */
    public static final Set<RefundStatus> RESERVING = Set.of(REQUESTED, APPROVED, PROOF_UPLOADED, COMPLETED, DISPUTED, SUCCEEDED, PENDING);
}
