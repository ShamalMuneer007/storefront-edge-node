package com.datamate.storefront.application.port.inbound;

public interface PlaceOrderUseCase {

    /**
     * Places the order once per terminal and idempotency key; a retry returns the committed order.
     *
     * @throws com.datamate.storefront.domain.exception.OutOfStockException if stock is insufficient
     * @throws com.datamate.storefront.application.exception.IdempotencyKeyReusedException if the key
     *     was used for a different basket
     */
    PlaceOrderResult place(PlaceOrderCommand command);
}
