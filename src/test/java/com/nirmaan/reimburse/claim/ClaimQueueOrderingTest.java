package com.nirmaan.reimburse.claim;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimQueueOrderingTest {

    static ClaimRow row(long id, boolean priority, String stageEntered) {
        return new ClaimRow(id, "NRM-" + id, 1L, "Team", "Cohort", "Parts", "Vendor", BigDecimal.TEN, null,
                ClaimStatus.SUBMITTED, priority, null, false, null, null,
                stageEntered == null ? null : Instant.parse(stageEntered), null, 0);
    }

    @Test
    void priorityFirstThenOldestFirst() {
        List<ClaimRow> rows = new ArrayList<>(List.of(
                row(1, false, "2026-09-01T00:00:00Z"),
                row(2, true, "2026-09-20T00:00:00Z"),
                row(3, false, "2026-08-15T00:00:00Z"),
                row(4, true, "2026-09-05T00:00:00Z"),
                row(5, false, null)));
        rows.sort(ClaimQueueOrdering.PRIORITY_THEN_OLDEST);
        assertThat(rows).extracting(ClaimRow::id).containsExactly(4L, 2L, 3L, 1L, 5L);
    }

    @Test
    void ties_areBrokenById() {
        List<ClaimRow> rows = new ArrayList<>(List.of(row(9, false, "2026-09-01T00:00:00Z"), row(7, false, "2026-09-01T00:00:00Z")));
        rows.sort(ClaimQueueOrdering.PRIORITY_THEN_OLDEST);
        assertThat(rows).extracting(ClaimRow::id).containsExactly(7L, 9L);
    }
}
