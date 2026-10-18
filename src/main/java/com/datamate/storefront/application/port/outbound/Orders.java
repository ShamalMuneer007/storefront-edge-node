package com.datamate.storefront.application.port.outbound;

import com.datamate.storefront.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

public interface Orders {

    void save(Order order);

    Optional<Order> findById(UUID id);
}
