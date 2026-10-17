package com.datamate.storefront.application.port;

import com.datamate.storefront.domain.Order;
import java.util.Optional;
import java.util.UUID;

public interface Orders {

    void save(Order order);

    Optional<Order> findById(UUID id);
}
