package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.common.audit.AuditEntryView;
import com.nirmaan.reimburse.document.DocumentView;
import com.nirmaan.reimburse.payment.PaymentView;
import com.nirmaan.reimburse.team.BudgetSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record ClaimDetailView(
        Long id,
        String publicCode,
        Long teamId,
        String teamName,
        Long categoryId,
        String categoryName,
        BigDecimal claimedAmount,
        BigDecimal approvedAmount,
        LocalDate expenseDate,
        String vendorName,
        String vendorGstin,
        String invoiceNumber,
        LocalDate invoiceDate,
        String justification,
        ClaimStatus status,
        boolean priority,
        String priorityReason,
        boolean teamPriority,
        boolean priorityRequested,
        String priorityRequestReason,
        String decisionReason,
        String submittedByName,
        String verifiedByName,
        String approvedByName,
        Instant createdAt,
        Instant submittedAt,
        Instant stageEnteredAt,
        long version,
        List<DocumentView> documents,
        List<CommentView> comments,
        List<AuditEntryView> timeline,
        BudgetSnapshot budget,
        List<ClaimWarning> warnings,
        Set<ClaimAction> availableActions,
        boolean editable,
        boolean canFlagPriority,
        boolean canRequestPriority,
        PaymentView payment) {

    public boolean can(String action) {
        return availableActions.contains(ClaimAction.valueOf(action));
    }

    public boolean isPartial() {
        return approvedAmount != null && approvedAmount.compareTo(claimedAmount) < 0;
    }
}
