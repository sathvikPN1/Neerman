package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.claim.ClaimQueueService;
import com.nirmaan.reimburse.payment.PaymentForm;
import com.nirmaan.reimburse.payment.PaymentMode;
import com.nirmaan.reimburse.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasAuthority('PAYMENT_RECORD')")
public class FinanceController {

    private final ClaimQueueService queues;
    private final PaymentService payments;

    public FinanceController(ClaimQueueService queues, PaymentService payments) {
        this.queues = queues;
        this.payments = payments;
    }

    @GetMapping("/finance/payments")
    public String queue(Model model) {
        model.addAttribute("rows", queues.paymentQueue());
        model.addAttribute("modes", PaymentMode.values());
        return "finance/payments";
    }

    @PostMapping("/finance/payments/{claimId}")
    public String markPaid(@PathVariable Long claimId, @Valid @ModelAttribute PaymentForm form, BindingResult errors,
                           RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", errors.getFieldErrors().getFirst().getDefaultMessage());
            return "redirect:/finance/payments";
        }
        payments.markPaid(claimId, form);
        redirect.addFlashAttribute("success", "Payment recorded and the team has been notified.");
        return "redirect:/finance/payments";
    }
}
