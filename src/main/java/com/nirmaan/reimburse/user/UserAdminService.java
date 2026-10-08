package com.nirmaan.reimburse.user;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.notification.EmailMessage;
import com.nirmaan.reimburse.notification.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

/**
 * User lifecycle: invitations, activation, deactivation and COO hand-over.
 * Guarantees the platform always has exactly one active COO.
 */
@Service
public class UserAdminService {

    static final Duration INVITE_TTL = Duration.ofDays(7);
    static final int MIN_PASSWORD_LENGTH = 10;

    private final UserRepository users;
    private final PermissionService permissions;
    private final UserTokenService tokens;
    private final NotificationService notifications;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserAdminService(UserRepository users, PermissionService permissions, UserTokenService tokens,
                            NotificationService notifications, PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.permissions = permissions;
        this.tokens = tokens;
        this.notifications = notifications;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional(readOnly = true)
    public List<StaffRow> listStaff() {
        return users.findAllStaff().stream()
                .sorted(Comparator.comparing((User u) -> u.primaryRole().ordinal()).thenComparing(User::getName))
                .map(u -> new StaffRow(u.getId(), u.getName(), u.getEmail(), u.primaryRole(), u.isActive(),
                        u.getPasswordHash() == null,
                        permissions.grantsOf(u.getId()).stream()
                                .sorted(Comparator.comparing(UserPermission::getPermission))
                                .map(g -> new StaffRow.GrantView(g.getPermission(), g.getExpiresAt())).toList(),
                        u.getLastActiveAt()))
                .toList();
    }

    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional(readOnly = true)
    public StaffRow getStaff(Long userId) {
        return listStaff().stream().filter(r -> r.id().equals(userId)).findFirst()
                .orElseThrow(() -> NotFoundException.of("Staff user", userId));
    }

    /** Invite a Nirmaan staff member or Finance user. They receive their role's default permissions. */
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional
    public User inviteStaff(InviteForm form) {
        if (!form.role().isStaffRole()) {
            throw new BusinessRuleException("Only Nirmaan staff or Finance users can be invited here. "
                    + "Use COO hand-over to change the COO, and team pages to add students.");
        }
        User user = createInvitedUser(form.name(), form.email(), form.role());
        permissions.applyRoleDefaults(user, CurrentUser.require().id());
        return user;
    }

    /**
     * Create a user and email them a one-time link to set their password.
     * Internal: callers are responsible for authorisation (staff invites, team member invites).
     */
    @Transactional
    public User createInvitedUser(String name, String email, Role role) {
        if (users.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("A user with email " + email + " already exists.");
        }
        User user = users.save(new User(name.trim(), email, role));
        audit.record(AuditEntity.USER, user.getId(), "USER_INVITED", detail("email", user.getEmail(), "role", role));
        sendInvite(user);
        return user;
    }

    @PreAuthorize("hasAuthority('STAFF_MANAGE') or hasAuthority('TEAM_MANAGE')")
    @Transactional
    public void resendInvite(Long userId) {
        User user = find(userId);
        if (user.getPasswordHash() != null) {
            throw new BusinessRuleException("This user has already activated their account.");
        }
        sendInvite(user);
    }

    private void sendInvite(User user) {
        String token = tokens.create(user.getId(), UserTokenService.INVITE, INVITE_TTL);
        notifications.sendEmail(new EmailMessage(user.getEmail(), "You're invited to Nirmaan Reimbursements",
                "Hello " + user.getName() + ",\n\nYou have been added to the Nirmaan reimbursement platform as "
                        + user.primaryRole().label() + ".\nSet your password here (valid for 7 days):\n\n"
                        + notifications.absolute("/invite/" + token) + "\n\n— Nirmaan, IIT Madras"));
    }

    /** Public: complete an invitation by setting a password. */
    @Transactional
    public void acceptInvite(String token, String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        Long userId = tokens.consume(token, UserTokenService.INVITE);
        User user = find(userId);
        if (!user.isActive()) {
            throw new BusinessRuleException("This account has been deactivated.");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        audit.record(AuditEntity.USER, user.getId(), "INVITE_ACCEPTED", userId, null, null, null, null, detail());
    }

    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional
    public void deactivate(Long userId) {
        User user = find(userId);
        if (user.getId().equals(CurrentUser.require().id())) {
            throw new BusinessRuleException("You cannot deactivate your own account.");
        }
        if (user.isCoo() && users.countActiveCoos() <= 1) {
            throw new BusinessRuleException("The platform must always have a COO. Transfer the COO role first.");
        }
        user.setActive(false);
        audit.record(AuditEntity.USER, user.getId(), "USER_DEACTIVATED", detail("email", user.getEmail()));
    }

    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    @Transactional
    public void reactivate(Long userId) {
        User user = find(userId);
        user.setActive(true);
        audit.record(AuditEntity.USER, user.getId(), "USER_REACTIVATED", detail("email", user.getEmail()));
    }

    /**
     * Hand the COO role to another active staff or finance user. The outgoing COO becomes
     * Nirmaan staff with the default staff bundle. Takes effect on both users' next request.
     */
    @PreAuthorize("hasRole('COO')")
    @Transactional
    public void transferCoo(Long newCooId) {
        Long actorId = CurrentUser.require().id();
        User current = find(actorId);
        User next = find(newCooId);
        if (!current.isCoo()) {
            throw new BusinessRuleException("Only the COO can transfer the COO role.");
        }
        if (next.getId().equals(current.getId())) {
            throw new BusinessRuleException("You are already the COO.");
        }
        if (!next.isActive() || next.getPasswordHash() == null) {
            throw new BusinessRuleException("The new COO must have an active, activated account.");
        }
        if (next.hasRole(Role.TEAM_MEMBER)) {
            throw new BusinessRuleException("A team member cannot become COO.");
        }
        permissions.clearGrants(next, actorId, "Became COO");
        next.replaceRoles(EnumSet.of(Role.COO));
        current.replaceRoles(EnumSet.of(Role.NIRMAAN_STAFF));
        users.flush();
        permissions.applyRoleDefaults(current, actorId);
        if (users.countActiveCoos() != 1) {
            throw new IllegalStateException("COO hand-over would leave " + users.countActiveCoos() + " COOs");
        }
        audit.record(AuditEntity.USER, next.getId(), "COO_TRANSFERRED", actorId, null, null, null, null,
                detail("from", current.getEmail(), "to", next.getEmail()));
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }
}
