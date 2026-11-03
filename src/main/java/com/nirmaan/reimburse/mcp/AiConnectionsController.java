package com.nirmaan.reimburse.mcp;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Team members: how to connect ChatGPT / Claude, and the list of connected assistants with Revoke. */
@Controller
@PreAuthorize("hasRole('TEAM_MEMBER')")
public class AiConnectionsController {

    private final OAuthService oauth;

    public AiConnectionsController(OAuthService oauth) {
        this.oauth = oauth;
    }

    @GetMapping("/team/ai")
    public String page(Model model) {
        model.addAttribute("mcpUrl", oauth.resource());
        model.addAttribute("connections", oauth.myConnections());
        model.addAttribute("publicUrl", oauth.issuer().startsWith("https://"));
        return "team/ai-connections";
    }

    @PostMapping("/team/ai/{grantId}/revoke")
    public String revoke(@PathVariable long grantId, RedirectAttributes redirect) {
        oauth.revokeMyConnection(grantId);
        redirect.addFlashAttribute("success", "Access revoked. That assistant can no longer reach your claims.");
        return "redirect:/team/ai";
    }
}
