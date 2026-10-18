package com.datamate.storefront.application.port.outbound;

import com.datamate.storefront.domain.model.Order;

public interface KitchenOutbox {

    /** Records a kitchen ticket request in the current transaction; delivered asynchronously. */
    void enqueue(Order order);
}
