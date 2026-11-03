package com.nirmaan.reimburse.mcp;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Redirect URI rules for registered clients: HTTPS anywhere, or plain HTTP only on the loopback interface
 * (desktop / CLI clients such as Claude Code). No fragments. Loopback URIs match on any port (RFC 8252 §7.3).
 */
public final class RedirectUris {

    private RedirectUris() {
    }

    public static boolean isAcceptable(String uri) {
        try {
            URI u = URI.create(uri);
            if (u.getFragment() != null || u.getHost() == null || uri.length() > 500) {
                return false;
            }
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            return scheme.equals("https") || (scheme.equals("http") && isLoopback(u.getHost()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Exact match against a registered URI; loopback URIs may differ only in port. */
    public static boolean matches(List<String> registered, String requested) {
        if (requested == null) {
            return false;
        }
        for (String r : registered) {
            if (r.equals(requested)) {
                return true;
            }
            try {
                URI a = URI.create(r);
                URI b = URI.create(requested);
                if ("http".equals(a.getScheme()) && "http".equals(b.getScheme()) && isLoopback(a.getHost())
                        && a.getHost().equals(b.getHost()) && eq(a.getRawPath(), b.getRawPath())
                        && eq(a.getRawQuery(), b.getRawQuery()) && b.getFragment() == null) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // not a valid URI: no match
            }
        }
        return false;
    }

    private static boolean isLoopback(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
