# Distributed Locks

A distributed lock makes sure that only one process, across all instances of a service, does something at a time: sends the nightly report, sells the last item, refreshes a token. Redis is the usual tool, because every instance can see it and `SET ... NX` (topic 06) lets exactly one caller win. Writing a lock that *looks* right takes one line. Writing one that stays right when processes crash, pause, and run slow takes the rest of this topic, and even then it has limits you have to design around.

---

## The problem this solves

Three instances of a shop service share one Redis that holds the stock count. Each sale is "read the stock, check it's above zero, write stock − 1". That's check-then-act, the same race as module 03c's unsynchronized counter, and it needs a lock. But which lock?

## How it works

### Broken: `synchronized` only covers one JVM

```java
public synchronized boolean sellLocallySynchronized(String sku, Runnable afterRead) {
    int stock = Integer.parseInt(redis.opsForValue().get("stock:" + sku));
    if (stock <= 0) {
        return false;
    }
    afterRead.run();                    // the test holds the sale here
    redis.opsForValue().set("stock:" + sku, String.valueOf(stock - 1));
    return true;
}
```

`SynchronizedAcrossInstancesTest.synchronized_doesNotStopTheOtherInstance_soTheLastItemIsSoldTwice` creates two `StockService` objects, one per "instance", with one lamp in stock. It lets both read before either writes, and **both sales succeed**: two customers paid for one lamp. `synchronized` locks *this object*, and each instance has its own object in its own JVM. Nothing in Java can lock across machines. The lock has to live somewhere every instance can see.

(Before reaching for a lock here, note that this particular problem has a lock-free answer: `DECR stock:lamp` is atomic, like topic 06's `INCR`, and a negative result means "sold out, put it back". The section on alternatives below explains why that's often better.)

### Broken: `SETNX`, then `EXPIRE`

The first Redis lock most people write:

```java
Boolean acquired = redis.opsForValue().setIfAbsent(key, "locked");   // SETNX
if (!Boolean.TRUE.equals(acquired)) {
    return false;
}
redis.expire(key, lease);                                             // EXPIRE: a second command
```

A lock needs an expiry: if the holder crashes, the lock must free itself, or nobody can ever get it again. Here the expiry is a **second command**. If the process dies between the two (a deploy, an out-of-memory kill, `kill -9`), the key exists with no TTL. `NaiveLockTest.crashBetweenSetnxAndExpire_leavesALockThatNeverExpires`: after the simulated crash, `TTL` is `-1` and every later `tryLock` fails. The nightly job never runs again until someone deletes the key by hand.

### Fixed: `SET key value NX PX` in one command

```java
Boolean acquired = redis.opsForValue().setIfAbsent(key, owner, lease);   // SET key owner NX PX lease
```

One command, so there's no moment where the lock exists without an expiry (`setNxPx_isOneCommand_soThereIsNoMomentWithoutAnExpiry`).

### Broken: releasing with `DEL`

```java
public void unlock(String key) {
    redis.delete(key);           // whoever's lock it is now
}
```

The lease is a guess at how long the work takes. When the work takes longer, the lease expires, someone else takes the lock, and then the first holder finishes and deletes **their** lock. `plainDel_afterYourLeaseExpired_deletesSomeoneElsesLock`:

1. A takes the lock with a 200 ms lease, and is slow.
2. The lease expires. B takes the lock.
3. A finishes and calls `unlock`: `DEL` removes B's lock.
4. C takes the lock while B is still working. **B and C both hold it.**

### Fixed: an owner id, and a check-and-delete script

Each acquisition stores a random owner id as the value. Release deletes the key only if it still holds *your* id. The check and the delete must be one atomic step, or the lock could change hands between them, so they run as a Lua script. Redis runs a script without letting any other command in:

```java
private static final RedisScript<Long> RELEASE = RedisScript.of("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
        end
        return 0
        """, Long.class);

public boolean release(Lease lease) {
    return redis.execute(RELEASE, List.of(lease.key()), lease.owner()) == 1L;
}
```

`checkAndDeleteScript_onlyReleasesYourOwnLock`: A's release returns `false` and leaves B's lock alone, so C still has to wait. A `false` from `release` is also useful information: it means your lease expired while you were working, and someone else may have done the same work.

### Broken: the lease expires while the holder is still working

The script fixed *releasing*. It can't fix this: A takes the lock, then **pauses**. A long garbage-collection pause, a slow network call, a laptop lid closed: the process doesn't run for a while and doesn't know it. The lease expires, B takes the lock and writes its result. Then A wakes up, still believing it holds the lock, and writes too.

`FencingTokenTest.withoutFencing_thePausedHolderOverwritesTheNewHoldersWork`: B's report is overwritten by A's stale one. A checking "do I still hold the lock?" right before writing doesn't help either: the pause can happen between the check and the write.

This is the fundamental limit of a lock with a lease. **Any lock that frees itself on a timeout can end up with two holders.** No Redis command fixes it, because the problem is in the holder, not in Redis.

### Fixed: fencing tokens

Make every acquisition carry a number that only ever goes up, and make the **storage** reject writes carrying a number lower than one it has already seen:

```java
// on acquire
long token = redis.opsForValue().increment("fencing:" + key);    // INCR: 1, 2, 3, ...

// in the storage
public synchronized boolean write(long fencingToken, String newContent) {
    if (fencingToken < highestTokenSeen) {
        return false;                 // a holder that lost its lock
    }
    highestTokenSeen = fencingToken;
    content = newContent;
    return true;
}
```

`withFencing_storageRejectsTheOlderToken`: A got token 1, B got token 2. B writes with 2. A wakes up and writes with 1, which is rejected. The lock may have two holders for a moment, but only the newest one can change anything. In a database, the fenced write is a conditional update:

```sql
UPDATE report SET content = ?, fencing_token = ? WHERE id = ? AND fencing_token < ?
-- 0 rows updated: you're the stale holder; stop
```

The catch: the storage has to take part. If the protected thing is a third-party API or an email, there's nowhere to check the token, and the lock is best-effort.

### The library option: Redisson

Redisson is a Redis client with ready-made locks. Its `RLock` stores an owner id (so only the owner can unlock) and has a **watchdog**: while the holder's process is alive, a background task keeps extending the lease, so a slow job doesn't lose its lock halfway through.

```java
RLock lock = redisson.getLock("lock:report");
lock.lock();             // no lease time: the watchdog keeps it alive (30 s lease, renewed every 10 s)
try {
    generateReport();
} finally {
    lock.unlock();
}
```

`RedissonLockTest` (watchdog timeout lowered to 1 s so the test is quick):

- `watchdog_keepsExtendingTheLease_whileTheHolderIsAlive`: 2.5 seconds after `lock()`, well past the 1 s lease, A still holds the lock and instance B's `tryLock()` fails.
- `holderDies_theWatchdogStops_andTheLockExpires`: A shuts down without unlocking; the renewals stop, the lease runs out, and B gets the lock within seconds. It isn't stuck forever like the `SETNX` lock.
- `anExplicitLeaseTime_turnsTheWatchdogOff`: `lock(300, MILLISECONDS)` means exactly 300 ms, with no renewal. B gets in while A may still be working.
- `onlyTheOwnerCanUnlock`: `unlock()` from another client throws `IllegalMonitorStateException`.

The watchdog makes expiry rarer, not impossible: a JVM paused longer than the lease has a paused watchdog too, and its lock expires like any other. Fencing is still the only protection against that.

Redisson isn't managed by Spring Boot, so the demo pins its version (`org.redisson:redisson:4.8.0`) and creates the client itself. There's also a `redisson-spring-boot-starter` that auto-configures a `RedissonClient`.

### One Redis, or several? (Redlock)

Why a lock needs a consistent store, and what a network partition does to one that stays available, is [module 18 topic 08](../18-system-design-basics/08-cap-and-consistency-models.md).

Everything above uses a single Redis. If that Redis fails over to a replica, a lock written just before the failure may not have reached the replica, and a second client can take it. **Redlock** is an algorithm that takes the lock on a majority of several independent Redis servers to survive that. It's controversial: Martin Kleppmann's essay "How to do distributed locking" argues that because of pauses and clock jumps it still can't guarantee safety, and that if you need correctness you need fencing tokens anyway, at which point a single Redis is enough. For a backend engineer the practical summary is:

- If a duplicate run only costs **efficiency** (a report generated twice, a cache warmed twice), a single-Redis lock is fine.
- If a duplicate would break **correctness** (money moved twice, stock oversold), don't rely on any lock alone. Use fencing, or don't use a lock at all.

### Often the better answer: no lock

Most "I need a distributed lock" problems are better solved by the database, which already has the transactions and constraints that locks try to imitate:

- **A unique constraint.** "Only one payment per order" is `UNIQUE (order_id)`; the second insert fails. That's the idempotency approach of module 13 topic 19.
- **Optimistic locking.** A `@Version` field makes a stale update fail instead of overwriting (module 10 topics 16 and 17; module 13 topic 22 does the same with an expected version on an event stream).
- **An atomic conditional update.** `UPDATE product SET stock = stock - 1 WHERE sku = ? AND stock > 0`: one row updated means sold, zero means sold out. In Redis, `DECR` or a Lua script does the same.
- **A claim row for scheduled jobs.** For "only one instance runs this job", insert a row with a unique key, or use ShedLock, which keeps that lock row in your database (module 08 topic 13). Simpler than a hand-rolled Redis lock.

Reach for a Redis lock when the protected work isn't a single database write: calling an external system, a long multi-step job, or coordinating work that has no database of its own.

---

## When to use it / when not to

**Use a Redis lock** to avoid duplicate *effort*: one instance runs a job, one instance refreshes a token, one caller rebuilds an expensive cache entry (topic 08).

**Don't rely on it alone for correctness.** If two holders at once would corrupt data, add fencing tokens checked by the storage, or redesign around a constraint or an atomic update.

**Don't use it at all** when a database constraint, `@Version`, or one atomic command expresses the same rule.

---

## Common pitfalls

- **`SETNX` and `EXPIRE` as two commands.** A crash in between leaves a lock that never expires. Use `SET key owner NX PX ms`.
- **Releasing with plain `DEL`.** After your lease expires you delete the next holder's lock. Store an owner id and release with a check-and-delete script.
- **A lease shorter than the work.** The lock expires mid-job and a second holder starts. Use a watchdog (Redisson) or a generous lease, and treat a failed release as "someone else may have done this".
- **Assuming the lock is safe because the code is correct.** Pauses still produce two holders. If that matters, fence the writes.
- **Locking where a constraint would do.** A unique index or `@Version` is simpler and doesn't fail when Redis does.

---

## Code examples

`CODE_EXAMPLES/09-distributed-locks/lock-demo/`. Run with `mvn -q test` (Docker must be running). Read in this order:

1. `src/main/java/com/example/lock/StockService.java` — check-then-act on shared stock, with `synchronized` and with a Redis lock.
2. `src/test/java/com/example/lock/SynchronizedAcrossInstancesTest.java` — two instances sell the last lamp twice; the Redis lock refuses the second sale.
3. `src/main/java/com/example/lock/NaiveRedisLock.java` — `SETNX` + `EXPIRE`, and `DEL` to release.
4. `src/main/java/com/example/lock/RedisLock.java` — `SET NX PX`, an owner id, the check-and-delete script, a fencing token.
5. `src/test/java/com/example/lock/NaiveLockTest.java` — the lock that never expires; deleting someone else's lock; the script that doesn't.
6. `src/main/java/com/example/lock/ReportStorage.java` — a store that checks fencing tokens.
7. `src/test/java/com/example/lock/FencingTokenTest.java` — a paused holder overwrites the new holder's work, then gets rejected.
8. `src/test/java/com/example/lock/RedissonLockTest.java` — the watchdog, a dead holder, an explicit lease, owner-only unlock.

---

## Try this yourself

1. Rewrite `StockService` without any lock, using `redis.opsForValue().decrement("stock:" + sku)`: a result below zero means sold out (increment it back and return `false`). Run the two-instance test against it. Why does it pass without a lock?
2. In `FencingTokenTest`, replace `ReportStorage` with an H2 table and the conditional `UPDATE ... WHERE fencing_token < ?` from this topic. Check the update count to decide whether the write was rejected.

## Self-check

1. Why can't `synchronized` protect stock shared by three instances, and where does the lock have to live instead?
2. Walk through how a lock released with plain `DEL` ends up held by two processes. What does the check-and-delete script store and compare?
3. A lock holder pauses for 40 seconds with a 30-second lease. Even with a perfect Redis lock, what goes wrong, and how does a fencing token stop it? When can't you use a fencing token?
