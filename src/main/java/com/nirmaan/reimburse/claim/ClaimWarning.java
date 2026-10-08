package com.nirmaan.reimburse.claim;

/** A warning shown on the claim form or review screens. {@code blocking} warnings prevent submission. */
public record ClaimWarning(String code, String message, boolean blocking) {
}
