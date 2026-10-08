package com.nirmaan.reimburse.claim;

public enum ClaimStatus {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    VERIFIED("Verified"),
    RETURNED("Returned"),
    APPROVED("Approved"),
    PAID("Paid"),
    REJECTED("Rejected");

    private final String label;

    ClaimStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Team can edit the claim's data and documents. */
    public boolean isEditableByTeam() {
        return this == DRAFT || this == RETURNED;
    }

    public boolean isTerminal() {
        return this == PAID || this == REJECTED;
    }
}
