# Common mistakes — Microservices

Real distributed bugs from real systems, each with the broken code, what goes wrong, and the fix. These are worse than a typical bug: they hide on the happy path where every service is up and fast, and only surface under partial failure — the one moment you can't reproduce on your laptop. "It worked in the demo" is exactly how they reach production.

---

## 1. An outbound call with no timeout

```java
// order-service calling inventory-service
StockResponse stock = restClient.get()
        .uri("/api/inventory/{sku}", sku)
        .retrieve()
        .body(StockResponse.class);   // no connect/read timeout configured
```

**What goes wrong:** the default request factory has effectively no read timeout. When `inventory-service` gets slow (GC pause, overloaded, a lock), this call blocks the request thread for as long as it takes. Under load, every thread in the pool ends up parked on the same slow downstream; the pool exhausts; `order-service` stops serving *everyone* — including endpoints that never touch inventory. A slow downstream takes you down harder than a dead one, precisely because a dead one fails fast and a slow one makes you wait.

**Fix:** bound every outbound call. A timeout that fires is a controlled failure you can retry or fall back from; a hang is not.

```java
SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
f.setConnectTimeout(Duration.ofMillis(500));
f.setReadTimeout(Duration.ofSeconds(2));
RestClient restClient = RestClient.builder().requestFactory(f).baseUrl(baseUrl).build();
```

---

## 2. Retrying a non-idempotent operation

```java
@Retryable(maxAttempts = 3)                 // retry on any failure
public String reserve(String sku, int qty) {
    return inventory.reserve(sku, qty);      // POST that decrements stock
}
```

**What goes wrong:** the first attempt *succeeds* on the server, but the response is lost (the read times out, the connection drops). The client sees a failure and retries — reserving the stock again. A timeout never tells you whether the work happened; retrying a "reserve"/"charge"/"increment" turns one operation into two or three. Double-reserved stock, double-charged cards.

**Fix:** retries are only safe on **idempotent** operations. Make the write idempotent with a stable **idempotency key** the server dedupes on, then retrying the same key is a no-op.

```java
// client generates the key ONCE per logical request and reuses it on every retry
inventory.reserve(idempotencyKey, sku, qty);
// server: if this key was already processed, return the stored result instead of re-reserving
```

---

## 3. Retry storm — retrying everything, immediately, forever

```java
@Retryable(maxAttempts = 5)                 // no backoff, no jitter, retries 4xx too
public Stock getStock(String sku) { return inventory.getStock(sku); }
```

**What goes wrong:** two failures compound. (1) **No backoff:** when the downstream is struggling, five instant retries per caller multiply the load on the thing that's already failing — a *retry storm* that turns a blip into an outage. (2) **Synchronized retries:** every caller retries at the same instant (a thundering herd) because there's no jitter. (3) **Retrying 4xx:** a `400`/`404` is a permanent answer — retrying it wastes calls and never succeeds.

**Fix:** retry only *transient* failures (timeouts, `503`, connection errors — never `4xx`), with **exponential backoff + jitter**, and a small cap.

```java
@Retryable(retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
           maxAttempts = 3,
           backoff = @Backoff(delay = 200, multiplier = 2, random = true)) // jitter on
public Stock getStock(String sku) { return inventory.getStock(sku); }
```

---

## 4. Two services sharing one database

```java
// order-service AND inventory-service both point at the same schema
spring.datasource.url=jdbc:postgresql://db:5432/shop   // in BOTH services
// order-service reaches straight into inventory's table:
jdbc.query("UPDATE stock SET qty = qty - ? WHERE sku = ?", qty, sku);
```

**What goes wrong:** a shared database is a shared, invisible contract. `inventory-service` can't change its `stock` table without silently breaking `order-service`; a migration in one deploys a bug in the other. They can't scale, be released, or fail independently — you have two processes but *one* coupled unit. This is the classic distributed monolith: all the pain of distribution, none of the independence.

**Fix:** each service owns its data privately; others reach it only through its **API**. If `order-service` needs stock, it *asks* `inventory-service` — it never touches its tables.

```java
// order-service asks; it does not reach in
StockResponse stock = inventoryClient.getStock(sku);
```

---

## 5. Chatty calls — a network round-trip in a loop

```java
List<LineDto> lines = new ArrayList<>();
for (String sku : cart.skus()) {                     // 50 items in the cart
    StockResponse s = inventoryClient.getStock(sku); // 50 sequential HTTP calls
    lines.add(new LineDto(sku, s.available()));
}
```

**What goes wrong:** the in-process `for` loop that was free in the monolith is now 50 network round-trips, in series. At 30 ms each that's 1.5 s of latency, and each call is a new chance to fail — the probability the *whole* page renders drops geometrically with the number of hops. This is the network N+1.

**Fix:** design a **batch/aggregate** endpoint, or fetch concurrently. One coarse call beats fifty fine-grained ones across a network boundary.

```java
// one call for all skus
Map<String, Integer> stock = inventoryClient.getStock(cart.skus()); // GET /api/inventory?sku=a,b,c
// or, if you must call per-sku, do it in parallel and bound the total time
```

---

## 6. Dual write — commit the DB, then publish the event

```java
orderRepository.save(order);        // write #1: our database  (commits)
kafkaTemplate.send("orders", event); // write #2: the broker   (what if we crash here?)
```

**What goes wrong:** these are two systems, and you can't commit them atomically without a distributed transaction (which you're avoiding). Crash *after* the DB commit and *before* the publish → the order exists but no event is ever sent; downstream services never hear about it (**lost event**). Publish first, then fail the DB write → you announced an order that doesn't exist (**phantom event**).

**Fix:** the **transactional outbox** — write the event into an `outbox` table in the *same* local transaction as the business row, and let a separate relay publish it.

```java
@Transactional
public void placeOrder(Order o) {
    orderRepository.save(o);
    outboxRepository.save(new OutboxEvent("OrderPlaced", o.getId())); // same local tx: both or neither
}
// a poller/CDC relay publishes unsent outbox rows and marks them sent (at-least-once)
```

---

## 7. Assuming atomicity across services (a saga with no compensation)

```java
inventory.reserve(sku, qty);   // step 1 commits in inventory-service
payment.charge(card, cents);   // step 2 throws — card declined
order.create(sku, qty);        // never runs... and step 1 is NOT rolled back
```

**What goes wrong:** there is no `@Transactional` spanning three services. When step 2 fails, step 1 already **committed** in another database — nothing rolls it back. You've leaked a stock reservation (and if the order aborts after a charge, you've charged for nothing). Expecting a rollback that can't happen is the "charged for nothing" bug.

**Fix:** a **saga** — each step has a **compensating** action, and on failure you run the compensations for the already-completed steps in reverse. Compensation is a *semantic undo* (release/refund), not a rollback.

```java
try { reserve(); charge(); create(); }
catch (Exception e) { /* reverse completed steps */ releaseReservation(); /* refund() if charged */ throw; }
```

---

## 8. A fallback that isn't actually a fallback

```java
@CircuitBreaker(name = "inventory", fallbackMethod = "fallback")
public Stock getStock(String sku) { return inventory.getStock(sku); }

public Stock fallback(String sku, Throwable t) {
    return pricing.lookupStock(sku);   // ANOTHER remote call, from inside the fallback
}
```

**What goes wrong:** the breaker opened because the network/a dependency is in trouble — and the fallback makes *another* remote call into that same trouble. It hangs or fails too, so the breaker bought you nothing. A fallback that can fail the same way as the primary is not a fallback.

**Fix:** a fallback must be **local and fast** — a cached value, a safe default, an empty result, or a clean degraded error. It must not depend on the thing that just failed.

```java
public Stock fallback(String sku, Throwable t) {
    return Stock.unknown(sku);   // degrade: no network, always returns instantly
}
```

---

## 9. A synchronous call to a non-critical downstream

```java
public Order placeOrder(...) {
    Order o = save(order);
    emailClient.sendConfirmation(o);   // synchronous, in the request path
    return o;
}
```

**What goes wrong:** placing an order now *requires* the email service to be up and fast. If email is slow, the customer waits; if email is down, the order **fails** — even though a confirmation email is not essential to placing an order. You've coupled a critical operation's availability to a non-critical one's (temporal coupling), and multiplied your failure surface.

**Fix:** if the downstream isn't required for the response, **publish an event** and let it react asynchronously. The order succeeds regardless of email's health.

```java
Order o = save(order);
events.publish(new OrderPlaced(o.getId()));  // email-service consumes this on its own time
return o;
```

---

## 10. A breaking change to a published API

```java
// v1 response other services already parse:  {"sku":"A","available":5}
public record StockResponse(String sku, int availableUnits) {}  // renamed available -> availableUnits
```

**What goes wrong:** you renamed (or removed, or retyped) a field every consumer reads. The moment this deploys, callers deserializing `available` get `0`/null and make wrong decisions — and you can't roll every consumer at the same instant across a distributed system. A breaking change across a network boundary is any change that an existing caller can't tolerate.

**Fix:** evolve **additively** — add the new field, keep the old one through a deprecation window, or version the endpoint. Never rename/remove/retype a field callers depend on in place.

```java
public record StockResponse(String sku, int available, Integer availableUnits) {} // add, don't replace
```

---

## 11. A consumer that isn't a tolerant reader

```java
ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true); // the default is already true
StockResponse s = mapper.readValue(json, StockResponse.class); // throws when provider ADDS a field
```

**What goes wrong:** the provider makes a *safe, additive* change (adds a new field). Your consumer, configured to fail on unknown properties, throws on a change that was supposed to be backward-compatible. You've made the provider unable to evolve without breaking you — the mirror image of mistake 10.

**Fix:** be a **tolerant reader** — ignore fields you don't recognize, read only what you need.

```java
@JsonIgnoreProperties(ignoreUnknown = true)          // or set FAIL_ON_UNKNOWN_PROPERTIES=false
public record StockResponse(String sku, int available) {}
```

---

## 12. Dropping the correlation id across the hop

```java
// order-service logs with a trace id, then calls inventory with a bare client
log.info("placing order");                       // [traceId=abc123] ...
restClient.get().uri("/api/inventory/{sku}", sku).retrieve()...; // no trace header propagated
```

**What goes wrong:** `inventory-service` logs the same request under a *different* (or no) trace id. When something breaks, you can't stitch the two services' logs into one request's story — you're grepping timestamps across machines. In a distributed system, a request that can't be followed across hops is nearly undebuggable.

**Fix:** propagate the trace/correlation id on every outbound call (Micrometer Tracing / an interceptor does this automatically once configured), so one request is one trace end to end.

```java
// with micrometer-tracing on the classpath and an instrumented RestClient, the
// traceparent / X-B3 headers are added automatically; downstream continues the same trace
```

---

## 13. Not propagating the caller's identity downstream

```java
// order-service is authenticated, but calls inventory with a fresh, tokenless client
restClient.get().uri("/api/inventory/{sku}", sku).retrieve()...; // no Authorization header
```

**What goes wrong:** the incoming JWT authenticated the user *at order-service*, but the downstream call carries no credential. `inventory-service` sees an anonymous request — so either it's wide open (and trusts anyone who can reach it), or it correctly rejects with `401` and the feature breaks. Identity does not travel by magic; it stops at the first hop unless you carry it.

**Fix:** propagate the bearer token on the outbound call (relay the incoming token, or use a client-credentials token for service-to-service), so the downstream can authenticate and authorize the *real* caller.

```java
String bearer = /* current request's Authorization token */;
restClient.get().uri("/api/inventory/{sku}", sku)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
        .retrieve()...;
```

---

## 14. A message consumer that isn't idempotent

```java
@KafkaListener(topics = "orders")
public void on(OrderPlaced e) {
    inventory.reserve(e.sku(), e.qty());   // acts every time the message is delivered
}
```

**What goes wrong:** messaging is **at-least-once** — the broker can redeliver the same message (consumer restarted before committing its offset, a rebalance, a retry). A consumer that acts unconditionally reserves the stock twice for one order. The outbox and every broker guarantee *at-least-once*; it's the consumer's job to absorb the duplicate.

**Fix:** make the consumer **idempotent** — dedupe on the event's id (a `processed_events` table or a unique constraint) so a redelivery is a no-op.

```java
@KafkaListener(topics = "orders")
public void on(OrderPlaced e) {
    if (!processed.markIfNew(e.eventId())) return;   // already handled this event
    inventory.reserve(e.sku(), e.qty());
}
```

---

## 15. One slow dependency exhausts the shared thread pool

```java
// every outbound call shares the same Tomcat worker threads; no isolation
StockResponse s = inventoryClient.getStock(sku);
PriceResponse  p = pricingClient.getPrice(sku);   // if pricing hangs, it parks worker threads
```

**What goes wrong:** `order-service` calls two dependencies on the same pool of request-handling threads. When `pricing` gets slow, its calls pile up and consume every worker thread — so requests that only need `inventory` (perfectly healthy) also can't get a thread. One sick dependency sinks the whole service. This is why a timeout alone isn't enough.

**Fix:** a **bulkhead** — give each dependency its own bounded pool/permit count, so a slow one can only exhaust *its* compartment. Add a rate limiter to cap inbound load.

```java
@Bulkhead(name = "pricing", type = Bulkhead.Type.THREADPOOL)  // pricing gets its own compartment
public PriceResponse getPrice(String sku) { return pricingClient.getPrice(sku); }
```

---

## 16. The distributed monolith — services that can't deploy independently

```text
Deploying "add a field to the order" requires, in lockstep:
  order-service  + inventory-service + shipping-service  all released together,
  or the system is broken between deploys.
```

**What goes wrong:** the whole justification for microservices is independent deployability. If a routine change forces a coordinated, all-at-once release of several services — because they share a database, share DTO classes as a compile dependency, or form a synchronous call chain with no tolerance for version skew — you have a distributed monolith: microservices' operational cost with a monolith's coupling, and none of the benefit. It's the default outcome of splitting without discipline.

**Fix:** enforce independence deliberately — private databases (mistake 4), additive/versioned contracts and tolerant readers (mistakes 10–11), async where coupling isn't needed (mistake 9), and each service releasable on its own. If you can't deploy one service without the others, the split isn't paying for itself — consider whether those pieces should be one service (topic 03).
