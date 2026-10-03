package com.nirmaan.reimburse.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-01T00:00:00Z"));
    final Clock clock = new Clock() {
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    };
    final LoginAttemptService service = new LoginAttemptService(
            new AppProperties(null, null, null, null, new AppProperties.Security(3, 15), false), clock);

    @Test
    void locksAfterMaxFailuresAndUnlocksAfterTheLockPeriod() {
        service.recordFailure("A@x.com");
        service.recordFailure("a@x.com ");
        assertThat(service.isBlocked("a@x.com")).isFalse();
        service.recordFailure("a@x.com");
        assertThat(service.isBlocked("A@X.COM")).isTrue();
        assertThat(service.isBlocked("other@x.com")).isFalse();

        now.set(now.get().plusSeconds(16 * 60));
        assertThat(service.isBlocked("a@x.com")).isFalse();
        service.recordFailure("a@x.com"); // counter restarts after an expired lock
        assertThat(service.isBlocked("a@x.com")).isFalse();
    }

    @Test
    void successResetsTheCounter() {
        service.recordFailure("b@x.com");
        service.recordFailure("b@x.com");
        service.recordSuccess("b@x.com");
        service.recordFailure("b@x.com");
        assertThat(service.isBlocked("b@x.com")).isFalse();
    }
}
