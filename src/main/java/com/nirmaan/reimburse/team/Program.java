package com.nirmaan.reimburse.team;

public enum Program {
    PRATHAM("Pratham"),
    AKSHAR("Akshar");

    private final String label;

    Program(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Settings key holding the default budget for this program. */
    public String budgetSettingKey() {
        return "budget.default." + name().toLowerCase();
    }
}
