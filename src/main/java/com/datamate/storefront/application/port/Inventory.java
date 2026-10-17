package com.datamate.storefront.application.port;

import com.datamate.storefront.domain.Product;

public interface Inventory {

    /**
     * Atomically decrements stock and returns the product's current terms.
     *
     * @throws com.datamate.storefront.domain.OutOfStockException if stock is insufficient
     * @throws com.datamate.storefront.domain.UnknownProductException if the SKU does not exist
     */
    Product reserve(String sku, int quantity);
}
