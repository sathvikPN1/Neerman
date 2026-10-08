package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.category.Category;
import com.nirmaan.reimburse.category.CategoryRepository;
import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.ConcurrentUpdateException;
import com.nirmaan.reimburse.common.exception.IllegalTransitionException;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.document.ClaimDocumentRepository;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.team.BudgetService;
import com.nirmaan.reimburse.team.BudgetSnapshot;
import com.nirmaan.reimburse.team.TeamRepository;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimStateMachineTest {

    static final long TEAM = 10L;
    static final long OTHER_TEAM = 20L;
    static final long COO_ID = 1L;
    static final long STAFF_ID = 2L;
    static final long DELEGATE_ID = 3L;
    static final long FINANCE_ID = 4L;
    static final long MEMBER_ID = 5L;
    static final long OUTSIDER_ID = 6L;
    static final Instant NOW = Instant.parse("2026-10-01T06:00:00Z");

    ClaimRepository claims = mock(ClaimRepository.class);
    ClaimDocumentRepository documents = mock(ClaimDocumentRepository.class);
    CategoryRepository categories = mock(CategoryRepository.class);
    TeamRepository teams = mock(TeamRepository.class);
    BudgetService budgets = mock(BudgetService.class);
    UserRepository users = mock(UserRepository.class);
    AuditService audit = mock(AuditService.class);
    ClaimNotifier notifier = mock(ClaimNotifier.class);
    ClaimStateMachine machine;

    Category allowed = category("Parts", true, false, false);

    @BeforeEach
    void setUp() {
        machine = new ClaimStateMachine(claims, documents, categories, teams, budgets, users, audit, notifier,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(claims.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(categories.findById(100L)).thenReturn(Optional.of(allowed));
        when(documents.existsByClaimIdAndType(any(), any())).thenReturn(true);
        when(budgets.snapshot(TEAM)).thenReturn(budget("200000", "0", "0"));
        User coo = new User("COO", "coo@x", Role.COO);
        ReflectionTestUtils.setField(coo, "id", COO_ID);
        when(users.findActiveByRole(Role.COO)).thenReturn(List.of(coo));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ every transition

    static Stream<Arguments> allActionStatusPairs() {
        return Arrays.stream(ClaimAction.values())
                .flatMap(a -> Arrays.stream(ClaimStatus.values()).map(s -> Arguments.of(a, s)));
    }

    @ParameterizedTest(name = "{0} from {1}")
    @MethodSource("allActionStatusPairs")
    void everyLegalTransitionSucceedsAndEveryIllegalOneIsRejected(ClaimAction action, ClaimStatus from) {
        Claim claim = claim(from);
        if (from == ClaimStatus.VERIFIED || from == ClaimStatus.APPROVED) {
            claim.markVerified(STAFF_ID, NOW);
        }
        loginAs(capableActorFor(action));
        TransitionRequest request = new TransitionRequest(action.reasonRequired() ? "reason" : null, null, null, java.util.Map.of());

        if (action.isAllowedFrom(from)) {
            Claim result = machine.transition(claim.getId(), action, request);
            assertThat(result.getStatus()).isEqualTo(action.to());
            verify(audit).record(eq(AuditEntity.CLAIM), eq(claim.getId()), anyString(), any(), any(), anyString(),
                    eq(from.name()), eq(action.to().name()), anyMap());
            verify(notifier).onTransition(claim, action, from, request.comment());
        } else {
            assertThatThrownBy(() -> machine.transition(claim.getId(), action, request))
                    .isInstanceOf(IllegalTransitionException.class);
            assertThat(claim.getStatus()).isEqualTo(from);
            verify(claims, never()).saveAndFlush(any());
        }
    }

    @Test
    void legalTransitionTableMatchesTheSpecification() {
        assertThat(ClaimAction.SUBMIT.from()).containsExactlyInAnyOrder(ClaimStatus.DRAFT, ClaimStatus.RETURNED);
        assertThat(ClaimAction.VERIFY.from()).containsExactly(ClaimStatus.SUBMITTED);
        assertThat(ClaimAction.RETURN.from()).containsExactlyInAnyOrder(ClaimStatus.SUBMITTED, ClaimStatus.VERIFIED);
        assertThat(ClaimAction.REJECT.from()).containsExactlyInAnyOrder(ClaimStatus.SUBMITTED, ClaimStatus.VERIFIED);
        assertThat(ClaimAction.APPROVE.from()).containsExactlyInAnyOrder(ClaimStatus.VERIFIED, ClaimStatus.SUBMITTED);
        assertThat(ClaimAction.MARK_PAID.from()).containsExactly(ClaimStatus.APPROVED);
        // PAID is terminal: nothing leaves it
        assertThat(Arrays.stream(ClaimAction.values()).noneMatch(a -> a.isAllowedFrom(ClaimStatus.PAID))).isTrue();
        // REJECTED is only left via the COO override
        assertThat(Arrays.stream(ClaimAction.values()).filter(a -> a.isAllowedFrom(ClaimStatus.REJECTED)))
                .containsExactly(ClaimAction.REOPEN);
    }

    // ------------------------------------------------------------------ who may act

    @Nested
    class Authorisation {

        @Test
        void verifyRequiresClaimVerify() {
            Claim c = claim(ClaimStatus.SUBMITTED);
            loginAs(staff(STAFF_ID, Permission.CLAIM_RETURN_REJECT));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.VERIFY, plain()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void returnFromSubmittedRequiresClaimReturnReject() {
            Claim c = claim(ClaimStatus.SUBMITTED);
            loginAs(staff(STAFF_ID, Permission.CLAIM_VERIFY));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.RETURN, reason("fix")))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void approverMayReturnOrRejectAVerifiedClaim() {
            Claim c = verified(STAFF_ID);
            loginAs(staff(DELEGATE_ID, Permission.CLAIM_APPROVE));
            machine.transition(c.getId(), ClaimAction.REJECT, reason("not allowed"));
            assertThat(c.getStatus()).isEqualTo(ClaimStatus.REJECTED);
            assertThat(c.getDecisionReason()).isEqualTo("not allowed");
        }

        @Test
        void approveRequiresClaimApprove() {
            Claim c = verified(STAFF_ID);
            loginAs(staff(DELEGATE_ID, Permission.CLAIM_VERIFY, Permission.CLAIM_RETURN_REJECT));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.APPROVE, plain()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void verifierCannotApproveTheirOwnVerifiedClaim() {
            Claim c = verified(STAFF_ID);
            loginAs(staff(STAFF_ID, Permission.CLAIM_VERIFY, Permission.CLAIM_APPROVE));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.APPROVE, plain()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("someone else");
        }

        @Test
        void cooMayApproveAClaimTheyVerified() {
            Claim c = verified(COO_ID);
            loginAs(coo());
            machine.transition(c.getId(), ClaimAction.APPROVE, plain());
            assertThat(c.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        }

        @Test
        void onlyTheCooCanVerifyAndApproveInOneStep() {
            Claim c = claim(ClaimStatus.SUBMITTED);
            loginAs(staff(DELEGATE_ID, Permission.CLAIM_VERIFY, Permission.CLAIM_APPROVE));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.APPROVE, plain()))
                    .isInstanceOf(AccessDeniedException.class);

            loginAs(coo());
            machine.transition(c.getId(), ClaimAction.APPROVE, plain());
            assertThat(c.getStatus()).isEqualTo(ClaimStatus.APPROVED);
            assertThat(c.getVerifiedBy()).isEqualTo(COO_ID);
            verify(audit).record(eq(AuditEntity.CLAIM), eq(c.getId()), eq("VERIFIED_AND_APPROVED"), eq(COO_ID), isNull(),
                    eq("COO"), eq("SUBMITTED"), eq("APPROVED"), anyMap());
        }

        @Test
        void delegatedApprovalIsLoggedOnBehalfOfTheCoo() {
            Claim c = verified(STAFF_ID);
            loginAs(staff(DELEGATE_ID, Permission.CLAIM_APPROVE));
            machine.transition(c.getId(), ClaimAction.APPROVE, plain());
            verify(audit).record(eq(AuditEntity.CLAIM), eq(c.getId()), eq("APPROVED"), eq(DELEGATE_ID), eq(COO_ID),
                    eq("CLAIM_APPROVE"), eq("VERIFIED"), eq("APPROVED"), anyMap());
        }

        @Test
        void markPaidRequiresPaymentRecord() {
            Claim c = approved();
            loginAs(staff(STAFF_ID, Permission.CLAIM_VERIFY, Permission.CLAIM_APPROVE));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.MARK_PAID, plain()))
                    .isInstanceOf(AccessDeniedException.class);
            loginAs(staff(FINANCE_ID, Permission.PAYMENT_RECORD));
            machine.transition(c.getId(), ClaimAction.MARK_PAID, plain());
            assertThat(c.getStatus()).isEqualTo(ClaimStatus.PAID);
            assertThat(c.getPaidAt()).isEqualTo(NOW);
        }

        @Test
        void onlyTheCooCanReopen() {
            Claim c = claim(ClaimStatus.REJECTED);
            loginAs(staff(STAFF_ID, EnumSet.allOf(Permission.class).toArray(Permission[]::new)));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.REOPEN, reason("mistake")))
                    .isInstanceOf(AccessDeniedException.class);
            loginAs(coo());
            machine.transition(c.getId(), ClaimAction.REOPEN, reason("mistake"));
            assertThat(c.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        }

        @Test
        void onlyMembersOfTheClaimsTeamCanSubmit() {
            Claim c = claim(ClaimStatus.DRAFT);
            loginAs(member(OUTSIDER_ID, OTHER_TEAM));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.SUBMIT, plain()))
                    .isInstanceOf(AccessDeniedException.class);
            loginAs(coo());
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.SUBMIT, plain()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void canPerformReflectsStateAndPermissions() {
            Claim c = verified(STAFF_ID);
            AppUserPrincipal verifier = staff(STAFF_ID, Permission.CLAIM_VERIFY, Permission.CLAIM_APPROVE);
            assertThat(machine.canPerform(ClaimAction.APPROVE, c, verifier)).isFalse();
            assertThat(machine.canPerform(ClaimAction.APPROVE, c, coo())).isTrue();
            assertThat(machine.canPerform(ClaimAction.VERIFY, c, coo())).isFalse();
        }
    }

    // ------------------------------------------------------------------ business rules

    @Nested
    class Rules {

        @Test
        void returnAndRejectAndReopenRequireAReason() {
            loginAs(coo());
            for (ClaimAction a : List.of(ClaimAction.RETURN, ClaimAction.REJECT)) {
                Claim c = claim(ClaimStatus.SUBMITTED);
                assertThatThrownBy(() -> machine.transition(c.getId(), a, new TransitionRequest("  ", null, null, java.util.Map.of())))
                        .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reason");
            }
            Claim rejected = claim(ClaimStatus.REJECTED);
            assertThatThrownBy(() -> machine.transition(rejected.getId(), ClaimAction.REOPEN, plain()))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void partialApprovalNeedsAReasonAndSetsTheApprovedAmount() {
            Claim c = verified(STAFF_ID);
            loginAs(coo());
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.APPROVE,
                    TransitionRequest.approve(new BigDecimal("4000"), null, null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("partial");

            machine.transition(c.getId(), ClaimAction.APPROVE, TransitionRequest.approve(new BigDecimal("4000"), "One unit only", null));
            assertThat(c.getApprovedAmount()).isEqualByComparingTo("4000.00");
            assertThat(c.getDecisionReason()).isEqualTo("One unit only");
            verify(audit).record(eq(AuditEntity.CLAIM), eq(c.getId()), eq("PARTIALLY_APPROVED"), eq(COO_ID), isNull(),
                    anyString(), eq("VERIFIED"), eq("APPROVED"), anyMap());
        }

        @Test
        void fullApprovalUsesTheClaimedAmount() {
            Claim c = verified(STAFF_ID);
            loginAs(coo());
            machine.transition(c.getId(), ClaimAction.APPROVE, plain());
            assertThat(c.getApprovedAmount()).isEqualByComparingTo(c.getClaimedAmount());
            assertThat(c.getApprovedBy()).isEqualTo(COO_ID);
        }

        @Test
        void approvedAmountMustBePositiveAndNotAboveClaimed() {
            Claim c = verified(STAFF_ID);
            loginAs(coo());
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.APPROVE,
                    TransitionRequest.approve(new BigDecimal("6000"), "x", null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("more than the claimed");
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.APPROVE,
                    TransitionRequest.approve(BigDecimal.ZERO, "x", null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("more than zero");
        }

        @Test
        void approvalCannotExceedTheRemainingBudgetButPartialUpToItIsAllowed() {
            when(budgets.snapshot(TEAM)).thenReturn(budget("200000", "197000", "0")); // ₹3,000 left
            Claim c = verified(STAFF_ID);
            loginAs(coo());
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.APPROVE, plain()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("remaining budget of ₹3,000.00")
                    .hasMessageContaining("Partially approve up to ₹3,000.00");
            machine.transition(c.getId(), ClaimAction.APPROVE, TransitionRequest.approve(new BigDecimal("3000"), "Budget cap", null));
            assertThat(c.getApprovedAmount()).isEqualByComparingTo("3000");
            verify(teams, org.mockito.Mockito.times(2)).lockById(TEAM); // locked on every approval attempt
        }

        @Test
        void staleVersionIsRejected() {
            Claim c = verified(STAFF_ID);
            loginAs(coo());
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.APPROVE, TransitionRequest.approve(null, null, 99L)))
                    .isInstanceOf(ConcurrentUpdateException.class);
            assertThat(c.getStatus()).isEqualTo(ClaimStatus.VERIFIED);
        }

        @Test
        void optimisticLockFailureOnFlushBecomesConcurrentUpdate() {
            Claim c = verified(STAFF_ID);
            loginAs(coo());
            when(claims.saveAndFlush(any())).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Claim.class, c.getId()));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.APPROVE, plain()))
                    .isInstanceOf(ConcurrentUpdateException.class);
        }

        @Test
        void submitNeedsInvoiceAndPaymentProof() {
            Claim c = claim(ClaimStatus.DRAFT);
            loginAs(member(MEMBER_ID, TEAM));
            when(documents.existsByClaimIdAndType(c.getId(), DocumentType.INVOICE)).thenReturn(false);
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.SUBMIT, plain()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("invoice");
            when(documents.existsByClaimIdAndType(c.getId(), DocumentType.INVOICE)).thenReturn(true);
            when(documents.existsByClaimIdAndType(c.getId(), DocumentType.PAYMENT_PROOF)).thenReturn(false);
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.SUBMIT, plain()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("payment proof");
        }

        @Test
        void submitIsBlockedForDisallowedCategories() {
            when(categories.findById(100L)).thenReturn(Optional.of(category("Accommodation", false, false, false)));
            Claim c = claim(ClaimStatus.DRAFT);
            loginAs(member(MEMBER_ID, TEAM));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.SUBMIT, plain()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not reimbursable");
        }

        @Test
        void submitIsBlockedWithoutPreApprovalWhenRequired() {
            when(categories.findById(100L)).thenReturn(Optional.of(category("Travel", true, true, false)));
            Claim c = claim(ClaimStatus.DRAFT);
            loginAs(member(MEMBER_ID, TEAM));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.SUBMIT, plain()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("pre-approval");
        }

        @Test
        void otherCategoryNeedsADetailedJustification() {
            when(categories.findById(100L)).thenReturn(Optional.of(category("Other", true, false, true)));
            Claim c = claim(ClaimStatus.DRAFT);
            loginAs(member(MEMBER_ID, TEAM));
            assertThatThrownBy(() -> machine.transition(c.getId(), ClaimAction.SUBMIT, plain()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("detailed justification");
        }

        @Test
        void resubmittingAReturnedClaimClearsThePreviousDecision() {
            Claim c = claim(ClaimStatus.SUBMITTED);
            loginAs(coo());
            machine.transition(c.getId(), ClaimAction.RETURN, reason("Wrong invoice"));
            assertThat(c.getDecisionReason()).isEqualTo("Wrong invoice");
            loginAs(member(MEMBER_ID, TEAM));
            machine.transition(c.getId(), ClaimAction.SUBMIT, plain());
            assertThat(c.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(c.getDecisionReason()).isNull();
            verify(audit).record(eq(AuditEntity.CLAIM), eq(c.getId()), eq("RESUBMITTED"), eq(MEMBER_ID), isNull(),
                    eq("TEAM_MEMBER"), eq("RETURNED"), eq("SUBMITTED"), anyMap());
        }

        @Test
        void reopenClearsApproval() {
            Claim c = approved();
            loginAs(coo());
            machine.transition(c.getId(), ClaimAction.REOPEN, reason("Approved by mistake"));
            assertThat(c.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(c.getApprovedAmount()).isNull();
            assertThat(c.getVerifiedBy()).isNull();
        }
    }

    // ------------------------------------------------------------------ helpers

    private long nextId = 1000;

    Claim claim(ClaimStatus status) {
        Claim c = new Claim("NRM-2026-" + nextId, TEAM, MEMBER_ID, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(c, "id", nextId++);
        c.updateDetails(100L, new BigDecimal("5000.00"), LocalDate.of(2026, 9, 20), "Robu", null, "INV-1",
                LocalDate.of(2026, 9, 20), "Sensors for the prototype", NOW);
        c.moveTo(status, NOW.minusSeconds(60));
        when(claims.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    Claim verified(long verifierId) {
        Claim c = claim(ClaimStatus.VERIFIED);
        c.markVerified(verifierId, NOW);
        return c;
    }

    Claim approved() {
        Claim c = verified(STAFF_ID);
        c.markApproved(COO_ID, c.getClaimedAmount(), null, NOW);
        c.moveTo(ClaimStatus.APPROVED, NOW);
        return c;
    }

    static Category category(String name, boolean allowed, boolean preApproval, boolean justification) {
        Category c = new Category(name, allowed, preApproval, name + " rule");
        c.update(name, allowed, preApproval, false, justification, name + " rule", 0, true);
        ReflectionTestUtils.setField(c, "id", 100L);
        return c;
    }

    static BudgetSnapshot budget(String total, String approved, String paid) {
        return new BudgetSnapshot(new BigDecimal(total), new BigDecimal(approved), new BigDecimal(paid), BigDecimal.ZERO);
    }

    static TransitionRequest plain() {
        return TransitionRequest.of(null, null);
    }

    static TransitionRequest reason(String r) {
        return TransitionRequest.of(r, null);
    }

    /** An actor who is allowed to perform the action (so only the state decides). */
    static AppUserPrincipal capableActorFor(ClaimAction action) {
        return switch (action) {
            case SUBMIT -> member(MEMBER_ID, TEAM);
            case MARK_PAID -> staff(FINANCE_ID, Permission.PAYMENT_RECORD);
            default -> coo();
        };
    }

    static AppUserPrincipal coo() {
        return new AppUserPrincipal(COO_ID, "coo@x", "COO", "h", true, EnumSet.of(Role.COO),
                EnumSet.allOf(Permission.class), null);
    }

    static AppUserPrincipal staff(long id, Permission... perms) {
        Set<Permission> set = perms.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(Arrays.asList(perms));
        return new AppUserPrincipal(id, "s" + id + "@x", "Staff " + id, "h", true, EnumSet.of(Role.NIRMAAN_STAFF), set, null);
    }

    static AppUserPrincipal member(long id, long teamId) {
        return new AppUserPrincipal(id, "m" + id + "@x", "Member " + id, "h", true, EnumSet.of(Role.TEAM_MEMBER),
                EnumSet.noneOf(Permission.class), teamId);
    }

    static void loginAs(AppUserPrincipal p) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
    }
}
