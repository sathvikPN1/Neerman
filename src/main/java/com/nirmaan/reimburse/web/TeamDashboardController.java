package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.claim.ClaimRow;
import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.team.TeamService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Controller
@PreAuthorize("hasRole('TEAM_MEMBER')")
public class TeamDashboardController {

    private final TeamService teams;
    private final ClaimService claims;

    public TeamDashboardController(TeamService teams, ClaimService claims) {
        this.teams = teams;
        this.claims = claims;
    }

    @GetMapping("/team")
    public String dashboard(@RequestParam(required = false) String q, Model model) {
        AppUserPrincipal me = CurrentUser.require();
        if (me.teamId() == null) {
            model.addAttribute("message", "You are not part of a team yet. Ask Nirmaan staff to add you.");
            model.addAttribute("status", 200);
            return "error";
        }
        model.addAttribute("team", teams.get(me.teamId()));
        List<ClaimRow> all = claims.teamClaims(me.teamId());
        model.addAttribute("returned", all.stream().filter(c -> c.status() == ClaimStatus.RETURNED).toList());
        model.addAttribute("claims", matching(all, q));
        model.addAttribute("q", q == null ? "" : q.trim());
        return "team/dashboard";
    }

    /** The search box: claim code, vendor, category or description containing the text, ignoring case. */
    static List<ClaimRow> matching(List<ClaimRow> rows, String q) {
        if (q == null || q.isBlank()) {
            return rows;
        }
        String needle = q.trim().toLowerCase(Locale.ROOT);
        return rows.stream().filter(c -> Stream.of(c.publicCode(), c.vendorName(), c.categoryName(), c.justification())
                .anyMatch(f -> f != null && f.toLowerCase(Locale.ROOT).contains(needle))).toList();
    }
}
