package com.datamate.storefront.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FiscalNumberTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 17);

    @Test
    void formatsBranchDateAndPaddedSequence() {
        assertThat(new FiscalNumber("AIR01", DAY, 42).value()).isEqualTo("ORD-AIR01-20261017-0042");
    }

    @Test
    void growsBeyondFourDigitsInsteadOfWrapping() {
        assertThat(new FiscalNumber("AIR01", DAY, 10_000).value()).isEqualTo("ORD-AIR01-20261017-10000");
    }

    @Test
    void sequenceStartsAtOne() {
        assertThatThrownBy(() -> new FiscalNumber("AIR01", DAY, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void branchCodeIsRestrictedToUppercaseAlphanumerics() {
        assertThatThrownBy(() -> new FiscalNumber("air-01", DAY, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
