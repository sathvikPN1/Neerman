package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.category.Category;
import com.nirmaan.reimburse.team.BudgetSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimWarningsTest {

    static Category cat(boolean allowed, boolean pre, boolean recommended, boolean justification) {
        Category c = new Category("C", allowed, pre, "rule text");
        c.update("C", allowed, pre, recommended, justification, "rule text", 0, true);
        return c;
    }

    static final BudgetSnapshot BUDGET = new BudgetSnapshot(new BigDecimal("200000"), new BigDecimal("195000"), BigDecimal.ZERO, BigDecimal.ZERO);

    @Test
    void disallowedCategoryBlocksAndExplainsTheRule() {
        List<ClaimWarning> w = ClaimWarnings.evaluate(cat(false, false, false, false), BigDecimal.ONE, BUDGET, false);
        assertThat(w).extracting(ClaimWarning::code).containsExactly("CATEGORY_NOT_ALLOWED");
        assertThat(w.getFirst().blocking()).isTrue();
        assertThat(w.getFirst().message()).contains("rule text");
    }

    @Test
    void missingPreApprovalBlocks() {
        assertThat(ClaimWarnings.evaluate(cat(true, true, false, false), BigDecimal.ONE, BUDGET, false))
                .extracting(ClaimWarning::code, ClaimWarning::blocking).containsExactly(org.assertj.core.groups.Tuple.tuple("PRE_APPROVAL_MISSING", true));
        assertThat(ClaimWarnings.evaluate(cat(true, true, false, false), BigDecimal.ONE, BUDGET, true)).isEmpty();
    }

    @Test
    void recommendedPreApprovalAndJustificationAreNonBlockingNotes() {
        assertThat(ClaimWarnings.evaluate(cat(true, false, true, true), BigDecimal.ONE, BUDGET, false))
                .extracting(ClaimWarning::code).containsExactly("PRE_APPROVAL_RECOMMENDED", "JUSTIFICATION_REQUIRED");
    }

    @Test
    void overBudgetIsAWarningNotABlock() {
        List<ClaimWarning> w = ClaimWarnings.evaluate(cat(true, false, false, false), new BigDecimal("6000"), BUDGET, false);
        assertThat(w).extracting(ClaimWarning::code).containsExactly("OVER_BUDGET");
        assertThat(w.getFirst().blocking()).isFalse();
        assertThat(w.getFirst().message()).contains("₹5,000.00");
    }
}
