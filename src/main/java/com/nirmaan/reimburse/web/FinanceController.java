package com.nirmaan.reimburse.web;

import com.nirmaan.reimburse.claim.ClaimQueueService;
import com.nirmaan.reimburse.claim.ClaimRow;
import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.common.time.TimeFormats;
import com.nirmaan.reimburse.payment.PaymentForm;
import com.nirmaan.reimburse.payment.PaymentMode;
import com.nirmaan.reimburse.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

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
        List<ClaimRow> rows = queues.paymentQueue();
        model.addAttribute("rows", rows);
        model.addAttribute("total", rows.stream().map(ClaimRow::approvedAmount).filter(Objects::nonNull)
                .reduce(Money.ZERO, BigDecimal::add));
        model.addAttribute("modes", PaymentMode.values());
        return "finance/payments";
    }

    /** The payment queue as a spreadsheet, e.g. to prepare a bank upload. */
    @GetMapping(value = "/finance/payments.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv() {
        StringBuilder csv = new StringBuilder("Claim,Team,Vendor,Category,Approved amount,Approved on,Priority\n");
        for (ClaimRow r : queues.paymentQueue()) {
            csv.append(String.join(",", cell(r.publicCode()), cell(r.teamName()), cell(r.vendorName()),
                    cell(r.categoryName()), r.approvedAmount() == null ? "" : r.approvedAmount().toPlainString(),
                    r.stageEnteredAt() == null ? "" : TimeFormats.date(r.stageEnteredAt()), r.priority() ? "yes" : "no"))
               .append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("nirmaan-payment-queue-" + TimeFormats.todayIst() + ".csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString());
    }

    /** Quote a CSV cell; a leading = + - @ is neutralised so spreadsheets don't run it as a formula. */
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value.matches("^[=+\\-@].*") ? "'" + value : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
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
