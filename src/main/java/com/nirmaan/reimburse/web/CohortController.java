package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.cohort.CohortForm;
import com.nirmaan.reimburse.cohort.CohortService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CohortController {

    private final CohortService cohorts;

    public CohortController(CohortService cohorts) {
        this.cohorts = cohorts;
    }

    @GetMapping("/cohorts")
    @PreAuthorize("!hasRole('TEAM_MEMBER')")
    public String list(Model model) {
        model.addAttribute("cohorts", cohorts.list());
        return "cohorts/list";
    }

    @PostMapping("/cohorts")
    @PreAuthorize("hasAuthority('COHORT_MANAGE')")
    public String save(@Valid @ModelAttribute CohortForm form, BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", "Enter a name, start date and end date.");
        } else {
            cohorts.save(form);
            redirect.addFlashAttribute("success", "Cohort saved.");
        }
        return "redirect:/cohorts";
    }
}
