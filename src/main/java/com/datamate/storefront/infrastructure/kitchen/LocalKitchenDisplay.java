package com.datamate.storefront.infrastructure.kitchen;

import com.datamate.storefront.application.port.IdGenerator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class LocalKitchenDisplay implements KitchenDisplay {

    private final JdbcClient jdbc;
    private final IdGenerator ids;

    public LocalKitchenDisplay(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public void dispatch(KitchenTicket ticket) {
        jdbc.sql("""
                        INSERT INTO kitchen_ticket (id, order_id, event_id, payload)
                        VALUES (:id, :orderId, :eventId, CAST(:payload AS jsonb))
                        ON CONFLICT (order_id) DO NOTHING
                        """)
                .param("id", ids.next())
                .param("orderId", ticket.orderId())
                .param("eventId", ticket.eventId())
                .param("payload", ticket.payloadJson())
                .update();
    }
}
