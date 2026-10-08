package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.category.Category;
import com.nirmaan.reimburse.category.CategoryRepository;
import com.nirmaan.reimburse.cohort.Cohort;
import com.nirmaan.reimburse.cohort.CohortRepository;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.team.TeamRepository;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns claims into display rows with names resolved in bulk (no N+1 queries). */
@Component
public class ClaimRowMapper {

    private final TeamRepository teams;
    private final CohortRepository cohorts;
    private final CategoryRepository categories;
    private final UserRepository users;

    public ClaimRowMapper(TeamRepository teams, CohortRepository cohorts, CategoryRepository categories,
                          UserRepository users) {
        this.teams = teams;
        this.cohorts = cohorts;
        this.categories = categories;
        this.users = users;
    }

    public List<ClaimRow> map(Collection<Claim> claims) {
        if (claims.isEmpty()) {
            return List.of();
        }
        Map<Long, Team> teamById = teams.findAllById(claims.stream().map(Claim::getTeamId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        Map<Long, String> cohortNames = cohorts.findAll().stream().collect(Collectors.toMap(Cohort::getId, Cohort::getName));
        Map<Long, String> categoryNames = categories.findAll().stream().collect(Collectors.toMap(Category::getId, Category::getName));
        Set<Long> userIds = new HashSet<>();
        claims.forEach(c -> {
            if (c.getVerifiedBy() != null) userIds.add(c.getVerifiedBy());
        });
        Map<Long, String> userNames = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getName));

        return claims.stream().map(c -> {
            Team t = teamById.get(c.getTeamId());
            boolean priority = c.isPriority() || (t != null && t.isPriority());
            String reason = c.isPriority() ? c.getPriorityReason() : (t != null && t.isPriority() ? "Team: " + t.getPriorityReason() : null);
            return new ClaimRow(c.getId(), c.getPublicCode(), c.getTeamId(), t == null ? "—" : t.getName(),
                    t == null ? "—" : cohortNames.getOrDefault(t.getCohortId(), "—"),
                    categoryNames.getOrDefault(c.getCategoryId(), "—"), c.getVendorName(), c.getClaimedAmount(),
                    c.getApprovedAmount(), c.getStatus(), priority, reason, c.isPriorityRequested(), c.getExpenseDate(),
                    c.getSubmittedAt(), c.getStageEnteredAt(), userNames.get(c.getVerifiedBy()), c.getVersion());
        }).toList();
    }
}
