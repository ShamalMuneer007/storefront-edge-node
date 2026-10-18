package com.datamate.storefront.infrastructure.adapter.outbound.persistence;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Outbox reads and settlements for the relay; every method is a single autocommitted statement. */
@Repository
public class JdbcOutboxStore {

    private final JdbcClient jdbc;

    JdbcOutboxStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Leases up to {@code limit} due events by pushing {@code next_attempt_at} past the lease; oldest first. */
    public List<PendingEvent> claim(int limit, Duration lease) {
        return jdbc.sql("""
                        UPDATE outbox_event
                        SET attempts = attempts + 1,
                            next_attempt_at = now() + :leaseSeconds * INTERVAL '1 second'
                        WHERE id IN (SELECT id FROM outbox_event
                                     WHERE published_at IS NULL AND next_attempt_at <= now()
                                     ORDER BY next_attempt_at
                                     LIMIT :limit
                                     FOR UPDATE SKIP LOCKED)
                        RETURNING id, aggregate_id, payload::text AS payload, attempts, created_at
                        """)
                .param("leaseSeconds", lease.toSeconds())
                .param("limit", limit)
                .query((rs, row) -> new PendingEvent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("payload"),
                        rs.getInt("attempts"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list()
                .stream()
                .sorted(Comparator.comparing(PendingEvent::createdAt))
                .toList();
    }

    public void markPublished(List<UUID> ids) {
        jdbc.sql("UPDATE outbox_event SET published_at = now() WHERE id IN (:ids)")
                .param("ids", ids)
                .update();
    }

    public void retryIn(UUID id, Duration delay) {
        jdbc.sql("UPDATE outbox_event SET next_attempt_at = now() + :seconds * INTERVAL '1 second' WHERE id = :id")
                .param("seconds", delay.toSeconds())
                .param("id", id)
                .update();
    }

    public record PendingEvent(UUID id, UUID aggregateId, String payload, int attempts, OffsetDateTime createdAt) {}
}
