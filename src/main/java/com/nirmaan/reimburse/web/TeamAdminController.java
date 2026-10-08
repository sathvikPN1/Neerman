package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.cohort.CohortService;
import com.nirmaan.reimburse.common.web.FormErrors;
import com.nirmaan.reimburse.team.MemberForm;
import com.nirmaan.reimburse.team.Program;
import com.nirmaan.reimburse.team.TeamDetail;
import com.nirmaan.reimburse.team.TeamForm;
import com.nirmaan.reimburse.team.TeamService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TeamAdminController {

    private final TeamService teams;
    private final CohortService cohorts;
    private final ClaimService claims;

    public TeamAdminController(TeamService teams, CohortService cohorts, ClaimService claims) {
        this.teams = teams;
        this.cohorts = cohorts;
        this.claims = claims;
    }

    @GetMapping("/teams")
    @PreAuthorize("!hasRole('TEAM_MEMBER')")
    public String list(Model model) {
        model.addAttribute("teams", teams.list());
        return "teams/list";
    }

    @GetMapping("/teams/new")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public String newForm(Model model) {
        return form(model, new TeamForm(null, "", null, Program.PRATHAM, null));
    }

    @GetMapping("/teams/{id}/edit")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public String edit(@PathVariable Long id, Model model) {
        TeamDetail d = teams.get(id);
        var s = d.summary();
        return form(model, new TeamForm(s.id(), s.name(), s.cohortId(), s.program(), s.budget().total()));
    }

    private String form(Model model, TeamForm form) {
        model.addAttribute("form", form);
        model.addAttribute("cohorts", cohorts.list());
        model.addAttribute("programs", Program.values());
        model.addAttribute("prathamDefault", teams.defaultBudget(Program.PRATHAM));
        model.addAttribute("aksharDefault", teams.defaultBudget(Program.AKSHAR));
        return "teams/form";
    }

    @PostMapping("/teams")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public String save(@Valid @ModelAttribute("form") TeamForm form, BindingResult errors, Model model,
                       RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("errors", FormErrors.of(errors));
            return form(model, form);
        }
        Long id = teams.save(form);
        redirect.addFlashAttribute("success", "Team saved.");
        return "redirect:/teams/" + id;
    }

    @GetMapping("/teams/{id}")
    @PreAuthorize("!hasRole('TEAM_MEMBER')")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("team", teams.get(id));
        model.addAttribute("claims", claims.teamClaims(id));
        return "teams/detail";
    }

    @PostMapping("/teams/{id}/members")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public String addMember(@PathVariable Long id, @Valid @ModelAttribute MemberForm form, BindingResult errors,
                            RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", "Enter the member's name and a valid email.");
        } else {
            teams.addMember(id, form);
            redirect.addFlashAttribute("success", "Invitation sent to " + form.email() + ".");
        }
        return "redirect:/teams/" + id;
    }

    @PostMapping("/teams/{id}/members/{userId}/remove")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public String removeMember(@PathVariable Long id, @PathVariable Long userId, RedirectAttributes redirect) {
        teams.removeMember(id, userId);
        redirect.addFlashAttribute("success", "Member removed and their account deactivated.");
        return "redirect:/teams/" + id;
    }

    @PostMapping("/teams/{id}/priority")
    @PreAuthorize("hasAuthority('CLAIM_FLAG_PRIORITY')")
    public String priority(@PathVariable Long id, @RequestParam boolean priority,
                           @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        teams.setPriority(id, priority, reason);
        redirect.addFlashAttribute("success", priority ? "Team marked as priority." : "Team priority removed.");
        return "redirect:/teams/" + id;
    }
}
