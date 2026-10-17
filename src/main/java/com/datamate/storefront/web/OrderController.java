package com.datamate.storefront.web;

import com.datamate.storefront.application.PlaceOrderResult;
import com.datamate.storefront.application.PlaceOrderService;
import com.datamate.storefront.application.port.Orders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String TERMINAL_ID = "X-Terminal-Id";
    static final String REPLAYED = "Idempotent-Replayed";

    private final PlaceOrderService placeOrder;
    private final Orders orders;

    OrderController(PlaceOrderService placeOrder, Orders orders) {
        this.placeOrder = placeOrder;
        this.orders = orders;
    }

    @PostMapping
    ResponseEntity<OrderResponse> place(
            @RequestHeader(TERMINAL_ID) @NotBlank @Size(max = 64) String terminalId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResult result = placeOrder.place(request.toCommand(terminalId, idempotencyKey));
        OrderResponse body = OrderResponse.from(result.order());
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED, "true").body(body);
        }
        return ResponseEntity.created(URI.create("/api/orders/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    OrderResponse find(@PathVariable UUID id) {
        return orders.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
