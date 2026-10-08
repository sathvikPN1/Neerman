package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.category.CategoryService;
import com.nirmaan.reimburse.claim.ClaimQueueService;
import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.claim.QueueFilter;
import com.nirmaan.reimburse.cohort.CohortService;
import com.nirmaan.reimburse.team.TeamService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
                        Model model) {
        QueueFilter filter = new QueueFilter(status, teamId, cohortId, categoryId, minAgeDays);
        model.addAttribute("filter", filter);
        model.addAttribute("rows", queues.staffQueue(filter));
        model.addAttribute("statuses", ClaimStatus.values());
        model.addAttribute("teams", teams.list());
        model.addAttribute("cohorts", cohorts.list());
        model.addAttribute("categories", categories.listAll());
        return "staff/queue";
    }
}
