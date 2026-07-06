# 04 — Executor Framework

Creating threads with `new Thread()` works, but in a backend service you almost never want to do it. Each `new Thread()` allocates ~1 MB of stack, runs once, then dies. Under load you end up with thousands of short-lived threads, the GC working overtime, and the OS scheduler thrashing.

The fix is a **thread pool**: a fixed (or bounded) set of long-lived worker threads that pull tasks from a queue. Java's standard library wraps this in the **Executor framework**. This topic covers the three core types (`Executor`, `ExecutorService`, `ScheduledExecutorService`), the factory methods on `Executors`, the raw `ThreadPoolExecutor` constructor, and how to shut a pool down cleanly.

---

## The problem this solves

A request handler does `new Thread(this::process).start()`. Under 10k requests/second you create 10k threads/second. Stack allocation + OS thread creation cost roughly a millisecond each. The JVM heap fills with `Thread` objects. The OS scheduler has 10k threads to balance. Throughput collapses, latency spikes, eventually the JVM dies on `OutOfMemoryError: unable to create native thread`.

A pool of, say, 50 worker threads serving the same 10k tasks/second uses 50 stacks, zero thread-creation cost in the steady state, and the OS sees only 50 schedulable entities. The work is processed in parallel up to the pool size; the rest queues briefly.

---

## The three core types

| Type | Purpose |
|------|---------|
| `Executor` | One method: `execute(Runnable)`. The minimal abstraction. |
| `ExecutorService` | Adds `submit` (returns `Future`), `invokeAll`, `invokeAny`, `shutdown`. The one you use 95% of the time. |
| `ScheduledExecutorService` | Adds `schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay`. For periodic / delayed work. |

You almost always program to `ExecutorService`. `Executor` is too thin; `ScheduledExecutorService` is for cron-like work.

---

## Factory methods on `Executors`

`java.util.concurrent.Executors` has a handful of one-liners:

```java
Executors.newFixedThreadPool(8)        // exactly N workers; unbounded queue
Executors.newSingleThreadExecutor()    // one worker; serializes everything; unbounded queue
Executors.newCachedThreadPool()        // grows on demand; no bound on threads
Executors.newScheduledThreadPool(2)    // for schedule / scheduleAtFixedRate
```

What they actually do (from the source):

- `newFixedThreadPool(N)` → `new ThreadPoolExecutor(N, N, 0L, NANOSECONDS, new LinkedBlockingQueue<>())`. Unbounded queue. Under sustained overload the queue grows until you OOM.
- `newCachedThreadPool()` → core 0, max `Integer.MAX_VALUE`, `SynchronousQueue`. Will create a new thread on every task if none are idle. Under load: unlimited threads.

Both have the same failure mode: no real bound. For anything production, build a `ThreadPoolExecutor` directly with a bounded queue and an explicit rejection policy.

```java
ExecutorService pool = new ThreadPoolExecutor(
    8,                                  // corePoolSize: keep alive
    16,                                 // maximumPoolSize: cap
    60, TimeUnit.SECONDS,               // keepAlive for non-core threads
    new ArrayBlockingQueue<>(1000),     // BOUNDED queue
    new ThreadPoolExecutor.AbortPolicy() // fail fast when full
);
```

**Rejection policies:**

| Policy | Behavior when full |
|--------|--------------------|
| `AbortPolicy` (default) | throws `RejectedExecutionException` |
| `CallerRunsPolicy` | runs the task on the calling thread → natural backpressure |
| `DiscardPolicy` | silently drops |
| `DiscardOldestPolicy` | drops the oldest queued task |

`CallerRunsPolicy` is often the right choice for backend services: when the pool can't keep up, the producer slows down because it's now doing the work itself.

---

## `submit` vs `execute`

```java
pool.execute(runnable);             // fire-and-forget, no return value
Future<String> f = pool.submit(callable);  // returns Future for result/cancellation
```

`submit` accepts both `Runnable` and `Callable`. It always returns a `Future`. With `execute`, an exception inside the task is handled by the pool's `UncaughtExceptionHandler`; with `submit`, exceptions are captured inside the `Future` and surface when you call `get()`.

Topic 05 goes deep on `Future` and `CompletableFuture`.

---

## Scheduled executors

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

// run once after 5 seconds
scheduler.schedule(() -> refreshCache(), 5, TimeUnit.SECONDS);

// every 30 seconds, starting from now (fires whether previous finished or not -- waits if still running)
scheduler.scheduleAtFixedRate(this::reportMetrics, 0, 30, TimeUnit.SECONDS);

// 30 seconds AFTER the previous run completed
scheduler.scheduleWithFixedDelay(this::cleanup, 0, 30, TimeUnit.SECONDS);
```

`fixedRate` vs `fixedDelay`:

- `fixedRate` — next execution starts at `start + period`. If the task took 40s and the period is 30s, the next one starts immediately (or it falls behind).
- `fixedDelay` — next execution starts `period` after the previous one *finished*. Drift is bounded.

For most "every N seconds" backend jobs, `fixedDelay` is what you actually want.

**Pitfall:** if a scheduled task throws an exception, it is **silently cancelled** and never runs again. Always wrap the body in `try { ... } catch (Throwable t) { log.error(t); }`.

---

## Shutting down

A pool keeps non-daemon worker threads alive. If you forget to shut it down, the JVM doesn't exit.

```java
pool.shutdown();                                 // no new tasks; queued ones still run
boolean done = pool.awaitTermination(30, TimeUnit.SECONDS);
if (!done) {
    pool.shutdownNow();                          // interrupt running tasks, drop queue
    pool.awaitTermination(5, TimeUnit.SECONDS);
}
```

`shutdown()` is graceful. `shutdownNow()` interrupts. Tasks must respect `Thread.interrupted()` for `shutdownNow` to actually do anything useful — long blocking I/O without interrupt handling will not stop.

For a service with a clear lifecycle (Spring `@PreDestroy`, etc.), pair every pool creation with a shutdown hook.

---

## Sizing the pool

The rule of thumb:

- **CPU-bound work** (image processing, computation): `cores` or `cores + 1`.
- **I/O-bound work** (DB calls, HTTP, file I/O): much larger — threads spend most time blocked waiting.

A rough formula: `threads = cores * (1 + waitTime / serviceTime)`. For a service where each task waits 90ms on a DB and computes 10ms, on 8 cores: `8 * (1 + 90/10) = 80` threads.

In practice you measure under realistic load and adjust.

---

## Common pitfalls

- **`newCachedThreadPool` for unbounded incoming traffic.** Creates a thread per task with no limit. One bad day and you OOM the JVM.
- **`newFixedThreadPool` for an API endpoint.** Unbounded queue — under overload, requests queue indefinitely, latency climbs, clients time out, queue keeps growing.
- **No shutdown.** JVM hangs after `main` returns because workers are non-daemon. Always `shutdown()`.
- **Exceptions inside `scheduleAtFixedRate` tasks.** Task is silently cancelled. Always catch `Throwable` inside.
- **One global pool for everything.** A slow batch job fills the queue, blocking the latency-sensitive request handling. Separate pools by workload class.
- **`pool.submit(task).get()` on the calling thread that submitted to the same pool.** If the pool is full, deadlock — the submitter is holding a thread the task needs.

---

## Code examples

1. `FixedPoolBasics.java` — submit ten tasks to four workers, observe reuse.
2. `ExecuteVsSubmit.java` — same task, both APIs, show the `Future`.
3. `BoundedPoolWithBackpressure.java` — `ThreadPoolExecutor` with `CallerRunsPolicy` under overload.
4. `ScheduledFixedRateVsFixedDelay.java` — see the difference when a task takes longer than the period.
5. `ScheduledTaskSwallowsException.java` — task throws, then never runs again; the wrapped version keeps running.
6. `GracefulShutdown.java` — full `shutdown` / `awaitTermination` / `shutdownNow` dance.

---

## Try this yourself

1. In `BoundedPoolWithBackpressure.java`, switch the rejection policy to `AbortPolicy`. Watch submissions throw under overload instead of being absorbed.
2. In `ScheduledFixedRateVsFixedDelay.java`, make the task take longer than the period. Compare actual run times between the two scheduling modes.
3. In `GracefulShutdown.java`, remove the `awaitTermination` calls. Notice the JVM lingers, or `main` returns but the worker is killed mid-task.

---

## Self-check

1. Why is `Executors.newFixedThreadPool(8)` dangerous in a high-traffic service? What constructor would you use instead, and what is the one critical parameter you'd add?
2. A `scheduleAtFixedRate` task throws a `NullPointerException` once. What happens on the next scheduled tick?
3. Your code submits a task and waits for it via `future.get()`. The submitting thread is itself a worker in the same pool. Why is this dangerous?
