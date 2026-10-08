package com.nirmaan.reimburse.claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One line in a queue or list. {@code priority} is effective priority (claim or team). */
public record ClaimRow(
        Long id,
        String publicCode,
        Long teamId,
        String teamName,
        String cohortName,
        String categoryName,
        String vendorName,
        BigDecimal claimedAmount,
        BigDecimal approvedAmount,
        ClaimStatus status,
        boolean priority,
        String priorityReason,
        boolean priorityRequested,
        LocalDate expenseDate,
        Instant submittedAt,
        Instant stageEnteredAt,
        String verifiedByName,
        long version) {
}
