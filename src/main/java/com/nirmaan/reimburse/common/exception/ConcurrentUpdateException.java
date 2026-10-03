package com.nirmaan.reimburse.common.exception;

/** Someone else changed the record since the user loaded it (optimistic locking). */
public class ConcurrentUpdateException extends BusinessRuleException {
    public ConcurrentUpdateException() {
        super("This item was changed by someone else a moment ago. Reload the page and try again.");
    }
}
