# caffeine-demo

Caffeine as Spring Boot's cache, its statistics, and what happens with two instances each holding a local cache.

- `CacheStatsTest` — Boot picks `CaffeineCacheManager` because Caffeine is on the classpath; 10 reads give
  1 miss and 9 hits, in `stats()` and in the `cache.gets` metric Actuator publishes.
- `TwoInstancesTest` — two full Spring applications sharing one in-memory H2 database:
  - `localCaches_divergeAfterAnUpdate` — A updates and evicts; B keeps returning the old price.
  - `eachInstanceLoadsAndStoresItsOwnCopy` — one value, two database reads, two copies in memory.
- `ShortTtlTest` — with `expireAfterWrite(30s)` on a fake clock, B is stale at 29 s and correct at 30 s.

Run (no Docker needed):

```
mvn -q test
```
