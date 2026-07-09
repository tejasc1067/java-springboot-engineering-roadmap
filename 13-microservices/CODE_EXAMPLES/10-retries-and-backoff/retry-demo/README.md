# retry-demo

Retry mechanics with Resilience4j's core `Retry` (no Spring wiring), against a flaky
inventory stub that fails a set number of times then succeeds.

`RetryTest` proves, all green:

- **without retry, the first transient failure propagates** — one attempt, one failure.
- **with retry, it succeeds after transient failures** — 2 failures + 1 success = 3 attempts.
- **retry gives up after maxAttempts** — an always-failing call is tried exactly 3 times, then throws.
- **does not retry errors that won't get better** — a 4xx-style `IllegalArgumentException`
  is attempted once, not retried (retrying a 400 just fails four times).
- **exponential backoff waits longer between each attempt** — ~100ms then ~200ms
  between the three attempts.

## Run

```bash
mvn -q test
```

## The rules this encodes

1. Retry only *transient* failures (timeouts, 503, dropped connections) — never 4xx or
   business errors.
2. Back off exponentially (and add jitter in production) so retries don't hammer a
   struggling downstream.
3. Cap the attempts.
4. Only retry *idempotent* operations — a retried non-idempotent call can double-apply
   (topic 19). A timeout (topic 09) doesn't tell you whether the first call already worked.
