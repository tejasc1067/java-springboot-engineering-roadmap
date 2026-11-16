# redis-basics-demo

Redis data types and commands through Spring's `StringRedisTemplate`, against a real `redis:7.4-alpine`
started by Testcontainers (`RedisContainerConfig`, wired in with `@ServiceConnection(name = "redis")`).

- `StringsAndCountersTest` — `SET`/`GET`; a lost update from read-modify-write in Java; 1,000 concurrent `INCR`s = 1,000.
- `DataTypesTest` — hash (user fields, `HINCRBY`), list (last 3 viewed), set (unique visitors), sorted set (leaderboard).
- `ExpiryTest` — key expiry; `TTL` returns -1/-2; `PERSIST`; a plain `SET` drops an existing TTL; `INCR` keeps it; `SET NX`.
- `ScanTest` — `SCAN` with a cursor finds 20 keys among 5,020 without blocking; `KEYS` gives the same answer in one blocking walk.

Run (Docker running):

```
mvn -q test
```

The first run pulls `redis:7.4-alpine` and `testcontainers/ryuk`. After that the suite takes about 3 seconds.
`ExpiryTest` sleeps 400 ms once: Redis expires keys on its own real clock.
