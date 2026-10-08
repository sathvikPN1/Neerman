package com.nirmaan.reimburse.team;

public record TeamSummary(
        Long id,
        String name,
        Long cohortId,
        String cohortName,
        Program program,
        boolean priority,
        String priorityReason,
        boolean active,
        int memberCount,
        BudgetSnapshot budget) {
}
