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
