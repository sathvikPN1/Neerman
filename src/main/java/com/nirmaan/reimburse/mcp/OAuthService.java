package com.nirmaan.reimburse.mcp;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.config.AppProperties;
import com.nirmaan.reimburse.user.PrincipalLoader;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

/**
 * Minimal OAuth 2.1 authorization server for the MCP connector (authorization code + PKCE, refresh tokens).
 * Only startup team members can connect; every grant is per user and per client and can be revoked.
 */
@Service
public class OAuthService {

    public static final String SCOPE = "claims";
    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration ACCESS_TTL = Duration.ofHours(1);
    static final Duration REFRESH_TTL = Duration.ofDays(30);
    static final Duration UPLOAD_LINK_TTL = Duration.ofMinutes(30);
    static final int MAX_REDIRECT_URIS = 5;

    /** Validated parameters of an authorization request, kept in the session while the user decides. */
    public record AuthorizationRequest(String clientId, String clientName, String redirectUri, String state,
                                       String codeChallenge) implements java.io.Serializable {
    }

    /** A valid access token: the user it acts for and the app (client) using it. */
    public record Authenticated(AppUserPrincipal user, String clientName) {
    }

    /** Token endpoint response. */
    public record TokenResponse(String accessToken, long expiresIn, String refreshToken, String scope) {
    }

    private final OAuthStore store;
    private final PrincipalLoader principals;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    public OAuthService(OAuthStore store, PrincipalLoader principals, AuditService audit, AppProperties props, Clock clock) {
        this.store = store;
        this.principals = principals;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    public String issuer() {
        return props.baseUrl().replaceAll("/+$", "");
    }

    public String resource() {
        return issuer() + "/mcp";
    }

    // ---------------------------------------------------------------- dynamic client registration (RFC 7591)

    @Transactional
    public OAuthStore.Client register(String clientName, List<String> redirectUris) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw OAuthException.invalidRequest("redirect_uris is required");
        }
        if (redirectUris.size() > MAX_REDIRECT_URIS) {
            throw OAuthException.invalidRequest("At most " + MAX_REDIRECT_URIS + " redirect_uris are allowed");
        }
        for (String uri : redirectUris) {
            if (!RedirectUris.isAcceptable(uri)) {
                throw new OAuthException("invalid_redirect_uri",
                        "Redirect URIs must use https, or http on localhost, and have no fragment: " + uri, 400);
            }
        }
        String name = clientName == null || clientName.isBlank() ? "AI assistant" : clientName.trim();
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        OAuthStore.Client client = new OAuthStore.Client("mcp-" + Secrets.randomToken(), name, List.copyOf(redirectUris));
        store.saveClient(client);
        return client;
    }

    // ---------------------------------------------------------------- authorization endpoint

    /**
     * Checks an incoming authorization request. Client and redirect URI are verified first: if they are wrong
     * the caller must show an error page and must NOT redirect (RFC 6749 §4.1.2.1).
     */
    @Transactional(readOnly = true)
    public AuthorizationRequest validate(String responseType, String clientId, String redirectUri, String state,
                                         String codeChallenge, String codeChallengeMethod, String resource) {
        OAuthStore.Client client = store.findClient(clientId == null ? "" : clientId)
                .orElseThrow(() -> new OAuthException("invalid_client", "Unknown client. Reconnect the app.", 400));
        if (!RedirectUris.matches(client.redirectUris(), redirectUri)) {
            throw new OAuthException("invalid_request", "The redirect URI does not match the registered one.", 400);
        }
        // From here on, errors can be sent back to the client's redirect URI.
        if (!"code".equals(responseType)) {
            throw new RedirectableError(redirectUri, state, "unsupported_response_type", "Only response_type=code is supported");
        }
        if (codeChallenge == null || codeChallenge.isBlank() || !"S256".equals(codeChallengeMethod)) {
            throw new RedirectableError(redirectUri, state, "invalid_request", "PKCE with code_challenge_method=S256 is required");
        }
        if (resource != null && !resource.isBlank() && !resource.replaceAll("/+$", "").equals(resource())) {
            throw new RedirectableError(redirectUri, state, "invalid_target", "Unknown resource " + resource);
        }
        return new AuthorizationRequest(client.clientId(), client.clientName(), redirectUri, state, codeChallenge);
    }

    /** Only startup team members may connect an AI assistant. */
    public void requireTeamMember(AppUserPrincipal user) {
        if (!user.isTeamMember() || user.teamId() == null) {
            throw new AccessDeniedException("AI connections are available to startup team members only.");
        }
    }

    /** The signed-in user approved the request: create the grant and a single-use authorization code. */
    @Transactional
    public String approve(AuthorizationRequest request) {
        AppUserPrincipal user = CurrentUser.require();
        requireTeamMember(user);
        long grantId = store.createGrant(request.clientId(), user.id(), SCOPE);
        String code = Secrets.randomToken();
        store.saveCode(Secrets.sha256Hex(code), grantId, request.redirectUri(), request.codeChallenge(),
                Instant.now(clock).plus(CODE_TTL));
        audit.record(AuditEntity.USER, user.id(), "AI_CONNECTOR_AUTHORIZED", user.id(), null, null, null, null,
                detail("client", request.clientName(), "grantId", grantId));
        return code;
    }

    // ---------------------------------------------------------------- token endpoint

    // noRollbackFor: consuming the code and revoking a compromised grant must stick even though we reject the request.
    @Transactional(noRollbackFor = OAuthException.class)
    public TokenResponse exchangeCode(String clientId, String code, String redirectUri, String codeVerifier) {
        requireClient(clientId);
        if (code == null || code.isBlank()) {
            throw OAuthException.invalidRequest("code is required");
        }
        String codeHash = Secrets.sha256Hex(code);
        OAuthStore.AuthCode stored = store.findCode(codeHash).orElseThrow(() -> OAuthException.invalidGrant("Unknown code"));
        OAuthStore.Grant grant = store.findGrant(stored.grantId()).orElseThrow(() -> OAuthException.invalidGrant("Unknown grant"));
        Instant now = Instant.now(clock);
        if (!grant.clientId().equals(clientId)) {
            throw OAuthException.invalidGrant("Code was issued to another client");
        }
        if (!store.useCode(codeHash, now)) {
            // A code used twice suggests it was intercepted: revoke everything issued from it (RFC 6749 §4.1.2).
            store.revokeGrant(grant.id(), now);
            throw OAuthException.invalidGrant("Code already used");
        }
        if (stored.expiresAt().isBefore(now)) {
            throw OAuthException.invalidGrant("Code expired");
        }
        if (!stored.redirectUri().equals(redirectUri)) {
            throw OAuthException.invalidGrant("redirect_uri does not match the authorization request");
        }
        if (!Secrets.pkceMatches(codeVerifier, stored.codeChallenge())) {
            throw OAuthException.invalidGrant("PKCE verification failed");
        }
        if (!grant.active()) {
            throw OAuthException.invalidGrant("Access was revoked");
        }
        return issueTokens(grant.id(), now);
    }

    /** Refresh tokens rotate: each use returns a new one and the old one stops working. */
    @Transactional(noRollbackFor = OAuthException.class)
    public TokenResponse refresh(String clientId, String refreshToken) {
        requireClient(clientId);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw OAuthException.invalidRequest("refresh_token is required");
        }
        String hash = Secrets.sha256Hex(refreshToken);
        OAuthStore.Token token = store.findToken(hash).filter(t -> t.kind().equals("REFRESH"))
                .orElseThrow(() -> OAuthException.invalidGrant("Unknown refresh token"));
        OAuthStore.Grant grant = store.findGrant(token.grantId()).orElseThrow(() -> OAuthException.invalidGrant("Unknown grant"));
        Instant now = Instant.now(clock);
        if (!grant.clientId().equals(clientId)) {
            throw OAuthException.invalidGrant("Token was issued to another client");
        }
        if (token.revokedAt() != null) {
            // Reuse of a rotated refresh token: treat the grant as compromised.
            store.revokeGrant(grant.id(), now);
            throw OAuthException.invalidGrant("Refresh token already used");
        }
        if (!grant.active() || token.expiresAt().isBefore(now)) {
            throw OAuthException.invalidGrant("Refresh token expired or revoked");
        }
        if (!store.revokeToken(hash, now)) {
            throw OAuthException.invalidGrant("Refresh token already used");
        }
        return issueTokens(grant.id(), now);
    }

    /** RFC 7009: always succeeds, even for unknown tokens. */
    @Transactional
    public void revokeToken(String token) {
        if (token != null && !token.isBlank()) {
            store.revokeToken(Secrets.sha256Hex(token), Instant.now(clock));
        }
    }

    private TokenResponse issueTokens(long grantId, Instant now) {
        String access = Secrets.randomToken();
        String refresh = Secrets.randomToken();
        store.saveToken(Secrets.sha256Hex(access), grantId, "ACCESS", now.plus(ACCESS_TTL));
        store.saveToken(Secrets.sha256Hex(refresh), grantId, "REFRESH", now.plus(REFRESH_TTL));
        return new TokenResponse(access, ACCESS_TTL.toSeconds(), refresh, SCOPE);
    }

    private void requireClient(String clientId) {
        if (clientId == null || store.findClient(clientId).isEmpty()) {
            throw OAuthException.invalidClient("Unknown client");
        }
    }

    // ---------------------------------------------------------------- resource server

    /**
     * Resolves a bearer access token to the user it acts for. Empty if the token is unknown, expired or revoked,
     * or if the user is no longer an active team member.
     */
    @Transactional
    public Optional<Authenticated> authenticate(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        return store.findToken(Secrets.sha256Hex(accessToken))
                .filter(t -> t.kind().equals("ACCESS") && t.revokedAt() == null && t.expiresAt().isAfter(now))
                .flatMap(t -> store.findGrant(t.grantId()))
                .filter(OAuthStore.Grant::active)
                .flatMap(g -> {
                    store.touchGrant(g.id(), now);
                    return principals.loadById(g.userId())
                            .filter(p -> p.isEnabled() && p.isTeamMember() && p.teamId() != null)
                            .map(p -> new Authenticated(p, g.clientName()));
                });
    }

    // ---------------------------------------------------------------- the user's connections page

    @Transactional(readOnly = true)
    public List<OAuthStore.Grant> myConnections() {
        return store.grantsOfUser(CurrentUser.require().id()).stream().filter(OAuthStore.Grant::active).toList();
    }

    @Transactional
    public void revokeMyConnection(long grantId) {
        AppUserPrincipal user = CurrentUser.require();
        OAuthStore.Grant grant = store.findGrant(grantId).orElseThrow(() -> NotFoundException.of("Connection", grantId));
        if (grant.userId() != user.id()) {
            throw new AccessDeniedException("Not your connection");
        }
        store.revokeGrant(grantId, Instant.now(clock));
        audit.record(AuditEntity.USER, user.id(), "AI_CONNECTOR_REVOKED", detail("client", grant.clientName(), "grantId", grantId));
    }

    // ---------------------------------------------------------------- upload links

    @Transactional
    public String createUploadLink(long claimId) {
        String token = Secrets.randomToken();
        store.saveUploadLink(Secrets.sha256Hex(token), claimId, CurrentUser.require().id(), Instant.now(clock).plus(UPLOAD_LINK_TTL));
        return issuer() + "/upload/" + token;
    }

    @Transactional(readOnly = true)
    public Optional<OAuthStore.UploadLink> resolveUploadLink(String token) {
        Instant now = Instant.now(clock);
        return store.findUploadLink(Secrets.sha256Hex(token == null ? "" : token)).filter(l -> l.expiresAt().isAfter(now));
    }

    /** Error that must be reported to the client by redirecting to its redirect URI. */
    public static class RedirectableError extends RuntimeException {
        private final String redirectUri;
        private final String state;
        private final String error;

        public RedirectableError(String redirectUri, String state, String error, String description) {
            super(description);
            this.redirectUri = redirectUri;
            this.state = state;
            this.error = error;
        }

        public String redirectUri() {
            return redirectUri;
        }

        public String state() {
            return state;
        }

        public String error() {
            return error;
        }
    }
}
