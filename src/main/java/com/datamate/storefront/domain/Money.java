package com.datamate.storefront.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public Money percentage(TaxRate rate) {
        BigDecimal exact = amount.multiply(rate.percent()).movePointLeft(2);
        return new Money(exact.setScale(amount.scale(), RoundingMode.HALF_EVEN), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public void requireCurrency(Currency expected) {
        if (!currency.equals(expected)) {
            throw new CurrencyMismatchException(expected, currency);
        }
    }

    private void requireSameCurrency(Money other) {
        other.requireCurrency(currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
