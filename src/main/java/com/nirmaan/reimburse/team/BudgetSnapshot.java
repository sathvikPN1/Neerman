package com.nirmaan.reimburse.team;

import com.nirmaan.reimburse.common.money.Money;

import java.math.BigDecimal;

/**
 * Team budget position. remaining = total − (approved + paid). Pending (SUBMITTED + VERIFIED claimed amounts)
 * is shown separately and does not reduce remaining.
 */
public record BudgetSnapshot(BigDecimal total, BigDecimal approved, BigDecimal paid, BigDecimal pending) {

    public BudgetSnapshot {
        total = Money.orZero(total);
        approved = Money.orZero(approved);
        paid = Money.orZero(paid);
        pending = Money.orZero(pending);
    }

    public BigDecimal committed() {
        return approved.add(paid);
    }

    public BigDecimal remaining() {
        return total.subtract(committed());
    }

    /** Remaining after all pending claims were approved in full (may be negative). */
    public BigDecimal remainingAfterPending() {
        return remaining().subtract(pending);
    }

    public boolean exceedsRemaining(BigDecimal amount) {
        return amount != null && amount.compareTo(remaining()) > 0;
    }

    public int paidPercent() {
        return Money.percent(paid, total);
    }

    public int approvedPercent() {
        return Money.percent(approved, total);
    }

    public int pendingPercent() {
        return Math.min(Money.percent(pending, total), Math.max(0, 100 - paidPercent() - approvedPercent()));
    }
}
