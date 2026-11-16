# 15 — Caching and Redis

A cache keeps a copy of data somewhere faster than where it really lives, so the next request doesn't pay the full price again. Done well, a cache turns a 50 ms database query into a sub-millisecond lookup and lets a database survive traffic it never could alone. Done badly, it shows users old prices, serves one instance's data on another, melts the database the moment a popular key expires, and turns a simple bug into one that only happens in production.

This module teaches both halves: how to add caching to a Spring Boot service, and every way it goes wrong. It then goes past caching into the other jobs Redis does in backend systems: counters, distributed locks, rate limits shared across instances, messaging between instances, and shared HTTP sessions. Each topic shows the broken version first, as a test that demonstrates the bug, and then the fix.

## Who this is for

- Anyone who has finished modules 08–14 and has never put a cache in front of a database.
- Developers who have added `@Cacheable` and aren't sure why it sometimes does nothing, or why users see old data.
- Anyone running more than one instance of a service who has met "it works when I hit instance A".

## What you'll be able to do at the end

- [ ] Decide whether a piece of data is worth caching, and say what the cache will cost in staleness and complexity.
- [ ] Add `@Cacheable`, `@CachePut`, and `@CacheEvict` to a Spring service, and explain why calling a cached method from the same class skips the cache.
- [ ] Explain cache-aside, read-through, write-through, and write-behind, and the update-ordering race that leaves cache-aside stale for good.
- [ ] Choose a TTL and a size limit, and invalidate entries when the data changes.
- [ ] Use Caffeine as a fast local cache, and explain why local caches on two instances disagree.
- [ ] Use Redis's core data types (strings, hashes, lists, sets, sorted sets) and expiry from Java.
- [ ] Use Redis as Spring's cache, and avoid the serialization problems that break a deploy.
- [ ] Prevent cache stampedes, penetration, and avalanches.
- [ ] Explain why a Redis lock is harder than it looks (lease expiry, releasing someone else's lock) and when fencing tokens or a database constraint is the better answer.
- [ ] Build a rate limiter that holds across several instances.
- [ ] Choose between Redis Pub/Sub and Redis Streams, and use Pub/Sub to invalidate local caches on every instance.
- [ ] Share HTTP sessions across instances with Spring Session and Redis, and say when stateless tokens are the better choice.

If you can do all twelve, the module worked.

## Prerequisites

- **Modules 08 and 10 done.** You need Spring Boot configuration (08) and Spring Data repositories (10). Topic 02 relies on the proxy idea from module 02 topic 16 and module 07.
- **Module 14 topic 11 (Testcontainers).** From topic 06 on, the tests start a real Redis in Docker, exactly the way module 14 started Postgres.
- **Docker installed and running** for topics 06–12 (see [`SETUP.md`](../SETUP.md)). Topics 01–05 need no Docker. The Redis image (`redis:7.4-alpine`) is about 60 MB.
- **Module 13 topic 12** (rate limiting) is helpful before topic 10, which starts where it stops.

## How this module is organized

Topics 01–05 are **caching, without Redis**: why and when to cache (01), Spring's cache annotations (02), the standard caching patterns (03), keeping a cache fresh and bounded (04), and Caffeine as a local cache, including why local caches diverge across instances (05). These run with no Docker at all.

Topics 06–08 are **Redis as a cache**: what Redis is and its data types (06), Redis behind Spring's cache annotations (07), and the three classic ways a cache hurts the database it protects: stampede, penetration, and avalanche (08).

Topics 09–12 are **Redis beyond caching**: distributed locks (09), rate limiting across instances (10), Pub/Sub and Streams (11), and shared HTTP sessions (12).

```text
01-why-cache-and-what-it-costs        <- hit ratio, latency, the stale read nobody invalidated
02-spring-cache-abstraction           <- @Cacheable/@CachePut/@CacheEvict; self-invocation skips the cache
03-cache-patterns                     <- cache-aside, read/write-through, write-behind; the update-ordering race
04-ttl-eviction-and-invalidation      <- TTL, size limits, eviction; unbounded cache vs bounded
05-local-cache-with-caffeine          <- Caffeine, hit-ratio stats; two instances, two different answers
06-redis-fundamentals                 <- strings, hashes, lists, sets, sorted sets, TTL, atomic INCR, KEYS vs SCAN
07-redis-as-spring-cache              <- RedisCacheManager, per-cache TTL, serialization pitfalls
08-stampede-penetration-avalanche     <- thundering herd, missing keys, synchronized expiry
09-distributed-locks                  <- SET NX PX, safe release, lease expiry, fencing tokens, Redisson
10-redis-rate-limiting                <- per-instance limits multiply; fixed window vs sliding window
11-redis-pub-sub-and-streams          <- fire-and-forget vs durable; consumer groups and pending messages
12-spring-session-with-redis          <- logged out on the other instance; shared sessions; sessions vs tokens
```

```text
COMMON_MISTAKES.md       <- real caching and Redis bugs with code
INTERVIEW_QNA.md         <- interview-grade questions on caching strategy, consistency, Redis, locks, rate limits
```

## The toolkit

| Tool / library | What it does | First used in |
|---|---|---|
| Spring Cache abstraction (`spring-boot-starter-cache`) | `@Cacheable` and friends, independent of where the cache lives | topic 02 |
| Caffeine (`com.github.ben-manes.caffeine:caffeine`, Boot-managed) | a fast in-process cache with TTL, size limits, and stats | topic 04 |
| Redis 7.4 (`redis:7.4-alpine`) | an in-memory data store shared by every instance | topic 06 |
| Spring Data Redis (`spring-boot-starter-data-redis`, Lettuce client) | `StringRedisTemplate`, `RedisTemplate`, `RedisCacheManager`, listeners | topic 06 |
| Testcontainers + `@ServiceConnection(name = "redis")` | starts Redis from JUnit and points Spring at it | topic 06 |
| Redisson (`org.redisson:redisson`) | a Redis client with ready-made distributed locks | topic 09 |
| Spring Session (`spring-session-data-redis`, Boot-managed) | stores `HttpSession` in Redis | topic 12 |

The demos stay on Spring Boot 3.5.14 and Java 21, like modules 08–14.

## How to run the examples

Each topic has its own Maven project under `CODE_EXAMPLES/NN-topic-name/`. The tests are the lesson:

```bash
cd 15-caching-and-redis/CODE_EXAMPLES/02-spring-cache-abstraction/<demo-dir>
mvn -q test
```

From topic 06 on, the tests start a `redis:7.4-alpine` container themselves and remove it when they finish. To look inside Redis while experimenting, run one yourself with `docker run --rm -p 6379:6379 redis:7.4-alpine` and connect with `docker exec -it <container> redis-cli`.

## Checkpoint

You're done with this module when you can:

1. Name three things a cache costs besides memory, and give an example of data you wouldn't cache.
2. Explain why `@Cacheable` on a method does nothing when another method in the same class calls it, and fix it.
3. Write down the interleaving of two requests that leaves cache-aside stale until the TTL expires, and say which ordering of "update the database" and "delete the cache entry" is safer.
4. Explain why two instances with local Caffeine caches return different values after an update, and give two fixes.
5. Explain why `GET` then `SET` in Java loses updates while `INCR` doesn't.
6. Say what breaks after a deploy that renames a class whose instances are cached in Redis, and how to prevent it.
7. Explain a cache stampede, why `@Cacheable(sync = true)` stops it on Caffeine (per instance only) but not on Boot's default Redis cache, and what does stop it there.
8. Explain how a Redis lock can end up held by two processes at once, and what a fencing token does about it.
9. Show why a 100-requests-per-minute limit becomes 300 with three instances, and fix it with Redis.
10. Explain why a Pub/Sub subscriber misses messages a Stream consumer wouldn't.
11. Explain why a user is logged out when the load balancer sends them to another instance, and the two ways to fix it.

If those eleven feel comfortable, move to module 16, where Kafka and RabbitMQ take over the messaging that Redis does only lightly here.

## A note on what's *not* here

- **No managed cloud caches.** ElastiCache, Memorystore, and Azure Cache are Redis with a vendor's operations around it. They belong in a cloud roadmap.
- **No Redis operations.** Cluster sharding, Sentinel failover, persistence tuning, and memory policies get a paragraph where they matter to a developer, not a section.
- **No HTTP and CDN caching.** `Cache-Control`, ETags, and CDNs are covered in module 18 topic 05.
- **No JPA second-level cache.** Hibernate's L2 cache is a separate mechanism with its own pitfalls. The application-level caching here is what most teams use.
