package com.datamate.storefront.infrastructure.kitchen;

import com.datamate.storefront.application.port.IdGenerator;
import com.datamate.storefront.application.port.KitchenOutbox;
import com.datamate.storefront.domain.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcKitchenOutbox implements KitchenOutbox {

    static final String EVENT_TYPE = "KitchenTicketRequested";

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final IdGenerator ids;

    JdbcKitchenOutbox(JdbcClient jdbc, JsonMapper json, IdGenerator ids) {
        this.jdbc = jdbc;
        this.json = json;
        this.ids = ids;
    }

    @Override
    public void enqueue(Order order) {
        jdbc.sql("""
                        INSERT INTO outbox_event (id, aggregate_id, event_type, payload)
                        VALUES (:id, :aggregateId, :eventType, CAST(:payload AS jsonb))
                        """)
                .param("id", ids.next())
                .param("aggregateId", order.id())
                .param("eventType", EVENT_TYPE)
                .param("payload", json.writeValueAsString(Payload.of(order)))
                .update();
    }

    record Payload(UUID orderId, String fiscalNumber, Instant placedAt, List<Item> items) {

        static Payload of(Order order) {
            List<Item> items = order.kitchenLines().stream()
                    .map(line -> new Item(line.product().sku(), line.product().name(), line.quantity()))
                    .toList();
            return new Payload(order.id(), order.fiscalNumber().value(), order.placedAt(), items);
        }

        record Item(String sku, String name, int quantity) {}
    }
}
