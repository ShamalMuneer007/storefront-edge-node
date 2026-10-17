package com.datamate.storefront.application;

import com.datamate.storefront.application.port.FiscalSequence;
import com.datamate.storefront.application.port.IdGenerator;
import com.datamate.storefront.application.port.IdempotencyKeys;
import com.datamate.storefront.application.port.IdempotencyKeys.Claim;
import com.datamate.storefront.application.port.Inventory;
import com.datamate.storefront.application.port.KitchenOutbox;
import com.datamate.storefront.application.port.Orders;
import com.datamate.storefront.application.port.Transactions;
import com.datamate.storefront.domain.FiscalNumber;
import com.datamate.storefront.domain.Order;
import com.datamate.storefront.domain.OrderLine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Lock order inside the transaction is fixed — idempotency key, stock rows by SKU, fiscal
 * counter — so concurrent checkouts cannot deadlock. The fiscal counter is taken last to keep
 * the only branch-wide serialization point as short as possible.
 */
public final class PlaceOrderService {

    private final Transactions transactions;
    private final IdempotencyKeys idempotencyKeys;
    private final Inventory inventory;
    private final FiscalSequence fiscalSequence;
    private final Orders orders;
    private final KitchenOutbox kitchenOutbox;
    private final IdGenerator ids;
    private final BranchSettings branch;
    private final Clock clock;

    public PlaceOrderService(
            Transactions transactions,
            IdempotencyKeys idempotencyKeys,
            Inventory inventory,
            FiscalSequence fiscalSequence,
            Orders orders,
            KitchenOutbox kitchenOutbox,
            IdGenerator ids,
            BranchSettings branch,
            Clock clock) {
        this.transactions = transactions;
        this.idempotencyKeys = idempotencyKeys;
        this.inventory = inventory;
        this.fiscalSequence = fiscalSequence;
        this.orders = orders;
        this.kitchenOutbox = kitchenOutbox;
        this.ids = ids;
        this.branch = branch;
        this.clock = clock;
    }

    public PlaceOrderResult place(PlaceOrderCommand command) {
        SortedMap<String, Integer> quantities = command.quantitiesBySku();
        String requestHash = hash(quantities);
        UUID orderId = ids.next();

        return transactions.inTransaction(() -> idempotencyKeys
                .claim(command.terminalId(), command.idempotencyKey(), requestHash, orderId)
                .map(existing -> replay(existing, requestHash))
                .orElseGet(() -> new PlaceOrderResult(placeNew(orderId, command.terminalId(), quantities), false)));
    }

    private PlaceOrderResult replay(Claim existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        Order order = orders.findById(existing.orderId())
                .orElseThrow(() -> new IllegalStateException("Claimed order missing: " + existing.orderId()));
        return new PlaceOrderResult(order, true);
    }

    private Order placeNew(UUID orderId, String terminalId, SortedMap<String, Integer> quantities) {
        List<OrderLine> lines = quantities.entrySet().stream()
                .map(item -> new OrderLine(inventory.reserve(item.getKey(), item.getValue()), item.getValue()))
                .toList();

        LocalDate businessDate = LocalDate.ofInstant(clock.instant(), branch.zone());
        int sequence = fiscalSequence.next(branch.code(), businessDate);
        Instant placedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Order order = new Order(
                orderId, terminalId, new FiscalNumber(branch.code(), businessDate, sequence),
                branch.currency(), lines, placedAt);
        orders.save(order);
        if (order.requiresKitchen()) {
            kitchenOutbox.enqueue(order);
        }
        return order;
    }

    private static String hash(SortedMap<String, Integer> quantities) {
        StringBuilder canonical = new StringBuilder();
        quantities.forEach((sku, quantity) -> canonical.append(sku).append(':').append(quantity).append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
