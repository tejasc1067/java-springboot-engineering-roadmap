# ttl-demo

How entries leave a Caffeine cache: expiry, invalidation, and size eviction. Plain Caffeine, no Spring, and a
fake clock (`FakeTicker`), so "five minutes later" takes no time and nothing sleeps.

- `TtlTest`
  - `staleRead_cacheKeepsServingTheOldPriceUntilTheTtlRunsOut` — old price for 4 minutes, new one at 5.
  - `expireAfterAccess_aHotKeyIsNeverRefreshed` — read every minute for an hour: never reloads.
  - `expireAfterWrite_capsHowStaleAHotKeyCanGet` — same hour: 4 stale reads, a reload every 5 minutes.
- `InvalidationTest` — a write path that forgets the cache vs one that calls `invalidate`.
- `EvictionTest`
  - `unboundedCache_growsWithEveryDistinctKey` / `boundedCache_staysAtItsMaximumSize` — 50,000 entries vs 1,000.
  - `pureLru_aOneOffScanPushesOutThePopularKey` — a `LinkedHashMap` LRU loses a key read 20 times to a 10,000-key scan.
  - `caffeine_keepsTheFrequentlyUsedKey_throughTheSameScan` — Caffeine's W-TinyLFU keeps it.

Run (no Docker needed):

```
mvn -q test
```
