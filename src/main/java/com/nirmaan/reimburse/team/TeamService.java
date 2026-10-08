package com.nirmaan.reimburse.team;

import com.nirmaan.reimburse.cohort.Cohort;
import com.nirmaan.reimburse.cohort.CohortRepository;
import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.common.security.AccessPolicy;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.settings.SettingsService;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserAdminService;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

@Service
public class TeamService {

    private final TeamRepository teams;
    private final CohortRepository cohorts;
    private final UserRepository users;
    private final UserAdminService userAdmin;
    private final BudgetService budgets;
    private final SettingsService settings;
    private final AuditService audit;

    public TeamService(TeamRepository teams, CohortRepository cohorts, UserRepository users, UserAdminService userAdmin,
                       BudgetService budgets, SettingsService settings, AuditService audit) {
        this.teams = teams;
        this.cohorts = cohorts;
        this.users = users;
        this.userAdmin = userAdmin;
        this.budgets = budgets;
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TeamSummary> list() {
        AccessPolicy.requireStaffSide(CurrentUser.require());
        Map<Long, String> cohortNames = cohorts.findAll().stream().collect(Collectors.toMap(Cohort::getId, Cohort::getName));
        Map<Long, BudgetSnapshot> snapshots = budgets.allSnapshots();
        return teams.findAllByOrderByNameAsc().stream()
                .map(t -> summary(t, cohortNames.get(t.getCohortId()), snapshots.get(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamDetail get(Long teamId) {
        AccessPolicy.requireTeamView(CurrentUser.require(), teamId);
        Team t = find(teamId);
        String cohortName = cohorts.findById(t.getCohortId()).map(Cohort::getName).orElse("—");
        List<TeamDetail.Member> members = users.findAllById(teams.findMemberIds(teamId)).stream()
                .sorted(Comparator.comparing(User::getName))
                .map(u -> new TeamDetail.Member(u.getId(), u.getName(), u.getEmail(), u.isActive(), u.getPasswordHash() == null))
                .toList();
        return new TeamDetail(summary(t, cohortName, budgets.snapshot(teamId)), members);
    }

    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    @Transactional
    public Long save(TeamForm form) {
        cohorts.findById(form.cohortId()).orElseThrow(() -> NotFoundException.of("Cohort", form.cohortId()));
        teams.findByNameIgnoreCase(form.name().trim())
                .filter(t -> !t.getId().equals(form.id()))
                .ifPresent(t -> {
                    throw new BusinessRuleException("A team named \"" + form.name() + "\" already exists.");
                });
        BigDecimal budget = form.budgetAmount() != null
                ? Money.normalise(form.budgetAmount())
                : defaultBudget(form.program());
        if (form.id() == null) {
            Team t = teams.save(new Team(form.name(), form.cohortId(), form.program(), budget));
            audit.record(AuditEntity.TEAM, t.getId(), "TEAM_CREATED",
                    detail("name", t.getName(), "program", t.getProgram(), "budget", budget));
            return t.getId();
        }
        Team t = find(form.id());
        Map<String, Object> changes = detail("name", t.getName() + " → " + form.name().trim(),
                "program", t.getProgram() + " → " + form.program(),
                "budget", t.getBudgetAmount() + " → " + budget);
        t.setName(form.name());
        t.setCohortId(form.cohortId());
        t.setProgram(form.program());
        t.setBudgetAmount(budget);
        audit.record(AuditEntity.TEAM, t.getId(), "TEAM_UPDATED", changes);
        return t.getId();
    }

    public BigDecimal defaultBudget(Program program) {
        BigDecimal fallback = program == Program.AKSHAR ? new BigDecimal("500000.00") : new BigDecimal("200000.00");
        return Money.normalise(settings.getDecimal(program.budgetSettingKey(), fallback));
    }

    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    @Transactional
    public Long addMember(Long teamId, MemberForm form) {
        Team t = find(teamId);
        User user = userAdmin.createInvitedUser(form.name(), form.email(), Role.TEAM_MEMBER);
        teams.addMember(t.getId(), user.getId());
        audit.record(AuditEntity.TEAM, t.getId(), "MEMBER_ADDED", detail("email", user.getEmail()));
        return user.getId();
    }

    /** Removes a student from the team and deactivates their account. Their claims stay. */
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    @Transactional
    public void removeMember(Long teamId, Long userId) {
        User user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        if (teams.removeMember(teamId, userId) == 0) {
            throw new BusinessRuleException(user.getName() + " is not a member of this team.");
        }
        user.setActive(false);
        audit.record(AuditEntity.TEAM, teamId, "MEMBER_REMOVED", detail("email", user.getEmail()));
    }

    @PreAuthorize("hasAuthority('CLAIM_FLAG_PRIORITY')")
    @Transactional
    public void setPriority(Long teamId, boolean priority, String reason) {
        Team t = find(teamId);
        if (priority && (reason == null || reason.isBlank())) {
            throw new BusinessRuleException("A reason is required to mark a team as priority.");
        }
        t.setPriority(priority, reason == null ? null : reason.trim());
        audit.record(AuditEntity.TEAM, teamId, priority ? "TEAM_PRIORITY_SET" : "TEAM_PRIORITY_CLEARED",
                detail("reason", reason));
    }

    /** Internal lookups for other services. */
    @Transactional(readOnly = true)
    public Map<Long, Team> byId() {
        return teams.findAll().stream().collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<Long> memberIds(Long teamId) {
        return teams.findMemberIds(teamId);
    }

    private Team find(Long id) {
        return teams.findById(id).orElseThrow(() -> NotFoundException.of("Team", id));
    }

    private TeamSummary summary(Team t, String cohortName, BudgetSnapshot snapshot) {
        return new TeamSummary(t.getId(), t.getName(), t.getCohortId(), cohortName, t.getProgram(), t.isPriority(),
                t.getPriorityReason(), t.isActive(), teams.findMemberIds(t.getId()).size(),
                snapshot == null ? new BudgetSnapshot(t.getBudgetAmount(), null, null, null) : snapshot);
    }
}
