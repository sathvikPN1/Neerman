package com.nirmaan.reimburse.it;

import com.nirmaan.reimburse.claim.ClaimAction;
import com.nirmaan.reimburse.claim.ClaimForm;
import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.claim.ClaimStateMachine;
import com.nirmaan.reimburse.claim.ClaimWarning;
import com.nirmaan.reimburse.claim.TransitionRequest;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.document.DocumentService;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The same invoice, or the same receipt file, cannot be reimbursed twice. */
class DuplicateProtectionIT extends IntegrationTest {

    @Autowired ClaimService claims;
    @Autowired DocumentService documents;
    @Autowired ClaimStateMachine stateMachine;
    @Autowired JdbcTemplate jdbc;

    private ClaimForm form(String vendor, String gstin, String invoice, String amount, LocalDate date) {
        return new ClaimForm(fx.categoryId("Consumables & prototype parts"), new BigDecimal(amount), date, vendor, gstin,
                invoice, date, "Parts for the prototype build", null);
    }

    @Test
    void sameInvoiceTwiceInOneTeamIsRefusedWithTheOtherClaimCode() {
        User student = fx.member(fx.team());
        String vendor = Fixtures.unique("Robu");
        Long first = fx.as(student, () -> claims.createDraft(form(vendor, null, "INV-0042", "1000", LocalDate.now())));
        String code = fx.claim(first).getPublicCode();

        // Different spacing, case and punctuation is still the same invoice.
        fx.asRun(student, () -> assertThatThrownBy(() ->
                claims.createDraft(form(vendor.toLowerCase() + ".", null, "inv 0042", "1000", LocalDate.now())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(code).hasMessageContaining("Edit that claim instead"));
    }

    @Test
    void sameInvoiceFromAnotherTeamIsRefusedWithoutRevealingTheirClaim() {
        User alice = fx.member(fx.team());
        User bob = fx.member(fx.team());
        String vendor = Fixtures.unique("Autodesk");
        Long first = fx.as(alice, () -> claims.createDraft(form(vendor, null, "A-77", "500", LocalDate.now())));

        fx.asRun(bob, () -> assertThatThrownBy(() -> claims.createDraft(form(vendor, null, "A-77", "500", LocalDate.now())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("another team")
                .hasMessageNotContaining(fx.claim(first).getPublicCode()));
    }

    @Test
    void sameGstinAndInvoiceMatchesEvenIfTheVendorNameIsTypedDifferently() {
        User student = fx.member(fx.team());
        String inv = Fixtures.unique("G");
        fx.as(student, () -> claims.createDraft(form("Robu Labs Pvt Ltd", "33ABCDE1234F1Z5", inv, "800", LocalDate.now())));

        fx.asRun(student, () -> assertThatThrownBy(() ->
                claims.createDraft(form("Robu.in", "33ABCDE1234F1Z5", inv, "800", LocalDate.now())))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void editingADraftOntoAnExistingInvoiceIsRefusedButSavingItselfIsFine() {
        User student = fx.member(fx.team());
        String vendor = Fixtures.unique("Vendor");
        fx.as(student, () -> claims.createDraft(form(vendor, null, "X-1", "100", LocalDate.now())));
        Long second = fx.as(student, () -> claims.createDraft(form(vendor, null, "X-2", "100", LocalDate.now())));

        // Re-saving the claim's own invoice number is not a duplicate of itself.
        fx.asRun(student, () -> claims.updateDraft(second, form(vendor, null, "X-2", "150", LocalDate.now())));
        fx.asRun(student, () -> assertThatThrownBy(() -> claims.updateDraft(second, form(vendor, null, "X-1", "150", LocalDate.now())))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void rejectedClaimStillBlocksItsInvoiceAndSaysToAskForAReopen() {
        User student = fx.member(fx.team());
        User staff = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY, Permission.CLAIM_RETURN_REJECT);
        Long id = fx.submitted(student, "300");
        fx.asRun(staff, () -> stateMachine.transition(id, ClaimAction.REJECT, TransitionRequest.of("Not a project expense", null)));
        var c = fx.claim(id);

        fx.asRun(student, () -> assertThatThrownBy(() ->
                claims.createDraft(form(c.getVendorName(), null, c.getInvoiceNumber(), "300", LocalDate.now())))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reopen"));
    }

    @Test
    void databaseIndexIsTheBackstopIfTheServiceCheckIsBypassed() {
        User student = fx.member(fx.team());
        String vendor = Fixtures.unique("Race");
        Long first = fx.as(student, () -> claims.createDraft(form(vendor, null, "R-1", "100", LocalDate.now())));
        Long second = fx.as(student, () -> claims.createDraft(form(vendor, null, "R-2", "100", LocalDate.now())));

        assertThatThrownBy(() -> jdbc.update("update claims set invoice_number = 'r 1' where id = ?", second))
                .hasMessageContaining("ux_claims_vendor_invoice");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void claimsWithoutInvoiceNumbersAreAllowedButFlaggedAsPossibleDuplicates() {
        Team team = fx.team();
        User student = fx.member(team);
        String vendor = Fixtures.unique("Chai stall");
        LocalDate day = LocalDate.now().minusDays(1);
        Long first = fx.as(student, () -> claims.createDraft(form(vendor, null, null, "240", day)));
        Long second = fx.as(student, () -> claims.createDraft(form(vendor, null, "", "240", day)));
        Long different = fx.as(student, () -> claims.createDraft(form(vendor, null, null, "260", day)));

        var warnings = fx.as(student, () -> claims.detail(second).warnings());
        assertThat(warnings).filteredOn(w -> w.code().equals("POSSIBLE_DUPLICATE"))
                .singleElement()
                .satisfies(w -> {
                    assertThat(w.message()).contains(fx.claim(first).getPublicCode());
                    assertThat(w.blocking()).isFalse();
                });
        assertThat(fx.as(student, () -> claims.detail(different).warnings()))
                .extracting(ClaimWarning::code).doesNotContain("POSSIBLE_DUPLICATE");
    }

    @Test
    void sameReceiptFileCannotBackTwoClaims() {
        User alice = fx.member(fx.team());
        User bob = fx.member(fx.team());
        byte[] receipt = Fixtures.uniquePdf();
        Long a1 = fx.as(alice, () -> claims.createDraft(fx.claimForm("100")));
        Long a2 = fx.as(alice, () -> claims.createDraft(fx.claimForm("100")));
        Long b1 = fx.as(bob, () -> claims.createDraft(fx.claimForm("100")));

        fx.as(alice, () -> documents.upload(a1, DocumentType.INVOICE, "bill.pdf", receipt));
        // One file may be both the invoice and the payment proof of the same claim...
        fx.as(alice, () -> documents.upload(a1, DocumentType.PAYMENT_PROOF, "bill.pdf", receipt));
        // ...but not twice as the same type, nor on another claim of either team.
        fx.asRun(alice, () -> assertThatThrownBy(() -> documents.upload(a1, DocumentType.INVOICE, "again.pdf", receipt))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already attached to this claim"));
        fx.asRun(alice, () -> assertThatThrownBy(() -> documents.upload(a2, DocumentType.INVOICE, "renamed.pdf", receipt))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(fx.claim(a1).getPublicCode()));
        fx.asRun(bob, () -> assertThatThrownBy(() -> documents.upload(b1, DocumentType.PAYMENT_PROOF, "mine.pdf", receipt))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("another team"));
        // Supporting documents are exempt.
        fx.as(alice, () -> documents.upload(a2, DocumentType.OTHER, "quotation.pdf", receipt));
    }
}
