package com.nirmaan.reimburse.claim;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateKeysTest {

    @Test
    void ignoresCaseSpacesAndPunctuation() {
        assertThat(DuplicateKeys.of("INV-0042")).isEqualTo("INV0042");
        assertThat(DuplicateKeys.of(" inv / 0042 ")).isEqualTo("INV0042");
        assertThat(DuplicateKeys.of("Robu.in")).isEqualTo(DuplicateKeys.of("ROBU IN"));
    }

    @Test
    void keepsDigitsThatDiffer() {
        assertThat(DuplicateKeys.of("INV-0042")).isNotEqualTo(DuplicateKeys.of("INV-042"));
    }

    @Test
    void nothingToCompareIsNull() {
        assertThat(DuplicateKeys.of(null)).isNull();
        assertThat(DuplicateKeys.of("  ")).isNull();
        assertThat(DuplicateKeys.of("--/")).isNull();
    }
}
