package com.nirmaan.reimburse.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record PaymentForm(
        @NotNull(message = "Enter the payment date") @PastOrPresent(message = "Payment date cannot be in the future")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidOn,
        @NotNull(message = "Choose the payment mode") PaymentMode mode,
        @NotBlank(message = "The transaction reference / UTR is required") @Size(max = 40)
        @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "UTR can contain only letters, digits and dashes") String utr,
        Long version) {
}
