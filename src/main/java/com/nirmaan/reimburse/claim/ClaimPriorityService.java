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
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.PermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

/** Priority flags on claims. Staff set them; teams can only request them. All changes are audit-logged. */
@Service
public class ClaimPriorityService {

    private final ClaimRepository claims;
    private final TeamRepository teams;
    private final PermissionService permissions;
    private final NotificationService notifications;
    private final AuditService audit;

    public ClaimPriorityService(ClaimRepository claims, TeamRepository teams, PermissionService permissions,
                                NotificationService notifications, AuditService audit) {
        this.claims = claims;
        this.teams = teams;
        this.permissions = permissions;
        this.notifications = notifications;
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('CLAIM_FLAG_PRIORITY')")
    @Transactional
    public void setPriority(Long claimId, boolean priority, String reason) {
        Claim claim = find(claimId);
        if (claim.getStatus().isTerminal()) {
            throw new BusinessRuleException("Priority cannot be changed on a " + claim.getStatus().label().toLowerCase() + " claim.");
        }
        if (priority && blank(reason)) {
            throw new BusinessRuleException("A reason is required to mark a claim as priority.");
        }
        boolean wasRequested = claim.isPriorityRequested();
        claim.setPriority(priority, priority ? reason.trim() : null);
        audit.record(AuditEntity.CLAIM, claimId, priority ? "PRIORITY_SET" : "PRIORITY_CLEARED",
                detail("reason", reason, "acceptedTeamRequest", priority && wasRequested ? "yes" : null));
        if (priority) {
            notifications.notify(teams.findMemberIds(claim.getTeamId()),
                    claim.getPublicCode() + " has been marked as priority.", "/claims/" + claimId);
        }
    }

    /** A team member asks for priority handling; staff accept (setPriority) or decline. */
    @Transactional
    public void requestPriority(Long claimId, String reason) {
        AppUserPrincipal actor = CurrentUser.require();
        Claim claim = find(claimId);
        AccessPolicy.requireMember(actor, claim.getTeamId());
        if (claim.getStatus().isTerminal() || claim.getStatus() == ClaimStatus.DRAFT) {
            throw new BusinessRuleException("Priority can be requested only for claims in progress.");
        }
        if (claim.isPriority() || claim.isPriorityRequested()) {
            throw new BusinessRuleException("This claim is already priority or has a pending request.");
        }
        if (blank(reason)) {
            throw new BusinessRuleException("Tell us why this claim is urgent.");
        }
        claim.requestPriority(reason.trim());
        audit.record(AuditEntity.CLAIM, claimId, "PRIORITY_REQUESTED", detail("reason", reason.trim()));
        notifications.notify(permissions.holdersOf(Permission.CLAIM_FLAG_PRIORITY),
                "Priority requested for " + claim.getPublicCode() + ": \"" + reason.trim() + "\"", "/claims/" + claimId);
    }

    @PreAuthorize("hasAuthority('CLAIM_FLAG_PRIORITY')")
    @Transactional
    public void declineRequest(Long claimId, String note) {
        Claim claim = find(claimId);
        if (!claim.isPriorityRequested()) {
            throw new BusinessRuleException("There is no pending priority request.");
        }
        claim.declinePriorityRequest();
        audit.record(AuditEntity.CLAIM, claimId, "PRIORITY_REQUEST_DECLINED", detail("comment", note));
        notifications.notify(teams.findMemberIds(claim.getTeamId()),
                "Your priority request for " + claim.getPublicCode() + " was declined." + (blank(note) ? "" : " " + note.trim()),
                "/claims/" + claimId);
    }

    private Claim find(Long id) {
        return claims.findById(id).orElseThrow(() -> NotFoundException.of("Claim", id));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
