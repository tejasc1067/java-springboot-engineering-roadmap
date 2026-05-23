# Interview Q&A — Module 03c (Concurrency)

The classic concurrency interview ground. Each answer requires real understanding.

---

### Q1. `start()` vs `run()` — explain what the JVM actually does for each.

**A.**

- **`run()`** is a regular method call. The body executes on the calling thread. The `Thread` object's state is unchanged. No OS thread is created.
- **`start()`** is a native call. The JVM asks the OS for a new thread, registers it with the JVM, and arranges for it to invoke this `Thread`'s `run()` method as its entry point. The state of the `Thread` becomes `RUNNABLE`.

Calling `start()` twice on the same `Thread` throws `IllegalThreadStateException`. A `Thread` is a one-shot object.

The interview test: "would `t.run(); t.run();` print on two threads?" — no, both on the caller. `t.start(); t.start();` — throws on the second call.

---

### Q2. `synchronized` and `volatile` — what does each guarantee?

**A.**

- **`synchronized`** provides **mutual exclusion** AND **visibility** (memory barriers on lock acquire/release). Only one thread in the critical section; everything written before release is visible to threads after acquire.
- **`volatile`** provides only **visibility**: every read sees the latest write. It does *not* provide atomicity — `count++` is still racy.

When you need both atomicity and visibility on a single variable: `AtomicInteger` (lock-free) or `synchronized`. When you need atomicity across multiple variables: `synchronized` or a `Lock`.

---

### Q3. AB-BA deadlock — what is it, and what's the single discipline that prevents it?

**A.** Two threads each hold a lock the other needs:

- Thread 1: holds A, wants B.
- Thread 2: holds B, wants A.

Both wait forever.

Prevention: **acquire locks in a consistent global order.** If every code path that touches A and B locks A first, no cycle can form. The order can be by id, by class identity hash, anything — what matters is that *everyone* uses the same order.

Detection in production: `jstack <pid>` prints a deadlock report at the bottom. Programmatic: `ThreadMXBean.findDeadlockedThreads()`.

---

### Q4. `ExecutorService` shutdown — `shutdown()` vs `shutdownNow()`?

**A.**

- **`shutdown()`** — refuses new tasks. Already-submitted tasks (running or queued) finish. Returns immediately.
- **`shutdownNow()`** — refuses new tasks, attempts to interrupt running tasks, returns the list of queued tasks that were never started.

The graceful pattern:

```java
pool.shutdown();
if (!pool.awaitTermination(30, SECONDS)) {
    pool.shutdownNow();
    pool.awaitTermination(5, SECONDS);
}
```

For `shutdownNow` to interrupt running tasks meaningfully, those tasks must respect `Thread.currentThread().isInterrupted()` (or be blocked on a method that throws `InterruptedException`).

---

### Q5. Why is `Executors.newCachedThreadPool` considered dangerous in production?

**A.** It has `corePoolSize = 0`, `maximumPoolSize = Integer.MAX_VALUE`, and a `SynchronousQueue`. Behavior:

- Every submitted task that can't be handed to an idle thread spawns a new thread.
- No upper bound.

Under sustained overload, the JVM creates threads until it can't (`OutOfMemoryError: unable to create native thread`).

Same critique applies to `newFixedThreadPool(N)`: its queue is unbounded — the threads are bounded, but the queue grows until OOM.

Production: build a `ThreadPoolExecutor` directly with a bounded queue and an explicit `RejectedExecutionHandler`.

---

### Q6. `CompletableFuture.thenApply` vs `thenApplyAsync` — when to use each?

**A.**

- **`thenApply(fn)`** — `fn` runs on whatever thread completed the previous stage. Fast for cheap transforms (extract a field, format a string). Bad if `fn` is slow — it ties up the upstream completer (often a Netty event-loop or a downstream client's thread).
- **`thenApplyAsync(fn)`** — `fn` runs on the common ForkJoinPool (or an executor you pass).
- **`thenApplyAsync(fn, executor)`** — `fn` runs on *your* executor. The right choice for slow CPU work or for I/O.

Rule of thumb: synchronous transforms → `thenApply`. Anything you wouldn't be comfortable running on a Netty event-loop → `thenApplyAsync(fn, yourPool)`.
