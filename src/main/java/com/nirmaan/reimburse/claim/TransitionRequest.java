package com.nirmaan.reimburse.claim;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Input for a workflow transition.
 *
 * @param comment         reason / note (mandatory for RETURN, REJECT, REOPEN and partial approval)
 * @param approvedAmount  for APPROVE: amount to approve; null means the full claimed amount
 * @param expectedVersion claim version the user saw; null skips the check (system actions)
 * @param extra           additional audit detail (e.g. payment reference)
 */
public record TransitionRequest(String comment, BigDecimal approvedAmount, Long expectedVersion, Map<String, Object> extra) {

    public static TransitionRequest of(String comment, Long expectedVersion) {
        return new TransitionRequest(comment, null, expectedVersion, Map.of());
    }

    public static TransitionRequest approve(BigDecimal amount, String comment, Long expectedVersion) {
        return new TransitionRequest(comment, amount, expectedVersion, Map.of());
    }

    public boolean hasComment() {
        return comment != null && !comment.isBlank();
    }
}
