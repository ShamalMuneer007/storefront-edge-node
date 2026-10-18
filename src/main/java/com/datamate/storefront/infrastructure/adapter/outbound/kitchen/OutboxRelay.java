package com.datamate.storefront.infrastructure.adapter.outbound.kitchen;

import com.datamate.storefront.infrastructure.adapter.outbound.persistence.JdbcOutboxStore;
import com.datamate.storefront.infrastructure.adapter.outbound.persistence.JdbcOutboxStore.PendingEvent;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delivers pending outbox events at least once without holding a transaction during dispatch:
 * a single-statement claim leases a batch, the batch is dispatched with no transaction open, and
 * the outcome is settled with one more statement. If the batch fails, events are retried one by
 * one so a single bad event cannot block the rest. Events whose lease expires (crash mid-dispatch)
 * are claimed again.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long MAX_BACKOFF_SECONDS = 60;

    private final JdbcOutboxStore outbox;
    private final KitchenDisplay kitchen;
    private final int batchSize;
    private final Duration lease;

    public OutboxRelay(JdbcOutboxStore outbox, KitchenDisplay kitchen, int batchSize, Duration lease) {
        this.outbox = outbox;
        this.kitchen = kitchen;
        this.batchSize = batchSize;
        this.lease = lease;
    }

    /**
     * Relays batches back to back while they come back full and fully delivered, so a rush is
     * drained at full speed. Stops on a partial batch or any failure, leaving the pause to the caller.
     */
    public int drain() {
        int total = 0;
        int delivered;
        do {
            delivered = relayPending();
            total += delivered;
        } while (delivered == batchSize);
        return total;
    }

    /** Relays one batch; returns the number of events delivered successfully. */
    public int relayPending() {
        List<PendingEvent> claimed = outbox.claim(batchSize, lease);
        if (claimed.isEmpty()) {
            return 0;
        }
        try {
            kitchen.dispatch(claimed.stream().map(OutboxRelay::ticket).toList());
        } catch (RuntimeException batchFailure) {
            return deliverOneByOne(claimed);
        }
        outbox.markPublished(claimed.stream().map(PendingEvent::id).toList());
        return claimed.size();
    }

    private int deliverOneByOne(List<PendingEvent> claimed) {
        int delivered = 0;
        for (PendingEvent event : claimed) {
            try {
                kitchen.dispatch(List.of(ticket(event)));
                outbox.markPublished(List.of(event.id()));
                delivered++;
            } catch (RuntimeException e) {
                scheduleRetry(event, e);
            }
        }
        return delivered;
    }

    private void scheduleRetry(PendingEvent event, RuntimeException cause) {
        long backoff = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(event.attempts(), 6));
        log.warn("Kitchen dispatch failed for event {} (attempt {}), retrying in {}s",
                event.id(), event.attempts(), backoff, cause);
        outbox.retryIn(event.id(), Duration.ofSeconds(backoff));
    }

    private static KitchenTicket ticket(PendingEvent event) {
        return new KitchenTicket(event.id(), event.aggregateId(), event.payload());
    }
}
