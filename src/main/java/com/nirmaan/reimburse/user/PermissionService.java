package com.nirmaan.reimburse.user;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

/**
 * Grants, revokes and evaluates permissions. Invariants:
 * <ul>
 *   <li>The COO implicitly holds every permission; their permissions cannot be edited.</li>
 *   <li>STAFF_MANAGE can never be granted.</li>
 *   <li>Every grant, revoke and expiry is audit-logged.</li>
 * </ul>
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final UserRepository users;
    private final UserPermissionRepository grants;
    private final RoleDefaultPermissionRepository defaults;
    private final AuditService audit;
    private final Clock clock;

    public PermissionService(UserRepository users, UserPermissionRepository grants,
                             RoleDefaultPermissionRepository defaults, AuditService audit, Clock clock) {
        this.users = users;
        this.grants = grants;
        this.defaults = defaults;
        this.audit = audit;
        this.clock = clock;
    }

    /** Effective permissions right now. Expired grants are ignored even before the cleanup job runs. */
    @Transactional(readOnly = true)
    public Set<Permission> effectivePermissions(User user) {
        if (!user.isActive()) {
            return EnumSet.noneOf(Permission.class);
        }
        if (user.isCoo()) {
            return EnumSet.allOf(Permission.class);
        }
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        grants.findActiveByUserId(user.getId(), Instant.now(clock)).forEach(g -> result.add(g.getPermission()));
        return result;
    }

    /** Active users who currently hold a permission (COOs always included). */
    @Transactional(readOnly = true)
    public Set<Long> holdersOf(Permission permission) {
        Set<Long> ids = new LinkedHashSet<>();
        users.findActiveByRole(Role.COO).forEach(u -> ids.add(u.getId()));
        List<Long> holders = grants.findActiveHolders(permission, Instant.now(clock));
        users.findAllById(holders).stream().filter(User::isActive).forEach(u -> ids.add(u.getId()));
        return ids;
    }

    @Transactional(readOnly = true)
    public List<UserPermission> grantsOf(Long userId) {
        return grants.findByUserId(userId);
    }

    /**
     * Replace a user's explicit grants with {@code desired} (permission → optional expiry).
     * Grants that are missing are revoked; new ones are granted; changed expiries are updated.
     */
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional
    public void setPermissions(Long targetUserId, Map<Permission, Instant> desired) {
        User target = editableTarget(targetUserId);
        Map<Permission, Instant> wanted = new EnumMap<>(Permission.class);
        wanted.putAll(desired);
        if (wanted.containsKey(Permission.STAFF_MANAGE)) {
            throw new BusinessRuleException("STAFF_MANAGE belongs to the COO and cannot be granted.");
        }
        Instant now = Instant.now(clock);
        for (Instant expiry : wanted.values()) {
            if (expiry != null && !expiry.isAfter(now)) {
                throw new BusinessRuleException("Expiry dates must be in the future.");
            }
        }
        Long actorId = CurrentUser.require().id();

        for (UserPermission existing : grants.findByUserId(target.getId())) {
            Permission p = existing.getPermission();
            if (!wanted.containsKey(p)) {
                grants.delete(existing);
                audit.record(AuditEntity.PERMISSION, target.getId(), "PERMISSION_REVOKED", actorId, null, null, null, null,
                        detail("permission", p, "user", target.getEmail()));
            } else {
                Instant newExpiry = wanted.remove(p);
                if (!Objects.equals(existing.getExpiresAt(), newExpiry)) {
                    existing.setExpiresAt(newExpiry);
                    audit.record(AuditEntity.PERMISSION, target.getId(), "PERMISSION_UPDATED", actorId, null, null, null, null,
                            detail("permission", p, "user", target.getEmail(), "expiresAt", newExpiry));
                }
            }
        }
        wanted.forEach((p, expiry) -> doGrant(target, p, expiry, actorId, now));
    }

    /** Grant one permission (used by delegation). */
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional
    public void grant(Long targetUserId, Permission permission, Instant expiresAt) {
        User target = editableTarget(targetUserId);
        if (!permission.isGrantable()) {
            throw new BusinessRuleException(permission + " cannot be granted.");
        }
        Instant now = Instant.now(clock);
        Long actorId = CurrentUser.require().id();
        grants.findByUserIdAndPermission(target.getId(), permission).ifPresentOrElse(existing -> {
            existing.setExpiresAt(expiresAt);
            existing.setGrantedAt(now);
            existing.setGrantedBy(actorId);
            audit.record(AuditEntity.PERMISSION, target.getId(), "PERMISSION_UPDATED", actorId, null, null, null, null,
                    detail("permission", permission, "user", target.getEmail(), "expiresAt", expiresAt));
        }, () -> doGrant(target, permission, expiresAt, actorId, now));
    }

    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional
    public void revoke(Long targetUserId, Permission permission) {
        User target = editableTarget(targetUserId);
        grants.findByUserIdAndPermission(target.getId(), permission).ifPresent(g -> {
            grants.delete(g);
            audit.record(AuditEntity.PERMISSION, target.getId(), "PERMISSION_REVOKED", CurrentUser.require().id(),
                    null, null, null, null, detail("permission", permission, "user", target.getEmail()));
        });
    }

    /** Copy the role's default bundle onto a (new) user. Internal; callers enforce authorisation. */
    @Transactional
    public void applyRoleDefaults(User user, Long actorId) {
        if (user.isCoo() || user.hasRole(Role.TEAM_MEMBER)) {
            return;
        }
        Instant now = Instant.now(clock);
        for (RoleDefaultPermission d : defaults.findByRole(user.primaryRole())) {
            if (grants.findByUserIdAndPermission(user.getId(), d.getPermission()).isEmpty()) {
                doGrant(user, d.getPermission(), null, actorId, now);
            }
        }
    }

    /** Remove all explicit grants, e.g. when a user becomes COO (who holds everything implicitly). */
    @Transactional
    public void clearGrants(User user, Long actorId, String reason) {
        for (UserPermission g : grants.findByUserId(user.getId())) {
            grants.delete(g);
            audit.record(AuditEntity.PERMISSION, user.getId(), "PERMISSION_REVOKED", actorId, null, null, null, null,
                    detail("permission", g.getPermission(), "user", user.getEmail(), "reason", reason));
        }
    }

    @Transactional(readOnly = true)
    public Set<Permission> roleDefaults(Role role) {
        Set<Permission> set = EnumSet.noneOf(Permission.class);
        defaults.findByRole(role).forEach(d -> set.add(d.getPermission()));
        return set;
    }

    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional
    public void setRoleDefaults(Role role, Set<Permission> permissions) {
        if (!role.isStaffRole()) {
            throw new BusinessRuleException("Default bundles exist only for staff and finance roles.");
        }
        if (permissions.contains(Permission.STAFF_MANAGE)) {
            throw new BusinessRuleException("STAFF_MANAGE cannot be part of a default bundle.");
        }
        defaults.deleteAll(defaults.findByRole(role));
        defaults.flush();
        permissions.forEach(p -> defaults.save(new RoleDefaultPermission(role, p)));
        audit.record(AuditEntity.PERMISSION, null, "ROLE_DEFAULTS_CHANGED", detail("role", role, "permissions", permissions));
    }

    /** Remove expired grants and log each expiry. Access checks already ignore them. */
    @Scheduled(fixedDelayString = "${app.permissions.expiry-check-ms:60000}")
    @Transactional
    public int expireGrants() {
        List<UserPermission> expired = grants.findExpired(Instant.now(clock));
        for (UserPermission g : expired) {
            grants.delete(g);
            audit.record(AuditEntity.PERMISSION, g.getUserId(), "PERMISSION_EXPIRED", null, null, null, null, null,
                    detail("permission", g.getPermission(), "expiredAt", g.getExpiresAt(), "grantedBy", g.getGrantedBy()));
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} permission grant(s)", expired.size());
        }
        return expired.size();
    }

    private void doGrant(User target, Permission p, Instant expiresAt, Long actorId, Instant now) {
        grants.save(new UserPermission(target.getId(), p, actorId, now, expiresAt));
        audit.record(AuditEntity.PERMISSION, target.getId(), "PERMISSION_GRANTED", actorId, null, null, null, null,
                detail("permission", p, "user", target.getEmail(), "expiresAt", expiresAt));
    }

    private User editableTarget(Long userId) {
        User target = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        if (target.isCoo()) {
            throw new BusinessRuleException("The COO always holds every permission; their permissions cannot be changed.");
        }
        if (target.hasRole(Role.TEAM_MEMBER)) {
            throw new BusinessRuleException("Team members cannot be given staff permissions.");
        }
        return target;
    }
}
