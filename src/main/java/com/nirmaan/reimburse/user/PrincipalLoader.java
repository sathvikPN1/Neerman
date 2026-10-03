package com.nirmaan.reimburse.user;

import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Builds an {@link AppUserPrincipal} from the database. Used at login and on every request, so
 * permission grants, revocations, expiries and deactivation take effect immediately.
 * An SSO/OAuth2 login can reuse {@link #loadByEmail(String)} to map an external identity.
 */
@Component
public class PrincipalLoader {

    private final UserRepository users;
    private final PermissionService permissions;
    private final JdbcTemplate jdbc;

    public PrincipalLoader(UserRepository users, PermissionService permissions, JdbcTemplate jdbc) {
        this.users = users;
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<AppUserPrincipal> loadByEmail(String email) {
        return users.findByEmail(email).map(this::toPrincipal);
    }

    @Transactional(readOnly = true)
    public Optional<AppUserPrincipal> loadById(Long id) {
        return users.findById(id).map(this::toPrincipal);
    }

    private AppUserPrincipal toPrincipal(User u) {
        List<Long> teamIds = jdbc.queryForList("select team_id from team_members where user_id = ?", Long.class, u.getId());
        return new AppUserPrincipal(u.getId(), u.getEmail(), u.getName(), u.getPasswordHash(), u.isActive(),
                u.getRoles(), permissions.effectivePermissions(u), teamIds.isEmpty() ? null : teamIds.getFirst());
    }
}
