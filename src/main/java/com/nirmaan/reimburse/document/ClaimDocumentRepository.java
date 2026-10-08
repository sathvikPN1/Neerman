package com.nirmaan.reimburse.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {

    List<ClaimDocument> findByClaimIdOrderByUploadedAtAsc(Long claimId);

    List<ClaimDocument> findByClaimIdIn(Collection<Long> claimIds);

    boolean existsByClaimIdAndType(Long claimId, DocumentType type);
}
