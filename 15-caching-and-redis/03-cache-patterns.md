# Cache Patterns

There are only a few standard ways to arrange a cache and a database, and they differ on two questions: **who loads the cache on a miss** (your code, or the cache itself), and **what happens on a write** (update the database only, both together, or the cache first and the database later). This topic shows each pattern in a few lines of Java, then the races that make the most common one, cache-aside, go stale. Those races are why "update the database, then *delete* the cache entry" is the standard advice.

The demo is plain Java. The races are replayed step by step, so they happen on every run instead of once a month in production.

---

## The problem this solves

Topic 02's annotations hide one particular arrangement: the application checks the cache, loads from the database on a miss, and evicts on writes. That's a good default, but it's a choice. Once there's a cache, every read and every write has to decide in what order to touch two separate systems that can't be updated atomically together. Get the order wrong and the cache holds a value the database no longer has, with nothing to fix it.

---

## How it works: the patterns

### Cache-aside (lazy loading): the application does everything

The cache sits *beside* the database. Your code talks to both:

```java
public Long get(String sku) {
    Long cached = cache.get(sku);
    if (cached != null) return cached;     // hit
    Long loaded = database.find(sku);      // miss: your code loads...
    if (loaded != null) cache.put(sku, loaded);   // ...and fills the cache
    return loaded;
}

public void updateThenDelete(String sku, long cents) {
    database.save(sku, cents);             // write the source of truth
    cache.delete(sku);                     // drop the copy; the next read reloads it
}
```

`CacheAsideTest`: three reads cost one database read; after an update the entry is gone and the next read gets the new price. Only data someone actually reads gets cached, and if the cache is down, reads can fall back to the database. This is the pattern `@Cacheable` + `@CacheEvict` implement, and the one you'll use most.

Notice that the write doesn't put the new value into the cache. Writing only to the database and leaving the cache to be filled by the next read is called **write-around**. It's the usual write half of cache-aside, and it avoids filling the cache with data that's written but rarely read.

### Read-through: the cache loads on a miss

The caller only ever talks to the cache. The cache is configured with a *loader* and calls it itself on a miss:

```java
ReadThroughCache prices = new ReadThroughCache(database::find);
prices.get("sku-1");    // miss: the cache calls database.find
prices.get("sku-1");    // hit
```

`readThrough_theCallerOnlyAsksTheCache_theCacheLoadsOnAMiss`. The logic is the same as cache-aside; it just lives in the cache instead of in every caller. Caffeine's `LoadingCache` (topic 05) works this way.

### Write-through: write both, synchronously

Every write updates the database and then the cache, before returning:

```java
public void put(String sku, long cents) {
    database.save(sku, cents);   // if this throws, the cache is never touched
    cache.put(sku, cents);
}
```

`writeThrough_theCacheAndTheDatabaseAgreeAfterEveryWrite`: the next read is a hit with the new value. `writeThrough_whenTheDatabaseWriteFails_theCacheIsNotChangedEither`: the database comes first, so a failed write doesn't leave a value in the cache that was never stored. The cost: every write pays for two systems, and data that's written but never read still fills the cache.

### Write-behind (write-back): write the cache now, the database later

Writes go to the cache and return immediately. A background job flushes them to the database in batches:

```java
public void put(String sku, long cents) {
    cache.put(sku, cents);
    pendingWrites.put(sku, cents);   // a later write to the same key replaces this one
}

public void flush() {                // a scheduler calls this every few seconds
    pendingWrites.forEach(database::save);
    pendingWrites.clear();
}
```

`writesReturnWithoutTouchingTheDatabase_andRepeatedWritesCoalesceIntoOne`: ten writes to the same key become **one** database write. That's the attraction: very fast writes, and much less database load for things that change constantly (view counters, "last seen" timestamps, game scores).

The price is in the next two tests:

- `untilTheFlush_anyoneReadingTheDatabaseSeesTheOldValue`: a report, another service, or someone at a SQL console sees 10.00 while the app shows 8.00.
- `theProcessDiesBeforeTheFlush_theWritesAreGone`: two writes are pending, the JVM is killed before `flush()`, and the database never hears of them. **The cache has become the only copy of your data**, and caches are not built to be that.

Use write-behind only for data you can afford to lose a few seconds of, or with a cache that is itself durable and replicated.

---

## Broken: the cache-aside races

Cache-aside has two separate steps on each side: a read is "read the database, then fill the cache", and a write is "write the database, then delete the cache entry". Two requests running at once can interleave those steps. `CacheAside` has two **pause points** where a real thread can stall (a GC pause, a slow network packet, the scheduler running something else). In production they do nothing. Each test in `CacheAsideRaceTest` puts "what the other request did meanwhile" there:

```java
prices.onceBetweenWriteSteps(() -> prices.get("sku-1"));   // a reader arrives in the middle of the write
prices.deleteThenUpdate("sku-1", 800);
```

Every test starts with the price at 10.00 in the database and nothing cached.

### Race 1: delete the cache first, then write the database

```text
writer                               reader
------                               ------
cache.delete(sku-1)
                                     cache.get -> miss
                                     database.find -> 1000 (not written yet)
                                     cache.put(1000)
database.save(800)
```

`deleteThenUpdate_aReadInTheGapPutsTheOldValueBack_andItStaysForever`: the database says 8.00, the cache says 10.00, and every later read is a hit on 10.00. Nothing will delete it again. The window is the whole duration of the database write, which is long, so under real traffic on a popular key this happens often. **Never delete before writing.**

### Race 2: write the database, then *set* the cache (instead of deleting)

```text
writer A (900)                       writer B (800)
--------------                       --------------
database.save(900)
                                     database.save(800)
                                     cache.put(800)
cache.put(900)
```

`updateThenSetCache_twoWritersFinishOutOfOrder_theCacheKeepsTheOlderValue`: B wrote the database last, A wrote the cache last. The database says 8.00, the cache says 9.00, permanently. This is why cache-aside **deletes** on write instead of writing the new value: a delete can't put the wrong value in; at worst it removes an entry that's already gone. (`@CachePut` from topic 02 has this race when two writes for the same key overlap.)

### Race 3: even the recommended order has a (much smaller) window

Write the database, then delete. The only interleaving that still goes wrong:

```text
reader                               writer
------                               ------
cache.get -> miss
database.find -> 1000
                                     database.save(800)
                                     cache.delete -> nothing to delete
cache.put(1000)
```

`updateThenDelete_isStaleOnlyIfOneReadStraddlesTheWholeWrite`. For this to happen, the entry must be missing *and* the reader's gap between reading the database and filling the cache must be longer than the writer's whole write plus delete. Reads are normally much faster than writes, so it's rare, but rare times millions of requests is not never.

### Fixes

- **Write the database, then delete the cache entry.** This removes races 1 and 2 entirely.
- **Always set a TTL** (topic 04). Whatever race or forgotten writer leaves a wrong value, it's corrected when the entry expires. The TTL is the upper bound on how long you can be wrong, so pick it from "how stale can this be?", not from "how fast do we want it?".
- **Delayed double delete.** Delete, then delete again a short time later (longer than a typical read). The second delete removes what a straddling reader put back (`aSecondDeleteAfterTheSlowReadHasFinished_removesTheStaleValue`). It narrows race 3 further, at the cost of a scheduled task per write.
- **Drive deletes from the database's change log.** Tools that read the database's change log (change data capture, such as Debezium) delete cache entries after the commit. They catch writers that bypass your service too (topic 01). That's more infrastructure, worth it only when stale reads really matter.

If a value must *never* be stale, don't serve it from a cache at all. Read it from the database.

---

## When to use which

| Pattern | Use it when | Watch out for |
|---|---|---|
| **Cache-aside** + write-around | The default for read-heavy data | The races above. Delete after writing, and set a TTL. |
| **Read-through** | You want the loading logic in one place (a `LoadingCache`, a caching library) | Same races on the write side |
| **Write-through** | Data is read soon after it's written, and every read should be a hit | Slower writes; caching data nobody reads |
| **Write-behind** | Very write-heavy data you can lose a few seconds of (counters, presence, metrics) | Data loss on crash; the database lags; other readers see old data |

---

## Common pitfalls

- **Deleting the cache before writing the database.** A read in between caches the old value forever. Write first, then delete.
- **Updating the cache with the new value on every write.** Two concurrent writers can leave the older one cached. Delete instead.
- **No TTL "because we invalidate".** Invalidation is never perfect: races, failed deletes, other writers. The TTL is the safety net.
- **Write-behind for data you can't lose.** Orders, payments, anything a user was told was saved. Write those to the database before saying "saved".

---

## Code examples

`CODE_EXAMPLES/03-cache-patterns/cache-patterns-demo/`. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/cachepatterns/Database.java` and `Cache.java` — the source of truth and a stand-in for Redis.
2. `src/main/java/com/example/cachepatterns/CacheAside.java` — read and the three write orders, with the two pause points.
3. `src/test/java/com/example/cachepatterns/CacheAsideTest.java` — the pattern working.
4. `src/test/java/com/example/cachepatterns/CacheAsideRaceTest.java` — the three races and the second delete.
5. `src/main/java/com/example/cachepatterns/ReadThroughCache.java` and `WriteThroughCache.java`, then `src/test/java/com/example/cachepatterns/ReadWriteThroughTest.java`.
6. `src/main/java/com/example/cachepatterns/WriteBehindCache.java`, then `src/test/java/com/example/cachepatterns/WriteBehindTest.java` — coalescing, lag, and the crash.

---

## Try this yourself

1. Write a test where the reader arrives in the middle of `updateThenDelete` (use `onceBetweenWriteSteps`) instead of during its own read. Is the result stale? Explain why this interleaving is safe.
2. Add a TTL to `Cache`: store each entry with the time it was written and treat it as missing after a given `Duration`. Use a clock you can move forward in the test instead of sleeping. Show that race 1's stale value disappears after the TTL.

## Self-check

1. In cache-aside, why does a write delete the cache entry instead of putting the new value into it? Draw the interleaving that goes wrong otherwise.
2. "Delete the cache, then update the database" looks safe because the next read reloads. Walk through why it isn't.
3. A teammate proposes write-behind for order placement to make checkout faster. What do you tell them?
