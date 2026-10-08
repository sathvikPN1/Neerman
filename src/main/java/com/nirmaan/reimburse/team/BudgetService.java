package com.nirmaan.reimburse.team;

import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.security.AccessPolicy;
import com.nirmaan.reimburse.common.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class BudgetService {

    private static final String SUMS = """
            select team_id,
                   coalesce(sum(approved_amount) filter (where status = 'APPROVED'), 0) as approved,
                   coalesce(sum(approved_amount) filter (where status = 'PAID'), 0) as paid,
                   coalesce(sum(claimed_amount) filter (where status in ('SUBMITTED', 'VERIFIED')), 0) as pending
            from claims
            """;

    private final TeamRepository teams;
    private final JdbcTemplate jdbc;

    public BudgetService(TeamRepository teams, JdbcTemplate jdbc) {
        this.teams = teams;
        this.jdbc = jdbc;
    }

    /** Budget for a team the current user may see. */
    @Transactional(readOnly = true)
    public BudgetSnapshot snapshotForCurrentUser(Long teamId) {
        AccessPolicy.requireTeamView(CurrentUser.require(), teamId);
        return snapshot(teamId);
    }

    /** Internal: no access check. */
    @Transactional(readOnly = true)
    public BudgetSnapshot snapshot(Long teamId) {
        Team team = teams.findById(teamId).orElseThrow(() -> NotFoundException.of("Team", teamId));
        return jdbc.query(SUMS + " where team_id = ? group by team_id", rs -> rs.next()
                ? new BudgetSnapshot(team.getBudgetAmount(), rs.getBigDecimal("approved"), rs.getBigDecimal("paid"),
                rs.getBigDecimal("pending"))
                : new BudgetSnapshot(team.getBudgetAmount(), null, null, null), teamId);
    }

    /** Internal: snapshots for all teams, keyed by team id. */
    @Transactional(readOnly = true)
    public Map<Long, BudgetSnapshot> allSnapshots() {
        Map<Long, BigDecimal[]> sums = new HashMap<>();
        jdbc.query(SUMS + " group by team_id", rs -> {
            sums.put(rs.getLong("team_id"), new BigDecimal[]{rs.getBigDecimal("approved"), rs.getBigDecimal("paid"),
                    rs.getBigDecimal("pending")});
        });
        Map<Long, BudgetSnapshot> result = new HashMap<>();
        for (Team t : teams.findAll()) {
            BigDecimal[] s = sums.getOrDefault(t.getId(), new BigDecimal[3]);
            result.put(t.getId(), new BudgetSnapshot(t.getBudgetAmount(), s[0], s[1], s[2]));
        }
        return result;
    }
}
