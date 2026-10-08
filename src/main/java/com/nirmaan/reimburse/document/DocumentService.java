package com.nirmaan.reimburse.document;

import com.nirmaan.reimburse.claim.Claim;
import com.nirmaan.reimburse.claim.ClaimRepository;
import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.security.AccessPolicy;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.common.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

@Service
public class DocumentService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final ClaimDocumentRepository documents;
    private final ClaimRepository claims;
    private final StorageService storage;
    private final AuditService audit;
    private final Clock clock;

    public DocumentService(ClaimDocumentRepository documents, ClaimRepository claims, StorageService storage,
                           AuditService audit, Clock clock) {
        this.documents = documents;
        this.claims = claims;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    /** Upload a document to a claim the current user's team owns while it is DRAFT or RETURNED. */
    @Transactional
    public DocumentView upload(Long claimId, DocumentType type, String originalFilename, byte[] content) {
        AppUserPrincipal actor = CurrentUser.require();
        Claim claim = claims.findById(claimId).orElseThrow(() -> NotFoundException.of("Claim", claimId));
        AccessPolicy.requireMember(actor, claim.getTeamId());
        if (!claim.getStatus().isEditableByTeam()) {
            throw new BusinessRuleException("Documents can only be changed while the claim is a draft or returned.");
        }
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("The file is empty.");
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessRuleException("Files must be 10 MB or smaller.");
        }
        String contentType = FileTypeDetector.detect(content).orElseThrow(() ->
                new BusinessRuleException("Only PDF, JPG, PNG or HEIC files are accepted."));
        String key = "claims/" + claim.getId() + "/" + UUID.randomUUID();
        storage.put(key, content, contentType);
        deleteFromStorageOnRollback(key);

        String filename = sanitise(originalFilename);
        ClaimDocument doc = documents.save(new ClaimDocument(claim.getId(), type, key, filename, contentType,
                content.length, sha256(content), actor.id(), Instant.now(clock)));
        audit.record(AuditEntity.CLAIM, claim.getId(), "DOCUMENT_UPLOADED",
                detail("type", type, "file", filename, "sha256", doc.getSha256()));
        return DocumentView.of(doc);
    }

    @Transactional
    public void delete(Long documentId) {
        AppUserPrincipal actor = CurrentUser.require();
        ClaimDocument doc = documents.findById(documentId).orElseThrow(() -> NotFoundException.of("Document", documentId));
        Claim claim = claims.findById(doc.getClaimId()).orElseThrow();
        AccessPolicy.requireMember(actor, claim.getTeamId());
        if (!claim.getStatus().isEditableByTeam()) {
            throw new BusinessRuleException("Documents can only be changed while the claim is a draft or returned.");
        }
        documents.delete(doc);
        audit.record(AuditEntity.CLAIM, claim.getId(), "DOCUMENT_REMOVED",
                detail("type", doc.getType(), "file", doc.getOriginalFilename()));
        String key = doc.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<DocumentView> forClaim(Long claimId) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> NotFoundException.of("Claim", claimId));
        AccessPolicy.requireTeamView(CurrentUser.require(), claim.getTeamId());
        return documents.findByClaimIdOrderByUploadedAtAsc(claimId).stream().map(DocumentView::of).toList();
    }

    /** Stream a document to a user allowed to see its claim. */
    @Transactional(readOnly = true)
    public DocumentContent open(Long documentId) {
        ClaimDocument doc = documents.findById(documentId).orElseThrow(() -> NotFoundException.of("Document", documentId));
        Claim claim = claims.findById(doc.getClaimId()).orElseThrow();
        AccessPolicy.requireTeamView(CurrentUser.require(), claim.getTeamId());
        return new DocumentContent(doc.getOriginalFilename(), doc.getContentType(), doc.getSize(),
                storage.get(doc.getStorageKey()));
    }

    private void deleteFromStorageOnRollback(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        storage.delete(key);
                    }
                }
            });
        }
    }

    static String sanitise(String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
