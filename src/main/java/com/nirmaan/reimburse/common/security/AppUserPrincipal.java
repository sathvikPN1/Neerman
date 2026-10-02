package com.nirmaan.reimburse.common.security;

import com.nirmaan.reimburse.user.Permission;
import com.nirmaan.reimburse.user.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Authenticated user. Authorities are {@code ROLE_<role>} plus one authority per effective permission.
 * Rebuilt from the database on every request (see {@code AuthorityRefreshFilter}).
 */
public final class AppUserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String name;
    private final String passwordHash;
    private final boolean active;
    private final Set<Role> roles;
    private final Set<Permission> permissions;
    private final Long teamId;

    public AppUserPrincipal(Long id, String email, String name, String passwordHash, boolean active,
                            Set<Role> roles, Set<Permission> permissions, Long teamId) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.active = active;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        this.permissions = permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
        this.teamId = teamId;
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    public Set<Role> roles() {
        return EnumSet.copyOf(roles.isEmpty() ? EnumSet.noneOf(Role.class) : roles);
    }

    public Set<Permission> permissions() {
        return permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
    }

    /** Team of a TEAM_MEMBER, otherwise null. */
    public Long teamId() {
        return teamId;
    }

    public boolean isCoo() {
        return roles.contains(Role.COO);
    }

    public boolean isTeamMember() {
        return roles.contains(Role.TEAM_MEMBER);
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    public boolean hasAny(Permission... perms) {
        for (Permission p : perms) {
            if (permissions.contains(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        roles.forEach(r -> list.add(new SimpleGrantedAuthority("ROLE_" + r.name())));
        permissions.forEach(p -> list.add(new SimpleGrantedAuthority(p.name())));
        return list;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active && passwordHash != null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AppUserPrincipal p && p.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[" + id + ", " + email + "]";
    }
}
