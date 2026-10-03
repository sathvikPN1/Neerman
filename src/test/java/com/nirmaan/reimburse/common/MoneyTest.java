package com.nirmaan.reimburse.common;

import com.nirmaan.reimburse.common.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0|₹0.00",
            "5|₹5.00",
            "999.5|₹999.50",
            "1000|₹1,000.00",
            "12345.678|₹12,345.68",
            "125000|₹1,25,000.00",
            "200000|₹2,00,000.00",
            "500000|₹5,00,000.00",
            "1234567.89|₹12,34,567.89",
            "123456789|₹12,34,56,789.00",
            "-125000|-₹1,25,000.00"})
    void formatsWithIndianGrouping(String amount, String expected) {
        assertThat(Money.formatInr(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    void nullIsADash() {
        assertThat(Money.formatInr(null)).isEqualTo("—");
    }

    @Test
    void percentRoundsAndHandlesZeroTotals() {
        assertThat(Money.percent(new BigDecimal("50000"), new BigDecimal("200000"))).isEqualTo(25);
        assertThat(Money.percent(new BigDecimal("1"), BigDecimal.ZERO)).isZero();
        assertThat(Money.percent(null, BigDecimal.TEN)).isZero();
    }
}
