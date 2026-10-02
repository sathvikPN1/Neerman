package com.nirmaan.reimburse.common.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Static access to the authenticated principal for services. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AppUserPrincipal> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static AppUserPrincipal require() {
        return get().orElseThrow(() -> new AccessDeniedException("Not signed in"));
    }
}
