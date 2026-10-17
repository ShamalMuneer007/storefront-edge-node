package com.datamate.storefront.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesToCurrencyScale() {
        assertThat(Money.of("12.5", "AED").amount()).hasToString("12.50");
        assertThat(Money.of("100", "JPY").amount()).hasToString("100");
    }

    @Test
    void rejectsAmountsFinerThanTheCurrencyAllows() {
        assertThatThrownBy(() -> Money.of("1.005", "AED")).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.of("100.5", "JPY")).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void additionIsExact() {
        assertThat(Money.of("0.10", "USD").plus(Money.of("0.20", "USD"))).isEqualTo(Money.of("0.30", "USD"));
    }

    @Test
    void refusesMixedCurrencyArithmetic() {
        assertThatThrownBy(() -> Money.of("1.00", "USD").plus(Money.of("1.00", "EUR")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void multipliesByQuantity() {
        assertThat(Money.of("2.35", "AED").times(3)).isEqualTo(Money.of("7.05", "AED"));
    }

    @Test
    void percentageRoundsHalfEven() {
        assertThat(Money.of("37.50", "AED").percentage(TaxRate.of("5"))).isEqualTo(Money.of("1.88", "AED"));
        assertThat(Money.of("0.25", "AED").percentage(TaxRate.of("10"))).isEqualTo(Money.of("0.02", "AED"));
        assertThat(Money.of("0.35", "AED").percentage(TaxRate.of("10"))).isEqualTo(Money.of("0.04", "AED"));
    }

    @Test
    void taxRateMustBeAPercentage() {
        assertThatThrownBy(() -> TaxRate.of("-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaxRate.of("100.01")).isInstanceOf(IllegalArgumentException.class);
    }
}
