package com.nirmaan.reimburse.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecretsTest {

    @Test
    void pkceMatchesTheRfc7636Example() {
        // RFC 7636, Appendix B
        assertThat(Secrets.pkceMatches("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")).isTrue();
        assertThat(Secrets.pkceMatches("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXX",
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")).isFalse();
        assertThat(Secrets.pkceMatches("too-short", "x")).isFalse();
        assertThat(Secrets.pkceMatches(null, "x")).isFalse();
    }

    @Test
    void tokensAreRandomAndHashedConsistently() {
        String a = Secrets.randomToken();
        assertThat(a).hasSize(43).isNotEqualTo(Secrets.randomToken());
        assertThat(Secrets.sha256Hex(a)).hasSize(64).isEqualTo(Secrets.sha256Hex(a));
    }

    @Test
    void redirectUriRules() {
        assertThat(RedirectUris.isAcceptable("https://chatgpt.com/connector_platform_oauth_redirect")).isTrue();
        assertThat(RedirectUris.isAcceptable("https://claude.ai/api/mcp/auth_callback")).isTrue();
        assertThat(RedirectUris.isAcceptable("http://localhost:33418/callback")).isTrue();
        assertThat(RedirectUris.isAcceptable("http://127.0.0.1/cb")).isTrue();
        assertThat(RedirectUris.isAcceptable("http://evil.example.com/cb")).isFalse();
        assertThat(RedirectUris.isAcceptable("https://x.com/cb#frag")).isFalse();
        assertThat(RedirectUris.isAcceptable("javascript:alert(1)")).isFalse();
        assertThat(RedirectUris.isAcceptable("not a uri")).isFalse();
    }

    @Test
    void redirectMatchingIsExactExceptLoopbackPorts() {
        List<String> reg = List.of("https://claude.ai/api/mcp/auth_callback", "http://localhost:5000/callback");
        assertThat(RedirectUris.matches(reg, "https://claude.ai/api/mcp/auth_callback")).isTrue();
        assertThat(RedirectUris.matches(reg, "https://claude.ai/api/mcp/auth_callback/x")).isFalse();
        assertThat(RedirectUris.matches(reg, "https://evil.com/api/mcp/auth_callback")).isFalse();
        assertThat(RedirectUris.matches(reg, "http://localhost:61234/callback")).isTrue();
        assertThat(RedirectUris.matches(reg, "http://localhost:61234/other")).isFalse();
        assertThat(RedirectUris.matches(reg, null)).isFalse();
    }

    @Test
    void redirectParamsAreEncoded() {
        assertThat(OAuthAuthorizeController.withParams("https://a.com/cb", "code", "x y", "state", null, "iss", "https://n.in"))
                .isEqualTo("https://a.com/cb?code=x+y&iss=https%3A%2F%2Fn.in");
        assertThat(OAuthAuthorizeController.withParams("https://a.com/cb?x=1", "code", "c")).isEqualTo("https://a.com/cb?x=1&code=c");
    }
}
