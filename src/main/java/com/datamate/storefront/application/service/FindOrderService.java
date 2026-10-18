package com.datamate.storefront.application.service;

import com.datamate.storefront.application.port.inbound.FindOrderUseCase;
import com.datamate.storefront.application.port.outbound.Orders;
import com.datamate.storefront.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

public final class FindOrderService implements FindOrderUseCase {

    private final Orders orders;

    public FindOrderService(Orders orders) {
        this.orders = orders;
    }

    @Override
    public Optional<Order> find(UUID id) {
        return orders.findById(id);
    }
}
