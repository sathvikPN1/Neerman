package com.nirmaan.reimburse.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A reimbursement claim. Status changes only through {@link ClaimStateMachine}; that is why the
 * workflow mutators are package-private.
 */
@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String publicCode;

    @Column(nullable = false, updatable = false)
    private Long teamId;

    @Column(nullable = false)
    private Long submittedBy;

    @Column(nullable = false)
    private Long categoryId;

    private Long preApprovalId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal claimedAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false)
    private String vendorName;

    private String vendorGstin;

    private String invoiceNumber;

    private LocalDate invoiceDate;

    @Column(nullable = false)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Column(nullable = false)
    private boolean priority;

    private String priorityReason;

    @Column(nullable = false)
    private boolean priorityRequested;

    private String priorityRequestReason;

    private Long verifiedBy;

    private Long approvedBy;

    private String decisionReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant submittedAt;

    private Instant stageEnteredAt;

    private Instant verifiedAt;

    private Instant approvedAt;

    private Instant paidAt;

    @Version
    private long version;

    protected Claim() {
    }

    public Claim(String publicCode, Long teamId, Long submittedBy, Instant now) {
        this.publicCode = publicCode;
        this.teamId = teamId;
        this.submittedBy = submittedBy;
        this.createdAt = now;
        this.updatedAt = now;
        this.stageEnteredAt = now;
    }

    /** Team-editable fields. Only valid while the claim is DRAFT or RETURNED (checked by the service). */
    public void updateDetails(Long categoryId, BigDecimal claimedAmount, LocalDate expenseDate, String vendorName,
                              String vendorGstin, String invoiceNumber, LocalDate invoiceDate, String justification,
                              Instant now) {
        this.categoryId = categoryId;
        this.claimedAmount = claimedAmount;
        this.expenseDate = expenseDate;
        this.vendorName = vendorName;
        this.vendorGstin = vendorGstin;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.justification = justification;
        this.updatedAt = now;
    }

    // ---- workflow mutators (ClaimStateMachine only)

    void moveTo(ClaimStatus newStatus, Instant now) {
        this.status = newStatus;
        this.stageEnteredAt = now;
        this.updatedAt = now;
    }

    void markSubmitted(Long by, Instant now) {
        if (submittedAt == null) {
            submittedAt = now;
        }
        submittedBy = by;
        decisionReason = null;
        verifiedBy = null;
        verifiedAt = null;
        approvedAmount = null;
        approvedBy = null;
        approvedAt = null;
    }

    void markVerified(Long by, Instant now) {
        verifiedBy = by;
        verifiedAt = now;
    }

    void markApproved(Long by, BigDecimal amount, String reason, Instant now) {
        approvedBy = by;
        approvedAmount = amount;
        approvedAt = now;
        decisionReason = reason;
    }

    void markDecisionReason(String reason) {
        decisionReason = reason;
    }

    void markPaid(Instant now) {
        paidAt = now;
    }

    // ---- priority

    void setPriority(boolean priority, String reason) {
        this.priority = priority;
        this.priorityReason = priority ? reason : null;
        this.priorityRequested = false;
        this.priorityRequestReason = null;
    }

    void requestPriority(String reason) {
        this.priorityRequested = true;
        this.priorityRequestReason = reason;
    }

    void declinePriorityRequest() {
        this.priorityRequested = false;
    }

    public Long getId() {
        return id;
    }

    public String getPublicCode() {
        return publicCode;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getSubmittedBy() {
        return submittedBy;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getPreApprovalId() {
        return preApprovalId;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public String getVendorName() {
        return vendorName;
    }

    public String getVendorGstin() {
        return vendorGstin;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public String getJustification() {
        return justification;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public boolean isPriority() {
        return priority;
    }

    public String getPriorityReason() {
        return priorityReason;
    }

    public boolean isPriorityRequested() {
        return priorityRequested;
    }

    public String getPriorityRequestReason() {
        return priorityRequestReason;
    }

    public Long getVerifiedBy() {
        return verifiedBy;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getStageEnteredAt() {
        return stageEnteredAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public long getVersion() {
        return version;
    }
}
