# Interview questions — Caching and Redis

Each answer is what an interviewer hopes to hear: the mechanism, the trade-off, and the production gotcha. The behaviour described comes from this module's tests.

---

## 1. A page is slow and someone suggests "just add a cache". How do you decide, and what does the cache cost you?

First find out where the time goes. A cache helps when the same data is read many times between changes (a high hit ratio) and loading it is expensive. It does nothing for data read once, and it hides rather than fixes a missing index or an N+1 query. If the slow part is a query you can fix, fix it.

Then count the costs. **Staleness:** the cache is a second copy, so after an update some readers see the old value until it's invalidated or expires, and you must decide how stale is acceptable per piece of data. **Invalidation code:** every write path has to remember the cache, and missing one is a bug that only shows as old data (topic 01's stale read). **Memory**, and on several instances one copy per instance. **Cold starts:** after a deploy or a flush, every request misses at once, and the database must survive that. **A new failure mode:** if the cache is a separate system like Redis, you have to decide what happens when it's down.

Good candidates: product catalogue data, configuration, reference data, rendered fragments, expensive aggregates where seconds or minutes of staleness are fine. Bad candidates: balances, stock at checkout, permissions right after they're revoked: anything that must be correct at the moment of a decision.

## 2. You use cache-aside. Why delete the entry on update instead of writing the new value, and in what order?

Write the database first, then delete the cache entry.

Setting the new value instead races between two writers: A saves 900, B saves 800, B sets the cache to 800, then A sets it to 900. The database says 800 and the cache says 900 until something evicts it. A delete can't install a wrong value.

Deleting *before* the database write is worse: a reader in the gap misses, reads the old row, and caches it after the delete has already happened. The window is the whole database write, and without a TTL the old value stays forever (topic 03).

Write-then-delete still has a rare race: the entry is missing, a reader loads the old row, the writer saves and deletes (nothing to delete yet), then the reader stores the old row. It needs a read slower than a whole write, so it's rare. A TTL bounds it, a delayed second delete narrows it, and change-data-capture invalidation (deleting entries from the database's change log) removes the dependence on application code. Data that must never be stale shouldn't be served from a cache at all.

## 3. Compare read-through, write-through and write-behind. When would you accept write-behind?

**Read-through** puts the loading logic in the cache: callers only ask the cache, and it calls a loader on a miss (Caffeine's `LoadingCache`). Compared with cache-aside, the code is in one place instead of every caller.

**Write-through** updates the database and then the cache on every write, before returning. The cache always agrees with the database and reads after writes hit, but every write is slower and the cache fills with data nobody may read. Write the database first, so a failed write leaves the cache unchanged.

**Write-behind** writes the cache and returns; a background job flushes to the database in batches. Writes are fast and repeated writes to one key coalesce (in topic 03, 10 writes became 1). But until the flush the cache is the only copy: other readers of the database see old data, and a crash loses the pending writes. Accept it only for data you can afford to lose a few seconds of (view counters, presence, metrics), or with a durable, replicated store. Never for orders or payments.

## 4. You cache prices in Caffeine and the service runs as 3 instances. An admin updates a price and some users still see the old one. Why, and how do you fix it?

Each instance has its own in-heap cache. The update lands on one instance, which writes the database and runs `@CacheEvict`, but that evicts only its own copy. The other two keep serving the old value until their own TTL expires, and because the load balancer spreads requests, the same user sees new, old, new (topic 05).

Fixes, cheapest first:
- **A short `expireAfterWrite`.** It doesn't remove the disagreement, it bounds it. If "up to 30 seconds stale" is acceptable, that's the whole fix.
- **A shared cache in Redis** (topic 07). One copy, so one eviction is seen by everyone. The cost is a network hop per read and a system to operate.
- **Broadcast invalidation.** Keep local caches and publish "evict X" over Redis Pub/Sub or a broker so every instance evicts (topic 11). Reads stay in-process, but a lost message leaves an entry stale, so keep the TTL as a safety net.
- **Two levels:** a local cache with a few seconds' TTL in front of Redis.

Remember the hidden costs of local caches too: N copies in memory and N cold caches after every deploy.

## 5. Why is `INCR` safe across many app instances when `GET`, add one, `SET` isn't? What does that teach you about using Redis?

Redis executes commands one at a time. Extra I/O threads, where configured, only read and write the network; commands themselves never interleave. So `INCR` reads, adds, and writes inside the server as one step. `GET` then `SET` are two commands from the client, and another client can run its own `GET` in between: both instances read 5, both write 6, and one update is lost (topic 06).

The general rule: put each read-modify-write into one server-side command (`INCR`, `HINCRBY`, `ZINCRBY`, `SET NX PX`) or into a Lua script, which Redis also runs without interleaving. That's how the rate limiter (topic 10) and safe lock release (topic 09) work. `MULTI`/`WATCH` exists but is optimistic and easy to misuse.

The same single-threaded execution is the main danger: one slow command blocks every client. `KEYS *` over millions of keys, a huge `LRANGE`, or a long Lua script stalls the whole server, which is why you use `SCAN` and keep commands and values small.

## 6. A popular product's cache entry expires and the database spikes. What's happening, and how do you prevent it with Spring and Redis?

A cache stampede, or thundering herd. Every request that arrives while the entry is missing misses and runs the query itself. Nobody fills the entry until the first query finishes, so the slower the query, the bigger the pile-up.

`@Cacheable(sync = true)` asks the cache to let only one caller load a key. Caffeine honours it per key, but per JVM, so 10 instances still mean 10 loads. Spring Data Redis's default cache writer ignores it: 20 concurrent misses give 20 loads either way (topic 08). `RedisCacheWriter.lockingRedisCacheWriter` gives one load across all instances, but its lock covers the whole cache name, so hits on other keys wait too; it suits small, hot caches only. Alternatives: refresh hot entries before they expire (`refreshAfterWrite`, or a scheduled job), a per-key `SET NX PX` lock where losers wait briefly and re-read, or serving the stale value while one caller reloads.

Two neighbours to separate from it. **Avalanche:** many keys expiring together, fixed with TTL jitter. **Penetration:** lookups for ids that don't exist and so are never cached, fixed by validating input and caching "not found" briefly, with a Bloom filter at very large scale. One trap: with `sync = true`, a TTL function that depends on the value is always called with `null`.

## 7. How do you implement a distributed lock with Redis, and why might it still let two processes in?

Acquire with one atomic command: `SET lock:x <random-owner-id> NX PX 30000`. Never `SETNX` followed by `EXPIRE`: a crash between them leaves a lock that never expires. Release with a Lua script that deletes the key only if it still holds your owner id; a plain `DEL` after your lease ran out deletes the next holder's lock.

Even then, a lease-based lock can have two holders. If the holder pauses past its lease (a GC pause, a slow call), the lock expires, someone else takes it, and the first holder wakes up and carries on writing. Checking "do I still hold it?" before writing doesn't help, because the pause can come right after the check. Redisson's watchdog keeps renewing the lease while the process is alive, which makes this rarer, but a paused JVM pauses its watchdog too.

The real fix is a **fencing token**: each acquisition gets a number that only goes up (`INCR`), and the storage rejects writes carrying a lower number than one it has already seen (`UPDATE ... WHERE fencing_token < ?`). That needs the storage's cooperation. Redlock (a majority across several Redis servers) addresses failover, not pauses, and is disputed. So: a Redis lock is fine for avoiding duplicate *effort*. For *correctness*, prefer a unique constraint, `@Version`, or one conditional update, and use a lock only together with fencing.

## 8. How would you rate-limit a public API that runs as 6 instances? What can go wrong?

An in-process limiter (Resilience4j, module 13 topic 12) counts per instance, so 6 instances with a limit of 100 per minute allow 600, and the real limit changes every time you scale. Keep the counter in Redis, keyed by client (API key or user id rather than IP, since many users can share one IP).

The simplest algorithm is a fixed window: `INCR rl:{client}:{windowNumber}` with a TTL. `INCR` and `EXPIRE` must run in one Lua script, or a crash between them leaves a key that never expires and blocks that client forever. A fixed window allows up to twice the limit across a window edge (5 requests at 59.9 s and 5 at 60.1 s in topic 10). When that matters, use a sliding window log: a sorted set of timestamps where remove-old, count, and add run in one Lua script; without the script, concurrent requests race past the limit. The log costs one entry per allowed request, so for large limits use a sliding window counter or a token bucket (Bucket4j, or Spring Cloud Gateway's Redis rate limiter).

Rejected requests get `429 Too Many Requests` with `Retry-After`. Also decide what happens when Redis is down: fail open for abuse protection, fail closed for hard quotas.

## 9. Redis Pub/Sub vs Streams: when do you use each?

Pub/Sub broadcasts to whoever is subscribed at that moment and stores nothing. It's at-most-once: a subscriber that's restarting misses messages for good, and every subscriber gets every message. It fits notifications that are harmless to miss, such as cache-eviction broadcasts across instances, where a TTL bounds how long a missed message leaves data stale.

A Stream is an append-only log kept in Redis. A consumer group shares messages among its consumers, and each message stays in the group's pending entries list until a consumer `XACK`s it. Messages wait while consumers are down. A message a consumer received but never acknowledged stays pending: that consumer re-reads it from id `0` on restart, or another consumer takes it over with `XCLAIM`/`XAUTOCLAIM`. Delivery is at-least-once, so handlers must be idempotent, poison messages need a dead-letter path, and the stream must be trimmed. Beyond moderate volume or long retention, use Kafka or RabbitMQ (module 16).

## 10. Users get logged out at random after you scale a Spring Boot web app to two instances. Why, and what are your options?

The session lives in one instance's memory. The login request landed on instance A, which created the session and sent the cookie. The next request went to B, which has never seen that session id, so the user is anonymous (topic 12).

Options:
- **Sticky sessions:** the load balancer pins each user to one instance. No code change, but load spreads unevenly, and when that instance restarts or is scaled away its users are logged out.
- **A shared session store:** Spring Session with `spring-session-data-redis` stores each session as a Redis hash (`spring:session:sessions:<id>`) with a TTL from the session timeout, so any instance can read it. Session attributes must be serializable (`Serializable` with the default serializer, or a JSON serializer), and should be small: ids, not entity graphs. Run that Redis with `noeviction`, or in its own instance, so memory pressure doesn't evict live sessions.
- **Stateless tokens** (module 12 topic 08): every request carries a signed JWT and no instance keeps anything. Nothing to share, but logging out or revoking a token before it expires needs extra machinery.

For a server-rendered app with a login form, a shared session store is usually simplest. For an API used by SPAs, mobile apps, and other services, tokens fit better.
