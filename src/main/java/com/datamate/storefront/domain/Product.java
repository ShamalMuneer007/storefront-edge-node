package com.datamate.storefront.domain;

import java.util.Objects;

public record Product(String sku, String name, Money unitPrice, TaxRate taxRate, boolean requiresPreparation) {

    public Product {
        requireText(sku, "sku");
        requireText(name, "name");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(taxRate, "taxRate");
        if (unitPrice.isNegative()) {
            throw new IllegalArgumentException("Unit price must not be negative: " + sku);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
