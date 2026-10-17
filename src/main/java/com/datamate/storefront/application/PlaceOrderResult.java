package com.datamate.storefront.application;

import com.datamate.storefront.domain.Order;

public record PlaceOrderResult(Order order, boolean replayed) {}
