package com.nirmaan.reimburse.document;

public enum DocumentType {
    INVOICE("Invoice"),
    PAYMENT_PROOF("Payment proof"),
    OTHER("Other attachment");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
