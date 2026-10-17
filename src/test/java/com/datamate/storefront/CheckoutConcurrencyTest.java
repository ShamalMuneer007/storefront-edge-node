package com.datamate.storefront;

import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CheckoutConcurrencyTest extends IntegrationTest {

    private static final int TERMINALS = 50;

    @Test
    void sellsExactlyTheAvailableStockUnderContention() throws Exception {
        addProduct("MEAL-BOX", "25.00", 10, false);

        List<HttpResponse<String>> responses =
                concurrently(TERMINALS, i -> placeOrder("T" + i, "K" + i, "MEAL-BOX", 1));

        assertThat(statuses(responses)).containsOnly(201, 409);
        assertThat(statuses(responses).stream().filter(s -> s == 201)).hasSize(10);
        assertThat(stock("MEAL-BOX")).isZero();
        assertGapFreeAndChronological(10);
    }

    @Test
    void concurrentDuplicatesOfOneRequestCreateASingleOrder() throws Exception {
        addProduct("MEAL-BOX", "25.00", 100, false);

        List<HttpResponse<String>> responses =
                concurrently(TERMINALS, i -> placeOrder("T1", "RETRY-1", "MEAL-BOX", 1));

        assertThat(statuses(responses).stream().filter(s -> s == 201)).hasSize(1);
        assertThat(statuses(responses).stream().filter(s -> s == 200)).hasSize(TERMINALS - 1);
        assertThat(responses.stream().map(HttpResponse::body).distinct()).hasSize(1);
        assertThat(stock("MEAL-BOX")).isEqualTo(99);
        assertGapFreeAndChronological(1);
    }

    @Test
    void retriesMixedWithContentionNeverDoubleSpend() throws Exception {
        addProduct("MEAL-BOX", "25.00", 5, false);

        List<Attempt> attempts = concurrently(TERMINALS, i ->
                new Attempt("K" + i % 10, placeOrder("T" + i % 10, "K" + i % 10, "MEAL-BOX", 1)));

        Map<String, List<Attempt>> byKey = attempts.stream().collect(groupingBy(Attempt::key));
        byKey.values().forEach(CheckoutConcurrencyTest::assertConsistentOutcome);
        assertThat(attempts.stream().filter(a -> a.response().statusCode() == 201)).hasSize(5);
        assertThat(stock("MEAL-BOX")).isZero();
        assertGapFreeAndChronological(5);
    }

    @Test
    void multiItemOrdersInOppositeOrderDoNotDeadlock() throws Exception {
        addProduct("BURGER", "32.50", 100, true);
        addProduct("WATER", "8.00", 100, false);

        List<HttpResponse<String>> responses = concurrently(TERMINALS, i -> postOrder("T" + i, "K" + i, i % 2 == 0
                ? """
                  {"items":[{"sku":"BURGER","quantity":1},{"sku":"WATER","quantity":1}]}"""
                : """
                  {"items":[{"sku":"WATER","quantity":1},{"sku":"BURGER","quantity":1}]}"""));

        assertThat(statuses(responses)).containsOnly(201);
        assertThat(stock("BURGER")).isEqualTo(50);
        assertThat(stock("WATER")).isEqualTo(50);
        assertGapFreeAndChronological(TERMINALS);
    }

    private void assertGapFreeAndChronological(int expectedOrders) {
        List<Integer> sequences = jdbc.sql("SELECT fiscal_sequence FROM customer_order ORDER BY fiscal_sequence")
                .query(Integer.class).list();
        assertThat(sequences).containsExactlyElementsOf(IntStream.rangeClosed(1, expectedOrders).boxed().toList());

        int counter = jdbc.sql("SELECT last_value FROM fiscal_sequence").query(Integer.class).single();
        assertThat(counter).isEqualTo(expectedOrders);

        List<OffsetDateTime> placedAt = jdbc.sql("SELECT placed_at FROM customer_order ORDER BY fiscal_sequence")
                .query(OffsetDateTime.class).list();
        assertThat(placedAt).isSorted();
    }

    private static void assertConsistentOutcome(List<Attempt> sameKey) {
        List<Integer> statuses = sameKey.stream().map(a -> a.response().statusCode()).toList();
        if (statuses.stream().allMatch(s -> s == 409)) {
            return;
        }
        assertThat(statuses).containsOnly(201, 200);
        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(sameKey.stream().filter(a -> a.response().statusCode() != 409).map(a -> a.response().body()).distinct())
                .hasSize(1);
    }

    private static List<Integer> statuses(List<HttpResponse<String>> responses) {
        return responses.stream().map(HttpResponse::statusCode).toList();
    }

    private record Attempt(String key, HttpResponse<String> response) {}
}
