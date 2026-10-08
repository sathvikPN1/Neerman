package com.nirmaan.reimburse.claim;

import java.util.EnumSet;
import java.util.Set;

import static com.nirmaan.reimburse.claim.ClaimStatus.APPROVED;
import static com.nirmaan.reimburse.claim.ClaimStatus.DRAFT;
import static com.nirmaan.reimburse.claim.ClaimStatus.PAID;
import static com.nirmaan.reimburse.claim.ClaimStatus.REJECTED;
import static com.nirmaan.reimburse.claim.ClaimStatus.RETURNED;
import static com.nirmaan.reimburse.claim.ClaimStatus.SUBMITTED;
import static com.nirmaan.reimburse.claim.ClaimStatus.VERIFIED;

/**
 * Workflow actions with their legal source states and target state.
 * Who may perform each action is decided in {@link ClaimStateMachine}.
 */
public enum ClaimAction {
    /** Team submits a draft or resubmits a returned claim. */
    SUBMIT(EnumSet.of(DRAFT, RETURNED), SUBMITTED, false),
    /** Staff verify and forward to the COO. */
    VERIFY(EnumSet.of(SUBMITTED), VERIFIED, false),
    /** Send back to the team for changes. */
    RETURN(EnumSet.of(SUBMITTED, VERIFIED), RETURNED, true),
    /** Final rejection. */
    REJECT(EnumSet.of(SUBMITTED, VERIFIED), REJECTED, true),
    /**
     * Approve in full or partially (approved amount below the claimed amount, reason required).
     * From SUBMITTED only the COO may approve, verifying in the same step.
     */
    APPROVE(EnumSet.of(VERIFIED, SUBMITTED), APPROVED, false),
    /** Finance records the payment. */
    MARK_PAID(EnumSet.of(APPROVED), PAID, false),
    /** COO override: reopen a rejected or approved (unpaid) claim for review again. */
    REOPEN(EnumSet.of(REJECTED, APPROVED), SUBMITTED, true);

    private final Set<ClaimStatus> from;
    private final ClaimStatus to;
    private final boolean reasonRequired;

    ClaimAction(Set<ClaimStatus> from, ClaimStatus to, boolean reasonRequired) {
        this.from = from;
        this.to = to;
        this.reasonRequired = reasonRequired;
    }

    public Set<ClaimStatus> from() {
        return EnumSet.copyOf(from);
    }

    public ClaimStatus to() {
        return to;
    }

    public boolean reasonRequired() {
        return reasonRequired;
    }

    public boolean isAllowedFrom(ClaimStatus status) {
        return from.contains(status);
    }
}
