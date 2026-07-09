# circuit-breaker-demo

Resilience4j's `CircuitBreaker` used directly, so the state machine is observable.

`CircuitBreakerTest` proves, all green:

- **without a breaker, every call hammers the failing downstream** — 10 calls, 10 hits
  (and in reality each also pays a full timeout). This is how an outage cascades.
- **breaker opens and fails fast, leaving the downstream alone** — after 5 failures the
  state is `OPEN`; further calls throw `CallNotPermittedException` and the downstream
  call count stops rising (it gets breathing room).
- **fallback returns a degraded response instead of an error** — the guarded call
  returns a usable "temporarily unavailable" value while the breaker is open.
- **recovers through half-open back to closed** — after the wait window, trial calls
  succeed and the breaker returns to `CLOSED`.

## Run

```bash
mvn -q test
```

## The state machine

```
CLOSED  --failures exceed threshold-->  OPEN  --wait elapses-->  HALF_OPEN
  ^                                                                 |
  |------------ trial calls succeed --------------------------------|
                        trial calls fail --> back to OPEN
```
