package com.nirmaan.reimburse.common.exception;

/** A claim workflow transition that is not allowed from the current state. */
public class IllegalTransitionException extends BusinessRuleException {
    public IllegalTransitionException(String message) {
        super(message);
    }
}
