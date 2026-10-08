package com.nirmaan.reimburse.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    List<Claim> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    Optional<Claim> findByPublicCode(String publicCode);

    @Query("""
            select c from Claim c, com.nirmaan.reimburse.team.Team t
            where t.id = c.teamId
              and c.status in :statuses
              and (:teamId is null or c.teamId = :teamId)
              and (:cohortId is null or t.cohortId = :cohortId)
              and (:categoryId is null or c.categoryId = :categoryId)""")
    List<Claim> findForQueue(Collection<ClaimStatus> statuses, Long teamId, Long cohortId, Long categoryId);

    @Query(value = "select nextval('claim_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
