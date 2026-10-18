package com.datamate.storefront.application.port.inbound;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public record PlaceOrderCommand(String terminalId, String idempotencyKey, List<Item> items) {

    public PlaceOrderCommand {
        if (terminalId == null || terminalId.isBlank()) {
            throw new IllegalArgumentException("terminalId is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        items = List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }
    }

    /** Quantities merged per SKU, in SKU order — the order in which stock rows are locked. */
    public SortedMap<String, Integer> quantitiesBySku() {
        SortedMap<String, Integer> merged = new TreeMap<>();
        items.forEach(item -> merged.merge(item.sku(), item.quantity(), Math::addExact));
        return merged;
    }

    public record Item(String sku, int quantity) {

        public Item {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku is required");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive: " + sku);
            }
        }
    }
}
