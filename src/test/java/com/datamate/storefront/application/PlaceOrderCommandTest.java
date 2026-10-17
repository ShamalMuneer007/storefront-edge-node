package com.datamate.storefront.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.datamate.storefront.application.PlaceOrderCommand.Item;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceOrderCommandTest {

    @Test
    void mergesDuplicateSkusInLockOrder() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "T1", "K1", List.of(new Item("WATER", 1), new Item("BURGER", 2), new Item("WATER", 3)));

        assertThat(command.quantitiesBySku()).containsExactly(entry("BURGER", 2), entry("WATER", 4));
    }

    @Test
    void requiresItemsAndIdentity() {
        assertThatThrownBy(() -> new PlaceOrderCommand("T1", "K1", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaceOrderCommand(" ", "K1", List.of(new Item("WATER", 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaceOrderCommand("T1", "", List.of(new Item("WATER", 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
