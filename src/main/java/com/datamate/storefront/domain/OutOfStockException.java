package com.datamate.storefront.domain;

public class OutOfStockException extends DomainException {

    private final String sku;

    public OutOfStockException(String sku) {
        super("Insufficient stock for " + sku);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
