package com.nirmaan.reimburse.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long claimId;

    private Long batchId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMode mode;

    @Column(nullable = false)
    private String utr;

    @Column(nullable = false)
    private Long recordedBy;

    @Column(nullable = false)
    private Instant recordedAt;

    protected Payment() {
    }

    public Payment(Long claimId, BigDecimal amount, LocalDate paidOn, PaymentMode mode, String utr, Long recordedBy,
                   Instant recordedAt) {
        this.claimId = claimId;
        this.amount = amount;
        this.paidOn = paidOn;
        this.mode = mode;
        this.utr = utr;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getClaimId() {
        return claimId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public PaymentMode getMode() {
        return mode;
    }

    public String getUtr() {
        return utr;
    }

    public Long getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
