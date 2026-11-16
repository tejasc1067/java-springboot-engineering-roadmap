# Design Walkthrough: A URL Shortener

This is the capstone of the roadmap. It designs one small system, a URL shortener like `sho.rt/Q0u` → `https://shop.example/summer-sale`, from requirements to failure modes, the way a design review or a system design interview goes. Nothing here is new: every decision points back to the topic where it was taught. The point is to see the pieces fit together and to practise the order of thinking.

A URL shortener is the classic first design question because it looks trivial (a map from short code to long URL) and still touches estimation, ID generation, caching, storage, scaling, async processing, abuse, and observability.

The demo implements the core in plain Java: code generation, uniqueness, idempotent creates, a cache-aside read path, and the 301-vs-302 choice, with each design trade-off asserted in a test.

---

## Step 1: Requirements (and what's out of scope)

Start by agreeing on what you're building. Most design mistakes are requirement mistakes.

**Functional:**
- `shorten(longUrl)` returns a short code.
- `GET /{code}` redirects to the long URL.
- Optional: a custom alias (`sho.rt/summer-sale`), and an expiry date.

**Non-functional:**
- Redirects are fast: p99 under 50 ms at the server. A redirect is in the path of every click.
- Redirects are highly available. A broken short link in a printed flyer or an email can't be fixed.
- Heavily read-dominated: a link is created once and clicked many times.
- Codes should be short and, for most uses, not guessable.

**Out of scope for now:** user accounts and link management UI, editing a link's target, detailed analytics dashboards. Saying what you're *not* building is part of the answer.

## Step 2: Estimates

Back-of-envelope numbers decide whether you need one database or fifty ([topic 01](01-thinking-about-scale.md)). Assume 100 million new URLs a month and 100 reads per write. `EstimatesTest` computes each line:

| Quantity | Calculation | Result |
|---|---|---|
| Writes | 100M / 2,592,000 s in a month | **~39 per second** |
| Reads (average) | 39 × 100 | **~3,900 per second** |
| Reads (peak, 5× average) | 3,858 × 5 | **~19,300 per second** |
| URLs after 5 years | 100M × 12 × 5 | **6 billion** |
| Storage | 6B × 500 bytes per row (code, URL, timestamps, index) | **~3 TB** |
| Cache for the hottest 20% of a day's redirects | 333M redirects/day × 0.2 × 500 bytes | **~33 GB** |

What the numbers tell you: writes are tiny, so the write path can be simple. Reads are moderate, but they are concentrated: a small fraction of links gets most clicks, so a cache absorbs most of them. 3 TB is more than you'd like on one machine but nowhere near "exotic". 33 GB of hot data fits in memory on one Redis node or a small cluster.

## Step 3: The API

```http
POST /urls
Idempotency-Key: 7f3a...           (see step 7)
{ "url": "https://shop.example/summer-sale", "alias": "summer-sale" }    -> 201 { "code": "summer-sale" }

GET /{code}                         -> 301 or 302, Location: https://shop.example/summer-sale
                                    -> 404 if unknown or expired
```

**301 or 302?** This choice has consequences:

- **301 Moved Permanently** tells browsers (and CDNs) they may cache the redirect. Repeat clicks never reach you. Faster for users and cheaper for you, but you stop seeing those clicks, and changing or expiring the link won't reach browsers that cached it.
- **302 Found** (or 307) is temporary: every click comes back to your server. You can count every click, change the target, and expire links, at the cost of more traffic.

`ShortenerServiceTest.permanentRedirect_hidesRepeatClicksFromTheServer` models a browser that remembers permanent redirects: ten clicks reach the server once with 301, and ten times with 302. Most commercial shorteners use 302 because analytics are their product. HTTP and CDN caching of responses is [topic 05](05-cdn-and-edge-caching.md).

## Step 4: Generating short codes

Codes use **base62** (`0-9`, `A-Z`, `a-z`): URL-safe and compact. Length decides capacity:

| Length | Codes (62^length) |
|---|---|
| 6 | 56.8 billion |
| 7 | **3.52 trillion** |

Six characters would hold the 6 billion URLs of five years, but how full the space gets matters for random codes (below). There are three common ways to choose a code, and each fails in its own way.

### Option A: hash the URL and truncate

```java
long first8Bytes = ByteBuffer.wrap(sha256(longUrl)).getLong();
return Base62.padded(Math.floorMod(first8Bytes, Base62.capacity(length)), length);
```

The same URL always gets the same code, which deduplicates for free. But squeezing 256 bits into 7 characters means different URLs *must* sometimes share a code. The birthday bound says when: with N possible codes, a 50% chance of some collision arrives after about 1.18 × √N items. For 7 characters that's **about 2.2 million URLs, under 16 hours of traffic** at our rate, and about **5 million colliding pairs** after five years (`EstimatesTest.truncatedHash_birthdayBound`).

`HashCodeGeneratorTest` shrinks the code to 3 characters (238,328 codes) to make it visible: `/item/190` and `/item/207` both hash to `jUw`, after only 208 URLs. And because a hash gives the same answer every time, "try another code" doesn't help: `collidingUrl_cannotBeStoredAtAll` fails after 5 attempts. Real hash schemes add a salt or counter on retry, which gives up the deduplication.

### Option B: a counter, encoded in base62

```java
return Base62.encode(next++);
```

Never collides. But codes are **sequential**, so anyone holding one code can compute its neighbours and read other people's links: `SequenceCodeGeneratorTest.sequentialCodes_areEnumerable` gets someone's private document from `decode(mine) + 1`. It also exposes your volume to competitors.

A single shared counter is also a bottleneck and a single point of failure ([topic 02](02-scalability-availability-reliability.md)). The usual fix is **ranges**: each instance takes a block of numbers (say 1,000) from a shared sequence, then hands them out locally. `instancesWithTheirOwnBlocks_neverCollide`: two instances, 2,000 codes, no duplicates, and only 20 trips to the shared counter. A crashed instance wastes the rest of its block, which is fine. Scrambling the number before encoding (a reversible permutation) hides the order if you need it.

### Option C: random base62

```java
for (int i = 0; i < length; i++) code.append(ALPHABET.charAt(random.nextInt(62)));
```

Unguessable, no coordination between instances. Collisions are possible, so the **database must reject a duplicate code** and the service must retry. `RandomCodeGeneratorTest.collision_isDetectedByTheStore_andRetriedWithANewCode` forces one: the first code is taken, the insert is refused instead of overwriting the existing link, and the second attempt succeeds.

How often does it retry? Once 6 billion codes exist, a new random 6-character code is already taken 10.6% of the time, against 0.17% for 7 characters (`EstimatesTest.codeLength`). That's why 7. Use `SecureRandom` in production, so codes can't be predicted from earlier ones.

**Choice:** random 7-character codes with a unique constraint and retry, or counter ranges when codes may be guessable. Custom aliases go into the same key space.

## Step 5: Data model and storage

```sql
CREATE TABLE short_url (
    code        VARCHAR(16) PRIMARY KEY,    -- the uniqueness the retry loop relies on
    long_url    TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    expires_at  TIMESTAMP,
    request_id  VARCHAR(64) UNIQUE          -- idempotent creates (step 7)
);
```

Every read is a lookup by primary key, and there are no joins. That's the shape key-value stores are built for, and many designs pick one ([module 10 topic 15](../10-spring-data-jpa/15-nosql-and-when-to-use-it.md)). But be honest about the numbers: about 40 writes a second and a few thousand key lookups a second (most served by the cache) are comfortably within one PostgreSQL primary with read replicas. 3 TB over five years is the first real pressure, and it arrives slowly. Start relational; move to a key-value store or shard when the numbers say so, not because the interview expects it.

In the demo, `UrlStore.insert` uses `putIfAbsent`, which is exactly what the primary key does: a second insert of the same code is refused, never silently overwrites.

## Step 6: The read path

```text
GET /Q0u -> app instance -> cache (Redis) -- hit --> 302
                                  | miss
                                  v
                            read replica -> fill cache -> 302
```

This is **cache-aside** ([module 15 topic 03](../15-caching-and-redis/03-cache-patterns.md)): look in the cache, on a miss read the database and fill the cache. Links almost never change, so invalidation is easy: cache with a TTL, and delete the entry when a link is disabled or expires. `ShortenerServiceTest.cacheAside_hotCodeIsReadFromTheStoreOnce`: a viral link redirected 1,000 times reads the store once.

Three cache problems from [module 15 topic 08](../15-caching-and-redis/08-stampede-penetration-avalanche.md) all apply:

- **Stampede:** a link goes viral the moment it's created, or its entry expires under heavy traffic, and thousands of misses hit the database together. Use per-key locking or refresh hot entries early.
- **Penetration:** bots request random codes that don't exist. `unknownCode_is404_andIsNotCached` shows every miss reaching the store. Cache "not found" briefly, and reject codes with invalid characters or lengths before any lookup.
- **Avalanche:** don't give every entry the same TTL; add jitter.

With 302s, a CDN can still cache redirects for a short time to absorb spikes at the edge ([topic 05](05-cdn-and-edge-caching.md)), trading a little analytics accuracy.

## Step 7: The write path

Writes are rare but must not create surprises:

- **Idempotent creates.** A client's `POST` times out after the server stored the link; the client retries and, without protection, gets a *second* link (`retriedCreate_withoutARequestId_createsADuplicate`). With an `Idempotency-Key` stored in a unique column, the retry returns the first code (`retriedCreate_withTheSameRequestId_returnsTheSameCode`). That's [module 13 topic 19](../13-microservices/19-idempotency-and-the-outbox-pattern.md).
- **Custom aliases** are first come, first served, enforced by the same primary key, not by a "check then insert" in Java, which races (`customAlias_isFirstComeFirstServed`). Reserve words like `api` and `admin`.
- **Validate the URL** (scheme `http`/`https`, length limit) at the boundary.

## Step 8: Scaling out

- **Stateless app instances** behind a load balancer ([topic 03](03-stateless-services.md), [topic 04](04-load-balancing.md)). Nothing is kept in instance memory that another instance needs. The demo's counter-range generator is the one piece of per-instance state, and losing it only wastes numbers.
- **Read replicas** for the cache misses ([topic 06](06-replication-and-read-replicas.md)). Replica lag matters in one place: a user creates a link and clicks it immediately. Read your own writes: fill the cache on create, or fall back to the primary on a replica miss for very new codes.
- **Sharding by code** if storage or write volume outgrows one primary ([topic 07](07-partitioning-and-sharding.md)). Codes are random, so hashing them spreads load evenly; use consistent hashing or a fixed number of logical shards so adding servers doesn't move every key.
- **Consistency:** a redirect served from a slightly stale replica or cache is acceptable; losing a created link is not. That's an availability-leaning read path over a strongly consistent write path ([topic 08](08-cap-and-consistency-models.md)).

## Step 9: Analytics without slowing redirects

Writing a row to the database for every click would put a write on the hottest path, at 20,000 per second at peak. Instead, the redirect publishes a small click event (code, time, referrer, country) to Kafka and returns; a separate consumer aggregates counts in batches ([module 16](../16-messaging-kafka-and-rabbitmq/01-why-message-brokers.md)). Key events by code if per-link ordering matters ([module 16 topic 05](../16-messaging-kafka-and-rabbitmq/05-ordering-and-partition-keys.md)), and make the consumer idempotent, because delivery is at-least-once ([topic 08 there](../16-messaging-kafka-and-rabbitmq/08-idempotent-consumers.md)).

If Kafka is down, the redirect must still work: send asynchronously, drop or buffer events, and count the drops. Analytics are less important than the redirect.

## Step 10: Abuse

A public shortener is a gift to spammers and phishers:

- **Rate-limit creates** per API key or IP across all instances ([module 15 topic 10](../15-caching-and-redis/10-redis-rate-limiting.md)).
- **Check target URLs** against malicious-URL lists at creation, and re-check periodically, because a harmless target can turn malicious later. Disable flagged codes (and evict them from the cache).
- **Don't use sequential codes** for anything private (step 4).

## Step 11: Observability

Define what "working" means as SLOs ([module 17 topic 09](../17-observability/09-slis-slos-and-alerting.md)): for example 99.95% of redirects succeed (non-5xx, and 404 for unknown codes counts as success) and 99% complete in under 50 ms over 30 days. Alert on burn rate, not on single errors.

Metrics worth a dashboard ([module 17 topic 05](../17-observability/05-grafana-dashboards.md)): redirect rate, error ratio, and p99 latency; cache hit ratio (a sudden drop predicts database trouble); create rate and rate-limit rejections; collision retries per create (rising means the code space is filling); analytics consumer lag. Never tag metrics with the code itself: that's one time series per link ([module 17 topic 03](../17-observability/03-micrometer-metrics.md)).

## Step 12: Failure modes

Walk through what breaks and what degrades. This is often the most valuable part of a design review.

| Failure | What happens | Mitigation |
|---|---|---|
| Redis down | Every redirect goes to the database, which normally sees only the cache misses | Replicas sized for a cold cache, a bulkhead on database calls, a small in-process cache for the hottest codes |
| Database primary down | Creates fail; redirects keep working from cache and replicas | Automatic failover; return 503 on create with `Retry-After` |
| A replica lagging | Brand-new links 404 for a few seconds | Fill cache on create, fall back to the primary for misses on new codes |
| Kafka down | Click events lost or buffered; redirects unaffected | Async send, bounded buffer, a metric for dropped events |
| One app instance crashes | Load balancer stops sending it traffic; its counter range is wasted | Readiness probes ([module 17 topic 08](../17-observability/08-liveness-and-readiness.md)), several instances |
| Viral link | One key gets a huge share of reads | Cache, stampede protection, CDN caching of the redirect |

Deployment is the module 14 picture: the app as a container image, several replicas in a Kubernetes Deployment behind a Service, configuration and the database password injected at runtime, readiness and liveness probes wired to Actuator ([module 14 topic 12](../14-containers-and-docker/12-kubernetes-basics.md)).

---

## Common pitfalls

- **Truncating a hash without a collision plan.** Collisions start within hours at realistic volume. Use a unique constraint and a retry that changes the input, or don't use hashes.
- **Sequential codes for private links.** Anyone can enumerate them. Use random codes or scramble the counter.
- **Overwriting on collision.** An insert that replaces an existing code silently redirects someone else's link. Let the primary key refuse it.
- **301 when you need analytics or expiry.** Browsers cache it and never come back. Choose 302 deliberately.
- **Writing to the database on every redirect.** Publish click events asynchronously.

---

## Code examples

`CODE_EXAMPLES/09-design-walkthrough/url-shortener-demo/`. Plain Java and JUnit, no Spring, no Docker. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/shortener/Base62.java` — encode, decode, and `capacity(length)`.
2. `src/test/java/com/example/shortener/EstimatesTest.java` — every number in step 2 and step 4.
3. `src/main/java/com/example/shortener/CodeGenerator.java` and `HashCodeGenerator.java`, then `src/test/java/com/example/shortener/HashCodeGeneratorTest.java` — a real collision, and why retrying a hash can't fix it.
4. `src/main/java/com/example/shortener/SequenceCodeGenerator.java`, then `SequenceCodeGeneratorTest.java` — enumerable codes; ranges per instance.
5. `src/main/java/com/example/shortener/RandomCodeGenerator.java` and `UrlStore.java`, then `RandomCodeGeneratorTest.java` — a forced collision refused by the store and retried.
6. `src/main/java/com/example/shortener/ShortenerService.java`, then `ShortenerServiceTest.java` — idempotent creates, aliases, cache-aside, 404s, and 301 vs 302.

---

## Try this yourself

1. **Add expiry.** Give `UrlStore` an `expiresAt` per code and a `Clock`, make `resolve` return 404 for an expired code, and make sure the cache can't keep serving it: what TTL should a cache entry get when the link expires in 10 minutes?
2. **Salted hash retries.** Change `HashCodeGenerator` so the service can ask for attempt *n* (hash `url + "#" + n`). Make `collidingUrl_cannotBeStoredAtAll` pass by storing both URLs. What did you lose compared with the unsalted version?
3. **Scramble the counter.** Multiply the sequence number by a large odd constant modulo 62^7 before encoding, so codes from consecutive numbers look unrelated. Prove in a test that it never produces a duplicate for the first million numbers.

## Self-check

1. With 100 million new links a month and 100 reads per write, what are the average and peak redirects per second, and roughly how much hot data would you cache? Show the arithmetic.
2. Compare hash-and-truncate, counter, and random codes. For each, say how it fails and what you'd do about it. Why 7 characters rather than 6 for random codes?
3. A client retries `POST /urls` after a timeout and now has two links. What two mechanisms in this design prevent duplicate links and overwritten links, and where does each live?
4. Redis goes down at peak. What happens to redirects, creates, and analytics, and what did you put in place beforehand?
