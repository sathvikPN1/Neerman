package com.nirmaan.reimburse.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean allowed;

    @Column(nullable = false)
    private boolean requiresPreApproval;

    @Column(nullable = false)
    private boolean preApprovalRecommended;

    @Column(nullable = false)
    private boolean justificationRequired;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public Category(String name, boolean allowed, boolean requiresPreApproval, String description) {
        this.name = name;
        this.allowed = allowed;
        this.requiresPreApproval = requiresPreApproval;
        this.description = description;
    }

    public void update(String name, boolean allowed, boolean requiresPreApproval, boolean preApprovalRecommended,
                       boolean justificationRequired, String description, int sortOrder, boolean active) {
        this.name = name.trim();
        this.allowed = allowed;
        this.requiresPreApproval = requiresPreApproval;
        this.preApprovalRecommended = preApprovalRecommended;
        this.justificationRequired = justificationRequired;
        this.description = description.trim();
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public boolean isRequiresPreApproval() {
        return requiresPreApproval;
    }

    public boolean isPreApprovalRecommended() {
        return preApprovalRecommended;
    }

    public boolean isJustificationRequired() {
        return justificationRequired;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
