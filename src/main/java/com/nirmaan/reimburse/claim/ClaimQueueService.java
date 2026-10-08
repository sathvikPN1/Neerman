package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.document.ClaimDocument;
import com.nirmaan.reimburse.document.ClaimDocumentRepository;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.document.FileTypeDetector;
import com.nirmaan.reimburse.team.BudgetService;
import com.nirmaan.reimburse.team.BudgetSnapshot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Work queues. Every queue is sorted priority first, then oldest first ({@link ClaimQueueOrdering}). */
@Service
public class ClaimQueueService {

    private final ClaimRepository claims;
    private final ClaimRowMapper rowMapper;
    private final ClaimDocumentRepository documents;
    private final BudgetService budgets;
    private final Clock clock;

    public ClaimQueueService(ClaimRepository claims, ClaimRowMapper rowMapper, ClaimDocumentRepository documents,
                             BudgetService budgets, Clock clock) {
        this.claims = claims;
        this.rowMapper = rowMapper;
        this.documents = documents;
        this.budgets = budgets;
        this.clock = clock;
    }

    /** Staff verification queue. Defaults to SUBMITTED claims. */
    @PreAuthorize("hasAnyAuthority('CLAIM_VERIFY', 'CLAIM_RETURN_REJECT', 'CLAIM_APPROVE', 'CLAIM_FLAG_PRIORITY')")
    @Transactional(readOnly = true)
    public List<ClaimRow> staffQueue(QueueFilter filter) {
        EnumSet<ClaimStatus> statuses = filter.status() == null
                ? EnumSet.of(ClaimStatus.SUBMITTED)
                : EnumSet.of(filter.status());
        List<ClaimRow> rows = rowMapper.map(claims.findForQueue(statuses, filter.teamId(), filter.cohortId(),
                filter.categoryId()));
        Instant now = Instant.now(clock);
        return rows.stream()
                .filter(r -> filter.minAgeDays() == null || r.stageEnteredAt() == null
                        || Duration.between(r.stageEnteredAt(), now).toDays() >= filter.minAgeDays())
                .sorted(ClaimQueueOrdering.PRIORITY_THEN_OLDEST)
                .toList();
    }

    /** COO approval screen: VERIFIED claims with expansion details. */
    @PreAuthorize("hasAuthority('CLAIM_APPROVE')")
    @Transactional(readOnly = true)
    public List<ApprovalRow> approvalQueue() {
        AppUserPrincipal actor = CurrentUser.require();
        List<Claim> verified = claims.findForQueue(EnumSet.of(ClaimStatus.VERIFIED), null, null, null);
        Map<Long, Claim> byId = verified.stream().collect(Collectors.toMap(Claim::getId, Function.identity()));
        Map<Long, ClaimDocument> invoices = documents.findByClaimIdIn(byId.keySet()).stream()
                .filter(d -> d.getType() == DocumentType.INVOICE)
                .collect(Collectors.toMap(ClaimDocument::getClaimId, Function.identity(), (a, b) -> a));
        Map<Long, BudgetSnapshot> snapshots = budgets.allSnapshots();
        return rowMapper.map(verified).stream()
                .sorted(ClaimQueueOrdering.PRIORITY_THEN_OLDEST)
                .map(r -> {
                    Claim c = byId.get(r.id());
                    ClaimDocument invoice = invoices.get(r.id());
                    BudgetSnapshot b = snapshots.get(r.teamId());
                    return new ApprovalRow(r, c.getJustification(), invoice == null ? null : invoice.getId(),
                            invoice != null && FileTypeDetector.isImage(invoice.getContentType()), b,
                            b != null && b.exceedsRemaining(r.claimedAmount()),
                            !actor.isCoo() && Objects.equals(c.getVerifiedBy(), actor.id()));
                })
                .toList();
    }

    /** Finance payment queue: APPROVED claims. */
    @PreAuthorize("hasAuthority('PAYMENT_RECORD')")
    @Transactional(readOnly = true)
    public List<ClaimRow> paymentQueue() {
        return rowMapper.map(claims.findForQueue(EnumSet.of(ClaimStatus.APPROVED), null, null, null)).stream()
                .sorted(ClaimQueueOrdering.PRIORITY_THEN_OLDEST)
                .toList();
    }
}
