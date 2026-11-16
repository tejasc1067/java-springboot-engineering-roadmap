# stampede-demo

The three ways a cache lets a burst through to the database, each counted with `SlowDatabase`, against a real Redis.

- `StampedeTest` — 20 concurrent misses on one key: 20 loads; still 20 with `@Cacheable(sync = true)` on Boot's
  default Redis cache manager; 1 with `RedisCacheWriter.lockingRedisCacheWriter`.
- `SyncScopeTest` — Caffeine's `sync` is per JVM (two instances, 2 loads); the locking Redis writer covers other
  instances (1 load) but makes every other read of the cache wait, hits included.
- `PenetrationTest` — 100 lookups of a missing id: 100 loads; with negative caching (nulls cached for 30 s): 1. With
  `sync = true` the TTL function never sees the value, so found products get the short TTL too.
- `AvalancheTest` — 200 keys loaded together expire within one second; with TTL jitter, over two minutes.

Run (Docker running):

```
mvn -q test
```

The suite takes about 10 seconds. The protected stampede tests wait 2 seconds on purpose: they give the other 19
requests every chance to reach the database before letting the one load finish.
