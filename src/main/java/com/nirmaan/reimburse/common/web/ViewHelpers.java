package com.nirmaan.reimburse.common.web;

import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.common.time.TimeFormats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/** Formatting helpers exposed to templates as {@code ${@fmt...}}. */
@Component("fmt")
public class ViewHelpers {

    private final Clock clock;

    public ViewHelpers(Clock clock) {
        this.clock = clock;
    }

    public String inr(BigDecimal amount) {
        return Money.formatInr(amount);
    }

    public String inrWhole(BigDecimal amount) {
        return Money.formatInrWhole(amount);
    }

    /** Whole-number percentage of part in total (0 when total is zero). */
    public int percent(BigDecimal part, BigDecimal total) {
        return Money.percent(part, total);
    }

    public String shortDate(LocalDate date) {
        return TimeFormats.shortDate(date);
    }

    /** Up to two initials for an avatar: "Anita Subramanian" → "AS". */
    public String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        return (parts.length == 1 ? first : first + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    /** Inline colours of a status tag in the redesigned screens (palette from the design handoff). */
    public String statusTagStyle(ClaimStatus status) {
        if (status == null) {
            return "background:#EEF1EF;color:#4A5650";
        }
        return switch (status) {
            case DRAFT -> "background:#EEF1EF;color:#4A5650";
            case SUBMITTED -> "background:#E7EFF9;color:#1F4F8A";
            case VERIFIED -> "background:#FFF3D6;color:#6B4600";
            case RETURNED -> "background:#FDEBDD;color:#7A3410";
            case APPROVED -> "background:var(--color-accent-100);color:var(--color-accent-800)";
            case PAID -> "background:#DDF2F2;color:#0B5A62";
            case REJECTED -> "background:#FBE4E5;color:#A3212B";
        };
    }

    public String date(LocalDate date) {
        return TimeFormats.date(date);
    }

    public String date(Instant instant) {
        return TimeFormats.date(instant);
    }

    public String dateTime(Instant instant) {
        return TimeFormats.dateTime(instant);
    }

    public String age(Instant since) {
        return TimeFormats.age(since, Instant.now(clock));
    }

    public LocalDate today() {
        return TimeFormats.todayIst();
    }

    /** Tailwind classes for a status chip. */
    public String statusClass(ClaimStatus status) {
        if (status == null) {
            return "bg-slate-100 text-slate-700";
        }
        return switch (status) {
            case DRAFT -> "bg-slate-100 text-slate-700";
            case SUBMITTED -> "bg-sky-100 text-sky-800";
            case VERIFIED -> "bg-indigo-100 text-indigo-800";
            case RETURNED -> "bg-amber-100 text-amber-900";
            case APPROVED -> "bg-emerald-100 text-emerald-800";
            case PAID -> "bg-green-600 text-white";
            case REJECTED -> "bg-rose-100 text-rose-800";
        };
    }

    public String humanise(String code) {
        if (code == null) {
            return "";
        }
        String s = code.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
