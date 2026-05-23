# 03 — Thread Safety and Concurrency Problems

Topic 13 covered race conditions and `synchronized`. There are three more bugs you must recognize before writing serious concurrent code:

- **Visibility** — one thread writes a value, another thread never sees it.
- **Atomicity** — an operation that looks like one step is actually three, and threads interleave.
- **Liveness failures** — code that's "correct" but stops making progress: deadlock, livelock, starvation.

This topic also covers two practical paths to thread safety that avoid the bugs entirely: **immutability** and **thread confinement**.

---

## Thread safety, defined

A class is **thread-safe** if it behaves correctly when accessed from multiple threads with no external synchronization required by the caller. "Correctly" means: every method continues to satisfy its specification, regardless of timing.

Most ordinary classes (`ArrayList`, `HashMap`, your own POJOs) are **not** thread-safe. That's fine — most objects don't need to be. Only objects shared across threads do.

---

## Problem 1: visibility

A thread can cache a variable's value in a CPU register or local cache and never see another thread's write — possibly forever.

```java
class StopFlagBroken {
    boolean running = true;          // no synchronization, no volatile

    void runUntilStopped() {
        while (running) { /* work */ }   // may loop forever
    }
}
```

A second thread sets `running = false`. The first thread may *never observe* that write because it's reading from a stale cached value. Without a memory-flushing event (a lock acquire, a `volatile` read, etc.), there's no guarantee the write is visible.

**Fix: `volatile`.**

```java
volatile boolean running = true;
```

`volatile` says: every read goes to main memory, every write goes to main memory. No caching. Threads see each other's writes.

---

## Problem 2: atomicity (`volatile` doesn't fix everything)

`volatile` guarantees visibility, not atomicity.

```java
volatile int counter = 0;
counter++;   // STILL broken under contention
```

`counter++` is read, add, write — three operations. Two threads can each read 5, each add 1, each write 6. Volatile didn't help: it makes each read and each write atomic, but the *sequence* isn't.

**Three ways to fix.**

```java
// 1) synchronized: atomic by exclusion
synchronized (lock) { counter++; }

// 2) AtomicInteger: atomic by CAS (compare-and-swap, hardware instruction)
AtomicInteger counter = new AtomicInteger();
counter.incrementAndGet();

// 3) LongAdder: scales better under heavy contention (topic 06)
LongAdder counter = new LongAdder();
counter.increment();
```

When to use which:

- One read-only flag updated rarely → `volatile` is fine.
- Compound update on a number → `AtomicInteger` / `AtomicLong`.
- High-contention counter → `LongAdder`.
- Compound update on multiple fields → `synchronized` or `ReentrantLock` (topic 06).

---

## Problem 3: deadlock

Two threads each hold a lock the other needs.

```java
synchronized (lockA) {           // thread 1
    synchronized (lockB) { ... }
}

synchronized (lockB) {           // thread 2
    synchronized (lockA) { ... }
}
```

Thread 1 grabs A. Thread 2 grabs B. Thread 1 waits for B. Thread 2 waits for A. Forever.

**Prevention rules:**

1. **Acquire locks in a consistent global order.** If everyone takes A before B, the cycle can't form.
2. **Don't call foreign code with a lock held.** Callbacks may re-enter and try to lock something else.
3. **Use `tryLock(timeout)`** (topic 06) when ordering isn't possible — fail fast, back off, retry.

When in production: `jstack <pid>` will print a thread dump showing each thread, what lock it holds, and what lock it's waiting on. Deadlocks show up as a cycle.

---

## Problem 4: livelock and starvation

**Livelock** — threads aren't blocked; they're all actively *doing* something, but it cancels out. Two threads in a corridor, each politely stepping aside to let the other pass, both stepping the same way. Common in retry loops with no backoff: thread A retries, conflicts with B, both back off, both retry simultaneously, repeat.

**Starvation** — one thread keeps losing the scheduling race. Often happens with priority inversion or with locks that aren't fair: the same hot thread reacquires the lock before the queued waiters wake up.

`ReentrantLock(true)` (topic 06) creates a *fair* lock that serves waiters in FIFO order. Slower throughput, but no starvation.

---

## Two ways to sidestep the whole problem

### Immutability

If an object can't be changed after construction, no thread can corrupt it. Java's `String`, `Integer`, `LocalDate`, `UUID`, `Path` are all immutable by design.

To build your own immutable class:

- All fields `private final`.
- No setters.
- Make a defensive copy of any mutable input (e.g. arrays, collections).
- Don't leak `this` from the constructor.
- `final` class (or carefully designed for inheritance).

Immutable objects are thread-safe **for free**.

### Thread confinement

The safest shared variable is one that isn't actually shared. Keep state inside the thread that uses it. `ThreadLocal<T>` (topic 06) gives every thread its own copy of a variable, with the same name. Used heavily for: request context, transaction context, security principal in web frameworks.

---

## Common pitfalls

- **`volatile` on a counter.** Doesn't fix `count++`. Use `AtomicInteger`.
- **Holding a lock across a network call.** Deadlocks the whole service when the network is slow.
- **Different lock orders in different methods.** Establish one canonical order and document it.
- **Catching `InterruptedException` and swallowing it.** Either propagate it or re-set the flag (`Thread.currentThread().interrupt()`). Lost interrupts cause threads that should stop to keep running.
- **Assuming `synchronized List<T>` makes iteration safe.** Iteration must be hand-synchronized ([03a topic 11](../03a-java-language-depth/11-concurrent-collections.md)). Even thread-safe collections don't make compound operations atomic.

---

## Code examples

1. `VisibilityProblem.java` paired with `VisibilityFixedVolatile.java` — the missed `running=false` and the volatile fix.
2. `VolatileNotAtomic.java` — proves `volatile int` still loses increments under contention.
3. `AtomicIntegerCounter.java` — `AtomicInteger.incrementAndGet()` does what `volatile` can't.
4. `DeadlockTwoLocks.java` paired with `DeadlockFixedOrdering.java` — same threads, broken vs ordered acquisition.
5. `ImmutablePoint.java` — a class that's thread-safe for free.

---

## Try this yourself

1. In `VisibilityProblem.java`, watch the worker thread spin past the moment `main` sets the flag. (On some JVMs this loop terminates anyway — JIT optimization is undefined here, which is exactly the point: you can't rely on undefined behavior.) Then run the `Volatile` version and confirm the worker stops immediately.
2. In `VolatileNotAtomic.java`, increase thread count to 16. The loss grows.
3. In `DeadlockTwoLocks.java`, after about 2 seconds run `jstack <pid>` in another shell — you'll see Java's deadlock detector report the cycle.

---

## Self-check

1. A thread is reading a `boolean stop` flag in a hot loop. Another thread sets `stop = true`. Without `volatile` or `synchronized`, is the first thread guaranteed to ever see the change?
2. Why does making a counter `volatile` still result in lost updates under concurrent increment?
3. You have two methods that each lock `accountA` and `accountB`. What's the single discipline that prevents a deadlock between them?
