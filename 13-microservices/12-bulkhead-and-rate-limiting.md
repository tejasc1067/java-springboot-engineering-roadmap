# Bulkhead and Rate Limiting

Timeouts, retries, and circuit breakers (topics 09–11) all react to a call *failing or being slow*. This topic covers two patterns that protect you *before* failure, by limiting load: a **bulkhead** caps how many calls can run *concurrently* against a dependency, and a **rate limiter** caps how many calls happen *per unit time*. Both are about isolation and resource protection, and both are distinct from the circuit breaker — a point worth being precise about, because they're often lumped together as "resilience stuff" and used interchangeably when they solve different problems.

The demo shows a bulkhead rejecting a call beyond its concurrency limit and a rate limiter shedding calls beyond its throughput limit.

---

## The problem this solves

Two different failure stories the breaker doesn't cover:

**Story 1 — a slow dependency eats all your threads (needs a bulkhead).** order-service calls both inventory-service and a recommendations-service. Recommendations gets slow (not failing — slow, so timeouts help but each call still ties up a thread for its full timeout). Under load, *every* request thread ends up waiting on recommendations, and now order-service can't serve the inventory-backed checkout either — a non-critical dependency has consumed the resources the critical one needed. You want to *cap* how many threads recommendations can ever tie up, so it can degrade without taking everything down.

**Story 2 — you overwhelm a downstream, or blow a quota (needs a rate limiter).** order-service suddenly sends inventory-service 10,000 requests/second — a traffic spike, a retry storm, a batch job. inventory-service can handle 2,000/second; the extra 8,000 just make it slower for everyone. Or you're calling a third-party API with a hard 100-calls/minute quota and call 101. You want to *cap the rate*, shedding or delaying the excess.

Neither is about the downstream *failing* — it's about protecting resources (threads) and respecting limits (throughput). That's what makes them different from a circuit breaker.

## Bulkhead: isolate concurrency

Named after a ship's watertight compartments — a hull breach floods one compartment, not the whole ship. A bulkhead limits the number of *concurrent* calls to a dependency; calls beyond the limit are rejected fast (or briefly queued) instead of piling up. From the demo:

```java
Bulkhead bulkhead = Bulkhead.of("inventory", BulkheadConfig.custom()
        .maxConcurrentCalls(2)
        .maxWaitDuration(Duration.ZERO)  // reject immediately when full; don't queue
        .build());
```

With two calls in progress, a third is rejected with `BulkheadFullException` — the demo asserts exactly this, and that a slot frees up once a call completes. The effect: the dependency can only ever consume *N* of your threads. If it goes slow, at most *N* threads wait on it; the rest of your service keeps working. You give each dependency its own bulkhead sized to its importance — a generous one for the critical inventory call, a small one for the optional recommendations call, so recommendations can never starve inventory.

(Resilience4j also offers a `ThreadPoolBulkhead` that runs calls on a *separate, bounded* thread pool — even stronger isolation, since the calls don't run on your request threads at all. The semaphore bulkhead shown here is lighter and the common default.)

## Rate limiter: cap throughput

A rate limiter caps calls *per unit time*: "at most N calls per period." Calls beyond the limit are shed (or wait for a permit). From the demo:

```java
RateLimiter limiter = RateLimiter.of("inventory", RateLimiterConfig.custom()
        .limitForPeriod(3)                          // 3 calls...
        .limitRefreshPeriod(Duration.ofSeconds(1))  // ...per second
        .timeoutDuration(Duration.ZERO)             // don't block waiting for a permit
        .build());
```

The demo shows 3 calls/second passing and the 4th rejected with `RequestNotPermitted`, and permits replenishing after the refresh period. Use it to protect a downstream from being overwhelmed, to enforce a third-party quota, or (at the edge) to throttle abusive clients. `timeoutDuration` controls the behavior when no permit is available: `ZERO` sheds immediately; a positive value makes the caller wait up to that long for the next permit (smoothing bursts instead of dropping them).

## Bulkhead vs. rate limiter vs. circuit breaker

The distinction that this topic exists to make crisp:

| Pattern | Limits based on | Trips/rejects when | Answers the question |
|---------|-----------------|--------------------|----------------------|
| **Circuit breaker** (11) | recent **failures** | the downstream is *breaking* | "is it healthy enough to call?" |
| **Bulkhead** (12) | **concurrency** | too many calls are *in flight at once* | "how many of my threads may this consume?" |
| **Rate limiter** (12) | **throughput over time** | too many calls *per period* | "how fast am I allowed to call?" |

They are complementary, not alternatives, and compose in layers on a single call: a rate limiter caps the rate, a bulkhead caps concurrency, a circuit breaker stops calls when the downstream is failing, a timeout bounds each attempt, and a retry handles blips. A well-guarded remote call often has several of these. Resilience4j's `Decorators` lets you stack them; Spring Cloud wires them via annotations/config.

## When to use it / when not to

- **Bulkhead**: when one dependency's slowness could starve the threads other work needs — especially when a *non-critical* dependency shares a pool with a *critical* one. Size each dependency's bulkhead to its importance.
- **Rate limiter**: when a downstream (or a third party) has a capacity or quota you must respect, or when you want to protect a downstream from your own bursts. At the *edge*, to throttle clients.
- **Not a substitute for a circuit breaker.** A bulkhead limits how many can wait on a broken downstream, but it won't *stop* calling a dead one — you still want a breaker for that. Use them together.
- **Don't over-constrain.** A bulkhead of 1 or a rate limit far below real demand turns a resilience tool into an outage of your own making. Size from real concurrency/throughput, not fear.

## Common pitfalls

- **Confusing the three patterns.** Reaching for a circuit breaker to fix a thread-exhaustion problem (you needed a bulkhead), or a bulkhead to fix a "we're overwhelming the downstream" problem (you needed a rate limiter). Match the tool to whether the issue is *failure*, *concurrency*, or *rate*.
- **One shared bulkhead for all dependencies.** Defeats the purpose — a slow dependency can still consume the shared budget the critical one needs. One bulkhead per dependency.
- **Rate limit set below real demand.** You've built a self-inflicted throttle that sheds legitimate traffic. Size to actual capacity plus headroom.
- **Bulkhead too small.** `maxConcurrentCalls` below normal concurrency rejects healthy calls under ordinary load. Size to real peak concurrency.
- **No plan for the rejection.** `BulkheadFullException` / `RequestNotPermitted` are failures your caller must handle — return a fallback (topic 11) or a clean `429`/`503`, don't let them surface as an unhandled 500.

---

## Code examples

Runnable demo — `12-bulkhead-and-rate-limiting/bulkhead-ratelimiter-demo/`. Read then `mvn test`:

1. `bulkhead-ratelimiter-demo/src/test/java/com/example/isolation/BulkheadTest.java` — two concurrent calls fill a `maxConcurrentCalls=2` bulkhead; the third is rejected; a slot frees when a call finishes.
2. `bulkhead-ratelimiter-demo/src/test/java/com/example/isolation/RateLimiterTest.java` — 3 calls/second pass, the 4th is shed; permits refill after the period.

Resilience4j core here; in a Spring service, `spring-cloud-starter-circuitbreaker-resilience4j` plus `@Bulkhead` / `@RateLimiter` annotations configured in `application.yml`.

## Try this yourself

1. In `BulkheadTest`, set `maxWaitDuration` to 200ms instead of `ZERO`. Now a call that finds the bulkhead full *waits* up to 200ms for a slot instead of failing instantly. Observe how that trades latency for fewer rejections — the queue-vs-shed decision.
2. In `RateLimiterTest`, set `timeoutDuration` to 500ms and make 5 rapid calls with a `limitForPeriod(1)`/`refresh(300ms)`. Watch calls *wait* for permits rather than being rejected — smoothing a burst instead of dropping it.
3. Write one sentence each describing a real incident that a bulkhead would prevent, one a rate limiter would prevent, and one a circuit breaker would prevent — proving to yourself they're three different tools.

## Self-check

1. A non-critical recommendations-service gets slow and your whole service stops responding, even for requests that don't use recommendations. Which pattern fixes this, and how?
2. State the difference between a bulkhead and a rate limiter in one sentence each, and how both differ from a circuit breaker.
3. Why should each dependency get its own bulkhead rather than sharing one, and what goes wrong if you set a rate limit below real demand?
