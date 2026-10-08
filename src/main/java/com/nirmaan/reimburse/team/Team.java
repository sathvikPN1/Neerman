package com.nirmaan.reimburse.team;

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

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long cohortId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Program program;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetAmount;

    @Column(nullable = false)
    private boolean priority;

    private String priorityReason;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long version;

    protected Team() {
    }

    public Team(String name, Long cohortId, Program program, BigDecimal budgetAmount) {
        this.name = name.trim();
        this.cohortId = cohortId;
        this.program = program;
        this.budgetAmount = budgetAmount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.trim();
    }

    public Long getCohortId() {
        return cohortId;
    }

    public void setCohortId(Long cohortId) {
        this.cohortId = cohortId;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public boolean isPriority() {
        return priority;
    }

    public String getPriorityReason() {
        return priorityReason;
    }

    public void setPriority(boolean priority, String reason) {
        this.priority = priority;
        this.priorityReason = priority ? reason : null;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getVersion() {
        return version;
    }
}
