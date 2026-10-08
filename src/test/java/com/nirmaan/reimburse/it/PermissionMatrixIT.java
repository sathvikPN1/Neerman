package com.nirmaan.reimburse.it;

import com.nirmaan.reimburse.category.CategoryForm;
import com.nirmaan.reimburse.category.CategoryService;
import com.nirmaan.reimburse.claim.ClaimPriorityService;
import com.nirmaan.reimburse.claim.ClaimQueueService;
import com.nirmaan.reimburse.claim.QueueFilter;
import com.nirmaan.reimburse.cohort.CohortForm;
import com.nirmaan.reimburse.cohort.CohortService;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.settings.SettingsService;
import com.nirmaan.reimburse.team.Program;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.team.TeamForm;
import com.nirmaan.reimburse.team.TeamService;
import com.nirmaan.reimburse.user.InviteForm;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.PermissionService;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Every protected endpoint and service method is denied without its permission, allowed with exactly that
 * permission, and always allowed for the COO.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionMatrixIT extends IntegrationTest {

    @Autowired SettingsService settings;
    @Autowired AuditService audit;
    @Autowired CategoryService categories;
    @Autowired CohortService cohorts;
    @Autowired TeamService teams;
    @Autowired ClaimQueueService queues;
    @Autowired ClaimPriorityService priority;
    @Autowired UserAdminService userAdmin;
    @Autowired PermissionService permissionService;

    Team team;
    Long claimId;
    Long cohortId;

    @BeforeEach
    void data() {
        if (team == null) {
            team = fx.team();
            cohortId = team.getCohortId();
            claimId = fx.submitted(fx.member(team), "1000");
        }
    }

    /** {@code permission} alone must suffice; {@code alternatives} are other permissions the endpoint also accepts. */
    record Endpoint(String name, Permission permission, Set<Permission> alternatives,
                    Function<PermissionMatrixIT, MockHttpServletRequestBuilder> request) {
        Endpoint(String name, Permission permission, Function<PermissionMatrixIT, MockHttpServletRequestBuilder> request) {
            this(name, permission, Set.of(), request);
        }

        @Override
        public String toString() {
            return name + " needs " + permission + (alternatives.isEmpty() ? "" : " (or " + alternatives + ")");
        }
    }

    static final Set<Permission> QUEUE_ALTERNATIVES = Set.of(Permission.CLAIM_RETURN_REJECT, Permission.CLAIM_APPROVE, Permission.CLAIM_FLAG_PRIORITY);

    Stream<Endpoint> endpoints() {
        return Stream.of(
                new Endpoint("GET /staff/queue", Permission.CLAIM_VERIFY, QUEUE_ALTERNATIVES, t -> get("/staff/queue")),
                new Endpoint("GET /coo/approvals", Permission.CLAIM_APPROVE, t -> get("/coo/approvals")),
                new Endpoint("POST /coo/approvals/bulk", Permission.CLAIM_APPROVE, t -> post("/coo/approvals/bulk")),
                new Endpoint("GET /finance/payments", Permission.PAYMENT_RECORD, t -> get("/finance/payments")),
                new Endpoint("GET /audit", Permission.AUDIT_LOG_VIEW, t -> get("/audit")),
                new Endpoint("GET /teams/new", Permission.TEAM_MANAGE, t -> get("/teams/new")),
                new Endpoint("POST /teams", Permission.TEAM_MANAGE, t -> post("/teams")
                        .param("name", Fixtures.unique("T")).param("cohortId", t.cohortId.toString()).param("program", "PRATHAM")),
                new Endpoint("POST /cohorts", Permission.COHORT_MANAGE, t -> post("/cohorts")
                        .param("name", Fixtures.unique("C")).param("startDate", "2027-01-01").param("endDate", "2027-06-30")),
                new Endpoint("POST /categories", Permission.CATEGORY_MANAGE, t -> post("/categories")
                        .param("name", Fixtures.unique("Cat")).param("description", "d").param("allowed", "true")),
                new Endpoint("POST /teams/{id}/priority", Permission.CLAIM_FLAG_PRIORITY, t -> post("/teams/{id}/priority", t.team.getId())
                        .param("priority", "false")),
                new Endpoint("POST /claims/{id}/priority", Permission.CLAIM_FLAG_PRIORITY, t -> post("/claims/{id}/priority", t.claimId)
                        .param("priority", "false")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void endpointIsDeniedWithoutItsPermissionAndAllowedWithIt(Endpoint e) throws Exception {
        Permission[] allOthers = Arrays.stream(Permission.values())
                .filter(p -> p != e.permission() && !e.alternatives().contains(p) && p.isGrantable()).toArray(Permission[]::new);
        User without = fx.staff(Role.NIRMAAN_STAFF, allOthers);
        User with = fx.staff(Role.NIRMAAN_STAFF, e.permission());

        int denied = mvc.perform(e.request().apply(this).with(user(fx.principal(without))).with(csrf())).andReturn().getResponse().getStatus();
        int allowed = mvc.perform(e.request().apply(this).with(user(fx.principal(with))).with(csrf())).andReturn().getResponse().getStatus();
        int coo = mvc.perform(e.request().apply(this).with(user(fx.principal(fx.coo()))).with(csrf())).andReturn().getResponse().getStatus();

        assertThat(denied).as("without " + e.permission()).isEqualTo(403);
        assertThat(allowed).as("with " + e.permission()).isNotEqualTo(403).isLessThan(500);
        assertThat(coo).as("as COO").isNotEqualTo(403).isLessThan(500);
    }

    @Test
    void staffManagementScreensAreCooOnlyEvenWithEveryGrantablePermission() throws Exception {
        User everything = fx.staff(Role.NIRMAAN_STAFF, Arrays.stream(Permission.values()).filter(Permission::isGrantable).toArray(Permission[]::new));
        for (var req : new MockHttpServletRequestBuilder[]{get("/admin/staff"), post("/admin/staff/invite").param("name", "x").param("email", "x@y.z").param("role", "FINANCE"),
                post("/admin/staff/transfer-coo").param("newCooId", "1").param("confirm", "TRANSFER")}) {
            assertThat(mvc.perform(req.with(user(fx.principal(everything))).with(csrf())).andReturn().getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(mvc.perform(get("/admin/staff").with(user(fx.principal(fx.coo())))).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ service layer

    record ServiceCall(Permission permission, String name, java.util.function.Consumer<PermissionMatrixIT> call) {
        @Override
        public String toString() {
            return name + " needs " + permission;
        }
    }

    Stream<ServiceCall> serviceCalls() {
        return Stream.of(
                new ServiceCall(Permission.SETTINGS_MANAGE, "SettingsService.set", t -> t.settings.set("sla.normal.working_days", "5")),
                new ServiceCall(Permission.AUDIT_LOG_VIEW, "AuditService.search", t -> t.audit.search(null, null, 0)),
                new ServiceCall(Permission.CATEGORY_MANAGE, "CategoryService.save", t -> t.categories.save(
                        new CategoryForm(null, Fixtures.unique("Cat"), true, false, false, false, "d", 0, true))),
                new ServiceCall(Permission.COHORT_MANAGE, "CohortService.save", t -> t.cohorts.save(
                        new CohortForm(null, Fixtures.unique("C"), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30)))),
                new ServiceCall(Permission.TEAM_MANAGE, "TeamService.save", t -> t.teams.save(
                        new TeamForm(null, Fixtures.unique("T"), t.cohortId, Program.AKSHAR, null))),
                new ServiceCall(Permission.CLAIM_FLAG_PRIORITY, "TeamService.setPriority", t -> t.teams.setPriority(t.team.getId(), false, null)),
                new ServiceCall(Permission.CLAIM_FLAG_PRIORITY, "ClaimPriorityService.setPriority", t -> t.priority.setPriority(t.claimId, false, null)),
                new ServiceCall(Permission.CLAIM_VERIFY, "ClaimQueueService.staffQueue", t -> t.queues.staffQueue(QueueFilter.defaults())),
                new ServiceCall(Permission.CLAIM_APPROVE, "ClaimQueueService.approvalQueue", t -> t.queues.approvalQueue()),
                new ServiceCall(Permission.PAYMENT_RECORD, "ClaimQueueService.paymentQueue", t -> t.queues.paymentQueue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serviceCalls")
    void serviceMethodIsDeniedWithoutItsPermissionAndAllowedWithIt(ServiceCall c) {
        User without = fx.staff(Role.NIRMAAN_STAFF);
        User with = fx.staff(Role.NIRMAAN_STAFF, c.permission());
        assertThatThrownBy(() -> fx.asRun(without, () -> c.call().accept(this))).isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> fx.asRun(with, () -> c.call().accept(this))).doesNotThrowAnyException();
        assertThatCode(() -> fx.asRun(fx.coo(), () -> c.call().accept(this))).doesNotThrowAnyException();
    }

    @Test
    void staffManageServiceMethodsAreCooOnly() {
        User everything = fx.staff(Role.NIRMAAN_STAFF, Arrays.stream(Permission.values()).filter(Permission::isGrantable).toArray(Permission[]::new));
        User target = fx.staff(Role.FINANCE);
        fx.asRun(everything, () -> {
            assertThatThrownBy(() -> userAdmin.listStaff()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> userAdmin.inviteStaff(new InviteForm("X", Fixtures.unique("x") + "@t.local", Role.FINANCE)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> permissionService.setPermissions(target.getId(), Map.of())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> userAdmin.deactivate(target.getId())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> userAdmin.transferCoo(target.getId())).isInstanceOf(AccessDeniedException.class);
        });
        assertThatCode(() -> fx.asRun(fx.coo(), () -> userAdmin.listStaff())).doesNotThrowAnyException();
    }
}
