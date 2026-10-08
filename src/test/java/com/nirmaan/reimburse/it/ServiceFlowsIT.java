package com.nirmaan.reimburse.it;

import com.nirmaan.reimburse.category.CategoryForm;
import com.nirmaan.reimburse.category.CategoryService;
import com.nirmaan.reimburse.claim.BulkResult;
import com.nirmaan.reimburse.claim.ClaimDecisionService;
import com.nirmaan.reimburse.claim.ClaimPriorityService;
import com.nirmaan.reimburse.claim.ClaimQueueService;
import com.nirmaan.reimburse.claim.ClaimRow;
import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.claim.ClaimWarning;
import com.nirmaan.reimburse.claim.CommentService;
import com.nirmaan.reimburse.claim.QueueFilter;
import com.nirmaan.reimburse.cohort.CohortForm;
import com.nirmaan.reimburse.cohort.CohortService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.ConcurrentUpdateException;
import com.nirmaan.reimburse.document.DocumentService;
import com.nirmaan.reimburse.document.DocumentView;
import com.nirmaan.reimburse.notification.NotificationService;
import com.nirmaan.reimburse.notification.NotificationView;
import com.nirmaan.reimburse.team.MemberForm;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.team.TeamDetail;
import com.nirmaan.reimburse.team.TeamService;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserAdminService;
import com.nirmaan.reimburse.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Service-level flows around the core workflow: priority, comments, bulk approval, teams, drafts. */
class ServiceFlowsIT extends IntegrationTest {

    @Autowired ClaimService claims;
    @Autowired ClaimPriorityService priority;
    @Autowired ClaimQueueService queues;
    @Autowired ClaimDecisionService decisions;
    @Autowired CommentService comments;
    @Autowired DocumentService documents;
    @Autowired TeamService teams;
    @Autowired CohortService cohorts;
    @Autowired CategoryService categories;
    @Autowired NotificationService notifications;
    @Autowired UserAdminService userAdmin;
    @Autowired UserRepository users;

    @Test
    void teamRequestsPriorityAndStaffAcceptOrDecline() {
        Team team = fx.team();
        User student = fx.member(team);
        User staff = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY, Permission.CLAIM_FLAG_PRIORITY);
        Long first = fx.submitted(student, "1000");
        Long second = fx.submitted(student, "2000");

        fx.asRun(student, () -> assertThatThrownBy(() -> priority.requestPriority(first, " ")).isInstanceOf(BusinessRuleException.class));
        fx.asRun(student, () -> priority.requestPriority(first, "Paid from my scholarship, need it back"));
        fx.asRun(student, () -> assertThatThrownBy(() -> priority.requestPriority(first, "again")).isInstanceOf(BusinessRuleException.class));
        assertThat(fx.claim(first).isPriorityRequested()).isTrue();
        // teams cannot set priority themselves
        fx.asRun(student, () -> assertThatThrownBy(() -> priority.setPriority(first, true, "me"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class));
        assertThat(notifications.recent(staff.getId(), 10)).extracting(NotificationView::message).anyMatch(m -> m.contains("Priority requested"));

        fx.asRun(staff, () -> priority.setPriority(first, true, "Team request accepted"));
        assertThat(fx.claim(first).isPriority()).isTrue();
        assertThat(fx.claim(first).isPriorityRequested()).isFalse();

        fx.asRun(student, () -> priority.requestPriority(second, "Also urgent"));
        fx.asRun(staff, () -> priority.declineRequest(second, "Not urgent enough"));
        assertThat(fx.claim(second).isPriorityRequested()).isFalse();
        assertThat(fx.claim(second).isPriority()).isFalse();
        fx.asRun(staff, () -> assertThatThrownBy(() -> priority.declineRequest(second, null)).isInstanceOf(BusinessRuleException.class));
        fx.asRun(staff, () -> assertThatThrownBy(() -> priority.setPriority(second, true, "")).isInstanceOf(BusinessRuleException.class));

        // priority claims sort first in the staff queue
        List<ClaimRow> queue = fx.as(staff, () -> queues.staffQueue(new QueueFilter(null, team.getId(), null, null, null)));
        assertThat(queue).extracting(ClaimRow::id).containsExactly(first, second);

        fx.asRun(staff, () -> priority.setPriority(first, false, null));
        assertThat(fx.claim(first).isPriority()).isFalse();
    }

    @Test
    void teamPriorityLiftsAllItsClaims() {
        Team normal = fx.team();
        Team urgent = fx.team();
        User staff = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY, Permission.CLAIM_FLAG_PRIORITY);
        Long older = fx.submitted(fx.member(normal), "100");
        Long newer = fx.submitted(fx.member(urgent), "100");
        fx.asRun(staff, () -> assertThatThrownBy(() -> teams.setPriority(urgent.getId(), true, null)).isInstanceOf(BusinessRuleException.class));
        fx.asRun(staff, () -> teams.setPriority(urgent.getId(), true, "Large out-of-pocket spend"));

        List<ClaimRow> queue = fx.as(staff, () -> queues.staffQueue(QueueFilter.defaults())).stream()
                .filter(r -> r.id().equals(older) || r.id().equals(newer)).toList();
        assertThat(queue).extracting(ClaimRow::id).containsExactly(newer, older);
        assertThat(queue.getFirst().priorityReason()).contains("Large out-of-pocket");
    }

    @Test
    void commentThreadNotifiesTheOtherSide() {
        Team team = fx.team();
        User student = fx.member(team);
        User teammate = fx.member(team);
        User staff = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY);
        Long id = fx.verified(student, staff, "1500");

        fx.asRun(student, () -> assertThatThrownBy(() -> comments.add(id, "  ")).isInstanceOf(BusinessRuleException.class));
        fx.asRun(student, () -> assertThatThrownBy(() -> comments.add(id, "x".repeat(4001))).isInstanceOf(BusinessRuleException.class));
        fx.asRun(student, () -> comments.add(id, "Is the GST invoice fine?"));
        fx.asRun(staff, () -> comments.add(id, "Yes, all good."));

        assertThat(fx.as(student, () -> comments.forClaim(id)))
                .extracting(c -> c.body() + "|" + c.mine()).containsExactly("Is the GST invoice fine?|true", "Yes, all good.|false");
        assertThat(notifications.recent(staff.getId(), 5)).extracting(NotificationView::message).anyMatch(m -> m.contains("GST invoice"));
        assertThat(notifications.recent(teammate.getId(), 5)).extracting(NotificationView::message).anyMatch(m -> m.contains("all good"));
    }

    @Test
    void bulkApproveApprovesWhatItCanAndReportsTheRest() {
        Team team = fx.team(new BigDecimal("5000.00"));
        User student = fx.member(team);
        User verifier = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY);
        Long a = fx.verified(student, verifier, "3000");
        Long b = fx.verified(student, verifier, "1500");
        Long c = fx.verified(student, verifier, "2000"); // would exceed the budget after a and b
        Map<Long, Long> selection = new LinkedHashMap<>();
        selection.put(a, fx.claim(a).getVersion());
        selection.put(b, fx.claim(b).getVersion());
        selection.put(c, fx.claim(c).getVersion());

        BulkResult result = fx.as(fx.coo(), () -> decisions.bulkApprove(selection));
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failures()).singleElement().asString().contains(fx.claim(c).getPublicCode()).contains("budget");
        assertThat(fx.claim(a).getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(fx.claim(c).getStatus()).isEqualTo(ClaimStatus.VERIFIED);
    }

    @Test
    void draftsCanBeEditedAndDeletedOnlyWhileEditable() {
        User student = fx.member(fx.team());
        Long draft = fx.draftWithDocuments(student, "900");
        long version = fx.claim(draft).getVersion();

        fx.asRun(student, () -> claims.updateDraft(draft, withVersion(fx.claimForm("950.40"), version)));
        assertThat(fx.claim(draft).getClaimedAmount()).isEqualByComparingTo("950.40");
        fx.asRun(student, () -> assertThatThrownBy(() -> claims.updateDraft(draft, withVersion(fx.claimForm("1"), version)))
                .isInstanceOf(ConcurrentUpdateException.class));

        List<DocumentView> docs = fx.as(student, () -> documents.forClaim(draft));
        fx.asRun(student, () -> documents.delete(docs.getFirst().id()));
        assertThat(fx.as(student, () -> documents.forClaim(draft))).hasSize(1);

        assertThat(fx.as(student, () -> claims.formFor(draft)).claimedAmount()).isEqualByComparingTo("950.40");
        fx.asRun(student, () -> claims.deleteDraft(draft));
        assertThat(fx.as(student, () -> claims.teamClaims(fx.principal(student).teamId())))
                .extracting(ClaimRow::id).doesNotContain(draft);

        Long submitted = fx.submitted(student, "100");
        fx.asRun(student, () -> {
            assertThatThrownBy(() -> claims.updateDraft(submitted, fx.claimForm("1"))).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> claims.deleteDraft(submitted)).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> documents.upload(submitted, com.nirmaan.reimburse.document.DocumentType.OTHER, "a.pdf", Fixtures.PDF))
                    .isInstanceOf(BusinessRuleException.class);
        });
        fx.asRun(fx.coo(), () -> assertThatThrownBy(() -> claims.createDraft(fx.claimForm("10"))).isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void liveWarningsUseTheTeamsBudget() {
        User student = fx.member(fx.team(new BigDecimal("1000.00")));
        List<ClaimWarning> w = fx.as(student, () -> claims.warningsFor(fx.categoryId("Accommodation"), new BigDecimal("5000")));
        assertThat(w).extracting(ClaimWarning::code).contains("CATEGORY_NOT_ALLOWED", "OVER_BUDGET");
        assertThat(fx.as(fx.coo(), () -> claims.warningsFor(fx.categoryId("Accommodation"), BigDecimal.ONE))).isEmpty();
    }

    @Test
    void staffManageTeamsCohortsAndCategories() {
        User staff = fx.staff(Role.NIRMAAN_STAFF, Permission.TEAM_MANAGE, Permission.COHORT_MANAGE, Permission.CATEGORY_MANAGE,
                Permission.CLAIM_VERIFY);
        Long cohortId = fx.as(staff, () -> cohorts.save(new CohortForm(null, Fixtures.unique("Akshar"), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30))));
        fx.asRun(staff, () -> assertThatThrownBy(() -> cohorts.save(new CohortForm(cohortId, "x", LocalDate.of(2027, 6, 1), LocalDate.of(2027, 1, 1))))
                .isInstanceOf(BusinessRuleException.class));
        fx.asRun(staff, () -> cohorts.save(new CohortForm(cohortId, "Renamed " + cohortId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 7, 31))));
        assertThat(fx.as(staff, () -> cohorts.list())).anyMatch(c -> c.name().equals("Renamed " + cohortId));

        String name = Fixtures.unique("Rocket");
        Long teamId = fx.as(staff, () -> teams.save(new com.nirmaan.reimburse.team.TeamForm(null, name, cohortId,
                com.nirmaan.reimburse.team.Program.AKSHAR, null)));
        TeamDetail detail = fx.as(staff, () -> teams.get(teamId));
        assertThat(detail.summary().budget().total()).isEqualByComparingTo("500000.00"); // Akshar default
        fx.asRun(staff, () -> assertThatThrownBy(() -> teams.save(new com.nirmaan.reimburse.team.TeamForm(null, name, cohortId,
                com.nirmaan.reimburse.team.Program.PRATHAM, null))).isInstanceOf(BusinessRuleException.class));
        fx.asRun(staff, () -> teams.save(new com.nirmaan.reimburse.team.TeamForm(teamId, name, cohortId,
                com.nirmaan.reimburse.team.Program.AKSHAR, new BigDecimal("450000"))));
        assertThat(fx.as(staff, () -> teams.get(teamId)).summary().budget().total()).isEqualByComparingTo("450000");

        String email = Fixtures.unique("founder") + "@test.local";
        Long memberId = fx.as(staff, () -> teams.addMember(teamId, new MemberForm("Founder", email)));
        assertThat(fx.as(staff, () -> teams.get(teamId)).members()).extracting(TeamDetail.Member::email).containsExactly(email);
        fx.asRun(staff, () -> assertThatThrownBy(() -> teams.addMember(teamId, new MemberForm("Dup", email))).isInstanceOf(BusinessRuleException.class));
        fx.asRun(staff, () -> userAdmin.resendInvite(memberId));
        fx.asRun(staff, () -> teams.removeMember(teamId, memberId));
        assertThat(users.findById(memberId).orElseThrow().isActive()).isFalse();
        fx.asRun(staff, () -> assertThatThrownBy(() -> teams.removeMember(teamId, memberId)).isInstanceOf(BusinessRuleException.class));
        assertThat(fx.as(staff, () -> teams.list())).anyMatch(t -> t.id().equals(teamId));

        String cat = Fixtures.unique("Lab consumables");
        Long catId = fx.as(staff, () -> categories.save(new CategoryForm(null, cat, true, false, true, false, "Chemicals", 5, true)));
        fx.asRun(staff, () -> categories.save(new CategoryForm(catId, cat, true, false, false, true, "Chemicals for the product", 5, false)));
        assertThat(categories.listAll()).anyMatch(c -> c.id().equals(catId) && !c.active() && c.justificationRequired());
        assertThat(categories.listActive()).noneMatch(c -> c.id().equals(catId));
        fx.asRun(staff, () -> assertThatThrownBy(() -> categories.save(new CategoryForm(null, cat, true, false, false, false, "d", 0, true)))
                .isInstanceOf(BusinessRuleException.class));
        fx.asRun(staff, () -> assertThatThrownBy(() -> categories.save(new CategoryForm(null, Fixtures.unique("Bad"), false, true, false, false, "d", 0, true)))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void notificationsCanBeOpenedAndMarkedRead() {
        Team team = fx.team();
        User submitter = fx.member(team);
        User student = fx.member(team); // teammates hear about each other's claims; actors are never notified of their own actions
        User other = fx.member(fx.team());
        fx.submitted(submitter, "100");
        assertThat(notifications.unreadCount(submitter.getId())).isZero();
        List<NotificationView> mine = notifications.recent(student.getId(), 10);
        assertThat(mine).isNotEmpty();
        assertThat(notifications.unreadCount(student.getId())).isPositive();

        String link = fx.as(student, () -> notifications.open(mine.getFirst().id()));
        assertThat(link).startsWith("/claims/");
        fx.asRun(other, () -> assertThatThrownBy(() -> notifications.open(mine.getFirst().id()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class));
        notifications.markAllRead(student.getId());
        assertThat(notifications.unreadCount(student.getId())).isZero();
    }

    @Test
    void staffCanBeDeactivatedAndReactivated() {
        User staff = fx.staff(Role.NIRMAAN_STAFF);
        fx.asRun(fx.coo(), () -> userAdmin.deactivate(staff.getId()));
        assertThat(fx.principal(staff).isEnabled()).isFalse();
        assertThat(fx.as(fx.coo(), () -> userAdmin.getStaff(staff.getId())).active()).isFalse();
        fx.asRun(fx.coo(), () -> userAdmin.reactivate(staff.getId()));
        assertThat(fx.principal(staff).isEnabled()).isTrue();
        fx.asRun(fx.coo(), () -> assertThatThrownBy(() -> userAdmin.resendInvite(staff.getId())).isInstanceOf(BusinessRuleException.class));
        fx.asRun(fx.coo(), () -> assertThatThrownBy(() -> userAdmin.inviteStaff(
                new com.nirmaan.reimburse.user.InviteForm("X", Fixtures.unique("x") + "@t.local", Role.TEAM_MEMBER))).isInstanceOf(BusinessRuleException.class));
    }

    private static com.nirmaan.reimburse.claim.ClaimForm withVersion(com.nirmaan.reimburse.claim.ClaimForm f, long version) {
        return new com.nirmaan.reimburse.claim.ClaimForm(f.categoryId(), f.claimedAmount(), f.expenseDate(), f.vendorName(),
                f.vendorGstin(), f.invoiceNumber(), f.invoiceDate(), f.justification(), version);
    }
}
