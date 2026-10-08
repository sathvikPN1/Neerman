package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Workflow actions for controllers, including bulk approve (each claim in its own transaction). */
@Service
public class ClaimDecisionService {

    private final ClaimStateMachine stateMachine;
    private final ClaimRepository claims;
    private final TransactionTemplate tx;

    public ClaimDecisionService(ClaimStateMachine stateMachine, ClaimRepository claims, TransactionTemplate tx) {
        this.stateMachine = stateMachine;
        this.claims = claims;
        this.tx = tx;
    }

    public void act(Long claimId, ClaimAction action, TransitionRequest request) {
        stateMachine.transition(claimId, action, request);
    }

    /**
     * Approve each selected claim in full. One failure (e.g. over budget, changed by someone else)
     * does not stop the others; failures are reported back.
     */
    @PreAuthorize("hasAuthority('CLAIM_APPROVE')")
    public BulkResult bulkApprove(Map<Long, Long> claimIdToVersion) {
        int ok = 0;
        List<String> failures = new ArrayList<>();
        for (Map.Entry<Long, Long> e : claimIdToVersion.entrySet()) {
            try {
                tx.executeWithoutResult(s -> stateMachine.transition(e.getKey(), ClaimAction.APPROVE,
                        TransitionRequest.approve(null, null, e.getValue())));
                ok++;
            } catch (BusinessRuleException | AccessDeniedException ex) {
                String code = claims.findById(e.getKey()).map(Claim::getPublicCode).orElse("#" + e.getKey());
                failures.add(code + ": " + ex.getMessage());
            }
        }
        return new BulkResult(ok, failures);
    }
}
