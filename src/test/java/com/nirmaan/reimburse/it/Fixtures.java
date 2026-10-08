package com.nirmaan.reimburse.it;

import com.nirmaan.reimburse.category.CategoryRepository;
import com.nirmaan.reimburse.claim.Claim;
import com.nirmaan.reimburse.claim.ClaimAction;
import com.nirmaan.reimburse.claim.ClaimForm;
import com.nirmaan.reimburse.claim.ClaimRepository;
import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.claim.ClaimStateMachine;
import com.nirmaan.reimburse.claim.TransitionRequest;
import com.nirmaan.reimburse.cohort.Cohort;
import com.nirmaan.reimburse.cohort.CohortRepository;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.document.DocumentService;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.team.Program;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.team.TeamRepository;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.PrincipalLoader;
import com.nirmaan.reimburse.user.Role;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserPermission;
import com.nirmaan.reimburse.user.UserPermissionRepository;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Test data builders. Writes straight to repositories (bypassing authorisation) unless noted. */
@Component
public class Fixtures {

    public static final String PASSWORD = "Test-Password-1";
    public static final byte[] PDF = "%PDF-1.4\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final UserRepository users;
    private final UserPermissionRepository grants;
    private final CohortRepository cohorts;
    private final TeamRepository teams;
    private final CategoryRepository categories;
    private final ClaimRepository claims;
    private final ClaimService claimService;
    private final ClaimStateMachine stateMachine;
    private final DocumentService documents;
    private final PrincipalLoader principals;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private String encodedPassword;

    public Fixtures(UserRepository users, UserPermissionRepository grants, CohortRepository cohorts, TeamRepository teams,
                    CategoryRepository categories, ClaimRepository claims, ClaimService claimService,
                    ClaimStateMachine stateMachine, DocumentService documents, PrincipalLoader principals,
                    PasswordEncoder encoder, TransactionTemplate tx) {
        this.users = users;
        this.grants = grants;
        this.cohorts = cohorts;
        this.teams = teams;
        this.categories = categories;
        this.claims = claims;
        this.claimService = claimService;
        this.stateMachine = stateMachine;
        this.documents = documents;
        this.principals = principals;
        this.encoder = encoder;
        this.tx = tx;
    }

    public static String unique(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet() + "-" + System.nanoTime() % 100000;
    }

    public User coo() {
        return users.findActiveByRole(Role.COO).getFirst();
    }

    /** A staff/finance user holding exactly the given permissions. */
    public User staff(Role role, Permission... permissions) {
        return tx.execute(s -> {
            User u = new User(unique("Staff"), unique("staff") + "@test.local", role);
            u.setPasswordHash(password());
            users.save(u);
            for (Permission p : permissions) {
                grants.save(new UserPermission(u.getId(), p, null, Instant.now(), null));
            }
            return u;
        });
    }

    public Team team() {
        return team(new BigDecimal("200000.00"));
    }

    public Team team(BigDecimal budget) {
        return tx.execute(s -> {
            Cohort cohort = cohorts.save(new Cohort(unique("Cohort"), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31)));
            return teams.save(new Team(unique("Team"), cohort.getId(), Program.PRATHAM, budget));
        });
    }

    public User member(Team team) {
        return tx.execute(s -> {
            User u = new User(unique("Student"), unique("student") + "@test.local", Role.TEAM_MEMBER);
            u.setPasswordHash(password());
            users.save(u);
            teams.addMember(team.getId(), u.getId());
            return u;
        });
    }

    public Long categoryId(String name) {
        return categories.findByNameIgnoreCase(name).orElseThrow().getId();
    }

    public ClaimForm claimForm(String amount) {
        return new ClaimForm(categoryId("Consumables & prototype parts"), new BigDecimal(amount), LocalDate.now().minusDays(2),
                unique("Vendor"), null, unique("INV"), LocalDate.now().minusDays(2), "Parts for the prototype build", null);
    }

    /** Draft with invoice + payment proof, created through the real services as the member. */
    public Long draftWithDocuments(User member, String amount) {
        return as(member, () -> {
            Long id = claimService.createDraft(claimForm(amount));
            documents.upload(id, DocumentType.INVOICE, "invoice.pdf", PDF);
            documents.upload(id, DocumentType.PAYMENT_PROOF, "proof.pdf", (new String(PDF) + unique("x")).getBytes(StandardCharsets.US_ASCII));
            return id;
        });
    }

    public Long submitted(User member, String amount) {
        Long id = draftWithDocuments(member, amount);
        as(member, () -> stateMachine.transition(id, ClaimAction.SUBMIT, TransitionRequest.of(null, null)));
        return id;
    }

    public Long verified(User member, User verifier, String amount) {
        Long id = submitted(member, amount);
        as(verifier, () -> stateMachine.transition(id, ClaimAction.VERIFY, TransitionRequest.of(null, null)));
        return id;
    }

    public Claim claim(Long id) {
        return claims.findById(id).orElseThrow();
    }

    public AppUserPrincipal principal(User u) {
        return principals.loadById(u.getId()).orElseThrow();
    }

    /** Run as the given user. Each service call runs in its own transaction, as in a request. */
    public <T> T as(User user, Supplier<T> action) {
        AppUserPrincipal p = principal(user);
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        try {
            return action.get(); // services own their transactions
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    public void asRun(User user, Runnable action) {
        as(user, () -> {
            action.run();
            return null;
        });
    }

    private String password() {
        if (encodedPassword == null) {
            encodedPassword = encoder.encode(PASSWORD);
        }
        return encodedPassword;
    }
}
