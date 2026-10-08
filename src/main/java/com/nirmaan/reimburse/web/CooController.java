package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.claim.ApprovalRow;
import com.nirmaan.reimburse.claim.BulkResult;
import com.nirmaan.reimburse.claim.ClaimAction;
import com.nirmaan.reimburse.claim.ClaimDecisionService;
import com.nirmaan.reimburse.claim.ClaimQueueService;
import com.nirmaan.reimburse.claim.TransitionRequest;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.common.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mobile-first approval screen for the COO and anyone holding CLAIM_APPROVE. */
@Controller
@PreAuthorize("hasAuthority('CLAIM_APPROVE')")
public class CooController {

    private final ClaimQueueService queues;
    private final ClaimDecisionService decisions;

    public CooController(ClaimQueueService queues, ClaimDecisionService decisions) {
        this.queues = queues;
        this.decisions = decisions;
    }

    @GetMapping("/coo/approvals")
    public String approvals(Model model) {
        List<ApprovalRow> rows = queues.approvalQueue();
        model.addAttribute("priorityRows", rows.stream().filter(r -> r.row().priority()).toList());
        model.addAttribute("otherRows", rows.stream().filter(r -> !r.row().priority()).toList());
        model.addAttribute("total", rows.stream().map(r -> r.row().claimedAmount()).reduce(Money.ZERO, BigDecimal::add));
        model.addAttribute("count", rows.size());
        return "coo/approvals";
    }

    /** One-tap decision. HTMX replaces the row with its outcome; plain POST redirects back. */
    @PostMapping("/coo/approvals/{id}/{decision}")
    public String decide(@PathVariable Long id, @PathVariable String decision,
                         @RequestParam(required = false) String comment,
                         @RequestParam(required = false) BigDecimal approvedAmount,
                         @RequestParam Long version, @RequestParam(required = false) String code,
                         HttpServletRequest request, Model model, RedirectAttributes redirect) {
        ClaimAction action = switch (decision) {
            case "approve" -> ClaimAction.APPROVE;
            case "reject" -> ClaimAction.REJECT;
            case "return" -> ClaimAction.RETURN;
            default -> throw new BusinessRuleException("Unknown decision " + decision);
        };
        decisions.act(id, action, new TransitionRequest(comment, approvedAmount, version, Map.of()));
        String outcome = switch (action) {
            case APPROVE -> approvedAmount == null ? "Approved" : "Approved " + Money.formatInr(approvedAmount);
            case REJECT -> "Rejected";
            default -> "Returned to team";
        };
        if (Htmx.isHtmx(request)) {
            model.addAttribute("outcome", outcome);
            model.addAttribute("code", code);
            return "coo/fragments :: decided";
        }
        redirect.addFlashAttribute("success", (code == null ? "Claim" : code) + ": " + outcome);
        return "redirect:/coo/approvals";
    }

    @PostMapping("/coo/approvals/bulk")
    public String bulkApprove(@RequestParam(name = "selected", required = false) List<String> selected,
                              RedirectAttributes redirect) {
        if (selected == null || selected.isEmpty()) {
            throw new BusinessRuleException("Select at least one claim to approve.");
        }
        Map<Long, Long> idToVersion = new LinkedHashMap<>();
        for (String s : selected) {
            String[] parts = s.split(":");
            idToVersion.put(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        }
        BulkResult result = decisions.bulkApprove(idToVersion);
        if (result.succeeded() > 0) {
            redirect.addFlashAttribute("success", "Approved " + result.succeeded() + " claim(s).");
        }
        if (!result.failures().isEmpty()) {
            redirect.addFlashAttribute("error", "Not approved — " + String.join("; ", result.failures()));
        }
        return "redirect:/coo/approvals";
    }
}
