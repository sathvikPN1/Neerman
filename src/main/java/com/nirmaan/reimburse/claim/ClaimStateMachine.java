package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.category.Category;
import com.nirmaan.reimburse.category.CategoryRepository;
import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.ConcurrentUpdateException;
import com.nirmaan.reimburse.common.exception.IllegalTransitionException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.common.security.AccessPolicy;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.document.ClaimDocumentRepository;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.team.BudgetService;
import com.nirmaan.reimburse.team.BudgetSnapshot;
import com.nirmaan.reimburse.team.TeamRepository;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * The only place where a claim's status changes. For every transition it checks:
 * the source state, who may act (permissions / team membership / COO), required reasons,
 * business rules (documents, category, budget, four-eyes), and the optimistic-lock version.
 * Every transition is audit-logged and triggers notifications.
 */
@Service
public class ClaimStateMachine {

    static final int OTHER_JUSTIFICATION_MIN = 30;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final CategoryRepository categories;
    private final TeamRepository teams;
    private final BudgetService budgets;
    private final UserRepository users;
    private final AuditService audit;
    private final ClaimNotifier notifier;
    private final Clock clock;

    public ClaimStateMachine(ClaimRepository claims, ClaimDocumentRepository documents, CategoryRepository categories,
                             TeamRepository teams, BudgetService budgets, UserRepository users, AuditService audit,
                             ClaimNotifier notifier, Clock clock) {
        this.claims = claims;
        this.documents = documents;
        this.categories = categories;
        this.teams = teams;
        this.budgets = budgets;
        this.users = users;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional
    public Claim transition(Long claimId, ClaimAction action, TransitionRequest request) {
        AppUserPrincipal actor = CurrentUser.require();
        Claim claim = claims.findById(claimId).orElseThrow(() -> NotFoundException.of("Claim", claimId));
        if (request.expectedVersion() != null && request.expectedVersion() != claim.getVersion()) {
            throw new ConcurrentUpdateException();
        }
        ClaimStatus from = claim.getStatus();
        if (!action.isAllowedFrom(from)) {
            throw new IllegalTransitionException("Cannot " + verb(action) + " claim " + claim.getPublicCode()
                    + " because it is " + from.label().toLowerCase() + ".");
        }
        String permissionUsed = authorize(action, claim, actor);
        if (action.reasonRequired() && !request.hasComment()) {
            throw new BusinessRuleException("A reason is required to " + verb(action) + " a claim.");
        }

        Instant now = Instant.now(clock);
        String comment = request.hasComment() ? request.comment().trim() : null;
        String auditAction;
        Long onBehalfOf = null;
        Map<String, Object> detail = new HashMap<>(request.extra() == null ? Map.of() : request.extra());

        switch (action) {
            case SUBMIT -> {
                validateSubmission(claim);
                claim.markSubmitted(actor.id(), now);
                auditAction = from == ClaimStatus.RETURNED ? "RESUBMITTED" : "SUBMITTED";
            }
            case VERIFY -> {
                claim.markVerified(actor.id(), now);
                auditAction = "VERIFIED";
            }
            case RETURN -> {
                claim.markDecisionReason(comment);
                auditAction = "RETURNED";
            }
            case REJECT -> {
                claim.markDecisionReason(comment);
                auditAction = "REJECTED";
            }
            case APPROVE -> {
                BigDecimal amount = approvedAmount(claim, request);
                boolean partial = amount.compareTo(claim.getClaimedAmount()) < 0;
                if (partial && comment == null) {
                    throw new BusinessRuleException("A reason is required for a partial approval.");
                }
                checkBudget(claim, amount);
                if (from == ClaimStatus.SUBMITTED) {
                    claim.markVerified(actor.id(), now);
                }
                claim.markApproved(actor.id(), amount, comment, now);
                if (!actor.isCoo()) {
                    onBehalfOf = currentCooId();
                }
                detail.put("approvedAmount", amount.toPlainString());
                detail.put("claimedAmount", claim.getClaimedAmount().toPlainString());
                auditAction = (from == ClaimStatus.SUBMITTED ? "VERIFIED_AND_" : "")
                        + (partial ? "PARTIALLY_APPROVED" : "APPROVED");
            }
            case MARK_PAID -> {
                claim.markPaid(now);
                auditAction = "PAID";
            }
            case REOPEN -> {
                claim.markSubmitted(claim.getSubmittedBy(), now);
                auditAction = "REOPENED";
            }
            default -> throw new IllegalStateException("Unhandled action " + action);
        }

        claim.moveTo(action.to(), now);
        try {
            claims.saveAndFlush(claim);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException();
        }
        if (comment != null) {
            detail.put("comment", comment);
        }
        audit.record(AuditEntity.CLAIM, claim.getId(), auditAction, actor.id(), onBehalfOf, permissionUsed,
                from.name(), action.to().name(), detail);
        notifier.onTransition(claim, action, from, comment);
        return claim;
    }

    /** Whether {@code actor} could perform {@code action} on {@code claim} now (used to show buttons). */
    public boolean canPerform(ClaimAction action, Claim claim, AppUserPrincipal actor) {
        if (!action.isAllowedFrom(claim.getStatus())) {
            return false;
        }
        try {
            authorize(action, claim, actor);
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }

    /**
     * Who may perform {@code action} on {@code claim}. Returns the permission (or role) used, for the audit log.
     * The COO holds every permission, so permission checks pass for them automatically.
     */
    String authorize(ClaimAction action, Claim claim, AppUserPrincipal actor) {
        return switch (action) {
            case SUBMIT -> {
                // Submitting is the team's own declaration, so not even the COO submits on a team's behalf.
                if (AccessPolicy.isMemberOf(actor, claim.getTeamId())) {
                    yield Role.TEAM_MEMBER.name();
                }
                throw new AccessDeniedException("Only members of the claim's team can submit it.");
            }
            case VERIFY -> require(actor, Permission.CLAIM_VERIFY);
            case RETURN, REJECT -> {
                if (claim.getStatus() == ClaimStatus.VERIFIED && !actor.has(Permission.CLAIM_RETURN_REJECT)) {
                    yield require(actor, Permission.CLAIM_APPROVE);
                }
                yield require(actor, Permission.CLAIM_RETURN_REJECT);
            }
            case APPROVE -> {
                if (claim.getStatus() == ClaimStatus.SUBMITTED) {
                    if (!actor.isCoo()) {
                        throw new AccessDeniedException("Only the COO can verify and approve a claim in one step.");
                    }
                    yield Role.COO.name();
                }
                String used = require(actor, Permission.CLAIM_APPROVE);
                if (!actor.isCoo() && actor.id().equals(claim.getVerifiedBy())) {
                    throw new AccessDeniedException("You verified this claim, so someone else must approve it.");
                }
                yield used;
            }
            case MARK_PAID -> require(actor, Permission.PAYMENT_RECORD);
            case REOPEN -> {
                if (!actor.isCoo()) {
                    throw new AccessDeniedException("Only the COO can reopen a claim.");
                }
                yield Role.COO.name();
            }
        };
    }

    private void validateSubmission(Claim claim) {
        Category category = categories.findById(claim.getCategoryId())
                .orElseThrow(() -> new BusinessRuleException("Choose a category."));
        if (!category.isActive()) {
            throw new BusinessRuleException("The category \"" + category.getName() + "\" is no longer available.");
        }
        if (!category.isAllowed()) {
            throw new BusinessRuleException("\"" + category.getName() + "\" expenses are not reimbursable. "
                    + category.getDescription());
        }
        if (category.isRequiresPreApproval() && claim.getPreApprovalId() == null) {
            throw new BusinessRuleException("\"" + category.getName()
                    + "\" claims must be linked to an approved pre-approval.");
        }
        String justification = claim.getJustification() == null ? "" : claim.getJustification().trim();
        if (justification.isEmpty()) {
            throw new BusinessRuleException("Explain how this expense benefits your project.");
        }
        if (category.isJustificationRequired() && justification.length() < OTHER_JUSTIFICATION_MIN) {
            throw new BusinessRuleException("\"" + category.getName() + "\" claims need a detailed justification (at least "
                    + OTHER_JUSTIFICATION_MIN + " characters).");
        }
        if (claim.getExpenseDate() != null && claim.getExpenseDate().isAfter(LocalDate.now(clock.withZone(IST)))) {
            throw new BusinessRuleException("The expense date cannot be in the future.");
        }
        if (!documents.existsByClaimIdAndType(claim.getId(), DocumentType.INVOICE)) {
            throw new BusinessRuleException("Upload the invoice before submitting. Pro-forma invoices are not accepted.");
        }
        if (!documents.existsByClaimIdAndType(claim.getId(), DocumentType.PAYMENT_PROOF)) {
            throw new BusinessRuleException("Upload the payment proof (UPI screenshot, bank statement or card slip).");
        }
    }

    private BigDecimal approvedAmount(Claim claim, TransitionRequest request) {
        BigDecimal amount = request.approvedAmount() == null
                ? claim.getClaimedAmount()
                : Money.normalise(request.approvedAmount());
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("The approved amount must be more than zero.");
        }
        if (amount.compareTo(claim.getClaimedAmount()) > 0) {
            throw new BusinessRuleException("The approved amount cannot be more than the claimed amount ("
                    + Money.formatInr(claim.getClaimedAmount()) + ").");
        }
        return amount;
    }

    private void checkBudget(Claim claim, BigDecimal amount) {
        // Serialise approvals per team so two concurrent approvals cannot overspend the budget.
        teams.lockById(claim.getTeamId());
        BudgetSnapshot budget = budgets.snapshot(claim.getTeamId());
        if (budget.exceedsRemaining(amount)) {
            BigDecimal remaining = budget.remaining().max(Money.ZERO);
            throw new BusinessRuleException("Approving " + Money.formatInr(amount)
                    + " would exceed the team's remaining budget of " + Money.formatInr(remaining) + "."
                    + (remaining.signum() > 0 ? " Partially approve up to " + Money.formatInr(remaining) + " instead." : ""));
        }
    }

    private Long currentCooId() {
        return users.findActiveByRole(Role.COO).stream().map(User::getId).findFirst().orElse(null);
    }

    private static String require(AppUserPrincipal actor, Permission permission) {
        if (!actor.has(permission)) {
            throw new AccessDeniedException("You need the " + permission + " permission to do this.");
        }
        return permission.name();
    }

    static String verb(ClaimAction action) {
        return switch (action) {
            case SUBMIT -> "submit";
            case VERIFY -> "verify";
            case RETURN -> "return";
            case REJECT -> "reject";
            case APPROVE -> "approve";
            case MARK_PAID -> "mark as paid";
            case REOPEN -> "reopen";
        };
    }
}
