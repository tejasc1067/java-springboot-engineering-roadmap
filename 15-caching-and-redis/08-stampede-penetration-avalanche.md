# Stampede, Penetration, and Avalanche

A cache protects the database by answering most reads itself. That protection has three classic holes, and each one sends a burst of traffic straight to the database at the worst moment. A **stampede** is many requests missing on the *same* key at once. **Penetration** is requests for keys that *don't exist*, which never get cached. An **avalanche** is *many* keys expiring at the same moment. All three look fine in testing, where traffic is low, and appear in production as a database that falls over a few minutes after a deploy or a cache flush.

---

## The problem this solves

Say the cache serves 99% of 5,000 product-page reads per second, so the database sees 50 queries per second and is sized for maybe 300. Every one of the three problems below turns some of those 4,950 cached reads back into queries, all at once:

- the bestseller's entry expires, and the 200 requests arriving in the next 100 ms all miss (stampede);
- a scraper walks `/products/1` to `/products/9999999`, and none of the missing ids is ever cached (penetration);
- all 10,000 entries were loaded by one warm-up job, so they all expire in the same second (avalanche).

## How it works

The demo uses a Redis cache (topic 07) in front of `SlowDatabase`, which counts every load and can **hold loads open** so a test can line up many requests on one miss. `Herd.run(20, ...)` sends 20 concurrent requests for the same key while loads are held, and releases them once all 20 have reached the database, or after 2 seconds if they can't.

### Broken: stampede

```java
@Cacheable("prices")
public Long price(long productId) {
    return database.price(productId);
}
```

`StampedeTest.hotKeyMissing_everyConcurrentRequestHitsTheDatabase`: 20 concurrent requests for product 7, nothing cached yet, **20 database loads**. Each request checks Redis, sees nothing (the first load hasn't finished, so nobody has filled the entry), and runs the query itself. The slower the query, the wider that window, and a slow query is exactly what you cache.

In production this happens every time a hot key expires, and after every deploy or flush for every key. It's also called the *thundering herd* or *dog-piling*.

### Broken: `sync = true` on a Redis cache does nothing by default

Spring's answer to stampedes is `@Cacheable(sync = true)`:

```java
@Cacheable(cacheNames = "prices", sync = true)
public Long priceSync(long productId) { ... }
```

With `sync = true`, Spring doesn't do "get, then call the method, then put". It calls `cache.get(key, loader)` and leaves it to **the cache** to make sure only one caller runs the loader. Caffeine does that, per key. Spring Data Redis's default cache writer does **not take any lock**. `syncTrue_onADefaultRedisCacheManager_doesNotPreventIt`: 20 requests, **20 loads**, exactly as without `sync`. No warning, no error.

(This is Spring Data Redis 3.5, the version Boot 3.5 uses. What `sync = true` does depends entirely on the cache implementation and its version, so test it with concurrent requests, as this demo does.)

### Fixed: a locking cache writer

```java
@Bean
RedisCacheManager lockingCacheManager(RedisConnectionFactory connectionFactory) {
    return RedisCacheManager.builder(RedisCacheWriter.lockingRedisCacheWriter(connectionFactory))
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig().entryTtl(TTL))
            .build();
}

@Cacheable(cacheNames = "prices", sync = true, cacheManager = "lockingCacheManager")
public Long priceSyncWithLockingWriter(long productId) { ... }
```

`syncTrue_withALockingCacheWriter_loadsOnce`: 20 requests, **1 load**, and all 20 get the price. The locking writer sets a lock key in Redis (`prices~lock`) around the load. Every other caller waits, polling Redis, and then finds the value already cached.

Because the lock lives in Redis, it holds back **other instances too** (`SyncScopeTest.lockingRedisWriter_holdsBackOtherInstancesToo`: two cache managers, as two instances, 1 load). But read the lock's name again: it's per **cache name**, not per key, and every operation on that cache waits while it's held. `lockingRedisWriter_locksTheWholeCache_soOtherKeysWaitToo`: while product 1 is loading, a miss on product 2 waits, and so does a request for product 3, which is already cached. One slow load pauses every read of that cache, on every instance. Use it only for small caches with a few hot keys.

### How far does "only one load" reach?

| Cache | `sync = true` gives you |
|---|---|
| Caffeine (local, topic 05) | one load per key **per JVM**. With 10 instances, up to 10 loads. `localCacheSync_isPerJvm_soTwoInstancesLoadTwice`: two instances, 2 loads. |
| Redis, default writer | nothing: one load per request. |
| Redis, `lockingRedisCacheWriter` | one load at a time per **cache name** across all instances; meanwhile every other read of that cache waits, hits included. |

For most services, one load per instance is fine: 10 queries instead of 2,000. If even that's too many, or you can't use the locking writer, the other options are:

- **Refresh before expiry.** Reload hot entries in the background a little before their TTL runs out, so they never actually expire under load. Caffeine has this built in (`refreshAfterWrite`). With Redis you'd do it in a scheduled job (module 08 topic 13).
- **A per-key lock of your own** for the few keys that need it: `SET lock:price:7 <owner> NX PX 5000` (topic 09), with the losers waiting briefly and re-reading the cache.
- **Serve stale while reloading.** Keep the old value a bit past its "fresh" time and return it while one caller reloads.

### Broken: penetration

Lookups for things that don't exist always miss:

```java
@Cacheable(cacheNames = "products", unless = "#result == null")   // nulls aren't cached
public String find(long id) {
    return database.product(id);       // null for ids that don't exist
}
```

`PenetrationTest.missingProduct_isNeverCached_soEveryLookupHitsTheDatabase`: 100 lookups of id `999999`, **100 database loads**. An existing product, by comparison, is loaded once for 100 lookups. A bug that requests a deleted id in a loop, a scraper, or an attacker who has noticed this can drive load straight past the cache. (Topic 07 shows why `unless = "#result == null"` is there: with null values disabled, caching a `null` throws.)

### Fixed: cache "not found" too, briefly

Allow null values for this cache, and give them a much shorter TTL than real entries with a TTL function:

```java
"productsWithNegativeCaching", defaults.entryTtl((key, value) ->
        value == null ? Duration.ofSeconds(30) : TTL)
```

`negativeCaching_remembersNotFound_forAShortTime`: 100 lookups of the missing id plus one real product, **2 loads**. In Redis, the missing id's entry has a TTL of at most 30 seconds and the real product's up to 10 minutes. The short TTL matters: when that product is created, it should start showing up within 30 seconds, not 10 minutes. (Or evict the "not found" entry when the product is created, with `@CacheEvict` on the create method.)

Two more defences, outside the cache:

- **Validate before looking up.** Reject ids that can't exist (negative, wrong format, beyond the current maximum) with a 400 before they reach the cache or the database.
- **A Bloom filter** for huge key spaces: a compact structure in memory (or in Redis) that answers "definitely doesn't exist" or "might exist". Requests for "definitely doesn't exist" are answered immediately. It's worth it only at very large scale; mention it in an interview, and reach for negative caching first.

### Broken: `sync = true` and a TTL function together

The two fixes above don't combine the way you'd expect. On the `sync = true` path, Spring Data Redis works out the TTL **before** it loads the value, so the TTL function is always called with `value == null`. `withSyncTrue_theTtlFunctionNeverSeesTheValue_soFoundProductsGetTheShortTtl`: a product that exists gets the 30-second "not found" TTL. If you need both, use separate caches, or a TTL function that doesn't depend on the value.

### Broken: avalanche

```java
@Cacheable("prices")   // every entry: 10 minutes
```

`AvalancheTest.sameTtl_everyKeyExpiresInTheSameSecond` warms 200 prices in one go, as a warm-up job or the first minutes after a deploy do, and reads each key's remaining TTL from Redis. All 200 expire within the **same second**. Ten minutes later they all miss together and become 200 simultaneous queries, a stampede across every key at once. If they're reloaded together, they expire together again ten minutes after that.

The same thing happens when the whole cache disappears: Redis restarts without persistence, or someone runs `FLUSHALL`.

### Fixed: add jitter to the TTL

```java
"pricesWithJitter", defaults.entryTtl((key, value) ->
        TTL.plusSeconds(ThreadLocalRandom.current().nextLong(120)))   // 10 to 12 minutes
```

`ttlWithJitter_spreadsExpiryOverMinutes`: the same 200 keys now expire spread over two minutes, more than 60 seconds apart from first to last. The database sees a trickle of reloads instead of one spike. A random extra of 10–20% of the TTL is a common choice.

For the "whole cache gone" version: keep Redis highly available (replication, which is outside this roadmap), warm critical keys gradually rather than all at once, and protect the database itself with a limit on concurrent queries (a bulkhead, module 13 topic 12) so a cold cache slows responses down instead of taking the database down.

---

## When to use it / when not to

You don't need all of these everywhere. Ask, per cache:

- **Is any single key hot and slow to load?** Then stampede protection: `sync = true` on a local cache, or a locking strategy on Redis.
- **Can callers ask for ids that don't exist?** Public endpoints always can. Validate input and cache "not found" briefly.
- **Are many keys loaded at the same time?** After deploys, warm-ups, or batch jobs, yes. Add jitter. It costs one line.

---

## Common pitfalls

- **Assuming `@Cacheable(sync = true)` protects a Redis cache.** With the default writer it doesn't. Test it with concurrent requests and a counting loader.
- **Assuming `sync = true` is cluster-wide.** On a local cache it's per JVM: one load per instance.
- **The locking writer as a global fix.** It locks whole caches: while one key loads, every read of that cache waits, even cache hits. Use it for small caches with a few hot keys.
- **Negative-caching "not found" with the full TTL.** New items stay invisible for that long. Give misses a short TTL, or evict on create.
- **A fixed TTL on bulk-loaded data.** Everything expires together. Add jitter.

---

## Code examples

`CODE_EXAMPLES/08-stampede-penetration-avalanche/stampede-demo/`. Run with `mvn -q test` (Docker must be running). Read in this order:

1. `src/main/java/com/example/stampede/SlowDatabase.java` — counts loads and can hold them open.
2. `src/main/java/com/example/stampede/CacheConfig.java` — the default Redis cache manager with per-cache settings (nulls off, negative caching, jitter), and a second one with a locking writer.
3. `src/main/java/com/example/stampede/PriceService.java`, `ProductService.java` — the cached methods each test calls.
4. `src/test/java/com/example/stampede/Herd.java` — 20 concurrent requests on one missing key.
5. `src/test/java/com/example/stampede/StampedeTest.java` — 20 loads without `sync`, still 20 with `sync` on the default writer, 1 with the locking writer.
6. `src/test/java/com/example/stampede/SyncScopeTest.java` — Caffeine's sync is per JVM; the locking writer covers other instances, and blocks other keys, even cached ones.
7. `src/test/java/com/example/stampede/PenetrationTest.java` — 100 loads for a missing id, then 1 with negative caching; the `sync` + TTL function trap.
8. `src/test/java/com/example/stampede/AvalancheTest.java` — 200 keys expiring in the same second, then spread over minutes.

---

## Try this yourself

1. Change `Herd.run(20, ...)` in `syncTrue_withALockingCacheWriter_loadsOnce` to use two different product ids, alternating. How many loads do you expect, and how long does the test take compared with before? Why?
2. Add a `create(long id)` method to `ProductService` that evicts `productsWithNegativeCaching` for that id. Write a test: look up a missing id (cached as "not found"), create it, look it up again. Without the eviction, how long would the new product stay invisible?

## Self-check

1. Twenty requests miss on the same key at the same moment. Walk through why all twenty reach the database, and say what `@Cacheable(sync = true)` does about it on Caffeine, on a default Redis cache, and on a Redis cache with a locking writer.
2. Why is "not found" usually not cached, what does that cost when someone requests non-existent ids in a loop, and why should the fix use a short TTL?
3. A nightly job loads 50,000 entries with a 6-hour TTL. What happens at 6 hours, and what one-line change prevents it?
