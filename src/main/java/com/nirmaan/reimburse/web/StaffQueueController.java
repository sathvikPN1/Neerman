package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.category.CategoryService;
import com.nirmaan.reimburse.claim.ClaimQueueService;
import com.nirmaan.reimburse.claim.ClaimRow;
import com.nirmaan.reimburse.claim.ClaimSearch;
import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.claim.QueueFilter;
import com.nirmaan.reimburse.cohort.CohortService;
import com.nirmaan.reimburse.team.TeamService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class StaffQueueController {

    private final ClaimQueueService queues;
    private final TeamService teams;
    private final CohortService cohorts;
    private final CategoryService categories;

    public StaffQueueController(ClaimQueueService queues, TeamService teams, CohortService cohorts,
                                CategoryService categories) {
        this.queues = queues;
        this.teams = teams;
        this.cohorts = cohorts;
        this.categories = categories;
    }

    @GetMapping("/staff/queue")
    @PreAuthorize("hasAnyAuthority('CLAIM_VERIFY', 'CLAIM_RETURN_REJECT', 'CLAIM_APPROVE', 'CLAIM_FLAG_PRIORITY')")
    public String queue(@RequestParam(required = false) ClaimStatus status,
                        @RequestParam(required = false) Long teamId,
                        @RequestParam(required = false) Long cohortId,
                        @RequestParam(required = false) Long categoryId,
                        @RequestParam(required = false) Integer minAgeDays,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) String only,
                        Model model) {
        QueueFilter filter = new QueueFilter(status, teamId, cohortId, categoryId, minAgeDays);
        List<ClaimRow> found = ClaimSearch.filter(queues.staffQueue(filter), q);
        model.addAttribute("filter", filter);
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("only", only == null ? "" : only);
        model.addAttribute("allCount", found.size());
        model.addAttribute("priorityCount", found.stream().filter(ClaimRow::priority).count());
        model.addAttribute("requestedCount", found.stream().filter(ClaimRow::priorityRequested).count());
        // Tabs above the list: everything, priority only, or priority requested by the team.
        model.addAttribute("rows", switch (only == null ? "" : only) {
            case "priority" -> found.stream().filter(ClaimRow::priority).toList();
            case "requested" -> found.stream().filter(ClaimRow::priorityRequested).toList();
            default -> found;
        });
        model.addAttribute("statuses", ClaimStatus.values());
        model.addAttribute("teams", teams.list());
        model.addAttribute("cohorts", cohorts.list());
        model.addAttribute("categories", categories.listAll());
        return "staff/queue";
    }
}
