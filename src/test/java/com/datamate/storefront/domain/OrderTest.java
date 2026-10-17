package com.datamate.storefront.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Currency AED = Currency.getInstance("AED");
    private static final FiscalNumber FISCAL = new FiscalNumber("AIR01", LocalDate.of(2026, 10, 17), 1);

    @Test
    void computesTotalsWithPerLineTaxRounding() {
        Order order = order(
                new OrderLine(product("BURGER", "12.50", "5", true), 3),
                new OrderLine(product("WATER", "0.35", "10", false), 1));

        assertThat(order.subtotal()).isEqualTo(Money.of("37.85", "AED"));
        assertThat(order.tax()).isEqualTo(Money.of("1.92", "AED"));
        assertThat(order.total()).isEqualTo(Money.of("39.77", "AED"));
    }

    @Test
    void requiresAtLeastOneLine() {
        assertThatThrownBy(this::order).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLinesPricedInAnotherCurrency() {
        Product imported = new Product("SODA", "Soda", Money.of("1.00", "USD"), TaxRate.of("0"), false);

        assertThatThrownBy(() -> order(new OrderLine(imported, 1))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void rejectsNonPositiveQuantities() {
        assertThatThrownBy(() -> new OrderLine(product("WATER", "1.00", "0", false), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectsOnlyLinesThatNeedTheKitchen() {
        OrderLine burger = new OrderLine(product("BURGER", "12.50", "5", true), 1);
        Order order = order(burger, new OrderLine(product("WATER", "1.00", "5", false), 2));

        assertThat(order.requiresKitchen()).isTrue();
        assertThat(order.kitchenLines()).containsExactly(burger);
        assertThat(order(new OrderLine(product("WATER", "1.00", "5", false), 1)).requiresKitchen()).isFalse();
    }

    @Test
    void isImmutable() {
        List<OrderLine> lines = new ArrayList<>(List.of(new OrderLine(product("WATER", "1.00", "5", false), 1)));
        Order order = new Order(UUID.randomUUID(), "T1", FISCAL, AED, lines, Instant.EPOCH);

        lines.clear();

        assertThat(order.lines()).hasSize(1);
        assertThatThrownBy(() -> order.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private Order order(OrderLine... lines) {
        return new Order(UUID.randomUUID(), "T1", FISCAL, AED, List.of(lines), Instant.EPOCH);
    }

    private static Product product(String sku, String price, String taxPercent, boolean kitchen) {
        return new Product(sku, sku, Money.of(price, "AED"), TaxRate.of(taxPercent), kitchen);
    }
}
