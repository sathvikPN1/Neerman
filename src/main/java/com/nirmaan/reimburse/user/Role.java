package com.nirmaan.reimburse.user;

public enum Role {
    COO("COO"),
    NIRMAAN_STAFF("Nirmaan staff"),
    FINANCE("Finance"),
    TEAM_MEMBER("Team member");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Roles the COO can assign when inviting staff (COO itself only via hand-over). */
    public boolean isStaffRole() {
        return this == NIRMAAN_STAFF || this == FINANCE;
    }
}
