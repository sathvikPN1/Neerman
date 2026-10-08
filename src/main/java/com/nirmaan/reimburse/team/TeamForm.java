package com.nirmaan.reimburse.team;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** budgetAmount may be left empty to use the program default from settings. */
public record TeamForm(
        Long id,
        @NotBlank @Size(max = 120) String name,
        @NotNull Long cohortId,
        @NotNull Program program,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal budgetAmount) {
}
