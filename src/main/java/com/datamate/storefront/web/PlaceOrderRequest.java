package com.datamate.storefront.web;

import com.datamate.storefront.application.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

record PlaceOrderRequest(@NotEmpty @Size(max = 100) List<@Valid Item> items) {

    PlaceOrderCommand toCommand(String terminalId, String idempotencyKey) {
        return new PlaceOrderCommand(
                terminalId,
                idempotencyKey,
                items.stream().map(item -> new PlaceOrderCommand.Item(item.sku(), item.quantity())).toList());
    }

    record Item(@NotBlank @Size(max = 64) String sku, @Min(1) @Max(999) int quantity) {}
}
