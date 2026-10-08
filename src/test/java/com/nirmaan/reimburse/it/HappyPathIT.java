package com.nirmaan.reimburse.it;

import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditEntryView;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.document.ClaimDocumentRepository;
import com.nirmaan.reimburse.notification.EmailSender;
import com.nirmaan.reimburse.notification.LoggingEmailSender;
import com.nirmaan.reimburse.notification.NotificationService;
import com.nirmaan.reimburse.payment.PaymentRepository;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Submit → verify → approve → pay, entirely through the HTTP layer, as four different users. */
class HappyPathIT extends IntegrationTest {

    @Autowired ClaimDocumentRepository documents;
    @Autowired PaymentRepository payments;
    @Autowired AuditService audit;
    @Autowired NotificationService notifications;
    @Autowired EmailSender emailSender;

    @Test
    void submitVerifyApprovePay() throws Exception {
        Team team = fx.team();
        User student = fx.member(team);
        User staff = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY, Permission.CLAIM_RETURN_REJECT);
        User finance = fx.staff(Role.FINANCE, Permission.PAYMENT_RECORD);
        User coo = fx.coo();

        // 1. Team member creates a draft
        MvcResult created = mvc.perform(post("/claims").with(user(fx.principal(student))).with(csrf())
                        .param("categoryId", fx.categoryId("Consumables & prototype parts").toString())
                        .param("claimedAmount", "12500.50")
                        .param("expenseDate", java.time.LocalDate.now().minusDays(1).toString())
                        .param("vendorName", "Robu.in")
                        .param("invoiceNumber", "RB-2291")
                        .param("justification", "Motor drivers for the prototype drivetrain"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String redirect = created.getResponse().getRedirectedUrl();
        assertThat(redirect).matches("/claims/\\d+/edit");
        Long id = Long.parseLong(redirect.replaceAll("\\D+", " ").trim().split(" ")[0]);
        assertThat(fx.claim(id).getPublicCode()).matches("NRM-\\d{4}-\\d{5}");

        // 2. A file that is not really a PDF is rejected by content, whatever its name
        mvc.perform(multipart("/claims/{id}/documents", id)
                        .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", "MZ-not-a-pdf".getBytes()))
                        .param("type", "INVOICE").with(user(fx.principal(student))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Only PDF, JPG, PNG or HEIC files are accepted."));
        assertThat(documents.findByClaimIdOrderByUploadedAtAsc(id)).isEmpty();

        // Submitting without documents is refused
        mvc.perform(post("/claims/{id}/actions/submit", id).with(user(fx.principal(student))).with(csrf()))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("Upload the invoice")));

        // 3. Upload invoice and payment proof
        for (String type : new String[]{"INVOICE", "PAYMENT_PROOF"}) {
            mvc.perform(multipart("/claims/{id}/documents", id)
                            .file(new MockMultipartFile("file", type.toLowerCase() + ".pdf", "application/pdf",
                                    (new String(Fixtures.PDF) + type).getBytes()))
                            .param("type", type).with(user(fx.principal(student))).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("success"));
        }
        assertThat(documents.findByClaimIdOrderByUploadedAtAsc(id)).hasSize(2)
                .allSatisfy(d -> assertThat(d.getSha256()).hasSize(64));

        // 4. Submit
        mvc.perform(post("/claims/{id}/actions/submit", id).with(user(fx.principal(student))).with(csrf())
                        .param("version", String.valueOf(fx.claim(id).getVersion())))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("Location", "/team"));
        assertThat(fx.claim(id).getStatus()).isEqualTo(ClaimStatus.SUBMITTED);

        // 5. Staff sees it in the queue and verifies
        String code = fx.claim(id).getPublicCode();
        mvc.perform(get("/staff/queue").with(user(fx.principal(staff))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(code)));
        mvc.perform(post("/claims/{id}/actions/verify", id).with(user(fx.principal(staff))).with(csrf())
                        .param("version", String.valueOf(fx.claim(id).getVersion())))
                .andExpect(flash().attribute("success", "Claim verified and forwarded for approval."));
        assertThat(fx.claim(id).getStatus()).isEqualTo(ClaimStatus.VERIFIED);

        // 6. COO approves from the mobile approval screen (HTMX one-tap)
        mvc.perform(get("/coo/approvals").with(user(fx.principal(coo))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(code)));
        mvc.perform(post("/coo/approvals/{id}/approve", id).with(user(fx.principal(coo))).with(csrf())
                        .header("HX-Request", "true")
                        .param("version", String.valueOf(fx.claim(id).getVersion())).param("code", code))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Approved")));
        assertThat(fx.claim(id).getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(fx.claim(id).getApprovedAmount()).isEqualByComparingTo("12500.50");

        // 7. Finance records the payment (UTR required)
        mvc.perform(post("/finance/payments/{id}", id).with(user(fx.principal(finance))).with(csrf())
                        .param("paidOn", java.time.LocalDate.now().toString()).param("mode", "NEFT").param("utr", "")
                        .param("version", String.valueOf(fx.claim(id).getVersion())))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/finance/payments/{id}", id).with(user(fx.principal(finance))).with(csrf())
                        .param("paidOn", java.time.LocalDate.now().toString()).param("mode", "NEFT").param("utr", "utr998877")
                        .param("version", String.valueOf(fx.claim(id).getVersion())))
                .andExpect(flash().attribute("success", "Payment recorded and the team has been notified."));
        assertThat(fx.claim(id).getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(payments.findByClaimId(id)).hasValueSatisfying(p -> {
            assertThat(p.getUtr()).isEqualTo("UTR998877");
            assertThat(p.getAmount()).isEqualByComparingTo("12500.50");
        });

        // Every step is in the audit log with the right actor and permission
        var trail = fx.as(coo, () -> audit.forEntity(AuditEntity.CLAIM, id));
        assertThat(trail).extracting(AuditEntryView::action).containsSubsequence(
                "DRAFT_CREATED", "DOCUMENT_UPLOADED", "DOCUMENT_UPLOADED", "SUBMITTED", "VERIFIED", "APPROVED", "PAID");
        assertThat(trail).filteredOn(e -> e.action().equals("VERIFIED")).singleElement()
                .satisfies(e -> {
                    assertThat(e.actorName()).isEqualTo(staff.getName());
                    assertThat(e.permissionUsed()).isEqualTo("CLAIM_VERIFY");
                    assertThat(e.fromState()).isEqualTo("SUBMITTED");
                });

        // The team was notified in-app and by email at each step
        assertThat(notifications.recent(student.getId(), 20)).extracting(n -> n.message())
                .anyMatch(m -> m.contains("has been paid"))
                .anyMatch(m -> m.contains("approved"))
                .anyMatch(m -> m.contains("verified"));
        assertThat(((LoggingEmailSender) emailSender).sent())
                .anyMatch(e -> e.to().equals(student.getEmail()) && e.subject().contains("has been paid"));

        // Documents are served only through the authorised controller
        Long docId = documents.findByClaimIdOrderByUploadedAtAsc(id).getFirst().getId();
        mvc.perform(get("/documents/{d}", docId).with(user(fx.principal(student))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // Claim detail renders the full history for the team
        mvc.perform(get("/claims/{id}", id).with(user(fx.principal(student))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("UTR998877")));
    }

    @Test
    void teamDashboardShowsBudgetMeter() throws Exception {
        Team team = fx.team();
        User student = fx.member(team);
        fx.submitted(student, "5000");
        mvc.perform(get("/team").with(user(fx.principal(student))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("₹2,00,000.00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("₹5,000.00")));
    }
}
