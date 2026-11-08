package com.nirmaan.reimburse.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.mcp.McpTools;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** attach_inbox_file: files dropped in {@code <inbox>/<member email>/} are attached through the normal upload checks. */
class McpInboxIT extends IntegrationTest {

    static final Path INBOX = createInbox();

    static Path createInbox() {
        try {
            return Files.createTempDirectory("nirmaan-test-inbox").toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void inbox(DynamicPropertyRegistry registry) {
        registry.add("app.mcp.inbox-dir", INBOX::toString);
    }

    /** A tiny but valid PNG (signature + IHDR), different each time so it never collides with another claim's file. */
    static byte[] png() {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] rest = ("IHDR-" + Fixtures.unique("png")).getBytes();
        byte[] out = new byte[sig.length + rest.length];
        System.arraycopy(sig, 0, out, 0, sig.length);
        System.arraycopy(rest, 0, out, sig.length, rest.length);
        return out;
    }

    @Autowired McpTools tools;
    @Autowired com.nirmaan.reimburse.claim.ClaimService claimService;
    @Autowired ObjectMapper json;

    User member;
    Path folder;
    Long claimId;
    String code;

    @BeforeEach
    void setUp() throws IOException {
        Team team = fx.team();
        member = fx.member(team);
        folder = INBOX.resolve(member.getEmail().toLowerCase(Locale.ROOT));
        Files.createDirectories(folder);
        claimId = fx.as(member, () -> claimService.createDraft(fx.claimForm("500")));
        code = fx.claim(claimId).getPublicCode();
    }

    private JsonNode attach(User as, String claimCode, String filename) {
        return fx.as(as, () -> tools.call("attach_inbox_file", json.valueToTree(
                Map.of("claim_code", claimCode, "document_type", "INVOICE", "filename", filename))));
    }

    @Test
    void attachesAPngToADraftClaim() throws IOException {
        Files.write(folder.resolve("invoice.png"), png());
        JsonNode r = attach(member, code, "invoice.png");
        assertThat(r.get("isError").asBoolean()).as(r.toString()).isFalse();
        assertThat(r.at("/structuredContent/uploaded").asText()).isEqualTo("invoice.png");
        assertThat(r.at("/structuredContent/claim/missing_documents").toString()).isEqualTo("[\"Payment proof\"]");
    }

    @Test
    void toolIsListedWhenTheInboxIsConfigured() {
        assertThat(tools.definitions()).extracting(McpTools.ToolDef::name).contains("attach_inbox_file");
    }

    @Test
    void base64UploadStillWorks() {
        JsonNode r = fx.as(member, () -> tools.call("upload_document", json.valueToTree(Map.of("claim_code", code,
                "document_type", "INVOICE", "filename", "i.png", "content_base64", Base64.getEncoder().encodeToString(png())))));
        assertThat(r.get("isError").asBoolean()).isFalse();
    }
}
