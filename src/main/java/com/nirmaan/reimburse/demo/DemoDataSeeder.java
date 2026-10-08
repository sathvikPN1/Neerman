package com.nirmaan.reimburse.demo;

import com.nirmaan.reimburse.category.CategoryRepository;
import com.nirmaan.reimburse.claim.ClaimAction;
import com.nirmaan.reimburse.claim.ClaimForm;
import com.nirmaan.reimburse.claim.ClaimPriorityService;
import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.claim.ClaimStateMachine;
import com.nirmaan.reimburse.claim.TransitionRequest;
import com.nirmaan.reimburse.cohort.CohortForm;
import com.nirmaan.reimburse.cohort.CohortService;
import com.nirmaan.reimburse.document.DocumentService;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.team.MemberForm;
import com.nirmaan.reimburse.team.Program;
import com.nirmaan.reimburse.team.TeamForm;
import com.nirmaan.reimburse.team.TeamService;
import com.nirmaan.reimburse.user.InviteForm;
import com.nirmaan.reimburse.user.PrincipalLoader;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserAdminService;
import com.nirmaan.reimburse.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * Seeds demo users, a cohort, three teams and a few claims in different stages (when app.demo-data=true).
 * Runs through the real services as the relevant users so audit trails look genuine. Idempotent.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String PASSWORD = "Demo@1234";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final UserAdminService userAdmin;
    private final CohortService cohorts;
    private final TeamService teams;
    private final ClaimService claims;
    private final ClaimStateMachine stateMachine;
    private final ClaimPriorityService priority;
    private final DocumentService documents;
    private final CategoryRepository categories;
    private final PrincipalLoader principals;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;

    public DemoDataSeeder(UserRepository users, UserAdminService userAdmin, CohortService cohorts, TeamService teams,
                          ClaimService claims, ClaimStateMachine stateMachine, ClaimPriorityService priority,
                          DocumentService documents, CategoryRepository categories, PrincipalLoader principals,
                          PasswordEncoder encoder, TransactionTemplate tx) {
        this.users = users;
        this.userAdmin = userAdmin;
        this.cohorts = cohorts;
        this.teams = teams;
        this.claims = claims;
        this.stateMachine = stateMachine;
        this.priority = priority;
        this.documents = documents;
        this.categories = categories;
        this.principals = principals;
        this.encoder = encoder;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.findByEmail("staff@nirmaan.local").isPresent()) {
            return;
        }
        String cooEmail = users.findActiveByRole(Role.COO).stream().map(User::getEmail).findFirst().orElse(null);
        if (cooEmail == null) {
            log.warn("Demo data skipped: no COO account (set BOOTSTRAP_COO_EMAIL / BOOTSTRAP_COO_PASSWORD)");
            return;
        }
        log.info("Seeding demo data (password for all demo users: {})", PASSWORD);

        as(cooEmail, () -> {
            userAdmin.inviteStaff(new InviteForm("Sneha Iyer", "staff@nirmaan.local", Role.NIRMAAN_STAFF));
            userAdmin.inviteStaff(new InviteForm("Karthik Rao", "staff2@nirmaan.local", Role.NIRMAAN_STAFF));
            userAdmin.inviteStaff(new InviteForm("Lakshmi Narayanan", "finance@nirmaan.local", Role.FINANCE));
            return null;
        });
        activate("staff@nirmaan.local", "staff2@nirmaan.local", "finance@nirmaan.local");

        LocalDate start = LocalDate.of(2026, 7, 1);
        Long[] teamIds = as("staff@nirmaan.local", () -> {
            Long cohort = cohorts.save(new CohortForm(null, "Pratham Jul 2026", start, start.plusMonths(6).minusDays(1)));
            Long agri = teams.save(new TeamForm(null, "AgriSense", cohort, Program.PRATHAM, null));
            Long med = teams.save(new TeamForm(null, "MedTrack", cohort, Program.PRATHAM, null));
            Long volt = teams.save(new TeamForm(null, "VoltCycle", cohort, Program.AKSHAR, null));
            teams.addMember(agri, new MemberForm("Priya Raman", "priya@agrisense.local"));
            teams.addMember(agri, new MemberForm("Arjun Mehta", "arjun@agrisense.local"));
            teams.addMember(med, new MemberForm("Meera Krishnan", "meera@medtrack.local"));
            teams.addMember(volt, new MemberForm("Rahul Verma", "rahul@voltcycle.local"));
            return new Long[]{agri, med, volt};
        });
        activate("priya@agrisense.local", "arjun@agrisense.local", "meera@medtrack.local", "rahul@voltcycle.local");

        Long parts = categoryId("Consumables & prototype parts");
        Long research = categoryId("Market research");
        Long software = categoryId("Subscriptions & software");

        // AgriSense: one submitted claim waiting for staff, one draft.
        submitClaim("priya@agrisense.local", parts, "18450.00", "Robu.in", "Soil moisture sensors and ESP32 boards for field prototype v2");
        as("priya@agrisense.local", () -> claims.createDraft(new ClaimForm(research, new BigDecimal("3200.00"),
                LocalDate.now().minusDays(1), "SurveyMonkey", null, null, null,
                "Farmer survey tool for validating irrigation pain points", null)));

        // MedTrack: verified, waiting for the COO, flagged priority.
        Long med = submitClaim("meera@medtrack.local", software, "12999.00", "Autodesk India",
                "Fusion 360 licence for enclosure design of the pill dispenser");
        as("staff@nirmaan.local", () -> stateMachine.transition(med, ClaimAction.VERIFY, TransitionRequest.of(null, null)));
        as("staff@nirmaan.local", () -> {
            priority.setPriority(med, true, "Student paid from personal savings; cannot wait");
            return null;
        });

        // VoltCycle: approved, waiting for Finance.
        Long volt = submitClaim("rahul@voltcycle.local", parts, "64500.00", "Li-Ion Cells Pvt Ltd",
                "Battery cells and BMS for e-cycle conversion kit prototype");
        as("staff@nirmaan.local", () -> stateMachine.transition(volt, ClaimAction.VERIFY, TransitionRequest.of(null, null)));
        as(cooEmail, () -> stateMachine.transition(volt, ClaimAction.APPROVE, TransitionRequest.approve(null, null, null)));

        log.info("Demo data ready: {} teams", teamIds.length);
    }

    private Long submitClaim(String email, Long categoryId, String amount, String vendor, String justification) {
        return as(email, () -> {
            Long id = claims.createDraft(new ClaimForm(categoryId, new BigDecimal(amount), LocalDate.now().minusDays(3),
                    vendor, null, "INV-" + (1000 + (int) (Math.random() * 9000)), LocalDate.now().minusDays(3),
                    justification, null));
            documents.upload(id, DocumentType.INVOICE, "invoice.pdf", SimplePdf.of(List.of(
                    "TAX INVOICE", vendor, "Invoice date: " + LocalDate.now().minusDays(3),
                    "Bill to: " + email, "Total: Rs " + amount)));
            documents.upload(id, DocumentType.PAYMENT_PROOF, "upi-receipt.pdf", SimplePdf.of(List.of(
                    "UPI payment successful", "Paid to: " + vendor, "Amount: Rs " + amount)));
            stateMachine.transition(id, ClaimAction.SUBMIT, TransitionRequest.of(null, null));
            return id;
        });
    }

    private void activate(String... emails) {
        tx.executeWithoutResult(s -> {
            for (String email : emails) {
                users.findByEmail(email).ifPresent(u -> u.setPasswordHash(encoder.encode(PASSWORD)));
            }
        });
    }

    private Long categoryId(String name) {
        return categories.findByNameIgnoreCase(name).orElseThrow().getId();
    }

    private <T> T as(String email, Supplier<T> action) {
        var principal = principals.loadByEmail(email).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        try {
            return tx.execute(s -> action.get());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
