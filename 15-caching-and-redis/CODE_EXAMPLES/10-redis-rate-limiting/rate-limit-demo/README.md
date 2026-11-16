# rate-limit-demo

Rate limiters that hold across several instances of a service, because the counter lives in Redis.

- `InProcessLimiterTest` — module 13's Resilience4j limiter on two instances lets through 10 requests for a limit of 5.
  The Redis limiter on two instances lets through 5.
- `FixedWindowTest` — `INCR` then `EXPIRE` as two calls: the app dies in between and the key never expires (TTL `-1`),
  so the client is blocked forever. The Lua version always sets a TTL. Then the fixed window's weakness:
  10 requests within 0.2 s around a window edge for a limit of 5 per minute.
- `SlidingWindowTest` — a sorted-set log has no edge burst. Done as three separate calls, two concurrent requests
  both get in as the 5th; done as one Lua script, 50 concurrent requests let in exactly 5.

Run (Docker running):

```
mvn -q test
```

Testcontainers starts `redis:7.4-alpine`. Time is passed into the limiters as `nowMillis`, so the window-edge tests
are exact instead of depending on the wall clock.
