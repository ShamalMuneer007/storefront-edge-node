package com.datamate.storefront.application.port.inbound;

import com.datamate.storefront.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

public interface FindOrderUseCase {

    Optional<Order> find(UUID id);
}
