package com.nirmaan.reimburse.claim;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClaimForm(
        @NotNull(message = "Choose a category") Long categoryId,
        @NotNull(message = "Enter the amount") @DecimalMin(value = "1.00", message = "Amount must be at least ₹1")
        @Digits(integer = 10, fraction = 2, message = "Enter a valid amount") BigDecimal claimedAmount,
        @NotNull(message = "Enter the expense date") @PastOrPresent(message = "Expense date cannot be in the future")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseDate,
        @NotBlank(message = "Enter the vendor name") @Size(max = 200) String vendorName,
        @Pattern(regexp = "^$|^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "GSTIN format looks wrong (e.g. 33ABCDE1234F1Z5)")
        String vendorGstin,
        @Size(max = 60) String invoiceNumber,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
        @NotBlank(message = "Explain how this expense benefits your project") @Size(max = 2000) String justification,
        Long version) {

    public static ClaimForm empty() {
        return new ClaimForm(null, null, null, null, null, null, null, null, null);
    }
}
