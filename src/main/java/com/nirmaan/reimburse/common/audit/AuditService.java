package com.nirmaan.reimburse.common.audit;

import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final UserRepository users;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, UserRepository users, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.clock = clock;
    }

    /** Full-form audit entry. {@code actorId} null means the system acted. */
    @Transactional
    public void record(AuditEntity entity, Long entityId, String action, Long actorId, Long onBehalfOfId,
                       String permissionUsed, String fromState, String toState, Map<String, Object> detail) {
        repository.save(new AuditLog(entity, entityId, action, actorId, onBehalfOfId, permissionUsed,
                fromState, toState, detail, Instant.now(clock)));
    }

    /** Audit entry attributed to the signed-in user (or the system when nobody is signed in). */
    @Transactional
    public void record(AuditEntity entity, Long entityId, String action, Map<String, Object> detail) {
        Long actorId = CurrentUser.get().map(AppUserPrincipal::id).orElse(null);
        record(entity, entityId, action, actorId, null, null, null, null, detail);
    }

    @Transactional(readOnly = true)
    public List<AuditEntryView> forEntity(AuditEntity entity, Long entityId) {
        return toViews(repository.findByEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc(entity.name(), entityId));
    }

    @PreAuthorize("hasAuthority('AUDIT_LOG_VIEW')")
    @Transactional(readOnly = true)
    public Page<AuditEntryView> search(String entityType, String action, int page) {
        Page<AuditLog> result = repository.search(blankToNull(entityType), blankToNull(action), PageRequest.of(page, 50));
        return new PageImpl<>(toViews(result.getContent()), result.getPageable(), result.getTotalElements());
    }

    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional(readOnly = true)
    public List<AuditEntryView> permissionHistory(int limit) {
        return toViews(repository.permissionHistory(PageRequest.of(0, limit)).getContent());
    }

    private List<AuditEntryView> toViews(List<AuditLog> logs) {
        Set<Long> ids = new HashSet<>();
        logs.forEach(l -> {
            if (l.getActorId() != null) ids.add(l.getActorId());
            if (l.getOnBehalfOfId() != null) ids.add(l.getOnBehalfOfId());
        });
        Map<Long, String> names = new HashMap<>();
        users.findAllById(ids).forEach(u -> names.put(u.getId(), u.getName()));
        return logs.stream().map(l -> new AuditEntryView(
                l.getId(), l.getEntityType(), l.getEntityId(), l.getAction(),
                l.getActorId() == null ? "System" : names.getOrDefault(l.getActorId(), "User #" + l.getActorId()),
                l.getOnBehalfOfId() == null ? null : names.get(l.getOnBehalfOfId()),
                l.getPermissionUsed(), l.getFromState(), l.getToState(), l.getDetail(), l.getCreatedAt()
        )).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Small helper to build detail maps that tolerate null values. */
    public static Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put(String.valueOf(keyValues[i]), keyValues[i + 1] instanceof Enum<?> e ? e.name() : keyValues[i + 1].toString());
            }
        }
        return map;
    }
}
