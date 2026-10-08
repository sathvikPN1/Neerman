package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.category.Category;
import com.nirmaan.reimburse.category.CategoryRepository;
import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.ConcurrentUpdateException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.common.security.AccessPolicy;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.document.DocumentService;
import com.nirmaan.reimburse.payment.PaymentService;
import com.nirmaan.reimburse.team.BudgetService;
import com.nirmaan.reimburse.team.BudgetSnapshot;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.team.TeamRepository;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;



/** Claim drafts, details and lists. Status changes go through {@link ClaimStateMachine}. */
@Service
public class ClaimService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ClaimRepository claims;
    private final ClaimStateMachine stateMachine;
    private final ClaimRowMapper rowMapper;
    private final CategoryRepository categories;
    private final TeamRepository teams;
    private final UserRepository users;
    private final BudgetService budgets;
    private final DocumentService documents;
    private final CommentService comments;
    private final PaymentService payments;
    private final AuditService audit;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ClaimStateMachine stateMachine, ClaimRowMapper rowMapper,
                        CategoryRepository categories, TeamRepository teams, UserRepository users,
                        BudgetService budgets, DocumentService documents, CommentService comments,
                        PaymentService payments, AuditService audit, Clock clock) {
        this.claims = claims;
        this.stateMachine = stateMachine;
        this.rowMapper = rowMapper;
        this.categories = categories;
        this.teams = teams;
        this.users = users;
        this.budgets = budgets;
        this.documents = documents;
        this.comments = comments;
        this.payments = payments;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Long createDraft(ClaimForm form) {
        AppUserPrincipal actor = CurrentUser.require();
        if (!actor.isTeamMember() || actor.teamId() == null) {
            throw new BusinessRuleException("Only team members can create claims.");
        }
        Team team = teams.findById(actor.teamId()).orElseThrow(() -> NotFoundException.of("Team", actor.teamId()));
        if (!team.isActive()) {
            throw new BusinessRuleException("Your team is no longer active.");
        }
        Instant now = Instant.now(clock);
        String code = "NRM-" + Year.now(clock.withZone(IST)).getValue() + "-" + String.format("%05d", claims.nextCodeNumber());
        Claim claim = new Claim(code, team.getId(), actor.id(), now);
        apply(claim, form, now);
        claims.save(claim);
        audit.record(AuditEntity.CLAIM, claim.getId(), "DRAFT_CREATED", actor.id(), null, "TEAM_MEMBER",
                null, ClaimStatus.DRAFT.name(), AuditService.detail("amount", claim.getClaimedAmount()));
        return claim.getId();
    }

    @Transactional
    public void updateDraft(Long claimId, ClaimForm form) {
        AppUserPrincipal actor = CurrentUser.require();
        Claim claim = find(claimId);
        AccessPolicy.requireMember(actor, claim.getTeamId());
        if (!claim.getStatus().isEditableByTeam()) {
            throw new BusinessRuleException("Only draft or returned claims can be edited.");
        }
        if (form.version() != null && form.version() != claim.getVersion()) {
            throw new ConcurrentUpdateException();
        }
        apply(claim, form, Instant.now(clock));
        audit.record(AuditEntity.CLAIM, claim.getId(), "CLAIM_EDITED", AuditService.detail("amount", claim.getClaimedAmount()));
    }

    @Transactional
    public void deleteDraft(Long claimId) {
        AppUserPrincipal actor = CurrentUser.require();
        Claim claim = find(claimId);
        AccessPolicy.requireMember(actor, claim.getTeamId());
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new BusinessRuleException("Only drafts can be deleted.");
        }
        if (claim.getSubmittedAt() != null) {
            throw new BusinessRuleException("This claim has been submitted before and cannot be deleted.");
        }
        documents.forClaim(claimId).forEach(d -> documents.delete(d.id()));
        claims.delete(claim);
        audit.record(AuditEntity.CLAIM, claimId, "DRAFT_DELETED", AuditService.detail("code", claim.getPublicCode()));
    }

    private void apply(Claim claim, ClaimForm form, Instant now) {
        Category category = categories.findById(form.categoryId())
                .orElseThrow(() -> new BusinessRuleException("Choose a valid category."));
        if (!category.isActive()) {
            throw new BusinessRuleException("That category is no longer available.");
        }
        claim.updateDetails(category.getId(), Money.normalise(form.claimedAmount()), form.expenseDate(),
                form.vendorName().trim(), blankToNull(form.vendorGstin()), blankToNull(form.invoiceNumber()),
                form.invoiceDate(), form.justification().trim(), now);
    }

    @Transactional(readOnly = true)
    public ClaimForm formFor(Long claimId) {
        Claim c = find(claimId);
        AccessPolicy.requireMember(CurrentUser.require(), c.getTeamId());
        return new ClaimForm(c.getCategoryId(), c.getClaimedAmount(), c.getExpenseDate(), c.getVendorName(),
                c.getVendorGstin(), c.getInvoiceNumber(), c.getInvoiceDate(), c.getJustification(), c.getVersion());
    }

    @Transactional(readOnly = true)
    public ClaimDetailView detail(Long claimId) {
        AppUserPrincipal actor = CurrentUser.require();
        Claim c = find(claimId);
        AccessPolicy.requireTeamView(actor, c.getTeamId());
        Team team = teams.findById(c.getTeamId()).orElseThrow();
        Category category = categories.findById(c.getCategoryId()).orElse(null);
        BudgetSnapshot budget = budgets.snapshot(c.getTeamId());
        boolean member = AccessPolicy.isMemberOf(actor, c.getTeamId());

        Set<ClaimAction> actions = EnumSet.noneOf(ClaimAction.class);
        for (ClaimAction a : ClaimAction.values()) {
            if (stateMachine.canPerform(a, c, actor)) {
                actions.add(a);
            }
        }
        List<ClaimWarning> warnings = ClaimWarnings.evaluate(category,
                c.getStatus() == ClaimStatus.APPROVED || c.getStatus() == ClaimStatus.PAID ? null : c.getClaimedAmount(),
                budget, c.getPreApprovalId() != null);

        return new ClaimDetailView(c.getId(), c.getPublicCode(), team.getId(), team.getName(), c.getCategoryId(),
                category == null ? "—" : category.getName(), c.getClaimedAmount(), c.getApprovedAmount(),
                c.getExpenseDate(), c.getVendorName(), c.getVendorGstin(), c.getInvoiceNumber(), c.getInvoiceDate(),
                c.getJustification(), c.getStatus(), c.isPriority(), c.getPriorityReason(), team.isPriority(),
                c.isPriorityRequested(), c.getPriorityRequestReason(), c.getDecisionReason(),
                name(c.getSubmittedBy()), name(c.getVerifiedBy()), name(c.getApprovedBy()),
                c.getCreatedAt(), c.getSubmittedAt(), c.getStageEnteredAt(), c.getVersion(),
                documents.forClaim(claimId), comments.forClaim(claimId),
                audit.forEntity(AuditEntity.CLAIM, claimId), budget, warnings, actions,
                member && c.getStatus().isEditableByTeam(),
                actor.has(Permission.CLAIM_FLAG_PRIORITY) && !c.getStatus().isTerminal(),
                member && !c.isPriority() && !c.isPriorityRequested() && !c.getStatus().isTerminal()
                        && c.getStatus() != ClaimStatus.DRAFT,
                payments.forClaim(claimId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ClaimRow> teamClaims(Long teamId) {
        AccessPolicy.requireTeamView(CurrentUser.require(), teamId);
        return rowMapper.map(claims.findByTeamIdOrderByCreatedAtDesc(teamId));
    }

    /** Live warnings for the claim form (team member's own team). */
    @Transactional(readOnly = true)
    public List<ClaimWarning> warningsFor(Long categoryId, BigDecimal amount) {
        AppUserPrincipal actor = CurrentUser.require();
        if (actor.teamId() == null) {
            return List.of();
        }
        Category category = categoryId == null ? null : categories.findById(categoryId).orElse(null);
        return ClaimWarnings.evaluate(category, amount, budgets.snapshot(actor.teamId()), false);
    }

    private Claim find(Long id) {
        return claims.findById(id).orElseThrow(() -> NotFoundException.of("Claim", id));
    }

    private String name(Long userId) {
        return userId == null ? null : users.findById(userId).map(User::getName).orElse(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
