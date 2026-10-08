package com.nirmaan.reimburse.claim;

import java.time.Instant;
import java.util.Comparator;

/** Queue order everywhere: priority items first, then oldest in the current stage first. */
public final class ClaimQueueOrdering {

    public static final Comparator<ClaimRow> PRIORITY_THEN_OLDEST = Comparator
            .comparing(ClaimRow::priority, Comparator.reverseOrder())
            .thenComparing(ClaimRow::stageEnteredAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparing(ClaimRow::id);

    private ClaimQueueOrdering() {
    }
}
