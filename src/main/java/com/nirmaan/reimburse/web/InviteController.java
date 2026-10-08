package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.common.exception.BusinessRuleException;
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

    public InviteController(UserTokenService tokens, UserAdminService userAdmin) {
        this.tokens = tokens;
        this.userAdmin = userAdmin;
    }

    @GetMapping("/invite/{token}")
    public String form(@PathVariable String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", tokens.peek(token, UserTokenService.INVITE).isPresent());
        return "invite";
    }

    @PostMapping("/invite/{token}")
    public String accept(@PathVariable String token, @RequestParam String password,
                         @RequestParam String confirm, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", true);
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
}
