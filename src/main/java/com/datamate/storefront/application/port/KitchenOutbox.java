package com.datamate.storefront.application.port;

import com.datamate.storefront.domain.Order;

public interface KitchenOutbox {

    /** Records a kitchen ticket request in the current transaction; delivered asynchronously. */
    void enqueue(Order order);
}
