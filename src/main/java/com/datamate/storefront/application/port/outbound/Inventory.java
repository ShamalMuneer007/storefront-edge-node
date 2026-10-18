package com.datamate.storefront.application.port.outbound;

import com.datamate.storefront.domain.model.Product;

public interface Inventory {

    /**
     * Atomically decrements stock and returns the product's current terms.
     *
     * @throws com.datamate.storefront.domain.exception.OutOfStockException if stock is insufficient
     * @throws com.datamate.storefront.domain.exception.UnknownProductException if the SKU does not exist
     */
    Product reserve(String sku, int quantity);
}
