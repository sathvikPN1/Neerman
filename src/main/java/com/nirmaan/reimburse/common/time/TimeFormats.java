package com.nirmaan.reimburse.common.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Timestamps are stored in UTC and shown in Asia/Kolkata. */
public final class TimeFormats {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private TimeFormats() {
    }

    public static String date(LocalDate date) {
        return date == null ? "—" : DATE.format(date);
    }

    public static String date(Instant instant) {
        return instant == null ? "—" : DATE.format(instant.atZone(IST));
    }

    public static String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant.atZone(IST));
    }

    public static LocalDate todayIst() {
        return LocalDate.now(IST);
    }

    /** Short relative age such as "3d", "5h", "12m". */
    public static String age(Instant since, Instant now) {
        if (since == null) {
            return "—";
        }
        Duration d = Duration.between(since, now);
        if (d.toDays() >= 1) {
            return d.toDays() + "d";
        }
        if (d.toHours() >= 1) {
            return d.toHours() + "h";
        }
        return Math.max(0, d.toMinutes()) + "m";
    }
}
