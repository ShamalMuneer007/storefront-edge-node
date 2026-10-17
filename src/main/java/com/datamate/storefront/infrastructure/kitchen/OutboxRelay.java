package com.datamate.storefront.infrastructure.kitchen;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Delivers pending outbox events at least once without holding a transaction during dispatch:
 * a single-statement claim leases events by pushing {@code next_attempt_at} forward, dispatch
 * runs with no transaction open, and each outcome is settled with one more statement. Events
 * whose lease expires (crash mid-dispatch) are claimed again.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long MAX_BACKOFF_SECONDS = 60;

    private final JdbcClient jdbc;
    private final KitchenDisplay kitchen;
    private final int batchSize;
    private final Duration lease;

    public OutboxRelay(JdbcClient jdbc, KitchenDisplay kitchen, int batchSize, Duration lease) {
        this.jdbc = jdbc;
        this.kitchen = kitchen;
        this.batchSize = batchSize;
        this.lease = lease;
    }

    /** Returns the number of events delivered successfully. */
    public int relayPending() {
        int delivered = 0;
        for (ClaimedEvent event : claim()) {
            if (deliver(event)) {
                delivered++;
            }
        }
        return delivered;
    }

    private List<ClaimedEvent> claim() {
        return jdbc.sql("""
                        UPDATE outbox_event
                        SET attempts = attempts + 1,
                            next_attempt_at = now() + :leaseSeconds * INTERVAL '1 second'
                        WHERE id IN (SELECT id FROM outbox_event
                                     WHERE published_at IS NULL AND next_attempt_at <= now()
                                     ORDER BY next_attempt_at
                                     LIMIT :batchSize
                                     FOR UPDATE SKIP LOCKED)
                        RETURNING id, aggregate_id, payload::text AS payload, attempts, created_at
                        """)
                .param("leaseSeconds", lease.toSeconds())
                .param("batchSize", batchSize)
                .query((rs, row) -> new ClaimedEvent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("payload"),
                        rs.getInt("attempts"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list()
                .stream()
                .sorted(Comparator.comparing(ClaimedEvent::createdAt))
                .toList();
    }

    private boolean deliver(ClaimedEvent event) {
        try {
            kitchen.dispatch(new KitchenTicket(event.id(), event.aggregateId(), event.payload()));
        } catch (RuntimeException e) {
            long backoff = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(event.attempts(), 6));
            log.warn("Kitchen dispatch failed for event {} (attempt {}), retrying in {}s",
                    event.id(), event.attempts(), backoff, e);
            jdbc.sql("UPDATE outbox_event SET next_attempt_at = now() + :seconds * INTERVAL '1 second' WHERE id = :id")
                    .param("seconds", backoff)
                    .param("id", event.id())
                    .update();
            return false;
        }
        jdbc.sql("UPDATE outbox_event SET published_at = now() WHERE id = :id")
                .param("id", event.id())
                .update();
        return true;
    }

    private record ClaimedEvent(UUID id, UUID aggregateId, String payload, int attempts, OffsetDateTime createdAt) {}
}
