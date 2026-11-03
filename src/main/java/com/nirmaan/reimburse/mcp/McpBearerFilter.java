package com.nirmaan.reimburse.mcp;

import com.nirmaan.reimburse.common.audit.AuditContext;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Protects /mcp with OAuth bearer tokens. Without a valid token it answers 401 with a WWW-Authenticate header
 * pointing at the protected-resource metadata, which is how MCP clients discover where to log in (RFC 9728).
 */
public class McpBearerFilter extends OncePerRequestFilter {

    private final OAuthService oauth;

    public McpBearerFilter(OAuthService oauth) {
        this.oauth = oauth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return !(uri.equals("/mcp") || uri.startsWith("/mcp/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7).trim() : null;
        Optional<OAuthService.Authenticated> user = oauth.authenticate(token);
        if (user.isEmpty()) {
            String challenge = "Bearer resource_metadata=\"" + oauth.issuer() + "/.well-known/oauth-protected-resource\""
                    + (token == null ? "" : ", error=\"invalid_token\"");
            response.setHeader("WWW-Authenticate", challenge);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + (token == null ? "unauthorized" : "invalid_token") + "\"}");
            return;
        }
        AppUserPrincipal p = user.get().user();
        AuditContext.setVia("AI connector: " + user.get().clientName());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        try {
            chain.doFilter(request, response);
        } finally {
            AuditContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
