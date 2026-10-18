package com.datamate.storefront.domain.model;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Order(
        UUID id,
        String terminalId,
        FiscalNumber fiscalNumber,
        Currency currency,
        List<OrderLine> lines,
        Instant placedAt) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(fiscalNumber, "fiscalNumber");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(placedAt, "placedAt");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        lines.forEach(line -> line.product().unitPrice().requireCurrency(currency));
    }

    public Money subtotal() {
        return lines.stream().map(OrderLine::net).reduce(Money.zero(currency), Money::plus);
    }

    public Money tax() {
        return lines.stream().map(OrderLine::tax).reduce(Money.zero(currency), Money::plus);
    }

    public Money total() {
        return subtotal().plus(tax());
    }

    public List<OrderLine> kitchenLines() {
        return lines.stream().filter(line -> line.product().requiresPreparation()).toList();
    }

    public boolean requiresKitchen() {
        return lines.stream().anyMatch(line -> line.product().requiresPreparation());
    }
}
