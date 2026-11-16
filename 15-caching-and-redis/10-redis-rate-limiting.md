# Rate Limiting with Redis

A rate limiter caps how many requests a client may make per period: "100 requests per minute per API key". Module 13 topic 12 built one with Resilience4j, inside one JVM. That stops working the moment the service runs as more than one instance, because each instance counts on its own. This topic moves the counter into Redis, where every instance shares it, and covers the three bugs that Redis rate limiters usually ship with: a key that never expires, a burst at the window edge, and a race between "count" and "add".

---

## The problem this solves

Your public API promises "5 requests per minute per client" (small numbers make the demo readable). It runs as 2 instances behind a load balancer, which sends the client's requests to A, B, A, B, ...

With the in-process limiter from [13-12](../13-microservices/12-bulkhead-and-rate-limiting.md), each instance has its own counter in its own memory. `InProcessLimiterTest.inProcessLimiter_eachInstanceCountsAlone_soTwoInstancesAllowTwiceTheLimit` sends 20 requests round-robin:

```java
RateLimiter instanceA = RateLimiter.of("client-42", fivePerMinute);   // lives in instance A's memory
RateLimiter instanceB = RateLimiter.of("client-42", fivePerMinute);   // lives in instance B's memory
// ... 20 requests, alternating A and B ...
assertThat(allowed).isEqualTo(10);   // limit was 5
```

With N instances the real limit is N × 5, and it changes every time the service scales up or down. For protecting *your own* outgoing calls (13-12's use) that's often acceptable. For a limit you promise or enforce on clients, it isn't.

The fix is one shared counter. Redis is a good place for it: every instance can reach it, `INCR` is atomic, and keys can expire on their own. The same test with the Redis limiter lets through exactly 5 (`redisLimiter_bothInstancesShareOneCounter_soTheLimitHolds`).

---

## Fixed window: a counter per client per minute

The simplest algorithm: split time into windows (12:00:00–12:00:59, 12:01:00–12:01:59, ...), count requests per client in the current window, and reject once the count passes the limit.

### Broken: `INCR`, then `EXPIRE`, as two commands

The version found in most blog posts uses the key's lifetime as the window:

```java
Long count = redis.opsForValue().increment(key);   // INCR: creates the key at 1 if missing
if (count == 1) {
    redis.expire(key, window);                     // EXPIRE: end the window in 60 s
}
return count <= limit;
```

Each command is atomic. The *pair* isn't. If the instance dies between them (a deploy, an out-of-memory kill, a network timeout on the second call), the key exists with **no expiry**. `FixedWindowTest.incrThenExpire_appDiesBetweenTheTwoCommands_keyNeverExpires_clientBlockedForever` simulates exactly that:

```text
TTL rl:naive:client-42   ->  -1        (-1 means "exists, never expires")
requests 2-5             ->  allowed
request 6                ->  rejected
every request after that ->  rejected, forever: nothing will ever reset the counter
```

It happens once in a million requests, so it passes every test, and then one customer is locked out permanently and nobody can reproduce it.

### Fixed: one Lua script

Redis runs a Lua script **atomically**: no other command runs while it does, and the script either runs completely or not at all. Put both commands in one script and there's no "between" for anything to fail in:

```java
private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then
          redis.call('PEXPIRE', KEYS[1], ARGV[1])
        end
        return count
        """, Long.class);

public boolean tryAcquire(String clientId, long nowMillis) {
    long windowNumber = nowMillis / window.toMillis();
    String key = "rl:fixed:" + clientId + ":" + windowNumber;
    Long count = redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
    return count <= limit;
}
```

Two more details: the window number is in the key (`rl:fixed:client-42:2` is "client 42, third minute"), so a new window is a new key and starts at zero even if a TTL were ever lost; and the TTL only exists to delete old keys. `luaScript_incrAndExpireAreAtomic_soTheKeyAlwaysHasATtl` checks the key always has one. Scripts are covered in more depth in [09-distributed-locks.md](09-distributed-locks.md).

`nowMillis` is a parameter so the tests can stand exactly on a window edge. In production, pass `System.currentTimeMillis()`, and keep the instances' clocks in sync (NTP); a clock that is a minute off puts that instance in a different window.

### The fixed window's weakness: a burst at the edge

The limit is "5 per window", not "5 in any 60 seconds". `fixedWindow_boundaryBurst_twiceTheLimitInOneSecond`:

```text
00:59.9   5 requests   window 0   -> all 5 allowed
01:00.1   5 requests   window 1   -> all 5 allowed (fresh counter)
          10 requests in 0.2 seconds, for a limit of 5 per minute
```

For "stop a client hammering us" that's usually fine: over any long period, the average holds. For "the downstream can't take more than 5 at once", it isn't.

---

## Sliding window log: exactly N in any window

Keep a timestamp for every allowed request in a **sorted set** (a set where each member has a numeric score, kept ordered by score; see [06-redis-fundamentals.md](06-redis-fundamentals.md)). The score is the request's time. On each request:

1. `ZREMRANGEBYSCORE key -inf (now - window)`: drop entries older than the window.
2. `ZCARD key`: count what's left.
3. If under the limit, `ZADD key now <unique-id>` and allow; otherwise reject.

```java
private static final RedisScript<Long> SLIDING_LOG = RedisScript.of("""
        local key, now, window, limit, member = KEYS[1], tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3]), ARGV[4]
        redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
        if redis.call('ZCARD', key) < limit then
          redis.call('ZADD', key, now, member)
          redis.call('PEXPIRE', key, window)
          return 1
        end
        return 0
        """, Long.class);
```

`slidingWindow_noBurstAtTheEdge_theLast60SecondsAlwaysHoldAtMost5`: 5 requests at 00:59.9 are allowed; at 01:00.1 they still count, so all 5 new ones are rejected; they stay rejected until 01:59.9, when the first 5 have slid out.

The member is the timestamp plus a UUID, because a sorted set holds each member once: two requests in the same millisecond with the member `"10000"` would be stored as one, and the limiter would undercount.

The cost: one entry per allowed request. "5 per minute" is 5 entries per client. "10,000 per hour" is 10,000 per client (`slidingWindow_storesOneSortedSetEntryPerAllowedRequest`).

### Broken: the same three steps as separate calls

`SlidingWindowSeparateCallsBroken` does steps 1–3 with three `opsForZSet()` calls. `separateCalls_twoConcurrentRequestsBothSee4_andBothGetIn` lets 4 requests in, then holds two concurrent requests just after they've counted:

```text
request X: ZCARD -> 4  (under 5)
request Y: ZCARD -> 4  (under 5)
request X: ZADD        -> allowed
request Y: ZADD        -> allowed      6 requests, limit 5
```

The test forces that interleaving with a barrier so it happens every run. Real traffic produces it by itself: firing 50 concurrent requests at the separate-calls version let 13–16 through when we tried it. The Lua version lets exactly 5 through (`luaScript_concurrentRequests_neverExceedTheLimit`).

This is the same check-then-act race as two threads updating a shared counter (module 03c), just across processes. Any "read, decide, write" sequence against Redis has it. Use a Lua script, or a single command that does the whole thing.

---

## Other algorithms you'll meet

- **Sliding window counter**: keep the fixed-window counts for this window and the previous one, and estimate `previous × (fraction of previous window still inside the last 60 s) + current`. Two counters per client instead of one entry per request, close to sliding-log accuracy. A good default at large limits.
- **Token bucket**: a bucket holds up to N tokens and refills at a steady rate; each request takes one. It allows short bursts up to N while holding the long-run rate. Resilience4j's limiter is close to this. **Bucket4j** implements it on top of Redis (and other stores) if you'd rather not write Lua.

| Algorithm | Edge burst | Memory per client | Notes |
|---|---|---|---|
| Fixed window | up to 2× | 1 counter | simplest; fine for abuse protection |
| Sliding window log | none | 1 entry per allowed request | exact; costly for big limits |
| Sliding window counter | small, estimated | 2 counters | good default for big limits |
| Token bucket | bursts up to bucket size, by design | 2 numbers | smooth rate with controlled bursts |

---

## Answering a rejected request

Return **`429 Too Many Requests`** with a **`Retry-After`** header (seconds until the client may try again), and log or count the rejection: a sudden rise in 429s is a signal worth an alert (module 17). Don't return 500 or 503: those tell the client the *server* is broken, and well-written clients retry them, which is the opposite of what you want.

---

## When to use it / when not to

- **Use a shared (Redis) limiter** when the limit is per client and must hold however many instances run: public API quotas, login attempts per account, "send at most 3 password-reset emails per hour".
- **An in-process limiter is fine** when the limit protects something per instance, or when "roughly N × limit" is acceptable, like capping your own calls to a downstream (13-12).
- **Rate limiting at the gateway** (13-14) or a reverse proxy is often better than in every service: one place, applied before the request costs you anything. Spring Cloud Gateway's limiter uses Redis and a token bucket.
- **Decide what happens when Redis is down.** *Fail open* (allow everything) keeps the API up but unprotected; *fail closed* (reject everything) protects the downstream and takes the API down. For abuse protection, fail open with an alert; for a hard financial or third-party quota, fail closed.

---

## Common pitfalls

- **`INCR` and `EXPIRE` as two calls.** A crash between them leaves a key with no TTL and the client blocked forever. One Lua script, or put the window number in the key.
- **Read, decide, write as separate calls.** Concurrent requests both pass the check. Use a Lua script.
- **Sorted-set members that aren't unique.** Requests in the same millisecond collapse into one entry and the limiter undercounts. Add a random suffix.
- **Limiting per instance and assuming per client.** The real limit becomes instances × limit and changes as you scale.
- **Rate-limiting by IP only.** Thousands of users behind one office or mobile-carrier IP share a limit; one attacker with many IPs doesn't. Limit by API key or user ID where you have one.

---

## Code examples

`CODE_EXAMPLES/10-redis-rate-limiting/rate-limit-demo/`. Run with `mvn -q test` (Docker running). Read in this order:

1. `src/test/java/com/example/ratelimit/InProcessLimiterTest.java` — two in-process limiters allow 10 for a limit of 5; two Redis-backed limiters allow 5.
2. `src/main/java/com/example/ratelimit/IncrThenExpireLimiter.java` — the broken two-command fixed window.
3. `src/main/java/com/example/ratelimit/FixedWindowLimiter.java` — the fixed window as one Lua script, with the window number in the key.
4. `src/test/java/com/example/ratelimit/FixedWindowTest.java` — the never-expiring key, the always-present TTL, and the edge burst.
5. `src/main/java/com/example/ratelimit/SlidingWindowSeparateCallsBroken.java` — the sliding log as three separate calls.
6. `src/main/java/com/example/ratelimit/SlidingWindowLogLimiter.java` — the sliding log as one Lua script.
7. `src/test/java/com/example/ratelimit/SlidingWindowTest.java` — no edge burst, memory per request, the race, and 50 concurrent requests letting in exactly 5.

`RedisTestBase.java` starts one Redis container for all the test classes and empties it before each test.

---

## Try this yourself

1. In `IncrThenExpireLimiter`, put the window number in the key, as `FixedWindowLimiter` does, and re-run the crash test. Is the client still blocked forever? What's still wrong with the key that never expires?
2. Implement the sliding window counter: keys for the current and previous window, and the weighted estimate. Run `fixedWindow_boundaryBurst_twiceTheLimitInOneSecond`'s scenario against it. How many of the 10 get in?
3. Change `SlidingWindowLogLimiter` to use `"" + nowMillis` as the member and write a test that sends 8 requests in the same millisecond. How many are allowed, and why?

## Self-check

1. Your service runs 4 instances, each with an in-process limit of 100 requests per minute per client. What's the real limit, and what happens to it during a scale-up to 10 instances?
2. Explain how `INCR` followed by `EXPIRE` can block a client permanently, and two different ways to prevent it.
3. A limit of 5 per minute lets 10 requests through in under a second. Which algorithm is in use, and which one would stop it? What does the other one cost?
