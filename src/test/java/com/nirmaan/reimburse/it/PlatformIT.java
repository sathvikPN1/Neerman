package com.nirmaan.reimburse.it;

import com.nirmaan.reimburse.claim.ClaimAction;
import com.nirmaan.reimburse.claim.ClaimStateMachine;
import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.claim.TransitionRequest;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Concurrency, budgets against the real database, and audit-log immutability. */
class PlatformIT extends IntegrationTest {

    @Autowired ClaimStateMachine stateMachine;
    @Autowired JdbcTemplate jdbc;

    @Test
    void twoApproversCannotDoubleActOnTheSameClaim() throws Exception {
        Team team = fx.team();
        User student = fx.member(team);
        User verifier = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY);
        User delegate = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_APPROVE);
        Long id = fx.verified(student, verifier, "4000");

        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        for (User approver : List.of(fx.coo(), delegate)) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    fx.as(approver, () -> stateMachine.transition(id, ClaimAction.APPROVE, TransitionRequest.of(null, null)));
                } catch (Throwable t) {
                    failures.add(t);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(BusinessRuleException.class); // concurrent update or illegal transition
        assertThat(fx.claim(id).getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'CLAIM' and entity_id = ? and action = 'APPROVED'",
                Long.class, id)).isEqualTo(1);
    }

    @Test
    void approvalsCannotOverspendTheBudgetButCanBePartial() {
        Team team = fx.team(new BigDecimal("10000.00"));
        User student = fx.member(team);
        User verifier = fx.staff(Role.NIRMAAN_STAFF, Permission.CLAIM_VERIFY);
        Long first = fx.verified(student, verifier, "8000");
        Long second = fx.verified(student, verifier, "5000");
        User coo = fx.coo();

        fx.as(coo, () -> stateMachine.transition(first, ClaimAction.APPROVE, TransitionRequest.of(null, null)));
        assertThatThrownBy(() -> fx.as(coo, () -> stateMachine.transition(second, ClaimAction.APPROVE, TransitionRequest.of(null, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("₹2,000.00");
        fx.as(coo, () -> stateMachine.transition(second, ClaimAction.APPROVE,
                TransitionRequest.approve(new BigDecimal("2000"), "Capped at remaining budget", null)));
        assertThat(fx.claim(second).getApprovedAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void auditLogIsAppendOnly() {
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isPositive();
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'TAMPERED'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log"))
                .hasMessageContaining("append-only");
    }

    @Test
    void claimCodesAreHumanReadableAndUnique() {
        User student = fx.member(fx.team());
        Long a = fx.draftWithDocuments(student, "10");
        Long b = fx.draftWithDocuments(student, "10");
        assertThat(fx.claim(a).getPublicCode()).matches("NRM-20\\d\\d-\\d{5}").isNotEqualTo(fx.claim(b).getPublicCode());
    }
}
