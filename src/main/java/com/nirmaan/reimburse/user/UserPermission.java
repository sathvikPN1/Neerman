package com.nirmaan.reimburse.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A permission explicitly granted to a user, optionally time-limited. */
@Entity
@Table(name = "user_permissions")
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_code", nullable = false)
    private Permission permission;

    private Long grantedBy;

    @Column(nullable = false)
    private Instant grantedAt;

    private Instant expiresAt;

    protected UserPermission() {
    }

    public UserPermission(Long userId, Permission permission, Long grantedBy, Instant grantedAt, Instant expiresAt) {
        if (!permission.isGrantable()) {
            throw new IllegalArgumentException(permission + " cannot be granted");
        }
        this.userId = userId;
        this.permission = permission;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isActiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Permission getPermission() {
        return permission;
    }

    public Long getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setGrantedBy(Long grantedBy) {
        this.grantedBy = grantedBy;
    }

    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }
}
