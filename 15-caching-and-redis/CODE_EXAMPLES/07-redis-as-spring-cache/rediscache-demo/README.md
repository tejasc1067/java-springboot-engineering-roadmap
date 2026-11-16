# rediscache-demo

`@Cacheable` backed by Redis (`redis:7.4-alpine` from Testcontainers), first on Boot's defaults, then configured
properly, then against entries left behind by an older version of the app.

- `DefaultJdkSerializationTest` — no configuration: a non-`Serializable` record fails every call; a `Serializable` one
  is stored as Java bytes (`\xac\xed...`) with `TTL -1`.
- `JsonCacheTest` (profile `json`) — second call served from Redis; the value is readable JSON under `v1:products::1`
  with a 10-minute TTL; caching `null` with nulls disabled throws unless the method has `unless = "#result == null"`.
- `DeployBreaksCacheTest` — an entry from a renamed class and one with a removed field both fail every read; bumping
  `app.cache.version` to `v2` ignores them; a `LoggingCacheErrorHandler` turns them into misses.
- `TwoInstancesTest` — two application contexts, one Redis: an update on A is what B reads.

Run (Docker running):

```
mvn -q test
```

The first run pulls `redis:7.4-alpine` and `testcontainers/ryuk`. The suite takes about 5 seconds. A couple of
`WARN` log lines from `LoggingCacheErrorHandler` are expected: that test reads a broken entry on purpose.
