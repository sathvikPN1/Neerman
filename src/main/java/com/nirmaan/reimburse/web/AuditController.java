package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.common.audit.AuditEntity;
import com.nirmaan.reimburse.common.audit.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@PreAuthorize("hasAuthority('AUDIT_LOG_VIEW')")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/audit")
    public String list(@RequestParam(required = false) String entityType, @RequestParam(required = false) String action,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", audit.search(entityType, action, Math.max(0, page)));
        model.addAttribute("entityTypes", AuditEntity.values());
        model.addAttribute("entityType", entityType);
        model.addAttribute("action", action);
        return "audit/list";
    }
}
