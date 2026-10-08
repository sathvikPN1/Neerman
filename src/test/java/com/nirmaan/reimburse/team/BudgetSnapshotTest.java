package com.nirmaan.reimburse.team;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetSnapshotTest {

    static BudgetSnapshot of(String total, String approved, String paid, String pending) {
        return new BudgetSnapshot(new BigDecimal(total), new BigDecimal(approved), new BigDecimal(paid), new BigDecimal(pending));
    }

    @Test
    void remainingIsBudgetMinusApprovedAndPaid() {
        BudgetSnapshot b = of("200000", "30000", "50000", "40000");
        assertThat(b.committed()).isEqualByComparingTo("80000");
        assertThat(b.remaining()).isEqualByComparingTo("120000");
        // pending does not reduce remaining, but is shown separately
        assertThat(b.remainingAfterPending()).isEqualByComparingTo("80000");
    }

    @Test
    void exceedsRemaining() {
        BudgetSnapshot b = of("200000", "150000", "40000", "0");
        assertThat(b.remaining()).isEqualByComparingTo("10000");
        assertThat(b.exceedsRemaining(new BigDecimal("10000"))).isFalse();
        assertThat(b.exceedsRemaining(new BigDecimal("10000.01"))).isTrue();
        assertThat(b.exceedsRemaining(null)).isFalse();
    }

    @Test
    void nullsAreZeroAndScaleIsTwo() {
        BudgetSnapshot b = new BudgetSnapshot(new BigDecimal("500000"), null, null, null);
        assertThat(b.remaining()).isEqualByComparingTo("500000");
        assertThat(b.paid().scale()).isEqualTo(2);
    }

    @Test
    void meterPercentagesNeverExceedHundred() {
        BudgetSnapshot b = of("100000", "50000", "40000", "90000");
        assertThat(b.paidPercent()).isEqualTo(40);
        assertThat(b.approvedPercent()).isEqualTo(50);
        assertThat(b.pendingPercent()).isEqualTo(10);
        assertThat(b.paidPercent() + b.approvedPercent() + b.pendingPercent()).isLessThanOrEqualTo(100);
    }
}
