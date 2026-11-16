# Redis as Spring's Cache

Topic 02 put `@Cacheable` on a service and topic 05 moved the cache into Caffeine, inside each instance. This topic keeps the same annotations and moves the cache into Redis, so every instance reads and evicts the *same* entries. That one change fixes topic 05's "two instances, two answers", and brings two new problems a local cache never had: values have to be **serialized** to bytes to cross the network, and they **outlive your deploys**.

---

## The problem this solves

Topic 05's `TwoInstancesTest` ended with instance B serving an old price because the eviction happened on instance A. The cache was correct per instance and wrong for the system. A shared cache removes the problem at its source: there's only one copy of each entry.

## How it works

### Switching the cache to Redis

Add `spring-boot-starter-data-redis` next to `spring-boot-starter-cache`, keep `@EnableCaching`, and point Spring at Redis (`spring.data.redis.host`/`port`, or `@ServiceConnection` in tests). Boot sees a Redis connection and builds a `RedisCacheManager`. If more than one cache library is on the classpath, choose explicitly with `spring.cache.type=redis`.

The service doesn't change at all:

```java
@Cacheable(cacheNames = "products", unless = "#result == null")
public Product find(long id) {
    return catalog.load(id);
}

@CachePut(cacheNames = "products", key = "#product.id")
public Product update(Product product) {
    catalog.save(product);
    return product;
}
```

Each entry becomes one Redis key, by default `<cacheName>::<key>`, so `find(1)` stores `products::1`.

### Broken: Boot's defaults

With no configuration at all, the `RedisCacheManager` that Boot builds uses Java serialization and no expiry. `DefaultJdkSerializationTest` shows both problems:

```java
public record Product(long id, String name, long priceCents) { }   // not Serializable

service.find(1);
// SerializationException: Cannot serialize
//   ... DefaultSerializer requires a Serializable payload but received an object of type [com.example.rediscache.Product]
```

Every call to a cached method now fails, not just misses. Add `implements Serializable` and it works, but look at what's stored:

```text
redis-cli> GET products::1
"\xac\xed\x00\x05sr\x00Hcom.example.rediscache.DefaultJdkSerializationTest$SerializableProduct..."
redis-cli> TTL products::1
(integer) -1
```

- `\xac\xed` is the Java serialization header. Only a JVM with that exact class can read the value. You can't inspect it in `redis-cli`, and a service written in another language can't share the cache.
- `TTL -1` means **no expiry**. Every entry stays until someone deletes it. Nothing bounds staleness (topic 04), and Redis memory only grows until it hits its limit.

### Fixed: JSON values, a TTL, and a key prefix

Define a `RedisCacheConfiguration` bean. Boot uses it as the default for every cache it creates:

```java
@Bean
RedisCacheConfiguration cacheConfiguration() {
    return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))                    // the default TTL for every cache
            .prefixCacheNameWith(cacheVersion + ":")          // "v1:products::1"
            .disableCachingNullValues()
            .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
}

// Per-cache overrides
@Bean
RedisCacheManagerBuilderCustomizer perCacheTtl(RedisCacheConfiguration defaults) {
    return builder -> builder.withCacheConfiguration("products", defaults.entryTtl(Duration.ofMinutes(10)));
}
```

(`GenericJackson2JsonRedisSerializer` needs `jackson-databind`. A web app already has it through `spring-boot-starter-web`; this demo has no web layer, so its `pom.xml` adds it.)

`JsonCacheTest.storedValue_isReadableJson_withAVersionedKey_andATtl` shows the result:

```text
redis-cli> KEYS *
1) "v1:products::1"
redis-cli> GET v1:products::1
"{\"@class\":\"com.example.rediscache.Product\",\"id\":1,\"name\":\"Keyboard\",\"priceCents\":4999}"
redis-cli> TTL v1:products::1
(integer) 600
```

The value is readable, the record doesn't need `Serializable`, and the entry expires. The `@class` field is how `GenericJackson2JsonRedisSerializer` knows which class to rebuild when it reads the value back. Remember it, because it's the cause of the next problem.

Simple properties cover the common cases if you don't need a bean: `spring.cache.redis.time-to-live=10m`, `spring.cache.redis.key-prefix=...`, `spring.cache.redis.cache-null-values=false`. They apply to every cache alike, and they don't change the serializer.

### `disableCachingNullValues` has a catch

With nulls disabled, returning `null` from a `@Cacheable` method isn't silently skipped. It throws:

```text
IllegalArgumentException: Cache 'products' does not allow 'null' values;
Avoid storing null via '@Cacheable(unless="#result == null")' or configure RedisCache to allow 'null' via RedisCacheConfiguration
```

So every cached method that can return `null` needs `unless = "#result == null"` (`withUnless_aMissingProductIsSimplyNotCached`). Not caching "not found" at all has its own cost, covered in topic 08 (cache penetration).

### Broken: the deploy that renames a class

Redis isn't restarted when your app is. Entries written by yesterday's version are still there when today's version starts, and today's code has to read them. `DeployBreaksCacheTest` plants an entry written by an "old version" and runs the new code against it.

**A renamed class.** The old version cached `CatalogItem`; the new version renamed it to `Product`:

```text
{"@class":"com.example.rediscache.CatalogItem","id":1,...}

service.find(1)  ->  SerializationException: Could not read JSON:Failed to parse type 'com.example.rediscache.CatalogItem'
                     ... Cannot locate class 'com.example.rediscache.CatalogItem'
```

**A removed field.** The old `Product` had a `currency` field; the new one doesn't:

```text
{"@class":"com.example.rediscache.Product","id":1,"name":"Keyboard","priceCents":4999,"currency":"EUR"}

service.find(1)  ->  SerializationException: Could not read JSON:Unrecognized field "currency" (class com.example.rediscache.Product),
                     not marked as ignorable
```

Both fail on **every** read of every old entry, not just once, and with a 1-hour TTL they keep failing for an hour after the deploy. A field *added* in the new version is the opposite and quieter problem: old entries load without it (as `null` or `0`) and the error shows up somewhere else.

### Fixed: version the keys

Put a version in the key prefix and bump it whenever a cached class changes in a way old entries can't be read as:

```yaml
app:
  cache:
    version: v2      # was v1
```

`BumpedKeyPrefix.newVersionUsesNewKeys_andNeverReadsTheOldEntries`: the new code looks up `v2:products::1`, misses, loads from the database, and caches it. The old `v1:` entry is never read and expires on its own TTL. That's why a TTL matters here too: without one, the abandoned `v1:` entries stay in Redis forever.

During a rolling deploy, old and new instances run side by side, so with different prefixes each version has its own cache for a few minutes. That's the point: neither can read the other's entries.

Two more layers of defence, both worth having:

- **A `CacheErrorHandler` that treats a failed read as a miss.** `BadEntryIsAMiss`: with `LoggingCacheErrorHandler`, an unreadable entry is logged and the method runs as if nothing was cached. A broken cache then means slower responses instead of errors.

  ```java
  @Configuration
  class CachingConfig implements CachingConfigurer {
      @Override
      public CacheErrorHandler errorHandler() {
          return new LoggingCacheErrorHandler();
      }
  }
  ```

  The same handler also keeps the app working when Redis itself is down, which you usually want for a cache.
- **Cache small DTOs made for the cache, not entities.** A record with three fields changes less often than a JPA entity with relationships, and it never drags a lazy collection into the serializer.

### Fixed: two instances, one cache

`TwoInstancesTest` starts two complete application contexts against the same Redis, as two instances behind a load balancer:

```java
b.find(2);                                   // B caches the price: 1_999
a.update(new Product(2, "Mouse", 2_499));    // the update lands on A: @CachePut writes v1:products::2

b.find(2);                                   // 2_499, read from Redis
catalogB.reads();                            // unchanged: B didn't touch its database
```

The write on A is visible on B immediately, because there's only one entry. Compare topic 05, where B kept the old price until its own TTL ran out.

The price for that: every cache read is now a network round trip (well under a millisecond on the same network, but not the nanoseconds of a local cache), Redis is one more thing to run, and if Redis is slow, every cached call is slow. Topic 05 describes putting a small local cache in front of Redis when reads are very hot.

---

## When to use it / when not to

**Use a Redis cache** when you run more than one instance and stale data across instances matters, when the cache is too big to hold in every instance's heap, or when you want the cache to survive a restart (a cold cache after every deploy can overload the database).

**Stay local (topic 05)** for small, rarely changing data where a few seconds' disagreement is fine, or for a single instance. A local lookup is far faster than any network call.

---

## Common pitfalls

- **Running on Boot's defaults.** JDK serialization and no TTL. Configure a JSON serializer and a TTL before the first deploy, not after.
- **Changing a cached class without changing the key prefix.** Every read of an old entry fails until it expires. Bump the version prefix with the change.
- **`disableCachingNullValues()` without `unless = "#result == null"`.** Lookups that find nothing throw `IllegalArgumentException`.
- **Caching JPA entities.** Lazy collections, proxies, and big object graphs end up in (or break) the serializer. Cache a DTO.
- **No `CacheErrorHandler`.** A cache problem (bad entry, Redis down) becomes an error for the user instead of a slower response.

---

## Code examples

`CODE_EXAMPLES/07-redis-as-spring-cache/rediscache-demo/`. Run with `mvn -q test` (Docker must be running). Read in this order:

1. `src/main/java/com/example/rediscache/Product.java`, `ProductCatalog.java`, `ProductService.java` — the cached service and a fake database that counts reads.
2. `src/test/java/com/example/rediscache/RedisContainerSupport.java` — one Redis container for every test class, wired in with `@ServiceConnection`.
3. `src/test/java/com/example/rediscache/DefaultJdkSerializationTest.java` — Boot's defaults: a non-`Serializable` class fails, Java bytes in Redis, `TTL -1`.
4. `src/main/java/com/example/rediscache/JsonCacheConfig.java` — JSON values, default and per-cache TTLs, a versioned key prefix, no null values.
5. `src/test/java/com/example/rediscache/JsonCacheTest.java` — hits served from Redis, the stored JSON and its TTL, the null-value exception and `unless`.
6. `src/test/java/com/example/rediscache/DeployBreaksCacheTest.java` — entries from an old version: a renamed class and a removed field fail; a bumped prefix and a `CacheErrorHandler` don't.
7. `src/test/java/com/example/rediscache/TwoInstancesTest.java` — two application contexts, one Redis: B reads A's update.

---

## Try this yourself

1. Add a `String category` field to `Product`, then put the *old* JSON (without `category`) into `v1:products::1` by hand in a test and call `find(1)`. Does it fail, or does it load? What is `category`? Would you bump the version for this change?
2. Replace `GenericJackson2JsonRedisSerializer` with `new Jackson2JsonRedisSerializer<>(Product.class)` for the `products` cache only (`withCacheConfiguration`). Look at the stored value. What disappeared, and what does that mean for renaming the class?

## Self-check

1. What two things are wrong with the `RedisCacheManager` Boot builds when you configure nothing, and how do you see each one with `redis-cli`?
2. A release renames `CatalogItem` to `Product`. Describe exactly what users see for the next hour and why, and two changes that would have prevented it.
3. Why does a Redis cache fix topic 05's "two instances, two answers", and what does each cache read cost that it didn't before?
