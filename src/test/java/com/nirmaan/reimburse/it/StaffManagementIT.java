package com.nirmaan.reimburse.it;

import com.nirmaan.reimburse.claim.ClaimAction;
import com.nirmaan.reimburse.claim.ClaimStateMachine;
import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.claim.TransitionRequest;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.notification.EmailMessage;
import com.nirmaan.reimburse.notification.EmailSender;
import com.nirmaan.reimburse.notification.LoggingEmailSender;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.user.InviteForm;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.PermissionService;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserAdminService;
import com.nirmaan.reimburse.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** COO powers, permission grants/expiry, hand-over and the four-eyes rule. */
class StaffManagementIT extends IntegrationTest {

    @Autowired PermissionService permissions;
    @Autowired UserAdminService userAdmin;
    @Autowired UserRepository users;
    @Autowired ClaimStateMachine stateMachine;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailSender emailSender;

    @Test
    void cooIsBootstrappedFromConfigurationAndHoldsEveryPermission() {
        User coo = fx.coo();
        assertThat(coo.getEmail()).isEqualTo("coo@test.local");
        assertThat(fx.principal(coo).permissions()).containsExactlyInAnyOrder(Permission.values());
        assertThat(users.countActiveCoos()).isEqualTo(1);
    }

    @Test
    void nobodyCanBeGrantedStaffManage() {
        User staff = fx.staff(Role.NIRMAAN_STAFF);
        fx.asRun(fx.coo(), () -> {
            assertThatThrownBy(() -> permissions.grant(staff.getId(), Permission.STAFF_MANAGE, null)).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> permissions.setPermissions(staff.getId(), Map.of(Permission.STAFF_MANAGE, Instant.now().plusSeconds(60))))
                    .isInstanceOf(BusinessRuleException.class);
        });
        // ...and the database refuses it too
        assertThatThrownBy(() -> jdbc.update("insert into user_permissions (user_id, permission_code) values (?, 'STAFF_MANAGE')", staff.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(fx.principal(staff).permissions()).doesNotContain(Permission.STAFF_MANAGE);
    }

    @Test
    void cooPermissionsCannotBeRemovedAndTheLastCooCannotBeDeactivated() {
        User coo = fx.coo();
        fx.asRun(coo, () -> {
            assertThatThrownBy(() -> permissions.setPermissions(coo.getId(), Map.of())).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> permissions.revoke(coo.getId(), Permission.CLAIM_APPROVE)).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> userAdmin.deactivate(coo.getId())).isInstanceOf(BusinessRuleException.class);
        });
        assertThat(users.countActiveCoos()).isEqualTo(1);
        assertThat(fx.principal(coo).permissions()).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void permissionChangesApplyOnTheNextRequestWithoutReLogin() throws Exception {
        User staff = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY);
        MockHttpSession session = login(staff);
        mvc.perform(get("/coo/approvals").session(session)).andExpect(status().isForbidden());

        fx.asRun(fx.coo(), () -> permissions.grant(staff.getId(), Permission.CLAIM_APPROVE, null));
        mvc.perform(get("/coo/approvals").session(session)).andExpect(status().isOk());

        fx.asRun(fx.coo(), () -> permissions.revoke(staff.getId(), Permission.CLAIM_APPROVE));
        mvc.perform(get("/coo/approvals").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void expiredGrantsStopWorkingImmediatelyAndTheExpiryIsAudited() throws Exception {
        User staff = fx.staff(Role.NIRMAAN_STAFF);
        fx.asRun(fx.coo(), () -> permissions.grant(staff.getId(), Permission.CLAIM_APPROVE, Instant.now().plus(1, ChronoUnit.DAYS)));
        MockHttpSession session = login(staff);
        mvc.perform(get("/coo/approvals").session(session)).andExpect(status().isOk());

        // time passes: the grant's expiry is now in the past
        jdbc.update("update user_permissions set expires_at = ? where user_id = ?", Timestamp.from(Instant.now().minusSeconds(5)), staff.getId());
        mvc.perform(get("/coo/approvals").session(session)).andExpect(status().isForbidden());

        assertThat(permissions.expireGrants()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'PERMISSION_EXPIRED' and entity_id = ?",
                Long.class, staff.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_permissions where user_id = ?", Long.class, staff.getId())).isZero();
    }

    @Test
    void grantsAndRevokesAreAudited() {
        User staff = fx.staff(Role.FINANCE);
        fx.asRun(fx.coo(), () -> permissions.setPermissions(staff.getId(), Map.of(Permission.REPORT_VIEW_EXPORT, Instant.now().plus(2, ChronoUnit.DAYS))));
        fx.asRun(fx.coo(), () -> permissions.setPermissions(staff.getId(), Map.of()));
        var actions = jdbc.queryForList("select action from audit_log where entity_type = 'PERMISSION' and entity_id = ? order by id",
                String.class, staff.getId());
        assertThat(actions).containsExactly("PERMISSION_GRANTED", "PERMISSION_REVOKED");
        Long actor = jdbc.queryForObject("select actor_id from audit_log where entity_type = 'PERMISSION' and entity_id = ? order by id limit 1",
                Long.class, staff.getId());
        assertThat(actor).isEqualTo(fx.coo().getId());
    }

    @Test
    void deactivatedUsersAreSignedOutOnTheirNextRequest() throws Exception {
        User staff = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY);
        MockHttpSession session = login(staff);
        mvc.perform(get("/staff/queue").session(session)).andExpect(status().isOk());
        fx.asRun(fx.coo(), () -> userAdmin.deactivate(staff.getId()));
        mvc.perform(get("/staff/queue").session(session)).andExpect(redirectedUrl("/login?deactivated"));
    }

    @Test
    void cooHandOverMovesAllPowersAndCanBeHandedBack() throws Exception {
        User original = fx.coo();
        User successor = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY);
        MockHttpSession originalSession = loginAs(original.getEmail(), "Coo-Password-1");
        mvc.perform(get("/admin/staff").session(originalSession)).andExpect(status().isOk());

        fx.asRun(original, () -> userAdmin.transferCoo(successor.getId()));
        try {
            assertThat(users.countActiveCoos()).isEqualTo(1);
            assertThat(fx.principal(successor).permissions()).containsExactlyInAnyOrder(Permission.values());
            assertThat(fx.principal(original).isCoo()).isFalse();
            assertThat(fx.principal(original).permissions())
                    .contains(Permission.CLAIM_VERIFY).doesNotContain(Permission.STAFF_MANAGE, Permission.CLAIM_APPROVE);
            // the outgoing COO loses access on their very next request
            mvc.perform(get("/admin/staff").session(originalSession)).andExpect(status().isForbidden());
        } finally {
            fx.asRun(successor, () -> userAdmin.transferCoo(original.getId()));
        }
        assertThat(fx.coo().getId()).isEqualTo(original.getId());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'COO_TRANSFERRED' and entity_id = ?",
                Long.class, successor.getId())).isEqualTo(1);
    }

    @Test
    void cooCannotBeHandedToATeamMember() {
        User student = fx.member(fx.team());
        fx.asRun(fx.coo(), () -> assertThatThrownBy(() -> userAdmin.transferCoo(student.getId())).isInstanceOf(BusinessRuleException.class));
        assertThat(fx.principal(student).isCoo()).isFalse();
    }

    @Test
    void invitedStaffGetDefaultBundleAndActivateViaSingleUseLink() throws Exception {
        String email = Fixtures.unique("new") + "@test.local";
        User invited = fx.as(fx.coo(), () -> userAdmin.inviteStaff(new InviteForm("New Person", email, Role.FINANCE)));
        assertThat(fx.principal(invited).permissions())
                .containsExactlyInAnyOrder(Permission.PAYMENT_RECORD, Permission.BANK_DETAILS_VIEW, Permission.REPORT_VIEW_EXPORT);

        EmailMessage mail = ((LoggingEmailSender) emailSender).sent().stream().filter(m -> m.to().equals(email)).reduce((a, b) -> b).orElseThrow();
        Matcher m = Pattern.compile("/invite/([A-Za-z0-9_-]+)").matcher(mail.body());
        assertThat(m.find()).isTrue();
        String token = m.group(1);

        mvc.perform(post("/invite/{t}", token).with(csrf()).param("password", "short").param("confirm", "short"))
                .andExpect(status().isOk()); // re-renders with error
        mvc.perform(post("/invite/{t}", token).with(csrf()).param("password", "Long-Enough-Pass").param("confirm", "Long-Enough-Pass"))
                .andExpect(redirectedUrl("/login?activated"));
        loginAs(email, "Long-Enough-Pass");
        // the link is single-use
        assertThatThrownBy(() -> userAdmin.acceptInvite(token, "Another-Password-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void loginIsRateLimited() throws Exception {
        User staff = fx.staff(Role.NIRMAAN_STAFF);
        for (int i = 0; i < 5; i++) {
            mvc.perform(formLogin("/login").user(staff.getEmail()).password("wrong")).andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(formLogin("/login").user(staff.getEmail()).password(Fixtures.PASSWORD)).andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    void verifierCannotApproveTheirOwnClaimButAnotherApproverCanOnBehalfOfTheCoo() {
        Team team = fx.team();
        User student = fx.member(team);
        User both = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY, Permission.CLAIM_APPROVE);
        User delegate = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_APPROVE);
        Long id = fx.verified(student, both, "2500");

        assertThatThrownBy(() -> fx.as(both, () -> stateMachine.transition(id, ClaimAction.APPROVE, TransitionRequest.of(null, null))))
                .isInstanceOf(AccessDeniedException.class);
        fx.as(delegate, () -> stateMachine.transition(id, ClaimAction.APPROVE, TransitionRequest.of(null, null)));
        assertThat(fx.claim(id).getStatus()).isEqualTo(ClaimStatus.APPROVED);
        Long onBehalfOf = jdbc.queryForObject("select on_behalf_of_id from audit_log where entity_type = 'CLAIM' and entity_id = ? and action = 'APPROVED'",
                Long.class, id);
        assertThat(onBehalfOf).isEqualTo(fx.coo().getId());
    }

    @Test
    void cooCanVerifyAndApproveInOneStep() {
        User student = fx.member(fx.team());
        Long id = fx.submitted(student, "900");
        fx.as(fx.coo(), () -> stateMachine.transition(id, ClaimAction.APPROVE, TransitionRequest.of(null, null)));
        assertThat(fx.claim(id).getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(fx.claim(id).getVerifiedBy()).isEqualTo(fx.coo().getId());
    }

    private MockHttpSession login(User user) throws Exception {
        return loginAs(user.getEmail(), Fixtures.PASSWORD);
    }

    private MockHttpSession loginAs(String email, String password) throws Exception {
        var result = mvc.perform(formLogin("/login").user(email).password(password)).andExpect(redirectedUrl("/")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
