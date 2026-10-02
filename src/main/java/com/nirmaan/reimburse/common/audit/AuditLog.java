package com.nirmaan.reimburse.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Append-only audit entry. The database rejects UPDATE/DELETE on this table. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String entityType;

    private Long entityId;

    @Column(nullable = false)
    private String action;

    private Long actorId;

    private Long onBehalfOfId;

    private String permissionUsed;

    private String fromState;

    private String toState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> detail = new LinkedHashMap<>();

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    AuditLog(AuditEntity entityType, Long entityId, String action, Long actorId, Long onBehalfOfId,
             String permissionUsed, String fromState, String toState, Map<String, Object> detail, Instant createdAt) {
        this.entityType = entityType.name();
        this.entityId = entityId;
        this.action = action;
        this.actorId = actorId;
        this.onBehalfOfId = onBehalfOfId;
        this.permissionUsed = permissionUsed;
        this.fromState = fromState;
        this.toState = toState;
        this.detail = detail == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detail);
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public Long getActorId() {
        return actorId;
    }

    public Long getOnBehalfOfId() {
        return onBehalfOfId;
    }

    public String getPermissionUsed() {
        return permissionUsed;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
