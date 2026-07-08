# order-inventory-system — the two-service reference

This is the running system the rest of module 13 builds on. Two Spring Boot services:

- **`inventory-service`** (port **8081**) — owns stock data in its own H2 database (`inventorydb`). Exposes `GET /api/inventory/{sku}` → `{ "sku": ..., "available": ... }`.
- **`order-service`** (port **8080**) — owns orders in its own separate H2 database (`orderdb`). Exposes `POST /api/orders`. To decide whether it can accept an order, it **calls inventory-service over HTTP** — it never reads inventory's database.

That's the whole point in one picture:

```
POST /api/orders {sku, quantity}
        │
        ▼
  order-service  ──HTTP GET /api/inventory/{sku}──▶  inventory-service
   (orderdb)     ◀────── {sku, available} ─────────    (inventorydb)
        │
        ▼
  available >= quantity ?  CONFIRMED (201)  :  REJECTED (409)
```

Two services, two databases, one network hop between them. Every later topic adds one thing to this hop — a timeout, a retry, a circuit breaker, a propagated token, a trace id.

## Prerequisites

- JDK 21+ (examples are built/verified with Temurin 25, targeting Java 21).
- Maven. If it's not on your PATH, use the one bundled with IntelliJ (`.../IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`) or run the modules from inside IntelliJ.

## Build and test everything

From this directory:

```bash
mvn -q test          # builds both services and runs each one's context test
```

Each service's test boots only itself — no network, no other service required.

## Run it end to end

Two services means two terminals. **Start inventory-service first** (order-service calls it):

```bash
# terminal 1
cd inventory-service
mvn -q spring-boot:run
```

```bash
# terminal 2
cd order-service
mvn -q spring-boot:run
```

Then drive it:

```bash
# stock query straight to inventory-service
curl -s localhost:8081/api/inventory/SKU-BOOK
# -> {"sku":"SKU-BOOK","available":5}

# an order that fits available stock -> 201 CONFIRMED
curl -s -i -X POST localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-BOOK","quantity":2}'
# -> HTTP/1.1 201 ... {"id":1,"sku":"SKU-BOOK","quantity":2,"status":"CONFIRMED"}

# an order larger than stock -> 409 REJECTED
curl -s -i -X POST localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-BOOK","quantity":99}'
# -> HTTP/1.1 409 ... {"...","status":"REJECTED"}
```

## See the failure this system is NOT yet protected against

Stop inventory-service (Ctrl-C in terminal 1) and place an order again:

```bash
curl -s -i -X POST localhost:8080/api/orders \
  -H 'Content-Type: application/json' -d '{"sku":"SKU-BOOK","quantity":1}'
```

order-service returns a `500` — its call to a dead downstream failed with nothing to catch it. Worse, if inventory-service were merely *slow* instead of down, that `POST` would **hang** (there is no timeout yet). That is the problem topics 09–11 exist to fix. This scaffold is deliberately the "everything is up and fast" happy path so the resilience topics have something to break.

## Seed data (in inventory-service)

| SKU | available |
|-----|-----------|
| `SKU-BOOK` | 5 |
| `SKU-PEN` | 100 |
| `SKU-SOLDOUT` | 0 |
