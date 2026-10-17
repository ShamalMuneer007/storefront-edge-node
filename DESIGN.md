# Design — Storefront Edge Node

## Architectural boundaries

```
web ──► application ──► domain
            ▲
infrastructure (JDBC adapters, outbox relay, Spring wiring)
```

- **domain** — `Money`, `TaxRate`, `Product`, `OrderLine`, `Order`, `FiscalNumber`. Immutable records, pure Java. Business invariants live here: currency safety, rounding, non-empty orders, positive quantities, fiscal number format.
- **application** — `PlaceOrderService` orchestrates one checkout through ports (`Inventory`, `FiscalSequence`, `IdempotencyKeys`, `Orders`, `KitchenOutbox`, `Transactions`, `IdGenerator`). Also framework-free; `Transactions` gives it an explicit atomic boundary without importing Spring.
- **infrastructure** — plain SQL via Spring `JdbcClient`; rows are mapped straight into domain records, so there is no persistence model to keep in sync. Flyway owns the schema.
- **web** — thin REST adapter; validation and ProblemDetail error mapping.

`ArchitectureTest` (ArchUnit) fails the build if domain or application touch Spring, JDBC, Jakarta or Jackson.

JDBC over JPA is deliberate: the two critical operations are a conditional `UPDATE … RETURNING` and an `INSERT … ON CONFLICT … RETURNING`, which an ORM would hide or fight.

## Concurrency strategy

A checkout is one short READ COMMITTED transaction. Locks are always taken in the same order, so concurrent checkouts cannot deadlock:

1. idempotency key (unique index)
2. stock rows, **sorted by SKU** (duplicate SKUs merged)
3. fiscal counter row

**Inventory — atomic conditional update.**
`UPDATE product SET stock = stock - :q WHERE sku = :sku AND stock >= :q RETURNING …`
The row lock serializes buyers of the same SKU only. PostgreSQL re-evaluates the predicate after a blocked update wakes, so the 11th buyer of 10 units sees 0 rows and gets `409 out-of-stock`. A `CHECK (stock >= 0)` backstops the invariant. The same statement returns the authoritative price and tax rate, so client-supplied prices are never trusted.

| Alternative | Why not |
|---|---|
| `SELECT … FOR UPDATE` then `UPDATE` | Same locking, two round trips, easy to get wrong |
| Optimistic versioning | Under a 50-terminal rush on one hot row, most attempts lose and retry — wasted work and latency spikes |
| In-process queue/actor | Duplicates what the database already guarantees and breaks when a second process appears |

**Fiscal numbering — transactional counter.**
`INSERT … ON CONFLICT (branch_code, business_date) DO UPDATE SET last_value = last_value + 1 RETURNING last_value`
This creates the day's row on first use, so the daily reset is automatic and needs no scheduler.

*Guarantee:* for each branch and business date (in the branch's time zone), the sequences of committed orders are exactly 1..N, with no gaps or duplicates.
- A number is consumed only if its order commits: the increment shares the order's transaction, and a rollback (out of stock, crash) undoes it. A database `SEQUENCE` would leak gaps.
- The row lock is held until commit, so numbers follow commit order.
- `placed_at` is read after the lock, so time order matches number order.

Gap-free numbering forces serialization. That is unavoidable, so the counter is taken **last** and held only for the final inserts (≈1 ms), and only per branch-day. It assumes one node per branch; several nodes per branch would need a node code in the prefix.

**Starvation and overload.**
- Hikari pool of 20 with a 3 s acquisition timeout.
- `lock_timeout = 2s` on every connection.
- Virtual threads for request handling.

Overload surfaces as `503 Retry-After`, never a hung thread. The retry is safe because of the idempotency key.

## Downstream reliability (kitchen)

**Transactional outbox.** The `outbox_event` row is inserted in the checkout transaction, so an order and its kitchen notification commit or vanish together. A crash right after commit leaves the event durable and pending.

`OutboxRelay` never holds a transaction while talking to the kitchen:

1. **Claim** — one statement: `UPDATE … SET attempts = attempts + 1, next_attempt_at = now() + lease WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED) RETURNING …`. Pushing `next_attempt_at` forward *is* the lease; there is no lock table.
2. **Dispatch** to `KitchenDisplay` with no transaction open.
3. **Settle** — one statement: mark published, or schedule a retry with exponential backoff capped at 60 s. Events are never dropped.

If the relay dies mid-dispatch, the lease expires and the event is re-claimed. That gives **at-least-once** delivery. The consumer is idempotent: the event ID travels with the ticket, and `kitchen_ticket` has `UNIQUE (order_id)` with `ON CONFLICT DO NOTHING`.

Checkout never waits on the kitchen, and a kitchen outage only grows the pending queue.

## Network retry handling

Terminals send an `Idempotency-Key` per logical order, scoped by `X-Terminal-Id`. The server generates the order's UUID **before** the transaction, so the claim can reference it.

1. `INSERT INTO idempotency_key … ON CONFLICT DO NOTHING` is the first statement. The FK to the order is `DEFERRABLE INITIALLY DEFERRED` and is checked at commit, so a visible claim always points at a committed order.
2. A duplicate arriving milliseconds later **blocks on the unique index** until the original commits. It then reads the committed claim and returns the stored order: `200`, `Idempotent-Replayed: true`, and a body byte-identical to the original. It touches neither stock nor the counter.
3. If the original rolled back, the duplicate simply executes. Failures are not cached; re-running them is harmless because they consumed nothing.
4. The request hash (SHA-256 of the merged SKU/quantity list) detects a key reused for a different basket (`422`).

## Distributed sync readiness

- **IDs:** UUIDv7 generated in-app. They are collision-free across thousands of nodes without coordination, and time-ordered for B-tree locality. They imply nothing about clock synchronization and play no part in fiscal numbering.
- **Business identity** is branch-scoped (`ORD-{BRANCH}-{DATE}-{SEQ}`, unique per branch-day), so HQ can merge every node's orders without renumbering.
- **Orders are append-only facts.** Lines snapshot name, price and tax rate at sale time, and corrections would be new documents. Node → HQ sync is therefore conflict-free inserts, idempotent on the order ID.
- **The outbox is the sync feed.** Adding an HQ consumer with an `OrderPlaced` event reuses the same claim/lease/retry mechanics and works across arbitrarily long outages.
- **HQ → node (catalog, prices)** would be upserts keyed by `product.id`/`sku`, last-writer-wins on `updated_at`. Stock stays node-authoritative, with HQ sending adjustments rather than absolute values.
- **Retention:** idempotency keys only need to outlive the terminals' retry window. A periodic purge of old rows is the remaining operational task.
