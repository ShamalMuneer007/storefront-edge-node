package com.datamate.storefront.domain;

import java.util.Objects;

public record OrderLine(Product product, int quantity) {

    public OrderLine {
        Objects.requireNonNull(product, "product");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + product.sku());
        }
    }

    public Money net() {
        return product.unitPrice().times(quantity);
    }

    public Money tax() {
        return net().percentage(product.taxRate());
    }

    public Money gross() {
        return net().plus(tax());
    }
}
