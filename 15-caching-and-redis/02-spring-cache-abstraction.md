# The Spring Cache Abstraction

Spring lets you cache a method's result by adding an annotation: `@Cacheable` on a method means "if I've seen these arguments before, return the stored result instead of running the method". `@CachePut` and `@CacheEvict` keep that stored result up to date. Your code doesn't say *where* the cache lives. The same annotations work with an in-memory map (this topic), Caffeine (topic 05), or Redis (topic 07). Because the annotations work through a proxy, they also fail silently in a few specific ways, and this topic demonstrates each one.

The demo uses Spring Boot's default in-memory cache, so it needs no cache library and no Docker.

---

## The problem this solves

Topic 01 wrote the cache by hand: look in the map, on a miss call the database, store the result. Do that in every service method that needs caching and you get the same five lines everywhere, mixed into business code, each with its own small bugs. The cache abstraction moves those lines into one place, Spring's caching proxy, and leaves the method with only its real work:

```java
@Cacheable(cacheNames = "products", unless = "#result == null")
public Product findById(long id) {
    return repository.findById(id);
}
```

---

## How it works

### Turn it on: `@EnableCaching`

```java
@Configuration
@EnableCaching
public class CacheConfig {
}
```

`@EnableCaching` registers the caching interceptor. Spring Boot then auto-configures a `CacheManager`, the object that holds named caches. With `spring-boot-starter-cache` and no cache library on the classpath, Boot picks `ConcurrentMapCacheManager`: one `ConcurrentHashMap` per cache name, created the first time a name is used (`bootPicksTheSimpleInMemoryCacheManager_whenNoCacheLibraryIsOnTheClasspath`). It has no TTL and no size limit, so it's for learning and tests, not production.

### What actually happens: a proxy

Like `@Transactional` and `@Async`, caching is implemented with a **proxy** (module 02 topic 16, module 07 topic 10). Other beans don't get your `ProductService`; they get a generated subclass that wraps each annotated method:

```text
caller ──> ProductService proxy ──(miss)──> your findById(1) ──> repository
                │
                └──(hit)──> return the value stored under key 1 in cache "products"
```

Keep that picture in mind. Every silent failure below comes from it.

### `@Cacheable`: return the stored result if there is one

```java
productService.findById(1);
productService.findById(1);
productService.findById(1);
// repository.reads() == 1
```

`cacheable_secondCallWithTheSameKey_doesNotRunTheMethod`. The **key** is built from the method's arguments. With one parameter it's that argument (`1L`); with several it's a `SimpleKey` of all of them; with none it's `SimpleKey.EMPTY`. A cache is a map, and you can look inside it (`theCacheIsAMapYouCanLookInto_keyedByTheMethodArgument`):

```java
cacheManager.getCache("products").get(1L, Product.class);   // Product[id=1, name=Kettle, priceCents=2500]
```

Two attributes decide what gets cached:

- **`condition`** is checked *before* the call, on the arguments. `condition = "#query.length() >= 3"` means short queries bypass the cache entirely: no lookup, no store.
- **`unless`** is checked *after* the call, on the result. `unless = "#result == null"` means "run the method, but don't store a null". You'll want that one (below).

You can set the key yourself with SpEL (Spring Expression Language): `key = "#product.id"`, `key = "'name:' + #id"`.

### `@CachePut`: always run, then store the result

```java
@CachePut(cacheNames = "products", key = "#product.id")
public Product save(Product product) {
    return repository.save(product);
}
```

`@CachePut` never skips the method. It runs it and puts the return value into the cache, so the next `findById` gets the new value without a database read (`cachePut_alwaysRunsTheMethod_andReplacesTheCachedValue`). The `key` must produce the same key the `@Cacheable` method uses, or you're writing an entry nobody reads.

### `@CacheEvict`: remove entries

```java
@CacheEvict("products")
public void delete(long id) { ... }                          // removes the entry under id

@CacheEvict(cacheNames = "products", allEntries = true)
public void evictAll() { }                                   // empties the whole cache
```

`cacheEvict_removesOneEntry_soTheNextCallReloads` and `cacheEvictAllEntries_emptiesTheWholeCache`. Evicting is usually safer than `@CachePut`: the next read loads whatever is really in the database, rather than whatever this method happened to return. Topic 03 explains why.

---

## Broken: six ways caching silently does the wrong thing

None of these throws an error at startup. Each one is a test in the demo.

### 1. Forgetting `@EnableCaching`

`NoEnableCachingTest` builds the same beans with the same Boot cache auto-configuration, once without `CacheConfig` and once with it:

```text
without @EnableCaching:  findById(1) twice -> 2 repository reads, no CacheManager bean, no error
with    @EnableCaching:  findById(1) twice -> 1 repository read
```

`@Cacheable` is only metadata. Without `@EnableCaching` nothing reads it. The app works and is just slower than you think. (Boot doesn't even create a `CacheManager`: its cache auto-configuration only runs once `@EnableCaching` is present.)

### 2. Calling the cached method from the same class (self-invocation)

```java
public List<Product> findAllSelfInvoking(List<Long> ids) {
    return ids.stream().map(this::findById).toList();   // this = the real object, not the proxy
}
```

`callingACachedMethodFromTheSameClass_skipsTheCache`: two calls for products 1 and 2 cause **4** repository reads, and nothing is ever stored. The caller came through the proxy into `findAllSelfInvoking`, but `this.findById` is a plain Java call inside the real object. It never passes the proxy again. It's the same bug as `@Transactional` (module 02 topic 16) and `@Async` (module 08 topic 13).

**Fixed:** call it from another bean, which gets the proxy injected (`callingItFromAnotherBean_goesThroughTheProxy`, 2 reads):

```java
@Service
public class ProductPageService {
    private final ProductService productService;    // the proxy

    public List<Product> findAll(List<Long> ids) {
        return ids.stream().map(productService::findById).toList();
    }
}
```

### 3. Caching "not found"

By default `null` is a value like any other, and it gets cached. `byDefault_nullIsCached_soAProductCreatedLaterStaysNotFound`:

```java
productService.findByIdCachingMisses(3);              // null - and null is now cached under 3
repository.save(new Product(3, "Teapot", 3200));      // an admin creates it

productService.findByIdCachingMisses(3);              // still null; the repository isn't asked again
```

A user sees "product not found" for a product that exists, until the entry is evicted or the app restarts. **Fixed:** `unless = "#result == null"` (`unless_resultIsNull_misses_areNotCached`). Caching misses on purpose is sometimes right, to protect the database from repeated lookups of keys that don't exist, but then it needs a short TTL. Topic 08 covers that ("cache penetration").

### 4. Two methods, one cache name, the same default key

```java
// ProductService                                   // ProductNameService
@Cacheable("products")                              @Cacheable("products")
public Product findById(long id)                    public String nameOfSharingCache(long id)
```

Both store under key `1` in the cache `products`. Whichever runs first wins. `twoMethodsShareACacheAndTheDefaultKey_theSecondGetsTheFirstOnesValue`: after `findById(1)`, `nameOfSharingCache(1)` finds a `Product` under key 1, and the proxy's return throws **`ClassCastException`**. When both methods return the same type it's worse: no exception, just the wrong data. **Fixed:** give each method its own cache name (`product-names`), or prefix the key: `key = "'name:' + #id"`.

### 5. A multi-parameter method with the default key

```java
@CacheEvict("products")
public void changePriceWithDefaultKey(long id, long priceCents) { ... }
```

`findById(1)` stored its entry under `1`. This method has two parameters, so its default key is `SimpleKey [1,1999]`, and it evicts an entry that was never there. `multipleParameters_theDefaultKeyIsAllOfThem_soTheEvictMissesTheEntry`: the price stays 25.00 in the cache. **Fixed:** `@CacheEvict(cacheNames = "products", key = "#id")`. Whenever annotations on different methods must refer to the same entry, write the key out.

### 6. An exception after the write skips the eviction

`@CacheEvict` runs *after* the method by default, and only if it returns normally. Here the method writes the new price and then fails on something unrelated:

```java
@CacheEvict(cacheNames = "products", key = "#id")
public void changePriceThenNotify(long id, long priceCents) {
    repository.save(...);                                    // the table now says 19.99
    throw new IllegalStateException("price-change notification failed");
}
```

`defaultEvict_runsOnlyAfterSuccess_soAWriteFollowedByAnExceptionLeavesTheCacheStale`: the table says 19.99, the cache still says 25.00. **Fixed:** `beforeInvocation = true` evicts first, whatever happens next (`beforeInvocationTrue_evictsEvenWhenTheMethodThrows`).

When the method is `@Transactional`, the exception rolls back the database write too, so the default (evict only on success) is correct. `beforeInvocation = true` is for writes that *can't* be rolled back. It also opens a small window: a read between the eviction and the write can put the old value back. Topic 03 shows that race.

---

## When to use it / when not to

Use the annotations when a method's result depends only on its arguments, and the method is called from other beans. That covers most service-layer read methods.

Write cache code by hand (inject the `CacheManager`, or the cache client directly) when:

- The method is called from inside its own class and moving it would be awkward.
- You need to cache a *part* of the result, or several entries from one call (a batch lookup).
- You need control the annotations don't give you, such as different TTLs per entry.

Don't put `@Cacheable` on methods whose results depend on something that isn't an argument: the current user, the current time, a feature flag. The key won't include it, and one user's result will be served to another.

---

## Common pitfalls

- **No `@EnableCaching`.** Nothing is cached, nothing complains. Write one test that asserts the second call doesn't hit the repository.
- **`this.cachedMethod()`.** Self-invocation skips the proxy. Call it from another bean.
- **Cached nulls.** A not-found result is remembered. Add `unless = "#result == null"`.
- **Implicit keys across methods.** Shared cache names and multi-parameter methods produce keys that collide or never match. Write `key = "#id"` explicitly when methods must agree.
- **`ConcurrentMapCacheManager` in production.** No TTL, no size limit: stale forever and unbounded. Use Caffeine (topic 05) or Redis (topic 07).

---

## Code examples

`CODE_EXAMPLES/02-spring-cache-abstraction/spring-cache-demo/`. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/springcache/CacheConfig.java` — `@EnableCaching`.
2. `src/main/java/com/example/springcache/ProductRepository.java` — the counted "table".
3. `src/main/java/com/example/springcache/ProductService.java` — `@Cacheable`, `@CachePut`, `@CacheEvict`, and the broken variants.
4. `src/test/java/com/example/springcache/CacheAnnotationsTest.java` — what each annotation does.
5. `src/test/java/com/example/springcache/NoEnableCachingTest.java` — the same code without `@EnableCaching`.
6. `src/main/java/com/example/springcache/ProductPageService.java` and `src/test/java/com/example/springcache/SelfInvocationTest.java` — self-invocation, broken and fixed.
7. `src/test/java/com/example/springcache/NullCachingTest.java` — the cached "not found".
8. `src/main/java/com/example/springcache/ProductNameService.java` and `src/test/java/com/example/springcache/KeyCollisionTest.java` — shared cache names, multi-parameter keys.
9. `src/test/java/com/example/springcache/EvictOnExceptionTest.java` — eviction skipped by an exception, and `beforeInvocation`.

---

## Try this yourself

1. Add `@Cacheable(cacheNames = "search", condition = "#query.length() >= 3")` to a `search(String query)` method that counts its calls. Write a test showing that `"ke"` runs the method every time and `"kettle"` only once.
2. Change `ProductPageService.findAll` to be `@Cacheable("pages")` as well. Call `findAll(List.of(1L, 2L))`, then `productService.save` a new price for product 1, then `findAll` again. Which price do you get, and why didn't the `@CachePut` on `save` help?

## Self-check

1. A teammate adds `@Cacheable` to a method and the database load doesn't change at all. Give two causes you'd check first.
2. What key does `@Cacheable("orders")` use for `find(long customerId, String status)`, and why would `@CacheEvict("orders")` on `cancel(long customerId)` fail to remove those entries?
3. When is `@CacheEvict(beforeInvocation = true)` the right choice, and when is the default better?
