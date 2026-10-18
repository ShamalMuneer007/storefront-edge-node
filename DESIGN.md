# Design — Storefront Edge Node

## Architectural boundaries

Hexagonal (ports and adapters):

```
adapter/inbound (REST, scheduler) ──► port/inbound ◄── service ──► port/outbound ◄── adapter/outbound (JDBC, kitchen)
                                                          │
                                                          ▼
                                                        domain
```

- **domain** — immutable records (`Money`, `TaxRate`, `Product`, `OrderLine`, `Order`, `FiscalNumber`) that enforce currency safety, rounding, non-empty orders and the fiscal format.
- **application** — framework-free use cases (`port/inbound`), the dependencies they need (`port/outbound`), and their implementations (`service`). `Transactions` gives the service an atomic boundary without importing Spring.
- **infrastructure** — REST and scheduler adapters in; JDBC and kitchen adapters out; `config` is the only place services are wired.

`ArchitectureTest` fails the build if domain or application touch Spring, JDBC, Jakarta or Jackson, or if the REST adapter bypasses the use-case ports.

JDBC over JPA is deliberate: the critical operations are a conditional `UPDATE … RETURNING` and an `INSERT … ON CONFLICT … RETURNING`, which an ORM would hide. Rows map straight into domain records, so there is no entity model to keep in sync.

## Concurrency strategy

A checkout is one short READ COMMITTED transaction. Locks are always taken in the same order, so checkouts cannot deadlock: idempotency key → stock rows **sorted by SKU** → fiscal counter.

**Inventory — atomic conditional update.**
`UPDATE product SET stock = stock - :q WHERE sku = :sku AND stock >= :q RETURNING …`
The row lock serializes buyers of the same SKU only. PostgreSQL re-checks the predicate when a blocked update wakes, so the 11th buyer of 10 units gets `409`. `CHECK (stock >= 0)` backstops it. The statement also returns the authoritative price; client prices are never trusted.

| Alternative | Why not |
|---|---|
| `SELECT … FOR UPDATE` + `UPDATE` | Same locking, two round trips |
| Optimistic versioning | On one hot row under a rush, most attempts lose and retry |
| In-process queue/actor | Duplicates what the database guarantees; breaks with a second process |

**Fiscal numbering — transactional counter.**
`INSERT … ON CONFLICT (branch_code, business_date) DO UPDATE SET last_value = last_value + 1 RETURNING last_value`
The first order of the day creates the row, so the daily reset needs no scheduler.

*Guarantee:* per branch and business date (branch time zone), committed orders are numbered exactly 1..N.
- The increment shares the order's transaction, so a rollback returns the number. A `SEQUENCE` would leak gaps.
- The row lock is held until commit, and `placed_at` is read after it, so number order equals time order.

Gap-free numbering must serialize. The counter is therefore taken **last** and held for about 1 ms, per branch-day only. This assumes one node per branch; more would need a node code in the prefix.

**Overload.** Hikari pool of 20 with a 3 s acquisition timeout, `lock_timeout = 2s`, virtual threads. Overload returns `503 Retry-After`, never a hung request. Retrying is safe because of the idempotency key.

**Measured** (local Docker, 50 concurrent terminals, `load/`):
- **Rush on one hot SKU:** about 2,100 req/s, p99 about 130 ms.
- **Sustained multi-item baskets:** about 700 req/s, p99 about 200 ms.
- **Correctness:** across 46,000 orders, every invariant held and there were 0 deadlocks.

## Downstream reliability (kitchen)

**Transactional outbox.** The `outbox_event` row is written in the checkout transaction, so an order and its kitchen notification commit or vanish together. A crash right after commit leaves the event durable.

`OutboxRelay` never holds a transaction while talking to the kitchen:
1. **Claim** — one statement leases a batch (250): `UPDATE … SET attempts = attempts + 1, next_attempt_at = now() + lease WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED) RETURNING …`. Pushing `next_attempt_at` forward *is* the lease.
2. **Dispatch** to `KitchenDisplay` with no transaction open.
3. **Settle** — one statement marks the batch published.
   - If the batch fails, its events are retried one by one, so a single bad event cannot block the rest.
   - Failed events back off exponentially, capped at 60 s, and are never dropped.

The relay drains full batches back to back. At 700 orders/s, tickets landed 241 ms (p50) and 446 ms (p99) after checkout, and the backlog was empty at the end.

A relay that dies mid-dispatch loses its lease, and the event is re-claimed: **at-least-once** delivery. The consumer is idempotent: `kitchen_ticket` is `UNIQUE (order_id)` with `ON CONFLICT DO NOTHING`. Checkout never waits on the kitchen; an outage only grows the queue.

## Network retry handling

Terminals send an `Idempotency-Key` per logical order, scoped by `X-Terminal-Id`. The server generates the order's UUID **before** the transaction so the claim can reference it.

1. `INSERT INTO idempotency_key … ON CONFLICT DO NOTHING` is the first statement. Its FK to the order is `DEFERRABLE INITIALLY DEFERRED`, so a visible claim always points at a committed order.
2. A duplicate arriving milliseconds later **blocks on the unique index** until the original commits. It then returns the stored order with `200` and `Idempotent-Replayed: true`. The body is byte-identical to the original, and the replay touches neither stock nor the counter.
3. If the original rolled back, the duplicate simply executes. Failures are not cached; they consumed nothing.
4. A SHA-256 of the merged basket detects a key reused for a different order (`422`).

## Distributed sync readiness

- **IDs:** UUIDv7 generated in-app. They are collision-free across nodes without coordination and time-ordered for index locality. They play no part in fiscal numbering.
- **Business identity is branch-scoped** (`ORD-{BRANCH}-{DATE}-{SEQ}`), so HQ merges every node's orders without renumbering.
- **Orders are append-only facts.** Lines snapshot name, price and tax at sale time, so node → HQ sync is conflict-free inserts, idempotent on the order ID.
- **The outbox is the sync feed.** An HQ consumer reuses the same claim/lease/retry mechanics across arbitrarily long outages.
- **HQ → node (catalog, prices)** would be upserts keyed by SKU, last-writer-wins on `updated_at`.
  - Stock stays node-authoritative; HQ sends adjustments, not absolute values.
  - Catalog writes must go through `Money`: a price with more decimals than its currency allows is refused, never silently rounded.
- **Retention:** idempotency keys only need to outlive the retry window, and published outbox events only need to outlive the sync. A periodic purge is the remaining operational task.
