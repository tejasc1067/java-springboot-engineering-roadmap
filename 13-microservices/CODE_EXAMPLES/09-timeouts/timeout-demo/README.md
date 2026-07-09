# timeout-demo

The single most important resilience move: bound every outbound call with a timeout.

`TimeoutTest` runs a deliberately slow stub inventory-service (1500ms) and calls it
two ways:

- **without a tight timeout** (5s read timeout) — the caller *succeeds but waits the
  full 1500ms*; that thread is tied up the whole time.
- **with a tight timeout** (300ms) — the caller *fails fast* with a
  `ResourceAccessException` (wrapping `SocketTimeoutException`) at ~300ms instead of
  hanging for 1500ms.

The test timing (~3.2s total) reflects one test waiting out the slow downstream and
the other bailing early.

## Run

```bash
mvn -q test
```

## The point

A slow downstream is more dangerous than a dead one: without a timeout you *wait* for
it, and enough waiting threads exhaust your pool and take your own service down. A
timeout turns "hang forever" into "fail fast", which is what makes retries (topic 10)
and fallbacks (topic 11) possible.
