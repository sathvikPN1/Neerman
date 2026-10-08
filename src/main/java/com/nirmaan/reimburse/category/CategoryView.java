package com.nirmaan.reimburse.category;

public record CategoryView(Long id, String name, boolean allowed, boolean requiresPreApproval,
                           boolean preApprovalRecommended, boolean justificationRequired,
                           String description, int sortOrder, boolean active) {

    static CategoryView of(Category c) {
        return new CategoryView(c.getId(), c.getName(), c.isAllowed(), c.isRequiresPreApproval(),
                c.isPreApprovalRecommended(), c.isJustificationRequired(), c.getDescription(), c.getSortOrder(),
                c.isActive());
    }
}
