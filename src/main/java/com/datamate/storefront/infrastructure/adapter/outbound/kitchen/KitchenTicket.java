package com.datamate.storefront.infrastructure.adapter.outbound.kitchen;

import java.util.UUID;

/** A kitchen ticket request as delivered from the outbox; {@code eventId} is the dedupe key. */
public record KitchenTicket(UUID eventId, UUID orderId, String payloadJson) {}
