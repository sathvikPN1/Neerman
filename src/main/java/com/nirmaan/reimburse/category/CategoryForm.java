package com.nirmaan.reimburse.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Checkbox fields are wrappers: an unticked box sends nothing and binds as null (= false). */
public record CategoryForm(
        Long id,
        @NotBlank @Size(max = 120) String name,
        Boolean allowed,
        Boolean requiresPreApproval,
        Boolean preApprovalRecommended,
        Boolean justificationRequired,
        @NotBlank @Size(max = 1000) String description,
        Integer sortOrder,
        Boolean active) {

    static boolean on(Boolean b) {
        return Boolean.TRUE.equals(b);
    }
}
