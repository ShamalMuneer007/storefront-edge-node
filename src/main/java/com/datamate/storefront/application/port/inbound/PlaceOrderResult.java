package com.datamate.storefront.application.port.inbound;

import com.datamate.storefront.domain.model.Order;

public record PlaceOrderResult(Order order, boolean replayed) {}
