# TTL, Eviction, and Invalidation

A cache holds a *copy* of data, and a copy goes out of date the moment the original changes. A cache also lives in limited memory. This topic covers the three ways an entry leaves a cache: it **expires** (its time-to-live runs out), it's **evicted** (the cache is full and something has to go), or it's **invalidated** (your code deletes it because the data changed). Getting these three right is most of the work of caching. Getting them wrong gives you users who see old prices, or a service that runs out of memory.

The demo uses Caffeine, the standard in-process cache for Java, with no Spring, so the rules are visible on their own. Topic 05 plugs the same cache into Spring.

---

## The problem this solves

You cache product prices to save a database query on every page view (topic 01). Then:

- **Someone changes a price.** The database says 15.00, the cache still says 10.00. How long do customers see 10.00? Without a TTL and without invalidation: until the app restarts.
- **You cache search results by query text.** Every new search is a new key. The cache never removes anything, the heap fills up over a few days, and the service dies with `OutOfMemoryError`.
- **A nightly report reads every product once.** If the cache throws out the oldest entries to make room, the report pushes out the hundred products customers actually look at, and the next morning every page is a cache miss.

Each of these has a specific setting or a specific line of code that fixes it.

---

## How it works

### Expiry: how long an entry is allowed to live

Caffeine reads time through a `Ticker`. The demo passes a `FakeTicker` that the test moves by hand, so "wait five minutes" takes no time at all and the tests never sleep:

```java
LoadingCache<String, Long> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .ticker(clock)                 // in production you leave this out: the real clock is used
        .build(db::load);              // on a miss, call db.load(key) and cache the result
```

`TtlTest.staleRead_cacheKeepsServingTheOldPriceUntilTheTtlRunsOut`:

```java
db.update("book", 1_500);                        // the price changes in the database
clock.advance(Duration.ofMinutes(4));
cache.get("book");                               // 1_000: stale, still "fresh" to the cache

clock.advance(Duration.ofMinutes(1));
cache.get("book");                               // 1_500: 5 minutes old, expired, reloaded
```

This is the deal a TTL makes: **you accept data up to N minutes old in exchange for not querying the database.** The TTL is the maximum staleness, and nothing in the cache tells the user they're looking at old data.

There are two kinds of expiry, and the difference matters:

| | `expireAfterWrite(5m)` | `expireAfterAccess(5m)` |
|---|---|---|
| Timer starts | when the value was loaded or written | at the last read *or* write |
| A read... | doesn't change anything | resets the timer |
| Meaning | "never older than 5 minutes" | "remove if nobody used it for 5 minutes" |

#### Broken: `expireAfterAccess` on data that changes

`expireAfterAccess_aHotKeyIsNeverRefreshed` reads a popular product once a minute for an hour after its price changed:

```java
for (int minute = 0; minute < 60; minute++) {
    clock.advance(Duration.ofMinutes(1));
    cache.get("book");                           // 1_000 every time
}
db.queries();                                    // 1: it never reloaded
```

Every read resets the idle timer, so the entry never expires. The more popular a product is, the longer it stays wrong. Here it's an hour, and with steady traffic it's forever.

#### Fixed: `expireAfterWrite` caps the age

The same hour with `expireAfterWrite(5m)` (`expireAfterWrite_capsHowStaleAHotKeyCanGet`): 4 stale reads, then it reloads every 5 minutes, 13 database queries instead of 60.

Use `expireAfterWrite` whenever the data can change. `expireAfterAccess` is for freeing memory from entries nobody uses, on data that never goes stale (for example, a parsed template), or alongside `expireAfterWrite`, never instead of it.

### Choosing a TTL

Don't pick "5 minutes" because everyone does. Ask: **how wrong can this data be, and for how long, before it hurts?**

| Data | Staleness that's acceptable | TTL |
|---|---|---|
| Country list, feature catalogue | hours or days | 1–24 h |
| Product description, user's display name | a few minutes | 5–15 min |
| Price, stock count shown on a listing page | seconds to a minute (checkout re-checks) | 10–60 s, plus invalidation |
| Account balance, stock at checkout | none | don't cache it, or invalidate on every write and read from the database for the decision |

Even a 10-second TTL helps a lot. If a product is read 200 times a second, a 10-second TTL turns 2,000 queries into 1.

### Invalidation: delete the copy when you change the original

A TTL bounds staleness. **Invalidation** removes it for the instance that made the change. Whatever code updates the data also deletes the cached copy (`InvalidationTest`):

```java
// Broken: the database changes, the cache doesn't know
private void updatePriceBroken(String sku, long cents) {
    db.update(sku, cents);
}

// Fixed: update the database, then delete the cached entry
private void updatePrice(String sku, long cents) {
    db.update(sku, cents);
    cache.invalidate(sku);
}
```

With the broken version, `cache.get("book")` returns the old price for the whole TTL, an hour in the demo. With the fix, the next read misses and loads the new price.

Two rules:

- **Delete, don't update.** `cache.invalidate(key)` and let the next read load it, instead of `cache.put(key, newValue)`. Two concurrent writers that each `put` can leave the *older* value in the cache. Topic 03 goes through the write paths in detail.
- **Database first, then cache.** If you delete first, a reader can reload the old value between your delete and your database write, and it stays cached until the TTL.

**Keep the TTL even when you invalidate.** Invalidation misses things: a write from another service, a manual SQL fix, a bug in one write path, another instance's local cache (topic 05). The TTL is the safety net that makes every one of those mistakes heal itself.

In Spring you don't call `invalidate` yourself. `@CacheEvict` does it (topic 02).

### Eviction: what goes when the cache is full

Memory is finite. A cache without a size limit is a memory leak with a nice name. `EvictionTest.unboundedCache_growsWithEveryDistinctKey` puts 50,000 one-kilobyte values into a cache with no limit, and all 50,000 are still there, about 50 MB that nothing will ever free. Key it by something unbounded (search text, user id, a timestamp) and it grows until the JVM dies.

```java
Caffeine.newBuilder()
        .maximumSize(1_000)        // at most 1,000 entries
        .build();
```

`boundedCache_staysAtItsMaximumSize`: same 50,000 puts, 1,000 entries left. (Caffeine evicts on a background thread by default. The demo passes `.executor(Runnable::run)` and calls `cleanUp()` only so the test sees the result immediately. You don't need that in real code.)

When the cache is full, it must pick a **victim**. Common policies:

- **LRU (least recently used):** remove the entry not used for the longest time. Simple, and what most people assume.
- **LFU (least frequently used):** remove the entry used the fewest times.
- **W-TinyLFU (Caffeine):** keep a compact count of how often each key is used, and only let a new entry in if it's used more often than the one it would replace.

The difference shows up with a **scan**: a batch job that touches thousands of keys exactly once. `pureLru_aOneOffScanPushesOutThePopularKey` builds a textbook LRU from `LinkedHashMap`, reads a popular product 20 times, then runs a 10,000-key scan through it. The popular product is gone: 10,000 keys were used more recently. `caffeine_keepsTheFrequentlyUsedKey_throughTheSameScan` runs the identical sequence through Caffeine with the same size limit, and the popular product survives, because each one-off key has been seen once and the popular one twenty times.

That's why you use Caffeine instead of writing your own `LinkedHashMap` cache: it gets better hit rates on real traffic and it's thread-safe.

Caffeine can also limit by **weight** (`maximumWeight` plus a `weigher`), when entries have very different sizes, for example a cache of images or documents.

---

## When to use which

| Setting | Use it for | Don't |
|---|---|---|
| `expireAfterWrite` | Anything that can change. Your default. | Set it longer than the staleness you can accept. |
| `expireAfterAccess` | Freeing memory from idle entries of data that never changes | Use it as the only expiry on data that changes |
| `maximumSize` / `maximumWeight` | Every cache. Always. | Leave it out "because the key set is small". Key sets grow. |
| Invalidation on write | Data your service writes, where readers must see changes quickly | Rely on it alone. Keep a TTL too. |

---

## Common pitfalls

- **No `maximumSize`.** The cache grows until `OutOfMemoryError`. Always bound it.
- **`expireAfterAccess` alone on changing data.** Popular entries never reload. Use `expireAfterWrite`.
- **Invalidation in some write paths but not others.** The admin screen evicts, the CSV import doesn't. A TTL limits the damage, so keep one.
- **Updating the cache in the write path instead of deleting the entry.** Concurrent writes can leave the older value cached. Invalidate, and let the next read load it.

---

## Code examples

`CODE_EXAMPLES/04-ttl-eviction-and-invalidation/ttl-demo/`. Run with `mvn -q test` (no Docker needed). Read in this order:

1. `src/main/java/com/example/ttl/PriceDatabase.java` — the "database": true prices and a query counter.
2. `src/main/java/com/example/ttl/FakeTicker.java` — the clock the tests move by hand.
3. `src/test/java/com/example/ttl/TtlTest.java` — a stale read until the TTL, `expireAfterAccess` never refreshing a hot key, `expireAfterWrite` capping it.
4. `src/test/java/com/example/ttl/InvalidationTest.java` — update without invalidation (stale for the whole TTL), then with it.
5. `src/test/java/com/example/ttl/EvictionTest.java` — unbounded vs bounded, then LRU losing the popular key to a scan while Caffeine keeps it.

---

## Try this yourself

1. In `TtlTest`, combine `expireAfterWrite(5m)` and `expireAfterAccess(1m)` in one builder. Read the key every 2 minutes. When does it reload, and why?
2. In `EvictionTest`, lower the popular product's reads from 20 to 1. Does Caffeine still keep it through the scan? What does that tell you about how W-TinyLFU decides?

## Self-check

1. A product's price was changed 3 minutes ago, and some customers still see the old one. The cache uses `expireAfterAccess(5m)`. Explain why they might see it for hours, and what you'd change.
2. You must cache a stock count that a listing page shows. Pick a TTL and say what else you'd do, and what the checkout must do regardless.
3. Your service's heap grows by 200 MB a day and drops back after every restart. It caches search results keyed by the search text, with `Caffeine.newBuilder().build()`. What's happening, and which two settings would you add?
