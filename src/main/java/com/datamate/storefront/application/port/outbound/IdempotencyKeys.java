package com.datamate.storefront.application.port.outbound;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeys {

    /**
     * Claims the key for {@code orderId}. Returns empty if this call claimed it, otherwise the
     * committed claim. Blocks while a concurrent claim of the same key is still in flight.
     */
    Optional<Claim> claim(String terminalId, String key, String requestHash, UUID orderId);

    record Claim(UUID orderId, String requestHash) {}
}
