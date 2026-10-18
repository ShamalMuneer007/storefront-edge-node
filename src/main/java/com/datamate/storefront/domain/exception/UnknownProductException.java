package com.datamate.storefront.domain.exception;

public class UnknownProductException extends DomainException {

    private final String sku;

    public UnknownProductException(String sku) {
        super("Unknown product " + sku);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
