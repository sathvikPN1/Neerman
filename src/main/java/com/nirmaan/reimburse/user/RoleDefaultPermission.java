package com.nirmaan.reimburse.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;

@Entity
@Table(name = "role_default_permissions")
public class RoleDefaultPermission {

    @EmbeddedId
    private Key id;

    protected RoleDefaultPermission() {
    }

    public RoleDefaultPermission(Role role, Permission permission) {
        this.id = new Key(role, permission);
    }

    public Role getRole() {
        return id.role;
    }

    public Permission getPermission() {
        return id.permission;
    }

    @Embeddable
    public static class Key implements Serializable {
        @Enumerated(EnumType.STRING)
        @Column(name = "role")
        private Role role;

        @Enumerated(EnumType.STRING)
        @Column(name = "permission_code")
        private Permission permission;

        protected Key() {
        }

        Key(Role role, Permission permission) {
            this.role = role;
            this.permission = permission;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.role == role && k.permission == permission;
        }

        @Override
        public int hashCode() {
            return role.hashCode() * 31 + permission.hashCode();
        }
    }
}
