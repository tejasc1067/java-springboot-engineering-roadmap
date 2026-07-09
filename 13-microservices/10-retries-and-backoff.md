# Retries and Backoff

The network is unreliable (fallacy 1), so a fraction of your calls fail for reasons that have nothing to do with your request — a dropped packet, a brief blip, a downstream instance restarting. Many of those would succeed if you simply tried again. A **retry** does exactly that. But a naive retry is a foot-gun: it can turn one struggling downstream into a stampede that finishes it off, and it can silently double-apply operations that were never safe to repeat. This topic is how to retry *correctly* — which failures to retry, how to space attempts (backoff), when to stop, and the idempotency rule that keeps retries from causing damage.

Retries build directly on timeouts (topic 09): you can only retry a call that *returns* — fails fast — instead of hanging.

---

## The problem this solves

inventory-service is fine, but this one call hit a connection reset, or a 503 during a rolling deploy, or a momentary GC pause. Failing the user's order over a hiccup that would clear in 50ms is a bad trade. Retrying transient failures turns a large class of blips into invisible, self-healing events — your success rate goes up without anyone noticing there was a problem.

The catch is that "just retry" done wrong makes things worse in two specific ways (retry storms and double-applies), so the pattern is really "retry, *but with these four disciplines*."

## How it works: retry transient failures, with backoff, capped

Resilience4j (the library Spring Cloud Circuit Breaker wraps) expresses this as a policy around the call. From the demo:

```java
Retry retry = Retry.of("inventory", RetryConfig.custom()
        .maxAttempts(4)                                        // cap total attempts
        .intervalFunction(IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(100), 2.0))                  // 100ms, 200ms, 400ms...
        .retryOnException(e -> e instanceof TransientException) // ONLY transient failures
        .build());

Supplier<String> withRetry = Retry.decorateSupplier(retry, inventory::getStock);
String body = withRetry.get(); // succeeds if any attempt within the cap succeeds
```

The demo proves the behaviours: a call that fails twice then succeeds takes 3 attempts and returns; an always-failing call is tried exactly `maxAttempts` times then throws; a non-transient error is tried **once** and not retried.

### Discipline 1: retry only *transient* failures

Not every failure is worth retrying. Retrying the wrong ones wastes time and can be actively harmful:

- **Retry:** timeouts, connection refused/reset, `503 Service Unavailable`, `502`/`504` — things that might be different next time.
- **Do NOT retry:** `400 Bad Request`, `404 Not Found`, `401/403`, validation errors — the request is wrong and will fail identically every time. The demo shows a `400`-style error attempted once, not four times: retrying it just fails four times slower.

Retrying non-transient errors doesn't just waste effort — it amplifies load for zero benefit.

### Discipline 2: back off, and add jitter

Retrying *immediately* is the classic mistake. If a downstream is overloaded and 1,000 callers all fail and all retry instantly, you've just hit it with 1,000 more requests at once — a **retry storm** that keeps it down. So you space attempts out:

- **Exponential backoff** — wait longer after each failure (100ms, 200ms, 400ms…), giving the downstream room to recover. The demo measures these growing gaps.
- **Jitter** — add randomness to the delay so callers don't retry in lockstep. Without jitter, all 1,000 callers back off by the *same* 100ms and hit again *simultaneously* — synchronized waves. Resilience4j's `ofExponentialRandomBackoff` spreads them out. In production, use jittered backoff, not plain exponential.

### Discipline 3: cap the attempts (and the total time)

Retries must end. `maxAttempts` bounds the count; combined with backoff, also mind the total wall-clock — 5 attempts with exponential backoff can add up to seconds, and the user (or the outer timeout, topic 09) won't wait forever. Budget retries inside the caller's overall deadline: better to fail fast after 2–3 tries than to blow the request's time budget chasing a downstream that's clearly down (that's the circuit breaker's job, topic 11).

### Discipline 4: only retry *idempotent* operations

This is the one that causes data corruption, and it ties straight back to topic 09's timeout ambiguity. When a call fails with a timeout, **you don't know whether the server did the work** — the request may have succeeded and only the response was lost. Retrying it then runs the operation *again*:

- Retrying `GET /inventory/{sku}` — safe. Reading twice is harmless (idempotent).
- Retrying `POST /reserve` after a timeout — **dangerous**. The first call may have reserved the stock; the retry reserves it again. Double reservation, or a double charge on a payment.

So you may freely retry reads and other idempotent operations; for state-changing calls, you must *make them idempotent first* (idempotency keys, topic 19) before retrying. "The call timed out so I retried" is only safe when repeating the call is safe. This is why topic 19 is the required partner to retries.

## When to use it / when not to

- **Retry transient failures on idempotent calls** — reads, and writes you've made idempotent. This is where retries shine and cost nothing but a little latency.
- **Don't retry** non-transient errors (4xx), or state-changing calls that aren't idempotent, or when the downstream is clearly *down* rather than blipping (retrying a dead service just delays the inevitable — escalate to a circuit breaker, topic 11).
- **Combine with timeouts and a breaker.** Retry sits between them: a timeout (09) makes each attempt fail fast; retry (10) handles the occasional blip; a circuit breaker (11) stops retrying entirely when the downstream is sustainedly broken. Retrying *through* an outage is what a breaker prevents.

## Common pitfalls

- **Retrying without backoff.** Instant retries become a retry storm that keeps the downstream down. Always back off.
- **Backoff without jitter.** Synchronized clients retry in waves. Add randomness.
- **Retrying non-idempotent operations.** The timeout-ambiguity trap: double reserve, double charge. Make it idempotent first (topic 19).
- **Retrying 4xx errors.** A `400` will fail identically every time; you've just quadrupled the failures. Retry only transient/5xx/network errors.
- **Unbounded or too-many retries.** No cap, or a cap so high the retries blow the request's time budget. Cap attempts *and* mind total elapsed against the outer timeout.
- **Retrying at every layer.** If A retries B, and B retries C, and C retries the DB, one failure becomes N×M×K attempts — multiplicative amplification. Retry at *one* layer, usually closest to the fault.

---

## Code examples

Runnable demo — `10-retries-and-backoff/retry-demo/`. Read then `mvn test`:

1. `retry-demo/src/main/java/com/example/retry/TransientException.java` — a retryable failure vs. everything else.
2. `retry-demo/src/main/java/com/example/retry/FlakyInventory.java` — fails N times then succeeds; counts attempts.
3. `retry-demo/src/test/java/com/example/retry/RetryTest.java` — no-retry-propagates, retry-succeeds, gives-up-after-cap, does-not-retry-4xx, exponential-backoff-grows.

This uses Resilience4j core directly; in a Spring service you'd use `spring-cloud-starter-circuitbreaker-resilience4j` (the same library) or the `@Retry` annotation, configured in `application.yml`.

## Try this yourself

1. Swap `ofExponentialBackoff` for `IntervalFunction.ofExponentialRandomBackoff(...)` (jitter) and run the backoff test several times — the elapsed time now varies run to run. That variance is exactly what de-synchronizes a fleet of retrying clients.
2. Change `retryOnException` to `e -> true` (retry everything) and re-run the "does not retry 4xx" test. Watch a bad request get attempted 4 times. Then put the predicate back and articulate why retrying a `400` is pure waste.
3. Point the retry at the scaffold's `InventoryClient`: what would you have to guarantee about `POST /api/orders`'s downstream call before it's safe to retry? (Answer: the reserve must be idempotent — topic 19.)

## Self-check

1. Which failures should you retry and which should you never retry? Give an example of each and say why retrying the second kind is worse than not retrying.
2. What is a retry storm, and what two configuration choices (together) prevent it?
3. A `POST /reserve` times out and you retry it. What's the risk, why does the timeout make it unknowable, and what must be true of the operation for the retry to be safe?
