package com.nirmaan.reimburse.claim;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** The search boxes on claim lists: code, team, vendor, category or description containing the text, ignoring case. */
public final class ClaimSearch {

    private ClaimSearch() {
    }

    public static List<ClaimRow> filter(List<ClaimRow> rows, String q) {
        if (q == null || q.isBlank()) {
            return rows;
        }
        String needle = q.trim().toLowerCase(Locale.ROOT);
        return rows.stream().filter(c -> Stream.of(c.publicCode(), c.teamName(), c.vendorName(), c.categoryName(), c.justification())
                .anyMatch(f -> f != null && f.toLowerCase(Locale.ROOT).contains(needle))).toList();
    }
}
