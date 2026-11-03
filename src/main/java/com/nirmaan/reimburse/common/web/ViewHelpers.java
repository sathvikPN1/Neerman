package com.nirmaan.reimburse.common.web;

import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.common.time.TimeFormats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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

    /** Width of the "this claim" segment of a budget bar: the claim's share, capped at what is left of 100%. */
    public int addedPercent(BigDecimal amount, BigDecimal total, int usedPercent) {
        return Math.min(Money.percent(amount, total), Math.max(0, 100 - usedPercent));
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

    /** Icon for an audit-log action, e.g. APPROVED → a tick. First matching keyword wins. */
    public String auditIcon(String action) {
        return iconFor(action == null ? "" : action, AUDIT_ICONS, "icon-dot");
    }

    /** Icon for a notification, chosen from words in its text. */
    public String notificationIcon(String message) {
        return iconFor(message == null ? "" : message.toLowerCase(Locale.ROOT), NOTIFICATION_ICONS, "icon-bell");
    }

    private static String iconFor(String text, Map<String, String> icons, String fallback) {
        return icons.entrySet().stream().filter(e -> text.contains(e.getKey())).map(Map.Entry::getValue)
                .findFirst().orElse(fallback);
    }

    private static final Map<String, String> AUDIT_ICONS = linked(
            "PAID", "icon-banknote", "PAYMENT", "icon-banknote", "APPROV", "icon-check", "RETURN", "icon-undo-2",
            "REJECT", "icon-ban", "SUBMIT", "icon-send", "PRIORITY", "icon-flag", "PERMISSION", "icon-shield",
            "COO", "icon-shield", "INVITE", "icon-user-plus", "MEMBER", "icon-user-plus", "USER", "icon-user-plus",
            "DOCUMENT", "icon-paperclip", "COMMENT", "icon-message-square", "VERIF", "icon-clipboard-check");

    private static final Map<String, String> NOTIFICATION_ICONS = linked(
            "paid", "icon-banknote", "priority", "icon-flag", "returned", "icon-undo-2", "rejected", "icon-undo-2",
            "approved", "icon-circle-check", "message", "icon-message-square", "replied", "icon-message-square",
            "budget", "icon-triangle-alert", "submitted", "icon-inbox", "verified", "icon-inbox");

    private static Map<String, String> linked(String... keyThenValue) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyThenValue.length; i += 2) {
            map.put(keyThenValue[i], keyThenValue[i + 1]);
        }
        return map;
    }

    public String humanise(String code) {
        if (code == null) {
            return "";
        }
        String s = code.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
