# cache-patterns-demo

The standard caching patterns in plain Java, plus the cache-aside races replayed deterministically.

- `Database`, `Cache` — the source of truth (counted, can fail) and a stand-in for Redis.
- `CacheAside` — the app reads and writes both; three update orders, with pause points where a real thread could stall.
- `ReadThroughCache` — the cache loads on a miss; callers never see the database.
- `WriteThroughCache` — every write updates the database and then the cache before returning.
- `WriteBehindCache` — writes go to the cache and are flushed to the database later.

Tests:

- `CacheAsideTest` — miss then hits; update the database, delete the copy, next read reloads.
- `CacheAsideRaceTest` — delete-then-update: a read in the gap caches the old value forever. Update-then-set-cache: two writers leave the older value cached. Update-then-delete: stale only if one read straddles the whole write. A second, later delete clears it.
- `ReadWriteThroughTest` — read-through loads once; write-through keeps both in step; a failed database write leaves the cache unchanged.
- `WriteBehindTest` — 10 writes become 1 database write; the database is behind until the flush; a crash before the flush loses the writes.

Run:

```
mvn -q test
```
