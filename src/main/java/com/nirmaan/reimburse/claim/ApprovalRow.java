package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.team.BudgetSnapshot;

/** A row on the COO approval screen, with what is needed when the row is expanded. */
public record ApprovalRow(ClaimRow row, String justification, Long invoiceDocumentId, boolean invoiceIsImage,
                          BudgetSnapshot budget, boolean exceedsBudget, boolean verifiedByMe) {
}
