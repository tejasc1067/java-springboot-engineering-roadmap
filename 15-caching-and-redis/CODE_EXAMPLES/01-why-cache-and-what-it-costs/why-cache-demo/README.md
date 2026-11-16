# why-cache-demo

What a cache saves and what it costs, in plain Java with counted database reads:

- `PriceDatabase` — the source of truth. Every `findPrice` is counted.
- `PriceCache` — a map with hit and miss counters. Unbounded and never expires (topic 04 fixes both).
- `PriceService` — reads through the cache; `changePriceWithoutEvicting` (broken) vs `changePrice` (fixed).

Tests:

- `HitRatioTest` — no cache: 1000 requests, 1000 reads. 10 popular products: 10 reads, hit ratio 0.99. Keys that never repeat: hit ratio 0, 1000 useless entries. A restart: every first request goes to the database again.
- `StaleReadTest` — an update that doesn't evict leaves the old price cached forever; evicting fixes it; a writer that bypasses the service leaves it stale anyway.

Run:

```
mvn -q test
```
