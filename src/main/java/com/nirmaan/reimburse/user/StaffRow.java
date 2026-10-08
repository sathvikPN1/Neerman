package com.nirmaan.reimburse.user;

import java.time.Instant;
import java.util.List;

public record StaffRow(
        Long id,
        String name,
        String email,
        Role role,
        boolean active,
        boolean invitePending,
        List<GrantView> grants,
        Instant lastActiveAt) {

    public record GrantView(Permission permission, Instant expiresAt) {
    }
}
