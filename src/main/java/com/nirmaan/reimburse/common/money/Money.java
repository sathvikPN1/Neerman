package com.nirmaan.reimburse.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** INR helpers. Money is always BigDecimal with scale 2. */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private Money() {
    }

    public static BigDecimal of(String value) {
        return normalise(new BigDecimal(value));
    }

    public static BigDecimal normalise(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? ZERO : normalise(value);
    }

    /** Indian digit grouping: ₹1,25,000.00 */
    public static String formatInr(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        BigDecimal a = normalise(amount);
        boolean negative = a.signum() < 0;
        String plain = a.abs().toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        String fraction = plain.substring(dot + 1);

        StringBuilder grouped = new StringBuilder();
        int len = whole.length();
        if (len <= 3) {
            grouped.append(whole);
        } else {
            String last3 = whole.substring(len - 3);
            String rest = whole.substring(0, len - 3);
            StringBuilder restGrouped = new StringBuilder();
            int i = rest.length();
            while (i > 2) {
                restGrouped.insert(0, "," + rest.substring(i - 2, i));
                i -= 2;
            }
            restGrouped.insert(0, rest.substring(0, i));
            grouped.append(restGrouped).append(',').append(last3);
        }
        return (negative ? "-₹" : "₹") + grouped + "." + fraction;
    }

    /** Percentage of part in total, 0–100, rounded to whole number. */
    public static int percent(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0 || part == null) {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }
}
