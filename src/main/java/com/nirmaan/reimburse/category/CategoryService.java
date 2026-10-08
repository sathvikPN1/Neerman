package com.nirmaan.reimburse.category;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.nirmaan.reimburse.common.audit.AuditService.detail;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final AuditService audit;

    public CategoryService(CategoryRepository categories, AuditService audit) {
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listActive() {
        return categories.findByActiveTrueOrderBySortOrderAscNameAsc().stream().map(CategoryView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listAll() {
        return categories.findAllByOrderBySortOrderAscNameAsc().stream().map(CategoryView::of).toList();
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categories.findById(id).orElseThrow(() -> NotFoundException.of("Category", id));
    }

    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Transactional
    public Long save(CategoryForm form) {
        categories.findByNameIgnoreCase(form.name().trim())
                .filter(c -> !c.getId().equals(form.id()))
                .ifPresent(c -> {
                    throw new BusinessRuleException("A category named \"" + form.name() + "\" already exists.");
                });
        if (!CategoryForm.on(form.allowed()) && CategoryForm.on(form.requiresPreApproval())) {
            throw new BusinessRuleException("A disallowed category cannot also require pre-approval.");
        }
        boolean created = form.id() == null;
        Category c = created
                ? new Category(form.name(), CategoryForm.on(form.allowed()), CategoryForm.on(form.requiresPreApproval()), form.description())
                : get(form.id());
        c.update(form.name(), CategoryForm.on(form.allowed()), CategoryForm.on(form.requiresPreApproval()),
                CategoryForm.on(form.preApprovalRecommended()), CategoryForm.on(form.justificationRequired()),
                form.description(), form.sortOrder() == null ? 0 : form.sortOrder(), CategoryForm.on(form.active()));
        c = categories.save(c);
        audit.record(AuditEntity.CATEGORY, c.getId(), created ? "CATEGORY_CREATED" : "CATEGORY_UPDATED",
                detail("name", c.getName(), "allowed", c.isAllowed(), "requiresPreApproval", c.isRequiresPreApproval(),
                        "active", c.isActive()));
        return c.getId();
    }
}
