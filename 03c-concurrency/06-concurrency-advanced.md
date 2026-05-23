# 06 — Concurrency Advanced

`synchronized` is fine for most code. When it isn't, `java.util.concurrent` and `java.util.concurrent.locks` and `java.util.concurrent.atomic` give you tools that are more flexible, more scalable, or both. This topic covers the parts of those packages that you'll reach for in real services:

- `ReentrantLock` and `ReadWriteLock` — explicit locking with timeouts, fairness, condition variables.
- `AtomicInteger` / `LongAdder` — lock-free counters built on CAS.
- `Semaphore` — bound concurrent access to a finite resource.
- `CountDownLatch` / `CyclicBarrier` — coordination between threads.
- `ThreadLocal` — per-thread storage.

---

## When `synchronized` isn't enough

| Need | `synchronized` | Better tool |
|------|---------------|------------|
| Timeout while acquiring lock | no | `lock.tryLock(timeout)` |
| Interruptible lock acquisition | no | `lock.lockInterruptibly()` |
| Multiple wait conditions on one lock | one (`wait/notify`) | `lock.newCondition()` |
| Reader/writer separation | no | `ReadWriteLock` |
| Fair (FIFO) ordering | no | `new ReentrantLock(true)` |
| Lock-free counter | n/a | `AtomicLong`, `LongAdder` |

---

## `ReentrantLock`

The explicit version of `synchronized`. Always pair `lock()` with `unlock()` in a `try/finally`.

```java
Lock lock = new ReentrantLock();
lock.lock();
try {
    // critical section
} finally {
    lock.unlock();
}
```

What you get over `synchronized`:

- **`tryLock()`** — returns immediately, true/false. Don't wait.
- **`tryLock(time, unit)`** — wait up to a timeout. Returns false if it gives up.
- **`lockInterruptibly()`** — like `lock()`, but responds to `Thread.interrupt()` while waiting.
- **`new ReentrantLock(true)`** — fair lock, FIFO queue of waiters. Slower throughput, no starvation.
- **Condition variables** — multiple "wait sets" per lock instead of the single intrinsic one.

Cost: must explicitly `unlock`, easy to leak by forgetting `try/finally`.

---

## `ReadWriteLock`

If a structure is read-heavy and writes are rare, every reader holding the same mutual-exclusion lock is wasted serialization. `ReadWriteLock` splits one logical lock into two:

- **Read lock** — multiple readers can hold it concurrently.
- **Write lock** — exclusive. Blocks all readers and other writers.

```java
ReadWriteLock rw = new ReentrantReadWriteLock();

rw.readLock().lock();
try { return cache.get(key); }
finally { rw.readLock().unlock(); }

rw.writeLock().lock();
try { cache.put(key, value); }
finally { rw.writeLock().unlock(); }
```

Trap: under heavy contention writers can starve. Use `new ReentrantReadWriteLock(true)` (fair) if it matters, or prefer `StampedLock` (added in Java 8) which supports optimistic reads.

For caches, `ConcurrentHashMap` ([03a topic 11](../03a-java-language-depth/11-concurrent-collections.md)) is almost always the right answer instead — no explicit locking at all.

---

## `AtomicInteger`, `AtomicLong`, `AtomicReference`

Hardware-supported lock-free updates via **CAS** (compare-and-swap).

```java
AtomicInteger counter = new AtomicInteger();
counter.incrementAndGet();                 // atomic ++
counter.compareAndSet(5, 10);              // if value == 5 set to 10, return true; else false
counter.updateAndGet(n -> n * 2);          // atomic functional update
```

How CAS works: the CPU instruction `CMPXCHG` reads the value and writes a new one only if the read matched an expected value, all in one bus transaction. If another thread changed the value in the meantime, the operation returns false; the caller retries.

CAS scales better than locks at low-to-moderate contention. At very high contention (many cores hammering one counter), the retry loop dominates — `LongAdder` is the fix.

---

## `LongAdder` — counting under heavy contention

```java
LongAdder count = new LongAdder();
count.increment();
long total = count.sum();
```

Internally `LongAdder` keeps many cells and threads update different cells in parallel; `sum()` adds them up. Trade-off: `sum()` isn't a precise atomic snapshot, but for metrics counters that's exactly what you want.

Rule of thumb:

- Low contention or you need `compareAndSet` semantics → `AtomicLong`.
- Many writers, sum read occasionally → `LongAdder`.

---

## `Semaphore`

A counting permit pool. Threads `acquire()` a permit before doing work and `release()` it after. Useful when a resource has a fixed capacity (a DB connection pool, an external rate limit, a downstream service quota).

```java
Semaphore tickets = new Semaphore(3);   // 3 concurrent permits

tickets.acquire();
try { callExternalService(); }
finally { tickets.release(); }
```

`Semaphore` is also how you implement a custom-bounded thread pool guard at the application layer.

---

## `CountDownLatch` — wait for N events, once

```java
CountDownLatch ready = new CountDownLatch(workerCount);
for (int i = 0; i < workerCount; i++) {
    pool.execute(() -> { doWork(); ready.countDown(); });
}
ready.await();                  // blocks until count hits 0
```

One-shot. After the count hits zero, the latch stays at zero forever — you can't reset it. Use for: wait-for-startup, wait-for-N-tasks-to-finish.

---

## `CyclicBarrier` — wait for N parties, repeatedly

```java
CyclicBarrier barrier = new CyclicBarrier(3, () -> System.out.println("phase complete"));

// each thread calls:
barrier.await();   // blocks until all 3 arrive, then everyone proceeds, barrier resets
```

Reusable — the barrier recycles after each gate opens. The optional "barrier action" runs once each cycle. Use for: parallel phases of a computation where every worker must finish phase 1 before any starts phase 2.

---

## `ThreadLocal`

A `ThreadLocal<T>` gives every thread its own private copy of a variable. Same field reference, different value per thread.

```java
static final ThreadLocal<DateFormat> df = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
df.get().format(new Date());
```

Used heavily in web frameworks for per-request context (auth user, transaction ID, MDC log fields).

**Critical pitfall in thread pools.** A `ThreadLocal` is tied to the thread, and pool threads outlive any single task. A `ThreadLocal` set on task 1 is visible to task 2 if they happen to land on the same worker. Always clear it at the end of the task:

```java
try {
    context.set(...);
    doWork();
} finally {
    context.remove();        // critical in pooled environments
}
```

Spring/Tomcat handle this for you for request-scoped data; your own `ThreadLocal` needs it manually.

---

## Common pitfalls

- **Forgetting `unlock()`.** Exception in critical section → lock held forever → deadlock. Always `try/finally`.
- **`tryLock(timeout)` ignored return value.** If false, you didn't acquire — calling the critical section anyway is a silent data race.
- **`AtomicReference` on a mutable object.** The reference is atomic; mutations through it are not. Either use immutable references, or guard the object with a lock.
- **`ThreadLocal` leak.** In a long-lived pool, never `remove()` ing means stale values surface in unrelated requests, plus the value is GC-pinned by the worker thread.
- **`CountDownLatch` initial count off by one.** Latch goes negative? No, it just never opens. Common when starting N workers but the latch was constructed with N-1.

---

## Code examples

1. `ReentrantLockTryTimeout.java` — `tryLock(time, unit)` returns false instead of blocking forever.
2. `ReadWriteLockBenefit.java` — many readers run in parallel while writers exclude.
3. `AtomicCounterCAS.java` — CAS-based counter, including a manual `compareAndSet` retry loop.
4. `LongAdderVsAtomicLong.java` — `LongAdder` wins under heavy write contention.
5. `SemaphoreRateLimit.java` — limit concurrent calls to a downstream service.
6. `CountDownLatchWaitForReady.java` — main waits until N workers signal ready.
7. `CyclicBarrierPhases.java` — three workers complete phase 1 before any starts phase 2.
8. `ThreadLocalLeakInPool.java` paired with `ThreadLocalProperCleanup.java` — leaking values across pool tasks vs proper `remove()`.

---

## Try this yourself

1. In `LongAdderVsAtomicLong.java`, drop the thread count to 1. The two are comparable. At 8+ threads, `LongAdder` pulls ahead.
2. In `ReadWriteLockBenefit.java`, replace the `ReadWriteLock` with a plain `ReentrantLock`. Read throughput collapses to single-threaded.
3. In `ThreadLocalLeakInPool.java`, watch how task B sees the value task A set on the same worker.

---

## Self-check

1. Your service holds a lock across a slow downstream call. Replacing `synchronized` with `ReentrantLock` gives you which extra capability that fixes the symptom (long wait queues)?
2. You have one writer thread and 1000 reader threads. Why is `ReadWriteLock` (or `StampedLock`) usually better than `synchronized` here?
3. What's the one line you must add in a `finally` block to use `ThreadLocal` safely from a pool worker?
