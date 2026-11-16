# Redis Fundamentals

Redis is an in-memory data store that runs as its own server. Your application talks to it over the network, and every instance of your service talks to the *same* Redis, which is exactly what the local cache in topic 05 couldn't give you. It's used for shared caches (topic 07), locks (topic 09), rate limits (topic 10), messaging (topic 11), and sessions (topic 12).

Redis isn't a `Map` on another machine. It has its own data types, each with commands that change data *atomically* inside the server. Knowing those types and commands is what lets you use Redis correctly in every later topic. This topic covers them with a real Redis in a container.

---

## The problem this solves

You want state that every instance of your service shares and that's fast to read and write:

- a page-view counter that 10 instances increment at once,
- a "recently viewed" list per user,
- a leaderboard sorted by score,
- a one-time password that must disappear after 5 minutes.

A relational database can do all of these, but each is a disk-backed transaction and often a row lock. Redis does each one in microseconds, in memory, with one command.

---

## How it works

### The model: one server, data in memory, one command at a time

- **In memory.** All data lives in RAM, which is why it's fast and why the dataset must fit in memory.
- **Commands run one at a time.** Redis executes commands on a single thread (newer versions use extra threads for network I/O, not for running commands). While one command runs, no other command can see or change data halfway through it. That's what makes `INCR` atomic without any locks in your code. It's also why a slow command blocks *every* client (see `KEYS` below).
- **Persistence is optional, and it's not a database's guarantee.** Redis can snapshot memory to disk every so often (**RDB**) or log every write to an append-only file (**AOF**). The `redis:7.4-alpine` image used here snapshots by default (`save 3600 1 300 100 60 10000`: every hour if at least 1 key changed, every 5 minutes if 100 did, every minute if 10,000 did) and has AOF off. A crash can lose everything written since the last snapshot. For a cache that's fine, because the database still has the truth. Don't keep data in Redis that you can't afford to lose unless you've configured persistence and replication on purpose.

### Talking to it from Spring

`spring-boot-starter-data-redis` brings the Lettuce client and auto-configures a `StringRedisTemplate`, which reads and writes plain strings. The tests get a real Redis from Testcontainers (module 14 topic 11):

```java
@Bean
@ServiceConnection(name = "redis")   // "this container is Redis": Boot reads its host and port
GenericContainer<?> redis() {
    return new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
}
```

Each method on the template maps to one Redis command. The comments in the tests show which one. You can try every command by hand too:

```bash
docker run -d --name redis -p 6379:6379 redis:7.4-alpine
docker exec -it redis redis-cli
127.0.0.1:6379> SET shop:product:42:name "Coffee mug"
OK
```

### Key names

Redis has one flat key space per database, with no tables. The convention is colon-separated names, from general to specific: `app:entity:id[:field]`.

```text
shop:product:42:name
shop:user:7                 (a hash with the user's fields)
shop:user:7:recent          (a list)
shop:visitors:2026-11-16    (a set per day)
```

It keeps keys readable, avoids collisions between features, and lets you find a group of keys with a pattern (`shop:session:*`).

### Strings and atomic counters

The simplest type is a string. A string value can be text, a number, or serialized JSON (topic 07):

```java
redis.opsForValue().set("shop:product:42:name", "Coffee mug");      // SET
redis.opsForValue().get("shop:product:42:name");                    // GET -> "Coffee mug"
redis.opsForValue().get("shop:product:99:name");                    // GET a missing key -> null
```

#### Broken: read, add one, write back

`StringsAndCountersTest.readModifyWrite_inJava_losesAnUpdate` plays two instances counting a page view at the same moment:

```java
long seenByA = Long.parseLong(redis.opsForValue().get("shop:page:home:views"));   // 5
long seenByB = Long.parseLong(redis.opsForValue().get("shop:page:home:views"));   // 5
redis.opsForValue().set("shop:page:home:views", String.valueOf(seenByA + 1));      // 6
redis.opsForValue().set("shop:page:home:views", String.valueOf(seenByB + 1));      // 6

// two views, the counter says 6
```

Each `GET` and `SET` is atomic on its own, but the *sequence* isn't. This is the same lost update as `count++` across threads (module 03c topic 03) and the read-modify-write on a MongoDB document (module 10 topic 16), now across machines.

#### Fixed: let Redis do the arithmetic

```java
redis.opsForValue().increment("shop:page:home:views");    // INCR
```

`incr_isAtomic_noUpdateIsLost` fires 1,000 `INCR`s from 8 threads and always gets exactly 1,000. The read, the add, and the write happen inside one command, and nothing can run between them. The rule for Redis: **if you're about to read a value, change it in Java, and write it back, look for the command that does it in one step** (`INCR`, `INCRBY`, `HINCRBY`, `ZINCRBY`, `SET ... NX`, or, for anything else, a Lua script, topic 09).

### Hashes: one key, many fields

A hash is a small map stored under one key, a good fit for an object:

```java
redis.opsForHash().putAll("shop:user:7", Map.of("name", "Ann", "city", "Pune", "visits", "0"));  // HSET
redis.opsForHash().put("shop:user:7", "city", "Mumbai");       // change one field
redis.opsForHash().increment("shop:user:7", "visits", 1);      // HINCRBY, atomic
```

Compared with storing the whole object as one JSON string, you can read or change one field without rewriting the rest.

### Lists: ordered, push and pop at either end

```java
redis.opsForList().leftPush("shop:user:7:recent", product);    // LPUSH: add at the front
redis.opsForList().trim("shop:user:7:recent", 0, 2);           // LTRIM: keep positions 0..2
redis.opsForList().range("shop:user:7:recent", 0, -1);         // LRANGE: all -> [desk, lamp, book]
```

Push then trim is the standard "last N items" pattern: the list never grows past 3. Lists are also simple queues (`LPUSH` + `RPOP`), though Streams (topic 11) are better for real work queues.

### Sets: unique members, no order

```java
redis.opsForSet().add("shop:visitors:2026-11-16", "ann", "bob", "ann", "cid", "bob");   // SADD
redis.opsForSet().size("shop:visitors:2026-11-16");               // SCARD -> 3
redis.opsForSet().isMember("shop:visitors:2026-11-16", "bob");    // SISMEMBER -> true
```

Duplicates are ignored, so "unique visitors today" is one `SADD` per visit. Redis can also intersect and union sets (`SINTER`, `SUNION`): "users who bought A and B".

### Sorted sets: members ordered by a score

Each member has a numeric score, and Redis keeps them sorted. That makes a leaderboard trivial:

```java
redis.opsForZSet().incrementScore("shop:leaderboard", "ann", 50);   // ZINCRBY
redis.opsForZSet().incrementScore("shop:leaderboard", "bob", 80);
redis.opsForZSet().incrementScore("shop:leaderboard", "cid", 65);
redis.opsForZSet().incrementScore("shop:leaderboard", "ann", 40);   // ann: 90

redis.opsForZSet().reverseRangeWithScores("shop:leaderboard", 0, 1);  // ZREVRANGE ... WITHSCORES -> ann 90, bob 80
redis.opsForZSet().reverseRank("shop:leaderboard", "cid");            // ZREVRANK -> 2 (third place)
```

Sorted sets also power the sliding-window rate limiter in topic 10, with timestamps as scores.

### Expiry: keys that delete themselves

Any key can have a time-to-live. When it runs out, Redis deletes the key:

```java
redis.opsForValue().set("shop:otp:ann", "481516", Duration.ofMinutes(5));   // SET ... EX 300
redis.getExpire("shop:session:abc");      // TTL: seconds left
redis.persist("shop:session:abc");        // PERSIST: remove the TTL
redis.expire("shop:cart:ann", Duration.ofMinutes(30));   // EXPIRE: add a TTL to an existing key
```

`ExpiryTest` checks the details:

- `TTL` returns the seconds left, **-1** if the key exists with no TTL, and **-2** if the key doesn't exist.
- `setWithEx_redisDeletesTheKeyWhenTheTtlRunsOut`: a 200 ms TTL, a 400 ms wait (Redis runs on a real clock, so this test does sleep briefly), and the key is gone.
- **`plainSet_onAKeyWithATtl_silentlyRemovesTheTtl`**: `SET` without `EX` on a key that had a 30-minute TTL leaves it with **no TTL at all**. It now lives forever. This catches people who "update the cart" with a plain `set`. Pass the duration on every `set`.
- `incr_keepsTheExistingTtl`: `INCR` changes the value and keeps the TTL. Topic 10's rate limiter relies on that.

### `SET ... NX`: only if it doesn't exist

```java
redis.opsForValue().setIfAbsent("shop:order:1001:processing", "worker-a", Duration.ofSeconds(30));  // true
redis.opsForValue().setIfAbsent("shop:order:1001:processing", "worker-b", Duration.ofSeconds(30));  // false
```

`SET key value NX EX 30` sets the key only if it's absent, and sets the TTL in the same atomic command. Exactly one caller wins. It's the building block for distributed locks and "process this only once" checks. Topic 09 shows why a lock needs more than this one line.

### Broken: `KEYS *` in production

`KEYS pattern` returns every matching key, but to do it Redis walks **the whole key space** in one command. Because commands run one at a time, every other client waits until it finishes. With 5,000 keys (`ScanTest`) it's instant. With 50 million keys in production it can take seconds, and for those seconds every request on every service using that Redis hangs. Many teams disable the command entirely.

#### Fixed: `SCAN`

`SCAN` walks the key space in small batches. Each call returns some keys and a cursor to continue from, and other clients' commands run between the calls:

```java
try (Cursor<String> cursor = redis.scan(
        ScanOptions.scanOptions().match("shop:session:*").count(100).build())) {
    cursor.forEachRemaining(sessions::add);   // SCAN 0 MATCH shop:session:* COUNT 100, then the next cursor...
}
```

`scan_walksTheKeysInSmallBatches` finds the same 20 session keys among 5,020. `COUNT` is a hint for how much work each call does, not how many keys come back. A call can return zero matches and a non-zero cursor, so always loop until the cursor is 0 (Spring's `Cursor` does this for you). The demo's own test cleanup uses `KEYS`, which is fine on a test Redis with a handful of keys. Never in application code.

---

## When to use Redis / when not to

**Use it for** state shared by all instances that's read and written often: caches, counters, rate limits, sessions, leaderboards, short-lived tokens, locks.

**Don't use it as**:

- **your only copy of important data**, unless you've deliberately configured persistence and replication. By default it can lose recent writes.
- **a relational database.** There are no joins, no ad-hoc queries, and no schema. You find data by key, so you design keys around the questions you'll ask.
- **storage for more data than fits in RAM.** By default (`maxmemory 0`, `noeviction`) Redis keeps accepting writes until the machine runs out of memory. Topic 07 sets a memory limit and an eviction policy for cache use.

---

## Common pitfalls

- **Read-modify-write from Java.** Two instances lose updates. Use `INCR`/`HINCRBY`/`ZINCRBY` or a Lua script.
- **A plain `SET` on a key that had a TTL.** The TTL is removed and the key lives forever. Pass the TTL on every write.
- **`KEYS` in application code.** It blocks the whole server while it walks every key. Use `SCAN`.
- **Keys with no TTL and no plan to delete them.** Redis memory only grows. Give cache-like keys a TTL.

---

## Code examples

`CODE_EXAMPLES/06-redis-fundamentals/redis-basics-demo/`. Run with `mvn -q test` (Docker must be running; the first run pulls `redis:7.4-alpine`, about 60 MB). Read in this order:

1. `src/test/java/com/example/redisbasics/RedisContainerConfig.java` — a real Redis per test run, wired in with `@ServiceConnection`.
2. `src/test/java/com/example/redisbasics/StringsAndCountersTest.java` — `SET`/`GET`, a lost update from read-modify-write, then `INCR`.
3. `src/test/java/com/example/redisbasics/DataTypesTest.java` — hash, list (last 3 viewed), set (unique visitors), sorted set (leaderboard).
4. `src/test/java/com/example/redisbasics/ExpiryTest.java` — expiry, `TTL` -1/-2, `PERSIST`, a plain `SET` dropping the TTL, `INCR` keeping it, `SET NX`.
5. `src/test/java/com/example/redisbasics/ScanTest.java` — `SCAN` with a cursor vs `KEYS`.

---

## Try this yourself

1. Start Redis with `docker run -d --name redis -p 6379:6379 redis:7.4-alpine`, open `docker exec -it redis redis-cli`, and repeat the leaderboard with `ZINCRBY` and `ZREVRANGE shop:leaderboard 0 -1 WITHSCORES`. Then run `SET shop:x 1 EX 100`, `SET shop:x 2`, `TTL shop:x`. What does `TTL` return and why?
2. Add a test that counts unique visitors with a sorted set instead of a set, using the visit time as the score, and returns only visitors from the last hour with `rangeByScore`. Which command removes the older ones?

## Self-check

1. Two instances run `GET counter`, add 1 in Java, and `SET counter`. Explain how an update is lost, and why `INCR` can't lose one.
2. A teammate stores sessions with `set(key, value, Duration.ofMinutes(30))`, and later updates them with `set(key, newValue)`. What happens to those sessions, and how would you spot it with `redis-cli`?
3. Why can a single `KEYS *` slow down every service using the same Redis, and what do you use instead?
