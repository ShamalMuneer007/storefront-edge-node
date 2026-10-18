package com.datamate.storefront.domain.exception;

import java.util.Currency;

public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Expected " + expected.getCurrencyCode() + " but got " + actual.getCurrencyCode());
    }
}
