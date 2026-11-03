package com.nirmaan.reimburse.mcp;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-facing OAuth endpoints used by ChatGPT / Claude to discover how to log in, register themselves,
 * and obtain tokens. Stateless, no CSRF (no cookies are used here).
 */
@RestController
public class OAuthEndpointsController {

    private final OAuthService oauth;

    public OAuthEndpointsController(OAuthService oauth) {
        this.oauth = oauth;
    }

    /** RFC 9728: tells MCP clients which authorization server protects /mcp. */
    @GetMapping(value = {"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> protectedResource() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resource", oauth.resource());
        m.put("authorization_servers", List.of(oauth.issuer()));
        m.put("scopes_supported", List.of(OAuthService.SCOPE));
        m.put("bearer_methods_supported", List.of("header"));
        m.put("resource_name", "Nirmaan Reimbursements");
        return m;
    }

    /** RFC 8414 authorization server metadata (also served at the OpenID path, which some clients try first). */
    @GetMapping(value = {"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> authorizationServer() {
        String issuer = oauth.issuer();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issuer", issuer);
        m.put("authorization_endpoint", issuer + "/oauth/authorize");
        m.put("token_endpoint", issuer + "/oauth/token");
        m.put("registration_endpoint", issuer + "/oauth/register");
        m.put("revocation_endpoint", issuer + "/oauth/revoke");
        m.put("response_types_supported", List.of("code"));
        m.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        m.put("code_challenge_methods_supported", List.of("S256"));
        m.put("token_endpoint_auth_methods_supported", List.of("none"));
        m.put("scopes_supported", List.of(OAuthService.SCOPE));
        return m;
    }

    /** RFC 7591 dynamic client registration for public clients. */
    @PostMapping(value = "/oauth/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        Object uris = body.get("redirect_uris");
        List<String> redirectUris = uris instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        Object name = body.get("client_name");
        OAuthStore.Client client = oauth.register(name == null ? null : name.toString(), redirectUris);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("client_id", client.clientId());
        m.put("client_id_issued_at", System.currentTimeMillis() / 1000);
        m.put("client_name", client.clientName());
        m.put("redirect_uris", client.redirectUris());
        m.put("grant_types", List.of("authorization_code", "refresh_token"));
        m.put("response_types", List.of("code"));
        m.put("token_endpoint_auth_method", "none");
        m.put("scope", OAuthService.SCOPE);
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(m);
    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(@RequestParam("grant_type") String grantType,
                                                     @RequestParam(value = "client_id", required = false) String clientId,
                                                     @RequestParam(value = "code", required = false) String code,
                                                     @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                                     @RequestParam(value = "code_verifier", required = false) String codeVerifier,
                                                     @RequestParam(value = "refresh_token", required = false) String refreshToken) {
        OAuthService.TokenResponse t = switch (grantType) {
            case "authorization_code" -> oauth.exchangeCode(clientId, code, redirectUri, codeVerifier);
            case "refresh_token" -> oauth.refresh(clientId, refreshToken);
            default -> throw new OAuthException("unsupported_grant_type", "Unsupported grant_type " + grantType, 400);
        };
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("access_token", t.accessToken());
        m.put("token_type", "Bearer");
        m.put("expires_in", t.expiresIn());
        m.put("refresh_token", t.refreshToken());
        m.put("scope", t.scope());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(m);
    }

    @PostMapping(value = "/oauth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> revoke(@RequestParam(value = "token", required = false) String token) {
        oauth.revokeToken(token);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<Map<String, String>> oauthError(OAuthException e) {
        return ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore())
                .body(Map.of("error", e.error(), "error_description", e.getMessage()));
    }
}
