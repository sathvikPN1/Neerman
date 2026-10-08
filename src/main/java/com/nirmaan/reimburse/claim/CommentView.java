package com.nirmaan.reimburse.claim;

import java.time.Instant;

public record CommentView(Long id, String authorName, String authorRole, boolean mine, String body, Instant createdAt) {
}
