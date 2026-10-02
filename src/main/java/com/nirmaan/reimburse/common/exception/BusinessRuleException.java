package com.nirmaan.reimburse.common.exception;

/** A request that breaks a business rule. The message is safe to show to the user. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
