package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.security.AccessPolicy;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.notification.NotificationService;
import com.nirmaan.reimburse.team.TeamRepository;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

/** Claim comment thread between the team, staff, the COO and Finance (replaces email). */
@Service
public class CommentService {

    static final int MAX_LENGTH = 4000;

    private final CommentRepository comments;
    private final ClaimRepository claims;
    private final TeamRepository teams;
    private final UserRepository users;
    private final NotificationService notifications;
    private final ClaimNotifier claimNotifier;
    private final AuditService audit;
    private final Clock clock;

    public CommentService(CommentRepository comments, ClaimRepository claims, TeamRepository teams, UserRepository users,
                          NotificationService notifications, ClaimNotifier claimNotifier, AuditService audit, Clock clock) {
        this.comments = comments;
        this.claims = claims;
        this.teams = teams;
        this.users = users;
        this.notifications = notifications;
        this.claimNotifier = claimNotifier;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void add(Long claimId, String body) {
        AppUserPrincipal actor = CurrentUser.require();
        Claim claim = claims.findById(claimId).orElseThrow(() -> NotFoundException.of("Claim", claimId));
        AccessPolicy.requireTeamView(actor, claim.getTeamId());
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            throw new BusinessRuleException("Write a message first.");
        }
        if (text.length() > MAX_LENGTH) {
            throw new BusinessRuleException("Messages can be at most " + MAX_LENGTH + " characters.");
        }
        comments.save(new Comment(claimId, actor.id(), text, Instant.now(clock)));
        audit.record(AuditEntity.CLAIM, claimId, "COMMENT_ADDED", detail("length", text.length()));

        Set<Long> recipients = new LinkedHashSet<>(teams.findMemberIds(claim.getTeamId()));
        Set<Long> staffInvolved = staffInvolved(claim);
        if (staffInvolved.isEmpty() && AccessPolicy.isMemberOf(actor, claim.getTeamId())) {
            staffInvolved = claimNotifier.verifiers();
        }
        recipients.addAll(staffInvolved);
        String preview = text.length() > 80 ? text.substring(0, 77) + "…" : text;
        notifications.notify(recipients, actor.name() + " commented on " + claim.getPublicCode() + ": \"" + preview + "\"",
                "/claims/" + claimId + "#comments");
    }

    @Transactional(readOnly = true)
    public List<CommentView> forClaim(Long claimId) {
        AppUserPrincipal actor = CurrentUser.require();
        Claim claim = claims.findById(claimId).orElseThrow(() -> NotFoundException.of("Claim", claimId));
        AccessPolicy.requireTeamView(actor, claim.getTeamId());
        List<Comment> list = comments.findByClaimIdOrderByCreatedAtAsc(claimId);
        Map<Long, User> authors = users.findAllById(list.stream().map(Comment::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(c -> {
            User a = authors.get(c.getAuthorId());
            return new CommentView(c.getId(), a == null ? "Unknown" : a.getName(),
                    a == null ? "" : a.primaryRole().label(), c.getAuthorId().equals(actor.id()), c.getBody(),
                    c.getCreatedAt());
        }).toList();
    }

    /** Staff-side users who acted on or commented on the claim. */
    private Set<Long> staffInvolved(Claim claim) {
        Set<Long> members = new LinkedHashSet<>(teams.findMemberIds(claim.getTeamId()));
        Set<Long> ids = new LinkedHashSet<>();
        if (claim.getVerifiedBy() != null) ids.add(claim.getVerifiedBy());
        if (claim.getApprovedBy() != null) ids.add(claim.getApprovedBy());
        comments.findByClaimIdOrderByCreatedAtAsc(claim.getId()).forEach(c -> ids.add(c.getAuthorId()));
        ids.removeAll(members);
        return ids;
    }
}
