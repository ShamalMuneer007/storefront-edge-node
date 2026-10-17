package com.datamate.storefront.infrastructure.kitchen;

/** Delivery target for kitchen tickets. Must tolerate the same ticket being delivered more than once. */
public interface KitchenDisplay {

    void dispatch(KitchenTicket ticket);
}
