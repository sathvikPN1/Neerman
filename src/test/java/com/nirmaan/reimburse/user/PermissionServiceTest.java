package com.nirmaan.reimburse.user;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionServiceTest {

    static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    UserRepository users = mock(UserRepository.class);
    UserPermissionRepository grants = mock(UserPermissionRepository.class);
    RoleDefaultPermissionRepository defaults = mock(RoleDefaultPermissionRepository.class);
    AuditService audit = mock(AuditService.class);
    PermissionService service = new PermissionService(users, grants, defaults, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    User coo = user(1L, Role.COO);
    User staff = user(2L, Role.NIRMAAN_STAFF);
    User student = user(3L, Role.TEAM_MEMBER);

    @BeforeEach
    void login() {
        AppUserPrincipal p = new AppUserPrincipal(1L, "coo@x", "COO", "h", true, EnumSet.of(Role.COO),
                EnumSet.allOf(Permission.class), null);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        when(users.findById(1L)).thenReturn(Optional.of(coo));
        when(users.findById(2L)).thenReturn(Optional.of(staff));
        when(users.findById(3L)).thenReturn(Optional.of(student));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cooAlwaysHoldsEveryPermissionIncludingStaffManage() {
        assertThat(service.effectivePermissions(coo)).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void inactiveUsersHaveNoPermissions() {
        staff.setActive(false);
        assertThat(service.effectivePermissions(staff)).isEmpty();
    }

    @Test
    void effectivePermissionsComeFromActiveGrantsOnly() {
        when(grants.findActiveByUserId(2L, NOW)).thenReturn(List.of(
                new UserPermission(2L, Permission.CLAIM_VERIFY, 1L, NOW, null)));
        assertThat(service.effectivePermissions(staff)).containsExactly(Permission.CLAIM_VERIFY);
    }

    @Test
    void staffManageCanNeverBeGranted() {
        assertThatThrownBy(() -> service.setPermissions(2L, Map.of(Permission.STAFF_MANAGE, NOW.plusSeconds(60))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.grant(2L, Permission.STAFF_MANAGE, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new UserPermission(2L, Permission.STAFF_MANAGE, 1L, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(grants, never()).save(any());
    }

    @Test
    void cooPermissionsCannotBeChanged() {
        assertThatThrownBy(() -> service.setPermissions(1L, Map.of()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("COO");
        assertThatThrownBy(() -> service.revoke(1L, Permission.CLAIM_APPROVE)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void teamMembersCannotGetStaffPermissions() {
        assertThatThrownBy(() -> service.grant(3L, Permission.CLAIM_VERIFY, null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void expiryMustBeInTheFuture() {
        assertThatThrownBy(() -> service.setPermissions(2L, Map.of(Permission.CLAIM_APPROVE, NOW.minusSeconds(1))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void setPermissionsGrantsRevokesAndUpdatesWithAudit() {
        UserPermission verify = new UserPermission(2L, Permission.CLAIM_VERIFY, 1L, NOW, null);
        UserPermission approve = new UserPermission(2L, Permission.CLAIM_APPROVE, 1L, NOW, null);
        when(grants.findByUserId(2L)).thenReturn(List.of(verify, approve));

        Instant until = NOW.plusSeconds(86400);
        java.util.HashMap<Permission, Instant> desired = new java.util.HashMap<>();
        desired.put(Permission.CLAIM_APPROVE, until);      // expiry changed
        desired.put(Permission.REPORT_VIEW_EXPORT, null);   // new
        service.setPermissions(2L, desired);                // CLAIM_VERIFY revoked

        verify(grants).delete(verify);
        assertThat(approve.getExpiresAt()).isEqualTo(until);
        verify(grants).save(argThat(g -> g.getPermission() == Permission.REPORT_VIEW_EXPORT && g.getGrantedBy() == 1L));
        verify(audit).record(eq(AuditEntity.PERMISSION), eq(2L), eq("PERMISSION_REVOKED"), eq(1L), isNull(), isNull(), isNull(), isNull(), anyMap());
        verify(audit).record(eq(AuditEntity.PERMISSION), eq(2L), eq("PERMISSION_UPDATED"), eq(1L), isNull(), isNull(), isNull(), isNull(), anyMap());
        verify(audit).record(eq(AuditEntity.PERMISSION), eq(2L), eq("PERMISSION_GRANTED"), eq(1L), isNull(), isNull(), isNull(), isNull(), anyMap());
    }

    @Test
    void expiredGrantsAreRemovedAndLoggedAsSystemAction() {
        UserPermission expired = new UserPermission(2L, Permission.CLAIM_APPROVE, 1L, NOW.minusSeconds(7200), NOW.minusSeconds(60));
        when(grants.findExpired(NOW)).thenReturn(List.of(expired));
        assertThat(service.expireGrants()).isEqualTo(1);
        verify(grants).delete(expired);
        verify(audit).record(eq(AuditEntity.PERMISSION), eq(2L), eq("PERMISSION_EXPIRED"), isNull(), isNull(), isNull(), isNull(), isNull(), anyMap());
    }

    @Test
    void roleDefaultsCannotContainStaffManageOrApplyToCoo() {
        assertThatThrownBy(() -> service.setRoleDefaults(Role.NIRMAAN_STAFF, EnumSet.of(Permission.STAFF_MANAGE)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.setRoleDefaults(Role.COO, EnumSet.of(Permission.CLAIM_VERIFY)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void userPermissionExpiryCheck() {
        UserPermission p = new UserPermission(2L, Permission.CLAIM_APPROVE, 1L, NOW, NOW.plusSeconds(10));
        assertThat(p.isActiveAt(NOW)).isTrue();
        assertThat(p.isActiveAt(NOW.plusSeconds(10))).isFalse();
    }

    static User user(long id, Role role) {
        User u = new User("U" + id, "u" + id + "@x", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}
