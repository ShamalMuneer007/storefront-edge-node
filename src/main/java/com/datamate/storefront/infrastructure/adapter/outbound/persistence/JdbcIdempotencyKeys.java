package com.datamate.storefront.infrastructure.adapter.outbound.persistence;

import com.datamate.storefront.application.port.outbound.IdempotencyKeys;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotencyKeys implements IdempotencyKeys {

    private final JdbcClient jdbc;

    JdbcIdempotencyKeys(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Claim> claim(String terminalId, String key, String requestHash, UUID orderId) {
        int inserted = jdbc.sql("""
                        INSERT INTO idempotency_key (terminal_id, idempotency_key, request_hash, order_id)
                        VALUES (:terminalId, :key, :requestHash, :orderId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("terminalId", terminalId)
                .param("key", key)
                .param("requestHash", requestHash)
                .param("orderId", orderId)
                .update();
        if (inserted == 1) {
            return Optional.empty();
        }
        return Optional.of(jdbc.sql("""
                        SELECT order_id, request_hash FROM idempotency_key
                        WHERE terminal_id = :terminalId AND idempotency_key = :key
                        """)
                .param("terminalId", terminalId)
                .param("key", key)
                .query((rs, row) -> new Claim(rs.getObject("order_id", UUID.class), rs.getString("request_hash")))
                .single());
    }
}
