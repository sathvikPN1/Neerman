package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.notification.NotificationService;
import com.nirmaan.reimburse.team.Team;
import com.nirmaan.reimburse.team.TeamRepository;
import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.PermissionService;
import com.nirmaan.reimburse.user.User;
import com.nirmaan.reimburse.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** Decides who hears about each claim event. */
@Component
public class ClaimNotifier {

    private final NotificationService notifications;
    private final PermissionService permissions;
    private final TeamRepository teams;
    private final UserRepository users;

    public ClaimNotifier(NotificationService notifications, PermissionService permissions, TeamRepository teams,
                         UserRepository users) {
        this.notifications = notifications;
        this.permissions = permissions;
        this.teams = teams;
        this.users = users;
    }

    public void onTransition(Claim claim, ClaimAction action, ClaimStatus from, String comment) {
        String teamName = teams.findById(claim.getTeamId()).map(Team::getName).orElse("Team");
        String code = claim.getPublicCode();
        String link = "/claims/" + claim.getId();
        Set<Long> team = new LinkedHashSet<>(teams.findMemberIds(claim.getTeamId()));
        String why = comment == null ? "" : " Reason: " + comment;

        switch (action) {
            case SUBMIT -> {
                notifications.notify(team, code + " was " + (from == ClaimStatus.RETURNED ? "resubmitted" : "submitted")
                        + " and is waiting for verification.", link);
                notifications.notify(verifiers(), code + " from " + teamName + " (" + Money.formatInr(claim.getClaimedAmount())
                        + ") is waiting for verification.", link);
            }
            case VERIFY -> {
                notifications.notify(team, code + " was verified and sent to the COO for approval.", link);
                Set<Long> approvers = permissions.holdersOf(Permission.CLAIM_APPROVE);
                approvers.remove(claim.getVerifiedBy());
                notifications.notify(approvers, code + " from " + teamName + " ("
                        + Money.formatInr(claim.getClaimedAmount()) + ") is ready for approval.", link);
            }
            case RETURN -> notifications.notify(team, code + " was returned for changes." + why, link);
            case REJECT -> notifications.notify(team, code + " was rejected." + why, link);
            case APPROVE -> {
                boolean partial = claim.getApprovedAmount().compareTo(claim.getClaimedAmount()) < 0;
                notifications.notify(team, code + " was " + (partial ? "partially approved for " : "approved for ")
                        + Money.formatInr(claim.getApprovedAmount()) + "." + why, link);
                notifications.notify(permissions.holdersOf(Permission.PAYMENT_RECORD).stream()
                                .filter(id -> !isCoo(id)).toList(),
                        code + " from " + teamName + " is approved and ready for payment ("
                                + Money.formatInr(claim.getApprovedAmount()) + ").", link);
            }
            case MARK_PAID -> notifications.notify(team, code + " has been paid: "
                    + Money.formatInr(claim.getApprovedAmount()) + ".", link);
            case REOPEN -> {
                notifications.notify(team, code + " was reopened for review." + why, link);
                notifications.notify(verifiers(), code + " from " + teamName + " was reopened and needs verification.", link);
            }
        }
    }

    /** Staff who verify claims. The COO is only included when nobody else can verify. */
    public Set<Long> verifiers() {
        Set<Long> holders = permissions.holdersOf(Permission.CLAIM_VERIFY);
        Set<Long> nonCoo = new LinkedHashSet<>();
        holders.stream().filter(id -> !isCoo(id)).forEach(nonCoo::add);
        return nonCoo.isEmpty() ? holders : nonCoo;
    }

    private boolean isCoo(Long userId) {
        return users.findById(userId).map(User::isCoo).orElse(false);
    }
}
