# Common mistakes — Caching and Redis

Real caching and Redis bugs, each one reproduced by a test in this module. Almost none of them fail loudly. The cache "works", the tests pass, and the problem shows up later as an old price, a database that falls over at 9am, a lock with two owners, or a user who keeps getting logged out.

---

## 1. `@Cacheable` with no `@EnableCaching`

```java
@Service
public class ProductService {
    @Cacheable("products")
    public Product findById(long id) { ... }
}
// no @EnableCaching anywhere
```

**What goes wrong:** the annotation is only metadata. Without `@EnableCaching` nothing creates the caching proxy or a `CacheManager`, so every call goes to the database, with no error. Nobody notices until a load test (topic 02's `NoEnableCachingTest`).

**Fix:** put `@EnableCaching` on a `@Configuration` class, and keep one test that asserts the second call doesn't reach the repository.

---

## 2. Calling a cached method from the same class

```java
@Service
public class ProductService {
    @Cacheable("products")
    public Product findById(long id) { ... }

    public List<Product> findAll(List<Long> ids) {
        return ids.stream().map(this::findById).toList();   // this.findById: no proxy, no cache
    }
}
```

**What goes wrong:** caching is done by a proxy around the bean. A call on `this` goes straight to the real object, so `findAll` hits the database every time (topic 02's `SelfInvocationTest`). It's the same bug as `@Transactional` and `@Async` on a self-call.

**Fix:** call the cached method from another bean (topic 02's `ProductPageService` injects `ProductService`).

---

## 3. A multi-parameter method's default key never matches

```java
@Cacheable("products")  public Product findById(long id)
@CacheEvict("products") public void changePrice(long id, long priceCents)
```

**What goes wrong:** `findById` stores the entry under `1`. `changePrice` evicts `SimpleKey [1,1999]`, which doesn't exist. The price stays stale, with no error (topic 02's `KeyCollisionTest`). Two methods with different return types sharing one cache name break in another way: one reads the other's entry and throws `ClassCastException`.

**Fix:** write the key out whenever methods must agree on an entry: `@CacheEvict(cacheNames = "products", key = "#id")`. Give methods with different return types their own cache names.

---

## 4. Caching "not found"

```java
@Cacheable("products")
public Product findById(long id) { return repository.findById(id).orElse(null); }
```

**What goes wrong:** Spring's default cache stores the `null`. A product created a minute later stays "not found" until the entry is evicted or the app restarts (topic 02's `NullCachingTest`).

**Fix:** `@Cacheable(cacheNames = "products", unless = "#result == null")`. If you cache misses on purpose to stop penetration (topic 08), give them their own short TTL.

---

## 5. Deleting the cache entry before writing the database

```java
cache.delete(sku);
database.save(sku, newPrice);
```

**What goes wrong:** a read between the two lines misses, loads the *old* row, and caches it after your delete. With no TTL, the old price stays forever (topic 03's `CacheAsideRaceTest`). Writing the new value with `cache.put` instead of deleting goes wrong too, when two writers finish in the opposite order to their database writes.

**Fix:** write the database first, then delete the entry. Always set a TTL as the backstop for the rare race that remains.

---

## 6. `expireAfterAccess` on data that changes

```java
Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5))
```

**What goes wrong:** every read resets the timer, so a popular entry never expires. In topic 04, a key read once a minute still serves the old price after an hour.

**Fix:** use `expireAfterWrite` for anything that can change. `expireAfterAccess` is only for freeing memory held by idle entries.

---

## 7. A cache with no size limit

```java
Cache<String, List<Result>> searchCache = Caffeine.newBuilder().build();
```

**What goes wrong:** every distinct key stays forever. Keyed by search text or user id, the cache grows until `OutOfMemoryError`, and a restart "fixes" it for a while. Topic 04 keeps all 50,000 entries, about 50 MB.

**Fix:** `maximumSize` (or `maximumWeight`) on every cache, plus a TTL.

---

## 8. `@CacheEvict` on a local cache with several instances

```java
@CacheEvict(cacheNames = "prices", key = "#sku")
public void updatePrice(String sku, long cents) { ... }
```

**What goes wrong:** it evicts only the cache inside the instance that handled the request. The other instances keep serving the old value until their TTL, and the load balancer shows the user the new price, then the old one, then the new one (topic 05's `TwoInstancesTest`).

**Fix:** a TTL short enough for the staleness you can accept, a shared Redis cache (topic 07), or an eviction broadcast to every instance (topic 11) with the TTL kept as a safety net.

---

## 9. Read-modify-write on a Redis value from Java

```java
long views = Long.parseLong(redis.opsForValue().get(key));
redis.opsForValue().set(key, String.valueOf(views + 1));
```

**What goes wrong:** two instances both read 5 and both write 6. One update is lost, every time they overlap (topic 06's `StringsAndCountersTest`).

**Fix:** let Redis do it in one command: `redis.opsForValue().increment(key)` (`INCR`). For more than one command, use a Lua script, which Redis also runs without interleaving.

---

## 10. A plain `SET` on a key that had a TTL

```java
redis.opsForValue().set("shop:cart:ann", json, Duration.ofMinutes(30));
// later
redis.opsForValue().set("shop:cart:ann", updatedJson);
```

**What goes wrong:** `SET` without an expiry removes the old one. `TTL shop:cart:ann` now returns -1, the key lives forever, and memory grows with every abandoned cart (topic 06's `ExpiryTest`).

**Fix:** pass the duration on every write, or use `SET ... KEEPTTL`.

---

## 11. Redis cache with default settings, then a class change

```java
// Boot's default RedisCacheManager: JDK serialization, no TTL
public record Product(long id, String name, long priceCents) { }   // was CatalogItem, or had a currency field
```

**What goes wrong:** with the defaults, every cached class must implement `Serializable` (`SerializationException: Cannot serialize` otherwise), values are unreadable bytes, and entries never expire. Switch to JSON and the next problem appears: Redis outlives the deploy, so after a class rename or a removed field every read of an old entry throws `SerializationException: Could not read JSON`. With no TTL, that never stops (topic 07's `DeployBreaksCacheTest`).

**Fix:** a JSON serializer, a TTL on every cache, a version in the key prefix (`prefixCacheNameWith("v2:")`) bumped with every incompatible change, and a `LoggingCacheErrorHandler` so an unreadable entry counts as a miss instead of a 500.

---

## 12. Trusting `@Cacheable(sync = true)` to stop a stampede on Redis

```java
@Cacheable(cacheNames = "prices", sync = true)   // Boot's default RedisCacheManager
public Long price(long id) { ... }
```

**What goes wrong:** `sync = true` only asks the cache to let one caller load. Spring Data Redis's default cache writer takes no lock, so 20 concurrent misses still run 20 queries, with no warning (topic 08's `StampedeTest`). On Caffeine it works, but per JVM: 10 instances still mean 10 loads.

**Fix:** for small, hot caches, `RedisCacheManager.builder(RedisCacheWriter.lockingRedisCacheWriter(connectionFactory))`. Its lock covers the whole cache, so hits on other keys wait during a load too. Otherwise refresh hot keys before they expire, or take a per-key `SET NX PX` lock. Prove it with a concurrent test.

---

## 13. The same TTL for entries loaded together

```java
RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10))
```

**What goes wrong:** entries filled together by a warm-up, a deploy, or a batch job all expire in the same second, and all their queries hit the database at once: a cache avalanche (topic 08's `AvalancheTest`, 200 keys in one second).

**Fix:** add random jitter to the TTL: `entryTtl((k, v) -> TTL.plusSeconds(ThreadLocalRandom.current().nextLong(120)))`.

---

## 14. Releasing a Redis lock with `DEL`

```java
redis.opsForValue().setIfAbsent(key, "locked", lease);
// ... work that takes longer than the lease ...
redis.delete(key);
```

**What goes wrong:** your lease expires, B takes the lock, and your `DEL` removes B's lock. C gets in while B is still working: two holders (topic 09's `NaiveLockTest`). `SETNX` followed by a separate `EXPIRE` is worse: a crash between them leaves a lock that never expires.

**Fix:** `SET key <random-owner-id> NX PX <ms>` in one command, and release with a Lua script that deletes the key only if it still holds your id. Even then a long pause can give two holders, so protect the write itself with a fencing token or a database constraint, or use Redisson.

---

## 15. `INCR` and `EXPIRE` as two separate calls

```java
Long count = redis.opsForValue().increment(key);
if (count == 1) redis.expire(key, Duration.ofMinutes(1));
```

**What goes wrong:** if the instance dies between the two calls, the key has no TTL (`TTL` returns -1). The count only grows, and that client is rejected forever (topic 10's `FixedWindowTest`).

**Fix:** run `INCR` and `PEXPIRE` in one Lua script, and put the window number in the key so each window starts a new key.

---

## 16. Read, decide, write as separate Redis calls

```java
if (redis.opsForZSet().zCard(key) < limit) redis.opsForZSet().add(key, member, now);
```

**What goes wrong:** concurrent requests all count 4, all decide they're under the limit of 5, and all get in. In topic 10, 50 concurrent requests let 13–16 through.

**Fix:** remove, count, and add in one Lua script (topic 10's `SlidingWindowLogLimiter`).

---

## 17. Redis Pub/Sub for work that must happen

```java
redis.convertAndSend("orders", orderPlacedJson);   // the email service subscribes
```

**What goes wrong:** `PUBLISH` delivers only to clients subscribed at that moment, and stores nothing. Restart the email service during a deploy and every order in that window gets no email, with no error. `convertAndSend` returned 0, and nobody checked (topic 11's `PubSubTest`).

**Fix:** a Redis Stream with a consumer group and `XACK`, or a message broker (module 16). Keep Pub/Sub for messages that are harmless to miss, such as cache-eviction broadcasts backed by a TTL.

---

## 18. A non-`Serializable` session attribute

```java
session.setAttribute("cart", new Cart());   // Cart does not implement Serializable
```

**What goes wrong:** with sessions in memory it works. After adding `spring-session-data-redis`, the request fails with 500 *after* the controller has finished: `DefaultSerializer requires a Serializable payload` (topic 12's `RedisSessionTest`).

**Fix:** make session attributes `Serializable` and keep them small (ids, not entity graphs), or register a JSON `springSessionDefaultRedisSerializer` bean.
