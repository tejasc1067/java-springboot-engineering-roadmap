# Why Cache, and What It Costs

A **cache** is a copy of data kept somewhere faster than where the data really lives, so the next request for it doesn't pay the full price again. Backend services cache database rows, results of slow calls to other services, and expensive computations. A cache can make a service many times faster and protect a database from load it couldn't survive. It also creates a second copy of your data that can be wrong, which is where most caching bugs come from.

The demo is plain Java: a "database" that counts its reads and a map-based cache that counts hits and misses.

---

## The problem this solves

A product page shows a price. Ten thousand people view the ten most popular products every minute. Without a cache, that's ten thousand identical database queries a minute returning the same ten rows. Each one costs a network round trip, a connection from the pool (module 04 topic 17), and database CPU. When traffic doubles, the database is the first thing to fall over.

Most real traffic looks like this: a small number of keys get most of the requests. That repetition is what a cache exploits. Keep the answer after the first request, and the next 999 are served from memory.

---

## How it works

Every cache lookup is either a **hit** (the value is in the cache, return it) or a **miss** (it isn't, so load it from the source and usually store it for next time):

```java
public long get(String sku, Function<String, Long> loadFromSource) {
    Long cached = entries.get(sku);
    if (cached != null) {
        hits++;
        return cached;                       // hit: no database
    }
    misses++;
    long loaded = loadFromSource.apply(sku); // miss: pay the full price once
    entries.put(sku, loaded);
    return loaded;
}
```

The number that tells you whether a cache is worth having is the **hit ratio**: hits ÷ (hits + misses).

`HitRatioTest` runs the same 1000 requests three ways:

| Traffic | Database reads | Hit ratio |
|---|---|---|
| No cache | 1000 | — |
| Cache, 10 popular products (`popularKeys_highHitRatio_fewDatabaseReads`) | 10 | 0.99 |
| Cache, 1000 products each asked for once (`keysThatNeverRepeat_zeroHitRatio_theCacheOnlyCostsMemory`) | 1000 | 0.0 |

The third row is the important one. A cache in front of data nobody asks for twice saves nothing. It costs a lookup on every request, and it ends up holding 1000 entries that will never be read.

### How much faster is "faster"?

Rough orders of magnitude for one lookup, on typical server hardware in one data centre:

| Where the data is | Roughly |
|---|---|
| A `HashMap` in your own JVM (a *local* cache, topic 05) | well under a microsecond |
| Redis on another machine in the same data centre (a *shared* cache, topic 06) | a few hundred microseconds, mostly the network round trip |
| A simple indexed query in Postgres on another machine | about a millisecond, often more |
| A slow query, a call to another service, a third-party API | tens of milliseconds to seconds |

A local cache is about a thousand times faster than a network hop. Redis is faster than the database mainly because it does less work per request, not because the network is shorter. The bigger the gap between the cache and the source, the more a hit is worth.

---

## What a cache costs

### 1. Staleness: the copy goes out of date

The cache holds a *copy*. When the real data changes, the copy doesn't change with it. `StaleReadTest.updateWithoutInvalidating_usersKeepSeeingTheOldPrice`:

```java
service.priceOf("sku-1");                            // cached at 10.00
service.changePriceWithoutEvicting("sku-1", 800);    // the database now says 8.00

service.priceOf("sku-1");                            // 1000 - still 10.00
```

This cache never expires, so the old price is served until the process restarts. Users see one price on the product page and get charged another at checkout.

The fix is **invalidation**: when the data changes, remove the cached copy so the next read loads the new value (`updateThenEvict_nextReadReloadsTheNewPrice`):

```java
public void changePrice(String sku, long cents) {
    database.savePrice(sku, cents);
    cache.evict(sku);
}
```

### 2. A second source of truth

Invalidation only works if *every* write goes through the code that invalidates. In `aWriterThatBypassesTheService_leavesTheCacheStaleEvenWhenTheServiceEvictsCorrectly`, the service is correct, but a batch job writes the table directly and the cache still serves 10.00. In real systems the writers you forget are SQL scripts run by hand, a nightly import job, another service sharing the database (module 13 topic 04 explains why that's a problem anyway), and an admin tool.

There's an old joke that the two hard problems in computer science are naming things and cache invalidation. Topics 03 and 04 cover the standard answers: delete on write, and a **TTL** (time to live) so that even a missed invalidation fixes itself after a bounded time.

### 3. Memory

Every entry takes memory, in your JVM's heap for a local cache or in Redis for a shared one. An unbounded cache like the demo's grows until something runs out. Topic 04 adds size limits and eviction.

### 4. Cold start

A local cache is empty after every restart and every deploy. `coldStart_aFreshCacheSendsEveryFirstRequestToTheDatabase`: after a "restart", each of the 10 popular products is a miss again. If the database is only coping *because* of the cache, a deploy that restarts every instance at once sends the full uncached load to it at once. Topic 08 covers this and its relatives (stampedes and avalanches).

### 5. Complexity

The code now has two places data can come from, and a new class of bug that only appears under concurrency (topic 03) or across several instances (topic 05). Tests that pass with one instance and an empty cache can hide them.

---

## When to cache / when not to

Cache when:

- The same data is **read much more often than it changes**: product details, prices, configuration, reference data, a user's permissions.
- Loading it is **slow or expensive**: a heavy query, another service, a rate-limited third-party API.
- Serving a **slightly old value is acceptable**, for a time you can name ("a price can be up to 60 seconds old on the product page").

Don't cache when:

- Every request asks for something different (search results with arbitrary filters, one-off reports): the hit ratio will be near zero.
- The data must be exactly current: an account balance before a withdrawal, stock before confirming an order. Read those from the source of truth. A cached value can still be shown on a page, but a decision that moves money or stock must not depend on it.
- The database isn't actually the bottleneck. Measure first. An index or a better query is often the smaller fix.

---

## Common pitfalls

- **Caching without invalidation and without a TTL.** The first price change shows the bug. Every cache needs an answer to "how does this entry get fixed when the data changes?"
- **Forgetting other writers.** Invalidation in the service doesn't help when a script writes the table. Add a TTL as a safety net.
- **Caching data with a low hit ratio.** Measure hits and misses (topic 05 shows Caffeine's statistics). If the ratio is low, remove the cache.
- **Using cached data for decisions.** Show cached data; decide on fresh data.

---

## Code examples

`CODE_EXAMPLES/01-why-cache-and-what-it-costs/why-cache-demo/`. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/whycache/PriceDatabase.java` — the source of truth, counting its reads.
2. `src/main/java/com/example/whycache/PriceCache.java` — a map with hit and miss counters.
3. `src/main/java/com/example/whycache/PriceService.java` — reads through the cache; the update without and with eviction.
4. `src/test/java/com/example/whycache/HitRatioTest.java` — no cache, popular keys, keys that never repeat, cold start.
5. `src/test/java/com/example/whycache/StaleReadTest.java` — the stale price, the eviction fix, and the writer that bypasses it.

---

## Try this yourself

1. In `HitRatioTest`, send 1000 requests where 80% go to 10 products and 20% go to 900 others. What hit ratio do you get, and how many entries does the cache end up holding? Is it worth it?
2. Give `PriceCache` a maximum size of 10 entries: when it's full, remove any entry before adding a new one. Rerun the "keys that never repeat" test. What happened to memory, and what happened to the hit ratio?

## Self-check

1. A cache has a hit ratio of 0.95 and the database query takes 20 ms. Roughly how many database queries does it save per 1000 requests, and what would a ratio of 0.05 tell you?
2. You evict the cache entry every time the service updates a price, and users still see old prices. Name two ways that can happen.
3. Why might a deploy that restarts every instance overload a database that was fine a minute earlier?
