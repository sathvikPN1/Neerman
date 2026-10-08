package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.category.Category;
import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.team.BudgetSnapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Pure rules for live warnings. Kept free of I/O so they are easy to unit-test. */
public final class ClaimWarnings {

    private ClaimWarnings() {
    }

    public static List<ClaimWarning> evaluate(Category category, BigDecimal amount, BudgetSnapshot budget,
                                              boolean hasPreApproval) {
        List<ClaimWarning> warnings = new ArrayList<>();
        if (category != null) {
            if (!category.isAllowed()) {
                warnings.add(new ClaimWarning("CATEGORY_NOT_ALLOWED",
                        "\"" + category.getName() + "\" is not reimbursable. " + category.getDescription(), true));
            } else if (category.isRequiresPreApproval() && !hasPreApproval) {
                warnings.add(new ClaimWarning("PRE_APPROVAL_MISSING",
                        "\"" + category.getName() + "\" claims need an approved pre-approval before you can submit.", true));
            } else if (category.isPreApprovalRecommended() && !hasPreApproval) {
                warnings.add(new ClaimWarning("PRE_APPROVAL_RECOMMENDED",
                        "Pre-approval is recommended for \"" + category.getName() + "\".", false));
            }
            if (category.isAllowed() && category.isJustificationRequired()) {
                warnings.add(new ClaimWarning("JUSTIFICATION_REQUIRED",
                        "\"" + category.getName() + "\" needs a detailed justification (at least "
                                + ClaimStateMachine.OTHER_JUSTIFICATION_MIN + " characters).", false));
            }
        }
        if (amount != null && budget != null && budget.exceedsRemaining(amount)) {
            BigDecimal remaining = budget.remaining().max(Money.ZERO);
            warnings.add(new ClaimWarning("OVER_BUDGET",
                    "This is more than your remaining budget of " + Money.formatInr(remaining)
                            + ". The COO may approve it only partially.", false));
        }
        return warnings;
    }
}
