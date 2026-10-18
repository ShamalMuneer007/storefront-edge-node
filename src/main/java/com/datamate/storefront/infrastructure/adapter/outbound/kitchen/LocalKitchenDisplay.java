package com.datamate.storefront.infrastructure.adapter.outbound.kitchen;

import com.datamate.storefront.application.port.outbound.IdGenerator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LocalKitchenDisplay implements KitchenDisplay {

    private final JdbcTemplate jdbc;
    private final IdGenerator ids;

    public LocalKitchenDisplay(JdbcTemplate jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public void dispatch(List<KitchenTicket> tickets) {
        jdbc.batchUpdate("""
                        INSERT INTO kitchen_ticket (id, order_id, event_id, payload)
                        VALUES (?, ?, ?, CAST(? AS jsonb))
                        ON CONFLICT (order_id) DO NOTHING
                        """,
                tickets.stream()
                        .map(ticket -> new Object[] {ids.next(), ticket.orderId(), ticket.eventId(), ticket.payloadJson()})
                        .toList());
    }
}
