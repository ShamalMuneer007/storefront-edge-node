package com.datamate.storefront;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "storefront.outbox.relay-enabled=false")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected JsonMapper json;

    @BeforeEach
    void resetDatabase() {
        jdbc.sql("TRUNCATE kitchen_ticket, outbox_event, idempotency_key, order_line, customer_order, fiscal_sequence, product")
                .update();
    }

    protected void addProduct(String sku, String price, int stock, boolean requiresPreparation) {
        jdbc.sql("""
                        INSERT INTO product (id, sku, name, unit_price, currency, tax_rate, requires_preparation, stock)
                        VALUES (gen_random_uuid(), :sku, :sku, :price, 'AED', 5.00, :requiresPreparation, :stock)
                        """)
                .param("sku", sku)
                .param("price", new BigDecimal(price))
                .param("requiresPreparation", requiresPreparation)
                .param("stock", stock)
                .update();
    }

    protected HttpResponse<String> placeOrder(String terminalId, String key, String sku, int quantity) {
        return postOrder(terminalId, key, """
                {"items":[{"sku":"%s","quantity":%d}]}""".formatted(sku, quantity));
    }

    protected HttpResponse<String> postOrder(String terminalId, String key, String body) {
        return send(HttpRequest.newBuilder(uri("/api/orders"))
                .header("Content-Type", "application/json")
                .header("X-Terminal-Id", terminalId)
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    protected HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    protected HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    protected URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    protected JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    protected int stock(String sku) {
        return jdbc.sql("SELECT stock FROM product WHERE sku = :sku").param("sku", sku).query(Integer.class).single();
    }

    protected long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    /** Runs {@code task} on {@code threads} threads released at the same instant. */
    protected static <T> List<T> concurrently(int threads, IntFunction<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        return task.apply(i);
                    }))
                    .toList();
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
