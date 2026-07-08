# Synchronous Service-to-Service Calls

Once each service owns its own data (topic 04), the obvious next question is how one service gets an answer from another. The most common way is a **synchronous call**: `order-service` sends an HTTP request to `inventory-service` and *blocks* — its thread waits — until the response comes back, exactly like a method call, except it's going over the network. This topic is the mechanism: how to make that call cleanly in modern Spring, and what "the thread waits" actually costs you.

The demo is the `order-inventory-system` scaffold: `POST /api/orders` triggers a real HTTP call to inventory-service before the order is accepted.

---

## The problem this solves

At checkout, `order-service` must know *right now* whether an item is in stock. It can't read inventory's database (topic 04), and it can't wait for an overnight batch — it needs an answer this millisecond to decide whether to confirm the order. A synchronous request/response is the natural fit: ask, wait, act on the answer.

The naive way to make that call is to hand-build an HTTP request, and there's a decade of Spring APIs for doing so. The goal of this topic is to pick the right one and understand what the convenience hides.

## How it works: the request/response, and who's waiting

```
order-service thread                       inventory-service
   │  POST /api/orders                            │
   │─── HTTP GET /api/inventory/SKU-BOOK ────────▶│  looks up stock in inventorydb
   │        (this thread is BLOCKED,              │
   │         parked, waiting)                     │
   │◀────────── 200 {sku, available:5} ───────────│
   │  available >= qty? confirm order             │
   ▼                                              ▼
```

The key fact: while inventory-service is working, the order-service thread handling that user's request is *blocked* — it can do nothing else until the response arrives or the call gives up. On the servlet stack (this roadmap), that's one of a finite pool of Tomcat threads (default 200). If inventory-service is slow, those threads pile up waiting, and when they're all waiting, order-service can't serve *anyone* — even callers who don't need inventory. That's why "add a timeout" (topic 09) is the first resilience move: a blocked thread is a consumed resource.

## The client landscape (and what to actually use)

Spring has accumulated several ways to make this call. Know them so you recognize them in real code, but reach for the last one:

| Client | Status | Use it? |
|--------|--------|---------|
| `RestTemplate` | The classic blocking client. In maintenance mode — not deprecated, but no new features. | Only in old code you're maintaining. |
| `WebClient` | Reactive, non-blocking, from Spring WebFlux. Can be used blocking, but drags in the reactive stack. | If you're on WebFlux (this roadmap isn't). |
| `@FeignClient` (Spring Cloud OpenFeign) | Declarative, hugely popular. A separate Spring Cloud dependency; effectively feature-frozen now that the framework has its own. | Recognize it in existing microservices; don't start new code with it. |
| `RestClient` | The modern synchronous client (Spring 6.1 / Boot 3.2+). Fluent, blocking, no reactive baggage. | **Yes** — for imperative calls. |
| **HTTP Interface (`@HttpExchange`)** | Declarative interface, framework-native, backed by `RestClient`. | **Yes** — the cleanest option, what the scaffold uses. |

### The recommended approach: a declarative HTTP interface

You describe the remote API as a Java interface and let Spring generate the implementation. From the scaffold:

```java
public interface InventoryClient {
    @GetExchange("/api/inventory/{sku}")
    StockResponse getStock(@PathVariable String sku);
}
```

That's the entire client. No URL string-building, no manual deserialization — calling `inventory.getStock("SKU-BOOK")` issues `GET /api/inventory/SKU-BOOK` and parses the JSON into `StockResponse`. You wire it up once by turning the interface into a bean:

```java
@Bean
public InventoryClient inventoryClient(@Value("${inventory.base-url}") String baseUrl) {
    RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
    HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build();
    return factory.createClient(InventoryClient.class);
}
```

`HttpServiceProxyFactory` creates a dynamic proxy: at runtime, calls to the interface methods are translated into HTTP requests via the `RestClient` underneath. The base URL is a property (`inventory.base-url`), not a literal — that one indirection is what lets topic 13 replace `http://localhost:8081` with a discovered service name without touching this code.

Then the controller just calls it like any collaborator:

```java
StockResponse stock = inventory.getStock(request.sku());
if (stock.available() >= request.quantity()) { /* confirm */ }
```

### The same call with plain `RestClient` (no interface)

If you don't want an interface, `RestClient` does it inline — useful for one-off calls:

```java
StockResponse stock = RestClient.create()
        .get()
        .uri("http://localhost:8081/api/inventory/{sku}", sku)
        .retrieve()
        .body(StockResponse.class);
```

Both hit the same endpoint. The interface version scales better as the number of remote endpoints grows (one interface method per endpoint, testable, mockable); the inline version is fine for a single call.

## Handling the non-happy path

A synchronous call has response codes, and they mean different things:

- **2xx** → deserialize the body, proceed. (The scaffold's normal path.)
- **4xx** → *you* sent a bad request, or the resource doesn't exist. `RestClient`/HTTP-interface throw `HttpClientErrorException` (e.g. `.NotFound` for 404). The scaffold catches `NotFound` and turns an unknown SKU into a `400` for its own caller:
  ```java
  try {
      stock = inventory.getStock(request.sku());
  } catch (HttpClientErrorException.NotFound e) {
      return ResponseEntity.badRequest().build(); // inventory doesn't know this SKU
  }
  ```
- **5xx** → the *downstream* failed. Throws `HttpServerErrorException`. Right now the scaffold lets this propagate and order-service returns a 500 — which is exactly the unprotected behavior topics 10–11 fix (retry the transient ones, fall back on the rest).
- **No response at all** (timeout, connection refused, downstream down) → an `IOException`/`ResourceAccessException`. This is the case with no status code, and the one most code forgets. Topic 09 (timeout) decides *how long* before you give up; topic 11 (fallback) decides *what to do* when you do.

The discipline: every synchronous call has four outcomes (ok, my-fault, their-fault, no-answer), and production code has a plan for all four. A call that only handles the happy path is a latent incident.

## When to use it / when not to

**Use a synchronous call when:**
- You need the answer *before you can proceed* — checkout can't confirm an order without knowing stock.
- The operation is naturally request/response and the caller genuinely can't continue without the result.

**Prefer an asynchronous event (topic 07) when:**
- You *don't* need an immediate answer — "send a confirmation email" can happen after the response returns.
- You want to avoid temporal coupling: a synchronous call means the downstream must be up *at the exact moment you call*. An event can be consumed whenever the other service is ready.
- You're fanning out to many services — N synchronous calls means N chances to be slow or fail, all on the critical path.

The honest trade-off: synchronous calls are simple and give you a fresh, immediate answer, but they couple availability (topics 09–12 are the price) and add latency to the critical path. Async decouples but gives up the immediate answer and adds eventual-consistency complexity (topics 18–19). Most systems use both — sync where an answer is required, async everywhere else.

## Common pitfalls

- **No timeout.** The default connect/read timeout for the underlying client can be effectively unbounded. A slow downstream then blocks your threads until your service falls over. Set timeouts on the `RestClient`'s request factory (topic 09). This is the single most common — and most damaging — mistake in this whole module.
- **Creating a new client per request.** `RestClient.create()` inside a controller method on every call spins up connection infrastructure each time and defeats connection pooling (fallacy 7). Build the client/interface **once** as a bean (as the scaffold does) and inject it.
- **Synchronous calls in a loop (the distributed N+1).** `for (item : items) inventory.getStock(item.sku())` is N sequential network round-trips. Add a batch endpoint (`getStock(List<String> skus)`) and make one call. (Fallacy 2.)
- **Only handling 2xx.** Not catching 4xx/5xx/timeout means any downstream hiccup becomes an unhandled 500 for your user. Have a plan for all four outcomes above.
- **Chaining calls deep (A→B→C→D).** Each hop adds latency and a failure point, all on one request's critical path. If you find deep chains, reconsider the boundaries (topic 03) or switch to events (topic 07).

---

## Code examples

Two demos: a focused one that isolates the call, and the full two-service scaffold.

**Focused demo** — `05-synchronous-service-calls/synchronous-call-demo/`. The call mechanics with nothing else to start; the test stubs inventory in-process. Read in this order, then `mvn test`:

1. `synchronous-call-demo/src/main/java/com/example/synccall/InventoryClient.java` — the declarative HTTP interface: the whole remote API as one Java interface.
2. `synchronous-call-demo/src/main/java/com/example/synccall/InventoryClients.java` — building it two ways: the interface via `HttpServiceProxyFactory` + `RestClient`, and the plain inline `RestClient`.
3. `synchronous-call-demo/src/test/java/com/example/synccall/SynchronousCallTest.java` — drives both against a stub inventory and shows the 404 → `HttpClientErrorException.NotFound` outcome. (`Tests run: 3` green.)

**Full end-to-end** — the `order-inventory-system` scaffold, where two real services talk over the network:

4. `order-inventory-system/order-service/src/main/java/com/example/order/InventoryClientConfig.java` — the same wiring as a `@Configuration`/`@Bean`, with the base URL externalized.
5. `order-inventory-system/order-service/src/main/java/com/example/order/OrderController.java` — calling it in a real flow, and handling the 404 case.
6. `order-inventory-system/README.md` — run both services and watch the call happen (`201 CONFIRMED` when stock is enough, `409 REJECTED` when it isn't).

Run the end-to-end version: start `inventory-service` (:8081), then `order-service` (:8080), then:

```bash
curl -s -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-BOOK","quantity":2}'      # -> 201, order-service called inventory-service to decide
```

## Try this yourself

1. Rewrite `InventoryClient`'s call using plain `RestClient` inline in the controller (the snippet above) instead of the interface. Confirm the behavior is identical. Then decide which you'd keep as the number of inventory endpoints grows — the answer is the point.
2. With both services running, place an order for a SKU that doesn't exist (`{"sku":"NOPE","quantity":1}`). Inventory returns 404; watch order-service's `catch (HttpClientErrorException.NotFound)` turn it into a `400`. Now remove that catch and place the same order — see the unhandled 404 become a `500`. That difference is "handling all four outcomes."
3. Stop inventory-service and place a valid order. Observe order-service return a `500` with no timeout to bound the wait. Keep this in mind — topic 09 makes that wait finite, and topic 11 replaces the 500 with a graceful fallback.

## Self-check

1. When `order-service` makes a synchronous call to `inventory-service`, what is the order-service thread doing while it waits, and why does that make a *slow* downstream dangerous even when it eventually responds?
2. Name the four possible outcomes of a synchronous call and what your code should do for each. Which one has no HTTP status code?
3. Why does the scaffold externalize `inventory.base-url` as a property instead of hardcoding `http://localhost:8081` in the client? What later topic depends on that choice?
