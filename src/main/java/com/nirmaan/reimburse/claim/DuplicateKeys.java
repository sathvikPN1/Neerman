package com.nirmaan.reimburse.claim;

import java.util.Locale;

/**
 * Normalises vendor names and invoice numbers so that "INV-0042", "inv 0042" and "INV/0042" compare equal.
 * Keep in step with the expressions in {@code V4__duplicate_protection.sql}: letters and digits only, upper case.
 */
public final class DuplicateKeys {

    private DuplicateKeys() {
    }

    /** The comparable form of a vendor name or invoice number, or {@code null} if nothing is left to compare. */
    public static String of(String value) {
        if (value == null) {
            return null;
        }
        String key = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return key.isEmpty() ? null : key;
    }
}
