package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.category.CategoryService;
import com.nirmaan.reimburse.claim.ClaimAction;
import com.nirmaan.reimburse.claim.ClaimDecisionService;
import com.nirmaan.reimburse.claim.ClaimForm;
import com.nirmaan.reimburse.claim.ClaimPriorityService;
import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.claim.CommentService;
import com.nirmaan.reimburse.claim.TransitionRequest;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.common.web.FormErrors;
import com.nirmaan.reimburse.common.web.Htmx;
import com.nirmaan.reimburse.document.DocumentContent;
import com.nirmaan.reimburse.document.DocumentService;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.team.BudgetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
public class ClaimController {

    private final ClaimService claims;
    private final ClaimDecisionService decisions;
    private final ClaimPriorityService priority;
    private final CommentService comments;
    private final DocumentService documents;
    private final CategoryService categories;
    private final BudgetService budgets;

    public ClaimController(ClaimService claims, ClaimDecisionService decisions, ClaimPriorityService priority,
                           CommentService comments, DocumentService documents, CategoryService categories,
                           BudgetService budgets) {
        this.claims = claims;
        this.decisions = decisions;
        this.priority = priority;
        this.comments = comments;
        this.documents = documents;
        this.categories = categories;
        this.budgets = budgets;
    }

    // ------------------------------------------------------------ team: drafts

    @GetMapping("/claims/new")
    public String newForm(Model model) {
        return form(model, ClaimForm.empty(), null);
    }

    @PostMapping("/claims")
    public String create(@Valid @ModelAttribute("form") ClaimForm form, BindingResult errors, Model model,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("errors", FormErrors.of(errors));
            return form(model, form, null);
        }
        Long id = claims.createDraft(form);
        redirect.addFlashAttribute("success", "Draft saved. Now upload the invoice and payment proof.");
        return "redirect:/claims/" + id + "/edit";
    }

    @GetMapping("/claims/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        return form(model, claims.formFor(id), id);
    }

    @PostMapping("/claims/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ClaimForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("errors", FormErrors.of(errors));
            return form(model, form, id);
        }
        claims.updateDraft(id, form);
        redirect.addFlashAttribute("success", "Changes saved.");
        return "redirect:/claims/" + id + "/edit";
    }

    @PostMapping("/claims/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        claims.deleteDraft(id);
        redirect.addFlashAttribute("success", "Draft deleted.");
        return "redirect:/team";
    }

    private String form(Model model, ClaimForm form, Long claimId) {
        model.addAttribute("form", form);
        model.addAttribute("claimId", claimId);
        model.addAttribute("categories", categories.listActive());
        model.addAttribute("warnings", form.categoryId() == null ? List.of()
                : claims.warningsFor(form.categoryId(), form.claimedAmount()));
        Long teamId = CurrentUser.require().teamId();
        if (teamId != null) {
            model.addAttribute("budget", budgets.snapshotForCurrentUser(teamId));
        }
        if (claimId != null) {
            model.addAttribute("claim", claims.detail(claimId));
        }
        return "claims/form";
    }

    /** HTMX: live warnings while the team fills in the form. */
    @PostMapping("/claims/warnings")
    public String warnings(@RequestParam(required = false) Long categoryId,
                           @RequestParam(required = false) String claimedAmount, Model model) {
        BigDecimal amount = null;
        try {
            if (claimedAmount != null && !claimedAmount.isBlank()) {
                amount = new BigDecimal(claimedAmount.replace(",", "").trim());
            }
        } catch (NumberFormatException ignored) {
            // invalid numbers are reported by form validation on save
        }
        model.addAttribute("warnings", claims.warningsFor(categoryId, amount));
        return "claims/fragments :: warnings";
    }

    @PostMapping("/claims/{id}/documents")
    public String upload(@PathVariable Long id, @RequestParam DocumentType type, @RequestParam("file") MultipartFile file,
                         RedirectAttributes redirect) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessRuleException("Choose a file to upload.");
        }
        documents.upload(id, type, file.getOriginalFilename(), file.getBytes());
        redirect.addFlashAttribute("success", type.label() + " uploaded.");
        return "redirect:/claims/" + id + "/edit";
    }

    @PostMapping("/claims/{id}/documents/{docId}/delete")
    public String deleteDocument(@PathVariable Long id, @PathVariable Long docId, RedirectAttributes redirect) {
        documents.delete(docId);
        redirect.addFlashAttribute("success", "Document removed.");
        return "redirect:/claims/" + id + "/edit";
    }

    /** Documents are only ever served through this authorised endpoint. */
    @GetMapping("/documents/{docId}")
    public ResponseEntity<InputStreamResource> document(@PathVariable Long docId,
                                                        @RequestParam(defaultValue = "false") boolean download) {
        DocumentContent content = documents.open(docId);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(content.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(content.stream()));
    }

    // ------------------------------------------------------------ detail & workflow

    @GetMapping("/claims/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("claim", claims.detail(id));
        return "claims/detail";
    }

    @PostMapping("/claims/{id}/actions/{action}")
    public String act(@PathVariable Long id, @PathVariable String action,
                      @RequestParam(required = false) String comment,
                      @RequestParam(required = false) BigDecimal approvedAmount,
                      @RequestParam(required = false) Long version,
                      RedirectAttributes redirect) {
        ClaimAction claimAction = parseAction(action);
        decisions.act(id, claimAction, new TransitionRequest(comment, approvedAmount, version, Map.of()));
        redirect.addFlashAttribute("success", switch (claimAction) {
            case SUBMIT -> "Claim submitted. You'll be notified at every step.";
            case VERIFY -> "Claim verified and forwarded for approval.";
            case RETURN -> "Claim returned to the team.";
            case REJECT -> "Claim rejected.";
            case APPROVE -> approvedAmount == null ? "Claim approved." : "Claim partially approved.";
            case MARK_PAID -> "Claim marked as paid.";
            case REOPEN -> "Claim reopened.";
        });
        return claimAction == ClaimAction.SUBMIT ? "redirect:/team" : "redirect:/claims/" + id;
    }

    private static ClaimAction parseAction(String action) {
        try {
            ClaimAction a = ClaimAction.valueOf(action.toUpperCase(Locale.ROOT).replace('-', '_'));
            if (a == ClaimAction.MARK_PAID) {
                throw new BusinessRuleException("Use the payment form to mark a claim as paid.");
            }
            return a;
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown action: " + action);
        }
    }

    @PostMapping("/claims/{id}/comments")
    public String comment(@PathVariable Long id, @RequestParam String body, Model model, HttpServletRequest request) {
        comments.add(id, body);
        if (Htmx.isHtmx(request)) {
            model.addAttribute("comments", comments.forClaim(id));
            model.addAttribute("claimId", id);
            return "claims/fragments :: comments";
        }
        return "redirect:/claims/" + id + "#comments";
    }

    @PostMapping("/claims/{id}/priority")
    public String setPriority(@PathVariable Long id, @RequestParam boolean priority,
                              @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        this.priority.setPriority(id, priority, reason);
        redirect.addFlashAttribute("success", priority ? "Marked as priority." : "Priority removed.");
        return "redirect:/claims/" + id;
    }

    @PostMapping("/claims/{id}/priority-request")
    public String requestPriority(@PathVariable Long id, @RequestParam String reason, RedirectAttributes redirect) {
        priority.requestPriority(id, reason);
        redirect.addFlashAttribute("success", "Priority requested. Staff will review it.");
        return "redirect:/claims/" + id;
    }

    @PostMapping("/claims/{id}/priority-request/decline")
    public String declinePriority(@PathVariable Long id, @RequestParam(required = false) String note,
                                  RedirectAttributes redirect) {
        priority.declineRequest(id, note);
        redirect.addFlashAttribute("success", "Priority request declined.");
        return "redirect:/claims/" + id;
    }
}
