package com.nirmaan.reimburse.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The AI connector end to end: OAuth 2.1 + PKCE login, MCP tools as the team member, upload links, revocation. */
class McpConnectorIT extends IntegrationTest {

    static final String REDIRECT = "https://claude.ai/api/mcp/auth_callback";
    static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    record Tokens(String access, String refresh, String clientId) {
    }

    @Test
    void discoveryAndUnauthenticatedAccess() throws Exception {
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("resource_metadata=\"http://localhost:8080/.well-known/oauth-protected-resource\"")));
        mvc.perform(post("/mcp").header("Authorization", "Bearer made-up").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
        mvc.perform(get("/.well-known/oauth-protected-resource"))
                .andExpect(jsonPath("$.resource").value("http://localhost:8080/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("http://localhost:8080"));
        mvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
                .andExpect(jsonPath("$.registration_endpoint").value("http://localhost:8080/oauth/register"));
    }

    @Test
    void registrationRejectsUnsafeRedirects() throws Exception {
        mvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_name\":\"Evil\",\"redirect_uris\":[\"http://evil.example.com/cb\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_redirect_uri"));
        mvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON).content("{\"client_name\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teamMemberConnectsAndPreparesADraftClaimButCannotSubmit() throws Exception {
        Team team = fx.team();
        User student = fx.member(team);
        Tokens t = connect(student, "Claude");

        JsonNode init = rpc(t.access(), "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "test", "version", "1")));
        assertThat(init.at("/result/protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(init.at("/result/instructions").asText()).contains("cannot submit");

        JsonNode list = rpc(t.access(), "tools/list", Map.of());
        assertThat(list.at("/result/tools").findValuesAsText("name"))
                .contains("get_my_team", "create_draft_claim", "upload_document", "get_upload_link")
                .noneMatch(n -> n.contains("submit") || n.contains("approve") || n.contains("pay"));

        JsonNode teamInfo = call(t.access(), "get_my_team", Map.of());
        assertThat(teamInfo.at("/structuredContent/team").asText()).isEqualTo(team.getName());
        assertThat(teamInfo.at("/structuredContent/budget/remaining").asText()).isEqualTo("₹2,00,000.00");

        JsonNode bad = call(t.access(), "create_draft_claim", Map.of("category", "Accommodation-xyz", "amount", 10,
                "expense_date", "2026-10-01", "vendor_name", "X", "justification", "y"));
        assertThat(bad.get("isError").asBoolean()).isTrue();
        assertThat(bad.at("/content/0/text").asText()).contains("Unknown category");

        JsonNode created = call(t.access(), "create_draft_claim", Map.of("category", "consumables", "amount", "2,450.50",
                "expense_date", java.time.LocalDate.now().minusDays(1).toString(), "vendor_name", "Robu.in",
                "invoice_number", "RB-77", "justification", "ESP32 boards for the soil sensor prototype"));
        assertThat(created.get("isError").asBoolean()).isFalse();
        String code = created.at("/structuredContent/claim_code").asText();
        assertThat(code).matches("NRM-\\d{4}-\\d{5}");
        assertThat(created.at("/structuredContent/status").asText()).isEqualTo("DRAFT");
        assertThat(created.at("/structuredContent/claimed_amount").asText()).isEqualTo("₹2,450.50");
        assertThat(created.at("/structuredContent/next_step").asText()).contains("press Submit themselves");
        String uploadLink = created.at("/structuredContent/upload_link").asText();
        assertThat(uploadLink).startsWith("http://localhost:8080/upload/");

        // upload the invoice through the tool (base64)
        JsonNode up = call(t.access(), "upload_document", Map.of("claim_code", code, "document_type", "INVOICE",
                "filename", "invoice.pdf", "content_base64", Base64.getEncoder().encodeToString(Fixtures.PDF)));
        assertThat(up.at("/structuredContent/claim/missing_documents").toString()).isEqualTo("[\"Payment proof\"]");

        // a disguised executable is still rejected by content
        JsonNode exe = call(t.access(), "upload_document", Map.of("claim_code", code, "document_type", "OTHER",
                "filename", "x.pdf", "content_base64", Base64.getEncoder().encodeToString("MZ...".getBytes())));
        assertThat(exe.get("isError").asBoolean()).isTrue();

        // the payment proof through the one-time upload link, without being signed in
        String path = uploadLink.replace("http://localhost:8080", "");
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString(code)));
        mvc.perform(multipart(path).file(new MockMultipartFile("file", "upi.pdf", "application/pdf",
                        (new String(Fixtures.PDF) + "proof").getBytes())).param("type", "PAYMENT_PROOF").with(csrf()))
                .andExpect(status().is3xxRedirection());
        JsonNode claim = call(t.access(), "get_claim", Map.of("claim_code", code));
        assertThat(claim.at("/structuredContent/missing_documents").size()).isZero();

        // the AI cannot submit: the claim stays a draft until the student presses Submit
        Long id = jdbc.queryForObject("select id from claims where public_code = ?", Long.class, code);
        assertThat(fx.claim(id).getStatus()).isEqualTo(ClaimStatus.DRAFT);

        JsonNode updated = call(t.access(), "update_draft_claim", Map.of("claim_code", code, "amount", 2500));
        assertThat(updated.at("/structuredContent/claimed_amount").asText()).isEqualTo("₹2,500.00");
        assertThat(call(t.access(), "list_my_claims", Map.of()).at("/structuredContent/count").asInt()).isEqualTo(1);
        assertThat(call(t.access(), "add_claim_message", Map.of("claim_code", code, "message", "Prepared with Claude"))
                .get("isError").asBoolean()).isFalse();

        // actions are audited as the student, marked as coming through the AI connector
        String via = jdbc.queryForObject("select detail->>'via' from audit_log where entity_type='CLAIM' and entity_id=? and action='DRAFT_CREATED'",
                String.class, id);
        assertThat(via).isEqualTo("AI connector: Claude");
        Long actor = jdbc.queryForObject("select actor_id from audit_log where entity_type='CLAIM' and entity_id=? and action='DRAFT_CREATED'",
                Long.class, id);
        assertThat(actor).isEqualTo(student.getId());
    }

    @Test
    void assistantSeesOnlyItsOwnTeam() throws Exception {
        User alice = fx.member(fx.team());
        User bob = fx.member(fx.team());
        Long bobsClaim = fx.submitted(bob, "900");
        String bobsCode = fx.claim(bobsClaim).getPublicCode();
        Tokens t = connect(alice, "ChatGPT");
        for (String tool : new String[]{"get_claim", "get_upload_link", "add_claim_message"}) {
            JsonNode r = call(t.access(), tool, Map.of("claim_code", bobsCode, "message", "hi"));
            assertThat(r.get("isError").asBoolean()).as(tool).isTrue();
            assertThat(r.at("/content/0/text").asText()).as(tool).contains("not found in your team");
        }
    }

    @Test
    void onlyTeamMembersCanConnect() throws Exception {
        User staff = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY);
        String clientId = register("ChatGPT");
        mvc.perform(get("/oauth/authorize").with(user(fx.principal(staff))).params(authorizeParams(clientId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pkceAndCodeReplayAreEnforced() throws Exception {
        User student = fx.member(fx.team());
        String clientId = register("Claude");
        String code = approve(student, clientId);
        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code").param("client_id", clientId).param("code", code)
                        .param("redirect_uri", REDIRECT).param("code_verifier", "x".repeat(43)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        // the code was consumed by the failed attempt; using it again is a replay
        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code").param("client_id", clientId).param("code", code)
                        .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        // a wrong redirect URI is refused at authorize time without redirecting
        mvc.perform(get("/oauth/authorize").with(user(fx.principal(student))).params(authorizeParams(clientId))
                        .param("redirect_uri", "https://evil.example.com/cb"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshTokensRotateAndReuseRevokesEverything() throws Exception {
        User student = fx.member(fx.team());
        Tokens t = connect(student, "ChatGPT");
        Tokens next = refresh(t.clientId(), t.refresh());
        assertThat(next.access()).isNotEqualTo(t.access());
        assertThat(rpc(next.access(), "ping", Map.of()).has("result")).isTrue();
        // reusing the old refresh token is treated as theft: the whole grant is revoked
        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token").param("client_id", t.clientId()).param("refresh_token", t.refresh()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/mcp").header("Authorization", "Bearer " + next.access()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCanRevokeAnAssistantFromTheAiPage() throws Exception {
        User student = fx.member(fx.team());
        Tokens t = connect(student, "ChatGPT");
        mvc.perform(get("/team/ai").with(user(fx.principal(student))))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("ChatGPT")));
        Long grantId = jdbc.queryForObject("select id from mcp_grants where user_id = ?", Long.class, student.getId());
        mvc.perform(post("/team/ai/{id}/revoke", grantId).with(user(fx.principal(student))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/mcp").header("Authorization", "Bearer " + t.access()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")).andExpect(status().isUnauthorized());
        // someone else cannot revoke it
        User other = fx.member(fx.team());
        Tokens t2 = connect(student, "Claude");
        Long grant2 = jdbc.queryForObject("select max(id) from mcp_grants where user_id = ?", Long.class, student.getId());
        mvc.perform(post("/team/ai/{id}/revoke", grant2).with(user(fx.principal(other))).with(csrf())).andExpect(status().isForbidden());
        assertThat(rpc(t2.access(), "ping", Map.of()).has("result")).isTrue();
    }

    @Test
    void loginReturnsToTheConsentScreen() throws Exception {
        User student = fx.member(fx.team());
        String clientId = register("ChatGPT");
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/oauth/authorize").session(session).params(authorizeParams(clientId)))
                .andExpect(status().is3xxRedirection());
        MvcResult login = mvc.perform(post("/login").session(session).with(csrf())
                .param("username", student.getEmail()).param("password", Fixtures.PASSWORD)).andReturn();
        assertThat(login.getResponse().getRedirectedUrl()).contains("/oauth/authorize");
    }

    @Test
    void expiredUploadLinkShowsAFriendlyPage() throws Exception {
        mvc.perform(get("/upload/not-a-real-token")).andExpect(status().isGone())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("expired")));
    }

    // ------------------------------------------------------------------ helpers

    private Tokens connect(User student, String clientName) throws Exception {
        String clientId = register(clientName);
        String code = approve(student, clientId);
        MvcResult r = mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code").param("client_id", clientId).param("code", code)
                        .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("token_type").asText()).isEqualTo("Bearer");
        return new Tokens(body.get("access_token").asText(), body.get("refresh_token").asText(), clientId);
    }

    private Tokens refresh(String clientId, String refreshToken) throws Exception {
        MvcResult r = mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token").param("client_id", clientId).param("refresh_token", refreshToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        return new Tokens(body.get("access_token").asText(), body.get("refresh_token").asText(), clientId);
    }

    private String register(String name) throws Exception {
        MvcResult r = mvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("client_name", name, "redirect_uris", java.util.List.of(REDIRECT)))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("client_id").asText();
    }

    /** Consent screen → Allow → returns the authorization code from the redirect. */
    private String approve(User student, String clientId) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult consent = mvc.perform(get("/oauth/authorize").session(session).with(user(fx.principal(student)))
                        .params(authorizeParams(clientId)))
                .andExpect(status().isOk()).andReturn();
        Matcher m = Pattern.compile("name=\"requestId\" value=\"([^\"]+)\"").matcher(consent.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        MvcResult allowed = mvc.perform(post("/oauth/authorize").session(session).with(user(fx.principal(student))).with(csrf())
                        .param("requestId", m.group(1)).param("decision", "allow"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = allowed.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(REDIRECT + "?code=").contains("state=st4te").contains("iss=");
        Matcher c = Pattern.compile("code=([^&]+)").matcher(location);
        assertThat(c.find()).isTrue();
        return java.net.URLDecoder.decode(c.group(1), StandardCharsets.UTF_8);
    }

    private static org.springframework.util.LinkedMultiValueMap<String, String> authorizeParams(String clientId) {
        var p = new org.springframework.util.LinkedMultiValueMap<String, String>();
        p.add("response_type", "code");
        p.add("client_id", clientId);
        p.add("redirect_uri", REDIRECT);
        p.add("state", "st4te");
        p.add("code_challenge", CHALLENGE);
        p.add("code_challenge_method", "S256");
        p.add("resource", "http://localhost:8080/mcp");
        return p;
    }

    private JsonNode rpc(String token, String method, Map<String, Object> params) throws Exception {
        MvcResult r = mvc.perform(post("/mcp").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 7, "method", method, "params", params))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode call(String token, String tool, Map<String, Object> args) throws Exception {
        return rpc(token, "tools/call", Map.of("name", tool, "arguments", args)).get("result");
    }

}
