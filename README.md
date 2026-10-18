# Storefront Edge Node

Offline-capable ordering, inventory and kitchen dispatch engine for a single store.
Java 21 · Spring Boot 4 · PostgreSQL · Flyway. Architecture and trade-offs: [DESIGN.md](DESIGN.md).

## Prerequisites
- JDK 21
- Docker (Testcontainers and the local database)

## Test
```powershell
.\mvnw.cmd test
```
Runs:
- domain unit tests (no Spring, no database)
- architecture rules
- integration tests against a real PostgreSQL container, including the 50-thread stress test (`CheckoutConcurrencyTest`)

## Run

There are two ways to run it.

**Throwaway database with a demo catalog**
```powershell
.\mvnw.cmd spring-boot:test-run
```

**Persistent local database**
```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```
To load the demo catalog, add `-Dspring-boot.run.profiles=demo`.

API docs are at http://localhost:8080/swagger-ui.html.

## Load test

Run against a fresh local database with the app started (`docker compose down -v`, `docker compose up -d`, `.\mvnw.cmd spring-boot:run`):
```powershell
Get-Content load\seed.sql | docker compose exec -T postgres psql -U storefront -d storefront
java load\LoadTest.java http://localhost:8080 rush 3000 50 0.2     # 50 terminals fight over 1,000 units, 20% duplicate bursts
java load\LoadTest.java http://localhost:8080 mixed 20000 50 0.1   # multi-item baskets at sustained load
Get-Content load\invariants.sql | docker compose exec -T postgres psql -U storefront -d storefront
```
`invariants.sql` checks gap-free numbering, chronology, stock reconciliation, one order per key, totals and kitchen delivery.

## API
```powershell
curl -i -X POST http://localhost:8080/api/orders `
  -H "Content-Type: application/json" `
  -H "X-Terminal-Id: KIOSK-1" `
  -H "Idempotency-Key: 7f9c2e" `
  -d '{"items":[{"sku":"BURGER-CLASSIC","quantity":2}]}'
```

| Response | Meaning |
|---|---|
| `201` | Order placed |
| `200` + `Idempotent-Replayed: true` | Retry of a committed request (same body) |
| `409 out-of-stock` | Not enough stock |
| `422 unknown-product` / `idempotency-key-reused` | Rejected request |
| `400` | Invalid input |
| `503` + `Retry-After` | Node busy; retry with the same key |

`GET /api/orders/{id}` returns a placed order.

## Configuration (`application.properties`)

| Property | Default |
|---|---|
| `storefront.branch-code` | `AIR01` |
| `storefront.currency` | `AED` |
| `storefront.zone` | `Asia/Dubai` |
| `storefront.outbox.*` | relay toggle, poll interval, batch size, lease |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | datasource overrides |
