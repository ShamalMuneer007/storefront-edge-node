package com.datamate.storefront.infrastructure.adapter.inbound.rest.dto;

import com.datamate.storefront.application.port.inbound.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaceOrderRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {

    public PlaceOrderCommand toCommand(String terminalId, String idempotencyKey) {
        return new PlaceOrderCommand(
                terminalId,
                idempotencyKey,
                items.stream().map(item -> new PlaceOrderCommand.Item(item.sku(), item.quantity())).toList());
    }

    public record Item(@NotBlank @Size(max = 64) String sku, @Min(1) @Max(999) int quantity) {}
}
