package com.nirmaan.reimburse.common.audit;

import java.time.Instant;
import java.util.Map;

public record AuditEntryView(
        Long id,
        String entityType,
        Long entityId,
        String action,
        String actorName,
        String onBehalfOfName,
        String permissionUsed,
        String fromState,
        String toState,
        Map<String, Object> detail,
        Instant createdAt) {

    public String comment() {
        Object c = detail == null ? null : detail.get("comment");
        return c == null ? null : c.toString();
    }
}
