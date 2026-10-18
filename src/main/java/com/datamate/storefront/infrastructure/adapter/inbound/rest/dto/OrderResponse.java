package com.datamate.storefront.infrastructure.adapter.inbound.rest.dto;

import com.datamate.storefront.domain.model.Money;
import com.datamate.storefront.domain.model.Order;
import com.datamate.storefront.domain.model.OrderLine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String fiscalNumber,
        String terminalId,
        Instant placedAt,
        String currency,
        List<Line> lines,
        String subtotal,
        String tax,
        String total) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.fiscalNumber().value(),
                order.terminalId(),
                order.placedAt(),
                order.currency().getCurrencyCode(),
                order.lines().stream().map(Line::from).toList(),
                amount(order.subtotal()),
                amount(order.tax()),
                amount(order.total()));
    }

    private static String amount(Money money) {
        return money.amount().toPlainString();
    }

    public record Line(String sku, String name, int quantity, String unitPrice, String taxRate, String total) {

        static Line from(OrderLine line) {
            return new Line(
                    line.product().sku(),
                    line.product().name(),
                    line.quantity(),
                    amount(line.product().unitPrice()),
                    line.product().taxRate().percent().toPlainString(),
                    amount(line.gross()));
        }
    }
}
