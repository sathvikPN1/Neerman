package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.team.TeamRepository;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.user.PrincipalLoader;
import com.nirmaan.reimburse.user.UserAdminService;
import com.nirmaan.reimburse.user.UserTokenService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Public: accept an invitation by setting a password. */
@Controller
public class InviteController {

    private final UserTokenService tokens;
    private final UserAdminService userAdmin;
    private final PrincipalLoader principals;
    private final TeamRepository teams;

    public InviteController(UserTokenService tokens, UserAdminService userAdmin, PrincipalLoader principals,
                            TeamRepository teams) {
        this.tokens = tokens;
        this.userAdmin = userAdmin;
        this.principals = principals;
        this.teams = teams;
    }

    @GetMapping("/invite/{token}")
    public String form(@PathVariable String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", tokens.peek(token, UserTokenService.INVITE).isPresent());
        addInvitee(token, model);
        return "invite";
    }

    @PostMapping("/invite/{token}")
    public String accept(@PathVariable String token, @RequestParam String password,
                         @RequestParam String confirm, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", true);
        addInvitee(token, model);
        if (!password.equals(confirm)) {
            model.addAttribute("error", "The two passwords do not match.");
            return "invite";
        }
        try {
            userAdmin.acceptInvite(token, password);
        } catch (BusinessRuleException e) {
            model.addAttribute("error", e.getMessage());
            return "invite";
        }
        return "redirect:/login?activated";
    }

    /** Name, email and team of the invited person, for the "Welcome, …" heading. */
    private void addInvitee(String token, Model model) {
        tokens.peek(token, UserTokenService.INVITE).flatMap(principals::loadById).ifPresent(p -> {
            model.addAttribute("inviteeName", p.name());
            model.addAttribute("inviteeEmail", p.email());
            if (p.teamId() != null) {
                teams.findById(p.teamId()).map(Team::getName).ifPresent(n -> model.addAttribute("inviteeTeam", n));
            }
        });
    }
}
