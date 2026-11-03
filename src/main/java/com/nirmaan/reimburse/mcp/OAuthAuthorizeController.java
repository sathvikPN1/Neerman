package com.nirmaan.reimburse.mcp;

import com.nirmaan.reimburse.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The browser part of the OAuth flow: after the user signs in (normal form login), they see a consent
 * screen and choose Allow or Deny. The validated request is kept in the session, never trusted from the form.
 */
@Controller
public class OAuthAuthorizeController {

    private static final String SESSION_KEY = "oauth.pendingRequests";

    private final OAuthService oauth;

    public OAuthAuthorizeController(OAuthService oauth) {
        this.oauth = oauth;
    }

    @GetMapping("/oauth/authorize")
    public String authorize(@RequestParam(value = "response_type", required = false) String responseType,
                            @RequestParam(value = "client_id", required = false) String clientId,
                            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                            @RequestParam(value = "state", required = false) String state,
                            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                            @RequestParam(value = "code_challenge_method", required = false) String method,
                            @RequestParam(value = "resource", required = false) String resource,
                            HttpSession session, Model model) {
        OAuthService.AuthorizationRequest request =
                oauth.validate(responseType, clientId, redirectUri, state, codeChallenge, method, resource);
        oauth.requireTeamMember(CurrentUser.require());
        String requestId = Secrets.randomToken();
        pending(session).put(requestId, request);
        model.addAttribute("requestId", requestId);
        model.addAttribute("clientName", request.clientName());
        model.addAttribute("redirectHost", java.net.URI.create(request.redirectUri()).getHost());
        return "oauth/consent";
    }

    @PostMapping("/oauth/authorize")
    public String decide(@RequestParam String requestId, @RequestParam String decision, HttpSession session) {
        OAuthService.AuthorizationRequest request = pending(session).remove(requestId);
        if (request == null) {
            throw new OAuthException("invalid_request", "This authorization request has expired. Start again from the app.", 400);
        }
        if (!"allow".equals(decision)) {
            return "redirect:" + withParams(request.redirectUri(), "error", "access_denied", "state", request.state(),
                    "iss", oauth.issuer());
        }
        String code = oauth.approve(request);
        return "redirect:" + withParams(request.redirectUri(), "code", code, "state", request.state(), "iss", oauth.issuer());
    }

    @ExceptionHandler(OAuthService.RedirectableError.class)
    public String redirectError(OAuthService.RedirectableError e) {
        return "redirect:" + withParams(e.redirectUri(), "error", e.error(), "error_description", e.getMessage(),
                "state", e.state());
    }

    @ExceptionHandler(OAuthException.class)
    public String error(OAuthException e, HttpServletResponse response, Model model) {
        response.setStatus(400);
        model.addAttribute("status", 400);
        model.addAttribute("message", e.getMessage());
        return "error";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, OAuthService.AuthorizationRequest> pending(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof Map<?, ?> m) {
            return (Map<String, OAuthService.AuthorizationRequest>) m;
        }
        Map<String, OAuthService.AuthorizationRequest> map = new HashMap<>();
        session.setAttribute(SESSION_KEY, map);
        return map;
    }

    /** Appends query parameters (skipping null values) to a redirect URI. */
    static String withParams(String uri, String... keyValues) {
        StringBuilder sb = new StringBuilder(uri);
        char sep = uri.contains("?") ? '&' : '?';
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] == null) {
                continue;
            }
            sb.append(sep).append(keyValues[i]).append('=').append(URLEncoder.encode(keyValues[i + 1], StandardCharsets.UTF_8));
            sep = '&';
        }
        return sb.toString();
    }
}
