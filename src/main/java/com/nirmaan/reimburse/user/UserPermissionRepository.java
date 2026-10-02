package com.nirmaan.reimburse.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    List<UserPermission> findByUserId(Long userId);

    Optional<UserPermission> findByUserIdAndPermission(Long userId, Permission permission);

    @Query("select p from UserPermission p where p.userId = :userId and (p.expiresAt is null or p.expiresAt > :now)")
    List<UserPermission> findActiveByUserId(Long userId, Instant now);

    @Query("select p from UserPermission p where p.expiresAt is not null and p.expiresAt <= :now")
    List<UserPermission> findExpired(Instant now);

    @Query("""
            select p.userId from UserPermission p
            where p.permission = :permission and (p.expiresAt is null or p.expiresAt > :now)""")
    List<Long> findActiveHolders(Permission permission, Instant now);
}
