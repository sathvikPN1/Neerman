package com.nirmaan.reimburse.it;

import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.claim.CommentService;
import com.nirmaan.reimburse.document.ClaimDocumentRepository;
import com.nirmaan.reimburse.document.DocumentService;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.team.BudgetService;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.team.TeamService;
import com.nirmaan.reimburse.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A team member must never read or change another team's data — enforced in services, not just the UI. */
class AccessControlIT extends IntegrationTest {

    @Autowired ClaimService claims;
    @Autowired DocumentService documents;
    @Autowired ClaimDocumentRepository documentRepository;
    @Autowired CommentService comments;
    @Autowired BudgetService budgets;
    @Autowired TeamService teams;

    Team teamA;
    Team teamB;
    User alice;
    User bob;
    Long bobsClaim;
    Long bobsDoc;

    @BeforeEach
    void data() {
        teamA = fx.team();
        teamB = fx.team();
        alice = fx.member(teamA);
        bob = fx.member(teamB);
        bobsClaim = fx.submitted(bob, "7000");
        bobsDoc = documentRepository.findByClaimIdOrderByUploadedAtAsc(bobsClaim).getFirst().getId();
    }

    @Test
    void serviceLayerRefusesCrossTeamReads() {
        fx.asRun(alice, () -> {
            assertThatThrownBy(() -> claims.detail(bobsClaim)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> claims.teamClaims(teamB.getId())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> documents.open(bobsDoc)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> documents.forClaim(bobsClaim)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> comments.forClaim(bobsClaim)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> budgets.snapshotForCurrentUser(teamB.getId())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> teams.get(teamB.getId())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> teams.list()).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void serviceLayerRefusesCrossTeamWrites() {
        Long bobsDraft = fx.draftWithDocuments(bob, "100");
        fx.asRun(alice, () -> {
            assertThatThrownBy(() -> claims.updateDraft(bobsDraft, fx.claimForm("1"))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> claims.deleteDraft(bobsDraft)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> documents.upload(bobsDraft, DocumentType.OTHER, "x.pdf", Fixtures.PDF))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> comments.add(bobsClaim, "hi")).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void httpLayerRefusesCrossTeamAccess() throws Exception {
        var a = user(fx.principal(alice));
        mvc.perform(get("/claims/{id}", bobsClaim).with(a)).andExpect(status().isForbidden());
        mvc.perform(get("/claims/{id}/edit", bobsClaim).with(a)).andExpect(status().isForbidden());
        mvc.perform(get("/documents/{id}", bobsDoc).with(a)).andExpect(status().isForbidden());
        mvc.perform(post("/claims/{id}/comments", bobsClaim).param("body", "x").with(a).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/claims/{id}/documents", bobsClaim).file(new MockMultipartFile("file", "a.pdf", "application/pdf", Fixtures.PDF))
                .param("type", "OTHER").with(a).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/claims/{id}/priority-request", bobsClaim).param("reason", "x").with(a).with(csrf())).andExpect(status().isForbidden());
        // ...while their own team's data is fine
        Long own = fx.submitted(alice, "300");
        mvc.perform(get("/claims/{id}", own).with(a)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/staff/queue", "/coo/approvals", "/finance/payments", "/teams", "/teams/new", "/cohorts",
            "/admin/staff", "/audit"})
    void teamMembersCannotOpenBackOfficeScreens(String path) throws Exception {
        mvc.perform(get(path).with(user(fx.principal(alice)))).andExpect(status().isForbidden());
    }

    @Test
    void teamMembersCannotActOnTheWorkflow() throws Exception {
        Long own = fx.submitted(alice, "300");
        for (String action : new String[]{"verify", "approve", "reject", "return"}) {
            mvc.perform(post("/claims/{id}/actions/{a}", own, action).param("comment", "x").with(user(fx.principal(alice))).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/claims/{id}/priority", own).param("priority", "true").param("reason", "x")
                .with(user(fx.principal(alice))).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void anonymousUsersAreSentToLogin() throws Exception {
        mvc.perform(get("/team")).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/documents/{id}", bobsDoc)).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void csrfIsRequired() throws Exception {
        Long own = fx.draftWithDocuments(alice, "300");
        mvc.perform(post("/claims/{id}/actions/submit", own).with(user(fx.principal(alice)))).andExpect(status().isForbidden());
    }
}
