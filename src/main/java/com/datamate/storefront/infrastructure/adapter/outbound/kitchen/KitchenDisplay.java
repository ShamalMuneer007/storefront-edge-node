package com.datamate.storefront.infrastructure.adapter.outbound.kitchen;

import java.util.List;

/**
 * Delivery target for kitchen tickets. A failed call may have applied part of the batch and will
 * be redelivered, so implementations must be idempotent per ticket.
 */
public interface KitchenDisplay {

    void dispatch(List<KitchenTicket> tickets);
}
