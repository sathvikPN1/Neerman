package com.nirmaan.reimburse.config;

import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.user.PrincipalLoader;
import com.nirmaan.reimburse.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reloads the signed-in user's roles and permissions from the database on every request, so grants,
 * revocations, expiries and deactivation apply immediately rather than at next login.
 * Deactivated users are signed out. Also records "last activity" (throttled).
 */
public class AuthorityRefreshFilter extends OncePerRequestFilter {

    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    private final PrincipalLoader loader;
    private final UserRepository users;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Map<Long, Instant> lastTouched = new ConcurrentHashMap<>();

    public AuthorityRefreshFilter(PrincipalLoader loader, UserRepository users, TransactionTemplate tx, Clock clock) {
        this.loader = loader;
        this.users = users;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication auth = context.getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal current) {
            Optional<AppUserPrincipal> fresh = loader.loadById(current.id());
            if (fresh.isEmpty() || !fresh.get().isEnabled()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendRedirect(request.getContextPath() + "/login?deactivated");
                return;
            }
            AppUserPrincipal p = fresh.get();
            UsernamePasswordAuthenticationToken refreshed =
                    UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities());
            refreshed.setDetails(auth.getDetails());
            SecurityContext newContext = SecurityContextHolder.createEmptyContext();
            newContext.setAuthentication(refreshed);
            SecurityContextHolder.setContext(newContext);
            touch(p.id());
        }
        chain.doFilter(request, response);
    }

    private void touch(Long userId) {
        Instant now = Instant.now(clock);
        Instant last = lastTouched.get(userId);
        if (last == null || last.plus(TOUCH_INTERVAL).isBefore(now)) {
            lastTouched.put(userId, now);
            tx.executeWithoutResult(s -> users.touchLastActive(userId, now));
        }
    }
}
