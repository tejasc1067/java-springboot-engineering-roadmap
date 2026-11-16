# Local Cache with Caffeine

A **local** (in-process) cache keeps cached values in your application's own heap. Reading from it is a method call, with no network trip and nothing to serialize, so it's the fastest cache you can have. Caffeine is the standard choice for Java, and Spring Boot plugs it in behind `@Cacheable` with one dependency and one line of configuration.

The catch appears as soon as you run more than one instance of the service, which you almost always do: every instance has **its own** cache, and they don't know about each other. This topic sets Caffeine up, measures whether the cache is actually working, and then shows two instances disagreeing about a price.

---

## The problem this solves

Topic 02 used Spring's default cache, a plain `ConcurrentHashMap` per cache name. It has no TTL, no size limit, and no statistics: everything topic 04 said you must have. Caffeine adds all three without changing a single `@Cacheable` annotation.

Then the real-world problem: your service runs as 3 instances behind a load balancer (module 13, module 14 topic 12). An admin changes a price. The request lands on instance A, which updates the database and evicts its own cache. Instances B and C still have the old price cached. Customers refreshing the page see the new price, then the old one, then the new one, depending on which instance answers.

---

## How it works

### Switching Spring's cache to Caffeine

Add the `caffeine` dependency next to `spring-boot-starter-cache`. When Spring Boot sees Caffeine on the classpath, it creates a `CaffeineCacheManager` instead of the `ConcurrentMap` one. Configure it in `application.yml`:

```yaml
spring:
  cache:
    cache-names: prices
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=10m,recordStats
```

- `spec` is Caffeine's builder in one string: the same `maximumSize` and `expireAfterWrite` as topic 04, applied to every cache.
- `recordStats` turns on hit and miss counting. **Without it, every statistic stays at 0**, and a dashboard showing a 0% hit rate tells you nothing.
- `cache-names` creates the caches at startup. That matters for metrics: caches created lazily on first use aren't registered with Actuator.

The service code is ordinary Spring caching (topic 02):

```java
@Cacheable("prices")
public long price(String sku) {
    databaseReads.incrementAndGet();
    return jdbc.queryForObject("SELECT cents FROM price WHERE sku = ?", Long.class, sku);
}

@CacheEvict(cacheNames = "prices", key = "#sku")
public void updatePrice(String sku, long cents) {
    jdbc.update("MERGE INTO price (sku, cents) KEY (sku) VALUES (?, ?)", sku, cents);
}
```

If you need different settings per cache (a 30-second TTL for `prices`, a day for `countries`), declare a `CaffeineCacheManager` bean yourself and call `registerCustomCache(name, Caffeine.newBuilder()...build())` for each one.

### Is the cache working? Measure the hit rate

A cache that's rarely hit costs memory and adds staleness for nothing. `CacheStatsTest` reads the same price 10 times: 1 miss, then 9 hits.

```java
Cache<Object, Object> nativeCache = (Cache<Object, Object>) cacheManager.getCache("prices").getNativeCache();
CacheStats stats = nativeCache.stats();
stats.hitCount();    // 9
stats.missCount();   // 1
stats.hitRate();     // 0.9
```

With Actuator on the classpath, the same numbers are published as metrics, which is how you'll actually watch them in production:

```text
GET /actuator/metrics/cache.gets?tag=cache:prices&tag=result:hit     -> 9
GET /actuator/metrics/cache.gets?tag=cache:prices&tag=result:miss    -> 1
```

(module 08 topic 12 covers Actuator; module 17 puts these on a dashboard.) A hit rate below roughly 80% usually means the TTL is too short, the size is too small, or the keys rarely repeat.

### Broken: two instances, two caches, two answers

`TwoInstancesTest` starts **two complete Spring applications** in one JVM, both connected to the same in-memory H2 database (`jdbc:h2:mem:shop`), exactly like two instances of a service sharing one real database:

```java
a.updatePrice("book", 1_000);
a.price("book");                 // 1_000, now cached in A
b.price("book");                 // 1_000, now cached in B

a.updatePrice("book", 1_500);    // the admin's request happened to land on A

a.price("book");                 // 1_500: A evicted its own entry and reloaded
b.price("book");                 // 1_000: B never heard about it
```

`@CacheEvict` worked perfectly: it evicted the entry from the cache it can see, the one inside instance A. Nothing in Spring or Caffeine tells instance B. B serves the old price until its own TTL expires, up to 10 minutes here.

There's a second, quieter cost. `eachInstanceLoadsAndStoresItsOwnCopy`: one price read through both instances takes **2 database reads and 2 copies in memory**. With 10 instances, a 500 MB cache is 5 GB of heap across the fleet, and each instance has to warm up its own cache after every deploy.

### Fixes, from cheapest to strongest

1. **A short TTL.** It doesn't remove the disagreement, it bounds it. `ShortTtlTest` configures `expireAfterWrite(30s)` and moves a fake clock: instance B is stale at 29 seconds and correct at 30. If the business can live with "up to 30 seconds old", this is the whole fix, and it's the most common one.
2. **One shared cache: Redis** (topic 07). All instances read and evict the same entries, so an eviction by A is seen by B. The cost is a network call per read, and Redis becomes something you run and monitor.
3. **Tell the other instances.** Keep the local caches and broadcast "evict `book`" when anything changes, for example over Redis pub/sub (topic 11). Every instance evicts its own copy. Fast reads, near-immediate consistency, but more moving parts, and a lost message means a stale entry until the TTL (so keep the TTL).
4. **Both levels.** A small local cache with a few seconds' TTL in front of Redis: most reads never leave the JVM, and staleness is bounded by those few seconds.

A note on the demo: the short-TTL test passes `--spring.cache.caffeine.spec=` (empty) and supplies a `Caffeine` bean with a fake clock. Spring Boot uses a `Caffeine` bean only when no `spec` is set, and it has to be a command-line argument, because `SpringApplicationBuilder.properties(...)` sets only *defaults*, which `application.yml` overrides.

---

## When to use a local cache / when not to

**Good fit:**

- Data that changes rarely and where seconds or minutes of staleness are fine: country lists, feature flags, configuration, product descriptions.
- Very hot reads where even a 1 ms network trip to Redis matters.
- A single-instance service (but plan for when it isn't).

**Bad fit:**

- Data that must look the same from every instance right after a write: prices at checkout, stock levels, permissions. Use Redis (topic 07) or no cache.
- Large datasets: every instance holds a full copy.
- Per-user data on a service with many instances: each user's entry is cached on whichever instance they hit, and the hit rate drops as you add instances.

---

## Common pitfalls

- **Testing with one instance only.** Every test passes, and the bug appears only in production with 3 instances. If a cached value can change, ask "what do the *other* instances see?"
- **Forgetting `recordStats`.** The hit-rate metrics exist but are all 0. Add it to the `spec`.
- **Caches not declared in `cache-names`.** They work, but they don't appear in `/actuator/metrics/cache.gets`.
- **A long TTL "because we evict on update".** The eviction only reaches the instance that handled the update. The TTL is your real staleness bound for every other instance.

---

## Code examples

`CODE_EXAMPLES/05-local-cache-with-caffeine/caffeine-demo/`. Run with `mvn -q test` (no Docker needed). Read in this order:

1. `pom.xml` — `spring-boot-starter-cache` + `caffeine` (+ Actuator for metrics).
2. `src/main/resources/application.yml` — the Caffeine `spec` and the shared in-memory database.
3. `src/main/java/com/example/caffeine/PriceService.java` — `@Cacheable` read, `@CacheEvict` write.
4. `src/test/java/com/example/caffeine/CacheStatsTest.java` — Caffeine is picked automatically; hit rate via `stats()` and via `cache.gets` metrics.
5. `src/test/java/com/example/caffeine/TwoInstancesTest.java` — two app instances: one evicts, the other keeps serving the old price; two copies of every value.
6. `src/test/java/com/example/caffeine/ShortTtlTest.java` — a 30-second TTL bounds how long the other instance stays wrong.

---

## Try this yourself

1. Remove `recordStats` from the `spec` in `application.yml` and run `CacheStatsTest`. What do the stats and the metrics report now?
2. In `TwoInstancesTest`, add a third instance and a third `price("book")` call. Which instances are stale after `a.updatePrice`? How many database reads did the fleet do to cache one price?

## Self-check

1. Instance A handles `PUT /prices/book` and runs `@CacheEvict`. Why does instance B still return the old price, and for how long?
2. Name three ways to fix that, and say what each one costs.
3. Your dashboard shows a 35% hit rate on the `prices` cache. Give two possible causes and how you'd check each.
