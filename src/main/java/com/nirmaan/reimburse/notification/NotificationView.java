package com.nirmaan.reimburse.notification;

import java.time.Instant;

public record NotificationView(Long id, String message, String link, boolean read, Instant createdAt) {
}
