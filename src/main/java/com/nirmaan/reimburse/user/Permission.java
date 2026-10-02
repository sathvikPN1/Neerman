package com.nirmaan.reimburse.user;

/**
 * Fine-grained permissions. Roles are only default bundles of these.
 * Must stay in sync with the {@code permissions} table (see V2 migration and {@link PermissionCatalogCheck}).
 */
public enum Permission {
    CLAIM_VERIFY("Verify claims and forward them to the COO"),
    CLAIM_RETURN_REJECT("Send claims back to the team or reject them"),
    CLAIM_APPROVE("Approve claims on the COO's behalf"),
    CLAIM_FLAG_PRIORITY("Mark claims or teams as priority"),
    PREAPPROVAL_DECIDE("Approve or reject pre-approval requests"),
    TEAM_MANAGE("Create and edit teams and members, set budgets, promote Pratham to Akshar"),
    COHORT_MANAGE("Create and edit cohorts"),
    BANK_DETAILS_VIEW("See full bank account numbers"),
    BANK_DETAILS_VERIFY("Verify team bank details"),
    PAYMENT_RECORD("Mark claims paid, create payout batches"),
    REPORT_VIEW_EXPORT("View analytics, export reports"),
    AUDIT_LOG_VIEW("View the global audit log"),
    CATEGORY_MANAGE("Edit expense categories and rules"),
    SETTINGS_MANAGE("Edit thresholds, SLAs, email templates"),
    STAFF_MANAGE("Invite staff and edit their permissions (COO only, cannot be granted)");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** STAFF_MANAGE is implied by the COO role and can never be granted to anyone. */
    public boolean isGrantable() {
        return this != STAFF_MANAGE;
    }
}
