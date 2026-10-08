package com.nirmaan.reimburse.team;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByOrderByNameAsc();

    Optional<Team> findByNameIgnoreCase(String name);

    /** Pessimistic row lock (SELECT … FOR UPDATE) to serialise budget-affecting approvals per team. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :id")
    Optional<Team> lockById(Long id);

    @Query(value = "select user_id from team_members where team_id = :teamId", nativeQuery = true)
    List<Long> findMemberIds(Long teamId);

    @Modifying
    @Query(value = "insert into team_members (team_id, user_id) values (:teamId, :userId)", nativeQuery = true)
    void addMember(Long teamId, Long userId);

    @Modifying
    @Query(value = "delete from team_members where team_id = :teamId and user_id = :userId", nativeQuery = true)
    int removeMember(Long teamId, Long userId);
}
