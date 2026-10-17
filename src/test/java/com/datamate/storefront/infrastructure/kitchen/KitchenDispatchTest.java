package com.datamate.storefront.infrastructure.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import com.datamate.storefront.IntegrationTest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class KitchenDispatchTest extends IntegrationTest {

    private static final KitchenDisplay OFFLINE = ticket -> {
        throw new IllegalStateException("KDS offline");
    };

    @Autowired
    private LocalKitchenDisplay kitchen;

    @Autowired
    private OutboxRelay relay;

    @BeforeEach
    void seedCatalog() {
        addProduct("BURGER", "32.50", 10, true);
        addProduct("WATER", "8.00", 10, false);
    }

    @Test
    void checkoutCommitsWhileTheKitchenIsDown() {
        assertThat(placeBurger().statusCode()).isEqualTo(201);

        assertThat(relayTo(OFFLINE).relayPending()).isZero();

        assertThat(count("customer_order")).isOne();
        assertThat(count("kitchen_ticket")).isZero();
        assertThat(pendingEvents()).isOne();
        assertThat(jdbc.sql("SELECT next_attempt_at > now() FROM outbox_event").query(Boolean.class).single()).isTrue();
    }

    @Test
    void failedDeliveryIsRetriedUntilTheKitchenAccepts() {
        String fiscalNumber = body(placeBurger()).get("fiscalNumber").asString();
        relayTo(OFFLINE).relayPending();

        assertThat(relay.relayPending()).as("still backing off").isZero();
        makeEventsDue();
        assertThat(relay.relayPending()).isOne();

        assertThat(pendingEvents()).isZero();
        assertThat(jdbc.sql("SELECT payload ->> 'fiscalNumber' FROM kitchen_ticket").query(String.class).single())
                .isEqualTo(fiscalNumber);
    }

    @Test
    void eventLeasedByACrashedRelayIsRedeliveredAfterTheLeaseExpires() {
        placeBurger();
        jdbc.sql("UPDATE outbox_event SET attempts = 1, next_attempt_at = now() + INTERVAL '30 seconds'").update();

        assertThat(relay.relayPending()).as("lease still held").isZero();
        makeEventsDue();
        assertThat(relay.relayPending()).isOne();

        assertThat(count("kitchen_ticket")).isOne();
    }

    @Test
    void redeliveryCreatesASingleTicket() {
        placeBurger();
        List<KitchenTicket> delivered = new ArrayList<>();
        relayTo(ticket -> {
            kitchen.dispatch(ticket);
            delivered.add(ticket);
        }).relayPending();

        kitchen.dispatch(delivered.getFirst());

        assertThat(count("kitchen_ticket")).isOne();
    }

    @Test
    void dispatchRunsOutsideAnyTransaction() {
        placeBurger();
        AtomicReference<Boolean> inTransaction = new AtomicReference<>();

        relayTo(ticket -> inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive()))
                .relayPending();

        assertThat(inTransaction).hasValue(false);
    }

    @Test
    void ticketListsOnlyItemsThatNeedPreparation() {
        postOrder("T1", "K1", """
                {"items":[{"sku":"BURGER","quantity":2},{"sku":"WATER","quantity":1}]}""");
        relay.relayPending();

        List<String> skus = jdbc.sql("SELECT jsonb_array_elements(payload -> 'items') ->> 'sku' FROM kitchen_ticket")
                .query(String.class).list();
        assertThat(skus).containsExactly("BURGER");
    }

    @Test
    void ordersWithoutPreparedFoodSkipTheKitchen() {
        placeOrder("T1", "K1", "WATER", 1);

        assertThat(count("outbox_event")).isZero();
    }

    private HttpResponse<String> placeBurger() {
        return placeOrder("T1", "K1", "BURGER", 1);
    }

    private OutboxRelay relayTo(KitchenDisplay display) {
        return new OutboxRelay(jdbc, display, 50, Duration.ofSeconds(30));
    }

    private void makeEventsDue() {
        jdbc.sql("UPDATE outbox_event SET next_attempt_at = now() WHERE published_at IS NULL").update();
    }

    private long pendingEvents() {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL").query(Long.class).single();
    }
}
