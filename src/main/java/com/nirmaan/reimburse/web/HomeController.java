package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.user.Permission;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/login")
    public String login() {
        return CurrentUser.get().isPresent() ? "redirect:/" : "login";
    }

    /** Sends each user to the screen they use most. */
    @GetMapping("/")
    public String home() {
        AppUserPrincipal me = CurrentUser.require();
        if (me.isTeamMember()) {
            return "redirect:/team";
        }
        if (me.has(Permission.CLAIM_APPROVE)) {
            return "redirect:/coo/approvals";
        }
        if (me.has(Permission.CLAIM_VERIFY)) {
            return "redirect:/staff/queue";
        }
        if (me.has(Permission.PAYMENT_RECORD)) {
            return "redirect:/finance/payments";
        }
        if (me.has(Permission.TEAM_MANAGE)) {
            return "redirect:/teams";
        }
        return "redirect:/notifications";
    }
}
