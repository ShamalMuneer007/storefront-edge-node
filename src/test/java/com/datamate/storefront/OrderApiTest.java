package com.datamate.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class OrderApiTest extends IntegrationTest {

    @BeforeEach
    void seedCatalog() {
        addProduct("BURGER", "12.50", 10, true);
    }

    @Test
    void placesAnOrderPricedByTheServer() {
        HttpResponse<String> response = placeOrder("T1", "K1", "BURGER", 3);

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode order = body(response);
        assertThat(response.headers().firstValue("Location")).hasValue("/api/orders/" + order.get("id").asString());
        assertThat(order.get("fiscalNumber").asString()).matches("ORD-AIR01-\\d{8}-0001");
        assertThat(order.get("currency").asString()).isEqualTo("AED");
        assertThat(order.get("subtotal").asString()).isEqualTo("37.50");
        assertThat(order.get("tax").asString()).isEqualTo("1.88");
        assertThat(order.get("total").asString()).isEqualTo("39.38");
        assertThat(stock("BURGER")).isEqualTo(7);
    }

    @Test
    void retryReturnsTheOriginalOutcome() {
        HttpResponse<String> first = placeOrder("T1", "K1", "BURGER", 1);
        HttpResponse<String> retry = placeOrder("T1", "K1", "BURGER", 1);

        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(retry.body()).isEqualTo(first.body());
        assertThat(stock("BURGER")).isEqualTo(9);
    }

    @Test
    void sameKeyFromAnotherTerminalIsAnIndependentOrder() {
        placeOrder("T1", "K1", "BURGER", 1);

        assertThat(placeOrder("T2", "K1", "BURGER", 1).statusCode()).isEqualTo(201);
        assertThat(stock("BURGER")).isEqualTo(8);
    }

    @Test
    void reusingAKeyForADifferentOrderIsRejected() {
        placeOrder("T1", "K1", "BURGER", 1);

        HttpResponse<String> reused = placeOrder("T1", "K1", "BURGER", 2);

        assertThat(reused.statusCode()).isEqualTo(422);
        assertThat(body(reused).get("code").asString()).isEqualTo("idempotency-key-reused");
        assertThat(stock("BURGER")).isEqualTo(9);
    }

    @Test
    void rejectedOrdersConsumeNeitherStockNorFiscalNumbers() {
        HttpResponse<String> rejected = placeOrder("T1", "K1", "BURGER", 11);

        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(body(rejected).get("code").asString()).isEqualTo("out-of-stock");
        assertThat(body(rejected).get("sku").asString()).isEqualTo("BURGER");
        assertThat(stock("BURGER")).isEqualTo(10);

        HttpResponse<String> next = placeOrder("T1", "K2", "BURGER", 1);
        assertThat(body(next).get("fiscalNumber").asString()).endsWith("-0001");
    }

    @Test
    void rejectsUnknownProducts() {
        HttpResponse<String> response = placeOrder("T1", "K1", "NOPE", 1);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(body(response).get("code").asString()).isEqualTo("unknown-product");
    }

    @Test
    void validatesInput() {
        assertThat(placeOrder("T1", "K1", "BURGER", 0).statusCode()).isEqualTo(400);
        assertThat(postOrder("T1", "K2", "{\"items\":[]}").statusCode()).isEqualTo(400);
        assertThat(postOrder("T1", "K".repeat(65), "{\"items\":[{\"sku\":\"BURGER\",\"quantity\":1}]}").statusCode())
                .isEqualTo(400);

        HttpResponse<String> missingTerminal = send(HttpRequest.newBuilder(uri("/api/orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "K3")
                .POST(HttpRequest.BodyPublishers.ofString("{\"items\":[{\"sku\":\"BURGER\",\"quantity\":1}]}"))
                .build());
        assertThat(missingTerminal.statusCode()).isEqualTo(400);
        assertThat(stock("BURGER")).isEqualTo(10);
    }

    @Test
    void findsPlacedOrders() {
        HttpResponse<String> placed = placeOrder("T1", "K1", "BURGER", 2);

        HttpResponse<String> found = get("/api/orders/" + body(placed).get("id").asString());

        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.body()).isEqualTo(placed.body());
        assertThat(get("/api/orders/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
    }
}
