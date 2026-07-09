# bulkhead-ratelimiter-demo

Two Resilience4j patterns that protect by *isolation*, not by reacting to failures.

`BulkheadTest` (limit concurrency):
- **isolates concurrency, rejects beyond the limit, then releases** — with
  `maxConcurrentCalls=2`, two in-progress calls fill the bulkhead, a third is rejected
  immediately with `BulkheadFullException`, and once the two finish a new call is admitted.

`RateLimiterTest` (limit throughput):
- **allows up to the limit per period then rejects** — 3 calls/second pass, the 4th is
  shed with `RequestNotPermitted`.
- **permits refill after the refresh period** — after the window elapses, calls succeed again.

## Run

```bash
mvn -q test
```

## How these differ from a circuit breaker

- **Circuit breaker** reacts to **failures** — opens when a downstream is breaking.
- **Bulkhead** limits **concurrency** — caps simultaneous calls so a slow dependency
  can't consume every thread (isolation).
- **Rate limiter** limits **throughput** — caps calls per unit time, shedding excess.

They compose: a call can be rate-limited, then bulkhead-isolated, then breaker-guarded.
