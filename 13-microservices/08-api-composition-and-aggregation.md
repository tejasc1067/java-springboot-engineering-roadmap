# API Composition and Aggregation

In a monolith, an "order detail" screen is one SQL query joining `orders`, `payments`, and `products`. Once those tables live in separate services with separate databases (topic 04), you *cannot* write that join — there's no shared database to run it in. Instead, one component calls each service and stitches the results together in memory. That's **API composition**, and this topic is how to do it and — more importantly — how it fails: partial data, added latency, and fan-out that multiplies both.

The demo composes an `OrderView` from an order-service and a payment-service, with each downstream switchable between fast, slow, and failing.

---

## The problem this solves

A client wants one response: "show me order 1 — its items *and* its payment status." Order data is owned by order-service; payment data by payment-service. You have no cross-service join (topic 04). Two honest options:

1. **API composition (read-time join):** a composer calls order-service and payment-service, joins the two results, returns one `OrderView`. Always fresh; simple; but its availability and latency are the *combination* of every service it calls.
2. **A materialized view / CQRS read model (write-time join):** maintain a precomputed `OrderView` table, updated by events (topic 07) as orders and payments change. Reads are one fast local lookup; but the view is eventually consistent and you carry the machinery to keep it updated.

This topic is mostly about option 1 (the common default) and its failure modes; option 2 is the escape hatch when composition gets too slow or fragile.

## How it works: call, then join

The composer is just a component that calls the services it needs and assembles the result:

```java
OrderInfo order = orderService.apply(orderId);      // from order-service
PaymentInfo payment = paymentService.apply(orderId); // from payment-service
return new OrderView(order.orderId(), order.sku(), order.quantity(), payment);
```

Where the composer *lives* varies: it can be a dedicated endpoint in one service, a **Backend-for-Frontend (BFF)** tailored to a specific client, or a route on the API gateway (topic 14). The pattern is the same; only the location differs.

The naive version above has two problems the rest of the topic fixes: it fails entirely if *either* service is down, and it's as slow as *both* services added together.

## Failure mode 1: partial results and graceful degradation

If the composer calls two services and one is down, what should happen? Failing the whole response is often wrong — the order detail is still useful without the payment badge. So you decide, per source, whether it's **required** or **optional**:

```java
OrderInfo order = orderService.apply(orderId);   // REQUIRED — no order, no view (propagates)
PaymentInfo payment = tryGetPayment(orderId);    // OPTIONAL — null if payment-service is down
```

The demo proves both: when payment-service throws, the view still returns with order data and a null payment section (`paymentAvailable() == false`); when the *required* order-service is down, the composer throws — because there is no order view without an order. **Not everything can degrade; you choose what's essential and what's a nice-to-have**, and the response shape has to represent "this part is temporarily unavailable" rather than lying with a default.

This is the composition-level version of the fallback you'll formalize with a circuit breaker in topic 11 — degrade a non-essential dependency instead of taking the whole response down with it.

## Failure mode 2: latency and the fan-out tax

A composed response is only as fast as the calls it makes — and if you make them **sequentially**, latency *adds up*. The demo shows two 200ms calls taking ~400ms sequentially versus ~200ms in parallel:

```java
// sequential: 200 + 200 ≈ 400ms
OrderInfo order = orderService.apply(id);
PaymentInfo payment = paymentService.apply(id);

// parallel: ≈ max(200, 200) ≈ 200ms — fan out independent calls at once
CompletableFuture<OrderInfo>   orderF   = supplyAsync(() -> orderService.apply(id));
CompletableFuture<PaymentInfo> paymentF = supplyAsync(() -> paymentService.apply(id))
                                              .exceptionally(ex -> null); // optional -> degrade
OrderInfo order = orderF.join();
PaymentInfo payment = paymentF.join();
```

Rule: **fan out independent calls in parallel.** Sequential is only justified when one call *needs the result of another* (e.g. you must fetch the order to learn its `sku` before you can ask inventory about that sku — a genuine dependency you can't parallelize away).

And beware the tail: a composed response's latency is dominated by its *slowest* dependency, and its p99 by whichever dependency is having a bad moment. Every service you add to a composition adds a chance to be the slow one — which is why composing across *many* services (deep fan-out) is a smell that either the boundaries are wrong (topic 03) or you want a materialized read model instead.

## The distributed N+1 (the aggregation trap)

The most common composition bug: fetching a list, then calling a service once *per item*.

```java
List<Order> orders = orderService.recentOrders(customerId);   // 1 call
for (Order o : orders) {
    PaymentInfo p = paymentService.getPayment(o.id());         // N calls — one per order!
}
```

Fifty orders = fifty sequential network round-trips (fallacy 2). The fix is a **batch endpoint**: `paymentService.getPayments(List<Long> ids)` — one call for all of them. Design composition-friendly APIs (accept and return collections) so aggregators don't have to loop.

## When to use it / when not to

- **Use read-time API composition** for most cross-service reads: it's simple and always fresh. Fan out in parallel, mark sources required/optional, and batch list lookups.
- **Switch to a materialized read model (CQRS)** when composition is too slow (too many hops), too fragile (depends on too many services being up), or too hot (the same expensive join on every request). You precompute the view from events (topic 07) and read it locally — trading freshness for speed and resilience.
- **Reconsider your boundaries** if a single view needs to compose four or five services. That often means the data was split too finely (topic 03), and some of it belongs together.

## Common pitfalls

- **All-or-nothing composition.** One optional dependency down takes the whole response with it. Mark sources required vs optional and degrade the optional ones (the demo).
- **Sequential fan-out of independent calls.** Latency adds up needlessly. Parallelize anything that doesn't depend on another call's result.
- **The distributed N+1.** A per-item call inside a loop. Add a batch endpoint.
- **No timeout on composed calls.** A composed response with no per-call timeout hangs on the slowest dependency forever (topic 09). Composition multiplies the number of calls that each need bounding.
- **Leaking partial-failure as success.** Returning a default (e.g. `payment.status = "PAID"`) when payment-service was actually down. Represent "unavailable" explicitly; never fabricate data to fill a gap.

---

## Code examples

Runnable demo — `08-api-composition-and-aggregation/aggregation-demo/`. Read then `mvn test`:

1. `aggregation-demo/src/main/java/com/example/aggregation/Dtos.java` — the parts (OrderInfo, PaymentInfo) and the joined OrderView, with a nullable payment section for degradation.
2. `aggregation-demo/src/main/java/com/example/aggregation/OrderViewComposer.java` — sequential vs parallel composition, required vs optional sources.
3. `aggregation-demo/src/test/java/com/example/aggregation/OrderViewComposerTest.java` — composes-two, degrades-on-optional-down, fails-on-required-down, parallel-faster-than-sequential.

## Try this yourself

1. Add a third source (`shippingService`) to `OrderViewComposer`, also keyed by orderId and also optional. Fan all three out in parallel. Confirm the total latency stays ≈ the slowest single call, not the sum.
2. Turn the parallel version's payment call `.exceptionally(ex -> null)` into one that instead records *why* it failed (a status like `"PAYMENT_UNAVAILABLE"`). Notice how "represent unavailable explicitly" changes the response shape — and why that's better than a null the client might misread.
3. Write a composition that has a genuine sequential dependency (fetch the order to get its `sku`, then ask inventory about that sku). Explain why this part can't be parallelized, and what that implies for its latency floor.

## Self-check

1. Why can't you serve a cross-service "order detail" view with a single query the way a monolith would, and what are the two ways to build that view instead?
2. A composed response calls three services and one is optional-and-down. Describe two different correct behaviors, and explain why returning a fabricated default for the missing part is *not* one of them.
3. Your aggregator calls two independent services and takes 400ms; each service responds in 200ms. What's the bug and the fix? Now the two calls have a real data dependency — what changes?
