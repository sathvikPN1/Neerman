package com.nirmaan.reimburse.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentView(BigDecimal amount, LocalDate paidOn, PaymentMode mode, String utr, String recordedByName,
                          Instant recordedAt) {
}
