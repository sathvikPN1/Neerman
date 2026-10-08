package com.nirmaan.reimburse.claim;

/** Staff queue filters; all optional. {@code minAgeDays} filters on time in the current stage. */
public record QueueFilter(ClaimStatus status, Long teamId, Long cohortId, Long categoryId, Integer minAgeDays) {

    public static QueueFilter defaults() {
        return new QueueFilter(null, null, null, null, null);
    }
}
