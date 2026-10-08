package com.nirmaan.reimburse.common.security;

import com.nirmaan.reimburse.user.Permission;
import org.springframework.security.access.AccessDeniedException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Data-scoping rules shared by services. Team members only ever see their own team;
 * staff-side users (anyone holding a back-office permission) see all teams.
 */
public final class AccessPolicy {

    /** Permissions that imply read access to claims and teams across the platform. */
    public static final Set<Permission> STAFF_SIDE = EnumSet.of(
            Permission.CLAIM_VERIFY, Permission.CLAIM_RETURN_REJECT, Permission.CLAIM_APPROVE,
            Permission.CLAIM_FLAG_PRIORITY, Permission.PREAPPROVAL_DECIDE, Permission.TEAM_MANAGE,
            Permission.PAYMENT_RECORD, Permission.REPORT_VIEW_EXPORT, Permission.AUDIT_LOG_VIEW,
            Permission.BANK_DETAILS_VERIFY);

    private AccessPolicy() {
    }

    public static boolean isStaffSide(AppUserPrincipal p) {
        if (p.isCoo()) {
            return true;
        }
        if (p.isTeamMember()) {
            return false;
        }
        return p.permissions().stream().anyMatch(STAFF_SIDE::contains);
    }

    public static boolean isMemberOf(AppUserPrincipal p, Long teamId) {
        return p.isTeamMember() && teamId != null && teamId.equals(p.teamId());
    }

    public static boolean canViewTeam(AppUserPrincipal p, Long teamId) {
        return isStaffSide(p) || isMemberOf(p, teamId);
    }

    public static void requireTeamView(AppUserPrincipal p, Long teamId) {
        if (!canViewTeam(p, teamId)) {
            throw new AccessDeniedException("You do not have access to this team's data");
        }
    }

    public static void requireMember(AppUserPrincipal p, Long teamId) {
        if (!isMemberOf(p, teamId)) {
            throw new AccessDeniedException("Only members of this team can do that");
        }
    }

    public static void requireStaffSide(AppUserPrincipal p) {
        if (!isStaffSide(p)) {
            throw new AccessDeniedException("Staff only");
        }
    }
}
