package com.nirmaan.reimburse.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(String email);

    @Query("select u from User u join u.roles r where r = :role and u.active = true")
    List<User> findActiveByRole(Role role);

    @Query("select count(u) from User u join u.roles r where r = com.nirmaan.reimburse.user.Role.COO and u.active = true")
    long countActiveCoos();

    @Query("""
            select distinct u from User u join u.roles r
            where r in (com.nirmaan.reimburse.user.Role.COO, com.nirmaan.reimburse.user.Role.NIRMAAN_STAFF,
                        com.nirmaan.reimburse.user.Role.FINANCE)
            order by u.name""")
    List<User> findAllStaff();

    @Modifying
    @Query("update User u set u.lastActiveAt = :at where u.id = :id")
    void touchLastActive(Long id, Instant at);
}
