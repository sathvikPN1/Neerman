package com.nirmaan.reimburse.payment;

import com.nirmaan.reimburse.claim.Claim;
import com.nirmaan.reimburse.claim.ClaimAction;
import com.nirmaan.reimburse.claim.ClaimStateMachine;
import com.nirmaan.reimburse.claim.TransitionRequest;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final ClaimStateMachine stateMachine;
    private final UserRepository users;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, ClaimStateMachine stateMachine, UserRepository users, Clock clock) {
        this.payments = payments;
        this.stateMachine = stateMachine;
        this.users = users;
        this.clock = clock;
    }

    /** Record the payment of an APPROVED claim; the claim moves to PAID and the team is notified. */
    @PreAuthorize("hasAuthority('PAYMENT_RECORD')")
    @Transactional
    public void markPaid(Long claimId, PaymentForm form) {
        String utr = form.utr().trim().toUpperCase();
        Claim claim = stateMachine.transition(claimId, ClaimAction.MARK_PAID, new TransitionRequest(null, null,
                form.version(), Map.of("utr", utr, "mode", form.mode().name(), "paidOn", form.paidOn().toString())));
        payments.save(new Payment(claim.getId(), claim.getApprovedAmount(), form.paidOn(), form.mode(), utr,
                CurrentUser.require().id(), Instant.now(clock)));
    }

    /** Internal: callers check access to the claim. */
    @Transactional(readOnly = true)
    public Optional<PaymentView> forClaim(Long claimId) {
        return payments.findByClaimId(claimId).map(p -> new PaymentView(p.getAmount(), p.getPaidOn(), p.getMode(),
                p.getUtr(), users.findById(p.getRecordedBy()).map(User::getName).orElse(null), p.getRecordedAt()));
    }
}
