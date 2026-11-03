package com.nirmaan.reimburse.mcp;

import com.nirmaan.reimburse.claim.ClaimDetailView;
import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.document.DocumentService;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.user.PrincipalLoader;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One-time upload page handed out by the AI assistant (valid 30 minutes). The link itself is the credential:
 * uploads run as the team member who requested it, through the normal DocumentService checks
 * (own team, draft/returned claim, magic-byte type check, 10 MB limit).
 */
@Controller
public class UploadLinkController {

    private final OAuthService oauth;
    private final PrincipalLoader principals;
    private final ClaimService claims;
    private final DocumentService documents;

    public UploadLinkController(OAuthService oauth, PrincipalLoader principals, ClaimService claims, DocumentService documents) {
        this.oauth = oauth;
        this.principals = principals;
        this.claims = claims;
        this.documents = documents;
    }

    @GetMapping("/upload/{token}")
    public String page(@PathVariable String token, Model model, HttpServletResponse response) {
        Optional<OAuthStore.UploadLink> link = oauth.resolveUploadLink(token);
        if (link.isEmpty()) {
            response.setStatus(410);
            model.addAttribute("expired", true);
            return "upload/link";
        }
        ClaimDetailView claim = asUser(link.get().userId(), () -> claims.detail(link.get().claimId()));
        model.addAttribute("claim", claim);
        model.addAttribute("token", token);
        model.addAttribute("expired", false);
        return "upload/link";
    }

    @PostMapping("/upload/{token}")
    public String upload(@PathVariable String token, @RequestParam DocumentType type,
                         @RequestParam("file") MultipartFile file, RedirectAttributes redirect) throws IOException {
        OAuthStore.UploadLink link = oauth.resolveUploadLink(token)
                .orElseThrow(() -> new BusinessRuleException("This upload link has expired. Ask your AI assistant for a new one."));
        if (file.isEmpty()) {
            throw new BusinessRuleException("Choose a file to upload.");
        }
        byte[] bytes = file.getBytes();
        asUser(link.userId(), () -> documents.upload(link.claimId(), type, file.getOriginalFilename(), bytes));
        redirect.addFlashAttribute("success", type.label() + " uploaded. You can close this page and tell your assistant.");
        return "redirect:/upload/" + token;
    }

    /** Runs an action as the link's owner, restoring whoever (if anyone) is signed in in this browser. */
    private <T> T asUser(long userId, Supplier<T> action) {
        AppUserPrincipal p = principals.loadById(userId).filter(AppUserPrincipal::isEnabled)
                .orElseThrow(() -> new BusinessRuleException("This upload link is no longer valid."));
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
