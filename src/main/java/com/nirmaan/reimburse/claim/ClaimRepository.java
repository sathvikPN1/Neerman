package com.nirmaan.reimburse.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Another claim with the same invoice number and the same vendor (by name or GSTIN). See V4 for the index. */
    @Query(value = """
            select * from claims c
            where c.id <> :excludeId
              and upper(regexp_replace(c.invoice_number, '[^A-Za-z0-9]', '', 'g')) = :invoiceKey
              and (upper(regexp_replace(c.vendor_name, '[^A-Za-z0-9]', '', 'g')) = :vendorKey
                   or (:gstin <> '' and c.vendor_gstin = :gstin))
            order by c.id
            limit 1""", nativeQuery = true)
    Optional<Claim> findSameInvoice(long excludeId, String invoiceKey, String vendorKey, String gstin);

    List<Claim> findByTeamIdAndClaimedAmountAndExpenseDateAndIdNot(Long teamId, BigDecimal claimedAmount,
                                                                   LocalDate expenseDate, Long id);

    @Query(value = "select nextval('claim_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
