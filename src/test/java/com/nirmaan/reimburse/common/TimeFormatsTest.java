package com.nirmaan.reimburse.common;

import com.nirmaan.reimburse.common.time.TimeFormats;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TimeFormatsTest {

    @Test
    void showsDatesInIst() {
        // 20:00 UTC on 31 Dec is already 1 Jan in India
        assertThat(TimeFormats.date(Instant.parse("2026-12-31T20:00:00Z"))).isEqualTo("01 Jan 2027");
        assertThat(TimeFormats.dateTime(Instant.parse("2026-10-01T06:00:00Z"))).isEqualTo("01 Oct 2026, 11:30 AM");
        assertThat(TimeFormats.date(LocalDate.of(2026, 3, 5))).isEqualTo("05 Mar 2026");
    }

    @Test
    void relativeAge() {
        Instant now = Instant.parse("2026-10-05T00:00:00Z");
        assertThat(TimeFormats.age(Instant.parse("2026-10-02T00:00:00Z"), now)).isEqualTo("3d");
        assertThat(TimeFormats.age(Instant.parse("2026-10-04T19:00:00Z"), now)).isEqualTo("5h");
        assertThat(TimeFormats.age(Instant.parse("2026-10-04T23:48:00Z"), now)).isEqualTo("12m");
        assertThat(TimeFormats.age(null, now)).isEqualTo("—");
    }
}
