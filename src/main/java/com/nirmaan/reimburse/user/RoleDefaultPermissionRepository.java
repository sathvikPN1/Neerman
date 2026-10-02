package com.nirmaan.reimburse.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoleDefaultPermissionRepository extends JpaRepository<RoleDefaultPermission, RoleDefaultPermission.Key> {

    @Query("select r from RoleDefaultPermission r where r.id.role = :role")
    List<RoleDefaultPermission> findByRole(Role role);
}
