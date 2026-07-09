# Circuit Breaker and Fallback

Timeouts (topic 09) stop one call from hanging; retries (topic 10) paper over blips. But when a downstream is *sustainedly* broken — not blipping, actually down — timeouts and retries make things *worse*: every request still tries, waits out the timeout, retries, and waits again, piling up slow, doomed calls that exhaust your threads and cascade the outage upstream. A **circuit breaker** is the pattern that notices "this downstream is broken" and stops calling it for a while — failing fast instead. A **fallback** decides what to return when it does. Together they're the difference between one service being down and your whole system being down.

This is the centerpiece of the resilience group; the demo makes the breaker's state machine observable.

---

## The problem this solves

inventory-service is fully down (bad deploy, dead database). Without a breaker, here's what happens to order-service on every single incoming order:

1. Call inventory-service → wait out the timeout (say 1s, topic 09).
2. Retry (topic 10) → wait again → 2-3s per order gone.
3. Multiply by every concurrent order → all your request threads are parked waiting on a downstream that will *never* answer.
4. order-service now can't serve *anyone* — the inventory outage has **cascaded** into an order-service outage.

The demo's first test shows the root of this: with no breaker, 10 calls to a down service produce 10 downstream hits (each also paying a timeout in reality). Retrying *through* an outage is exactly what makes it cascade. What you want instead: after a few failures, **stop trying** for a while — fail instantly, free your threads, and give the downstream room to recover.

## How it works: the state machine

A circuit breaker wraps the call and tracks its recent success/failure rate, moving through three states:

```
CLOSED  --failure rate exceeds threshold-->  OPEN  --wait duration elapses-->  HALF_OPEN
  ^                                                                                |
  |------------------- trial calls succeed ----------------------------------------|
                              trial calls fail --> back to OPEN
```

- **CLOSED** — normal. Calls pass through to the downstream; the breaker just counts outcomes. If the failure rate over a sliding window crosses the threshold, it trips to OPEN.
- **OPEN** — tripped. Calls are **short-circuited**: they fail *instantly* (Resilience4j throws `CallNotPermittedException`) without touching the downstream. This is the whole point — no thread is parked waiting, and the struggling downstream gets no traffic. After a configured wait, it moves to HALF_OPEN.
- **HALF_OPEN** — testing recovery. A limited number of trial calls are allowed through. If they succeed, the breaker returns to CLOSED (recovered); if they fail, it snaps back to OPEN and waits again.

The demo drives all of this with Resilience4j:

```java
CircuitBreaker breaker = CircuitBreaker.of("inventory", CircuitBreakerConfig.custom()
        .slidingWindowType(SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(5)
        .minimumNumberOfCalls(5)                     // don't judge before 5 calls
        .failureRateThreshold(50f)                    // open if >= 50% fail
        .waitDurationInOpenState(Duration.ofMillis(100))
        .permittedNumberOfCallsInHalfOpenState(2)
        .build());
Supplier<String> guarded = CircuitBreaker.decorateSupplier(breaker, inventory::getStock);
```

The tests show: after 5 failures the state is `OPEN`; once open, further calls throw `CallNotPermittedException` and the downstream's call count **stops rising** (it's being left alone); after the wait, successful trial calls return the breaker to `CLOSED`.

Key config knobs and what they trade:
- **failure rate threshold** — how broken is "broken." Too low → trips on normal noise; too high → tolerates a mostly-dead downstream.
- **sliding window** (count- or time-based) + **minimum calls** — don't judge on too little data; one failure out of one call shouldn't open the circuit.
- **wait duration in open state** — how long to leave the downstream alone before testing it. Too short → you re-hammer a service that's still down; too long → slow to notice recovery.
- **permitted calls in half-open** — how many trials before deciding it's healthy.

## The fallback: what to return when you fail fast

An open breaker fails *fast* — but it still fails. The fallback decides what the caller gets instead of an error. The demo wraps the guarded call:

```java
try {
    return guarded.get();
} catch (CallNotPermittedException e) {   // breaker is open
    return FALLBACK;                       // degraded but usable
} catch (RuntimeException e) {             // the call itself failed
    return FALLBACK;
}
```

A **good** fallback degrades gracefully to something safe and useful:
- Serve **stale cached data** ("last known stock") with a note that it's cached.
- Return a **sensible default** or an **empty result** the UI can handle ("recommendations unavailable right now").
- Queue the work to do later (topic 07) if it doesn't need an immediate answer.

A **bad** fallback lies or makes an unsafe assumption. For the *gating* stock check, "assume in stock" is dangerous — you'd confirm orders you can't fulfil. Sometimes the only correct fallback is a clean, fast `503`-style error ("checkout temporarily unavailable, try again") — failing fast and honestly beats hanging or lying. **The fallback must never fabricate a result that causes incorrect behavior** (this is the composition rule from topic 08, now enforced by the breaker).

## How the resilience patterns compose

Timeout, retry, and breaker are not alternatives — they layer:

- **Timeout (09):** bounds a *single* attempt so it fails fast instead of hanging.
- **Retry (10):** handles the *occasional* transient blip by trying again a few times.
- **Circuit breaker (11):** handles a *sustained* outage by stopping attempts entirely for a while.

Order matters: a retry should sit *inside* a breaker so that repeated failures (including exhausted retries) count toward tripping it — and once the breaker is open, you stop retrying a downstream that's clearly down. The breaker is what prevents retries from prolonging a cascade.

## When to use it / when not to

- **Put a breaker on every call to a downstream that can fail independently** — especially synchronous calls on the critical path. It's the primary defense against cascading failure.
- **Give each downstream its own breaker.** One breaker per dependency; inventory being down shouldn't open the circuit to payment.
- **Not needed for in-process calls** — a method call in your own service can't have a network outage. Breakers are for remote dependencies.
- **Pair with a fallback only where degrading is acceptable.** Where a correct answer is mandatory (the gating stock check), the "fallback" is a clean error, not a fabricated success.

## Common pitfalls

- **No breaker, just retries.** Retrying through a sustained outage prolongs and spreads it. The breaker is what stops that (the demo's first vs second test).
- **A fallback that lies.** "Assume in stock" on an outage → confirming unfulfillable orders. Degrade safely or fail honestly; never fabricate a result that causes wrong behavior.
- **One shared breaker for all downstreams.** A failure in one dependency trips calls to unrelated healthy ones. One breaker per dependency.
- **Thresholds tuned by vibes.** Opening on one failure (flaps constantly) or requiring 95% failure (never opens). Set `minimumNumberOfCalls` and a sensible rate over a window.
- **Wait duration too short.** The breaker re-opens and re-hammers a downstream that hasn't recovered. Give it real time.
- **Ignoring the open state operationally.** An open breaker is a signal something is down — it should raise an alert (topic 17/module 19), not just silently serve fallbacks forever.

---

## Code examples

Runnable demo — `11-circuit-breaker-and-fallback/circuit-breaker-demo/`. Read then `mvn test`:

1. `circuit-breaker-demo/src/main/java/com/example/breaker/ControllableInventory.java` — a downstream you can turn down and recover, counting calls.
2. `circuit-breaker-demo/src/test/java/com/example/breaker/CircuitBreakerTest.java` — no-breaker-hammers-downstream, opens-and-fails-fast, fallback-degrades, recovers-through-half-open.

This uses Resilience4j core; in a Spring service you'd use `spring-cloud-starter-circuitbreaker-resilience4j` and the `@CircuitBreaker(name=..., fallbackMethod=...)` annotation, configured in `application.yml`.

## Try this yourself

1. In `recoversThroughHalfOpenBackToClosed`, keep the downstream `down` after the wait instead of recovering it. Observe the trial calls fail and the breaker snap back to OPEN — the "still broken, wait again" path.
2. Change the fallback in `guardedWithFallback` to return a fake "in stock" JSON, and imagine it feeding the scaffold's order decision. Write one sentence on the incorrect behavior that causes — that's why the gating check's only safe fallback is an error.
3. Give `ControllableInventory` a second breaker for a *different* downstream and confirm tripping one doesn't affect the other. That's "one breaker per dependency" in action.

## Self-check

1. When a downstream is fully down, why do timeouts and retries alone make the caller's situation *worse*, and what does an OPEN circuit breaker do differently?
2. Walk the three states (CLOSED, OPEN, HALF_OPEN): what happens to calls in each, and what causes each transition?
3. Give one example of a *safe* fallback and one of a *dangerous* one for the order/inventory stock check, and state the rule a fallback must never violate.
