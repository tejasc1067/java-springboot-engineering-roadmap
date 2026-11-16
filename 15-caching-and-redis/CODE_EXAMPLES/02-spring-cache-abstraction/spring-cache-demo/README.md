# spring-cache-demo

Spring's cache annotations on a counted in-memory repository, with Boot's default `ConcurrentMapCacheManager`.
No cache library, no Docker.

- `CacheConfig` — `@EnableCaching`. Without it, nothing is cached and nothing complains.
- `ProductService` — `@Cacheable` (with `unless`), `@CachePut`, `@CacheEvict` (one key, `allEntries`, `beforeInvocation`), plus the broken variants.
- `ProductPageService` — the self-invocation fix: call the cached method from another bean.
- `ProductNameService` — two methods sharing one cache name and the default key.

Tests:

- `CacheAnnotationsTest` — Boot picks `ConcurrentMapCacheManager`; hits, misses, `@CachePut`, `@CacheEvict`, `allEntries`.
- `NullCachingTest` — `null` is cached by default, so a product created later stays "not found"; `unless = "#result == null"` fixes it.
- `SelfInvocationTest` — `this.findById(...)` skips the cache; the same call from another bean doesn't.
- `KeyCollisionTest` — a `String` method reading a `Product` from a shared cache: `ClassCastException`; a two-parameter `@CacheEvict` whose default key never matches; separate cache names.
- `EvictOnExceptionTest` — an exception after the write skips the eviction; `beforeInvocation = true` doesn't.
- `NoEnableCachingTest` — the same beans with and without `@EnableCaching`.

Run:

```
mvn -q test
```
