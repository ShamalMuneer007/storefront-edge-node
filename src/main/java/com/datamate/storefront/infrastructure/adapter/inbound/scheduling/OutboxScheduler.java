package com.datamate.storefront.infrastructure.adapter.inbound.scheduling;

import com.datamate.storefront.infrastructure.adapter.outbound.kitchen.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "storefront.outbox.relay-enabled", havingValue = "true")
class OutboxScheduler {

    private final OutboxRelay relay;

    OutboxScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${storefront.outbox.poll-interval-ms}")
    void relay() {
        relay.drain();
    }
}
