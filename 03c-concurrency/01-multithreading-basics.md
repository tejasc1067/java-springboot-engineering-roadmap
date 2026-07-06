# 01 — Multithreading Basics

A thread is a separate path of execution inside the same JVM process. Backend systems run many threads simultaneously: one per inbound HTTP request, one per background job, one per database connection that's mid-call. This topic covers the foundations — what a thread is, the two ways to create one, what `start()` actually does that `run()` doesn't, the lifecycle states, and what "daemon" means.

Topics 02–05 dig into the hard parts: synchronization, visibility, executors, futures. This one is just the vocabulary.

---

## The problem this solves

A web server with one thread can serve exactly one request at a time. The next request waits until the first finishes. If the first request hits a slow database query, the whole server stalls. Threads let the server do other work while one request is blocked on I/O — and on a multi-core CPU, threads can also run truly in parallel.

---

## Process vs thread

| | Process | Thread |
|---|---------|--------|
| Address space | Its own | Shared with other threads in the same process |
| Creation cost | Expensive (kernel call, page tables) | Cheap (a stack + bookkeeping) |
| Communication | IPC: pipes, sockets, shared memory | Just read/write shared variables |
| Isolation | Strong — a crash in one process doesn't kill others | Weak — a corrupt write affects everyone |

Threads share memory. That's why they're fast to communicate and dangerous to get wrong.

---

## Creating a thread — two ways

**Option A: implement `Runnable` (preferred).**

```java
Runnable task = () -> System.out.println("hi from " + Thread.currentThread().getName());
Thread t = new Thread(task, "worker-1");
t.start();
```

**Option B: extend `Thread` (legacy, avoid).**

```java
class MyThread extends Thread {
    public void run() { System.out.println("hi"); }
}
new MyThread().start();
```

Why prefer `Runnable`:

- Java has single inheritance — extending `Thread` burns your one slot.
- `Runnable` separates the *task* (what to do) from the *worker* (who runs it). The same `Runnable` can be submitted to an `ExecutorService` later (topic 04) without rewriting it.

---

## `start()` vs `run()` — the most-asked interview question

`t.start()` — asks the OS for a new thread of execution, then calls `run()` on that new thread.
`t.run()` — a plain method call on the current thread. No new thread is created.

If you call `run()` directly, the code executes but you've gained nothing. It's a normal sequential call.

```java
Thread t = new Thread(() -> System.out.println(Thread.currentThread().getName()));
t.run();    // prints "main" — ran on the calling thread
t.start();  // prints "Thread-0" — ran on a new thread
```

Calling `start()` twice on the same `Thread` throws `IllegalThreadStateException`. A `Thread` is single-use.

---

## Lifecycle states

A thread is always in exactly one of these states (see `Thread.State`):

| State | Meaning |
|-------|---------|
| `NEW` | Created with `new Thread(...)`, `start()` not called yet |
| `RUNNABLE` | Eligible to run — currently executing or waiting for a CPU |
| `BLOCKED` | Waiting to acquire a monitor lock (someone else holds the `synchronized` block) |
| `WAITING` | Parked indefinitely: `Object.wait()`, `Thread.join()`, `LockSupport.park()` |
| `TIMED_WAITING` | Same, but with a timeout: `Thread.sleep(ms)`, `wait(ms)`, `join(ms)` |
| `TERMINATED` | `run()` has returned (or thrown) |

There is **no `RUNNING` state** in Java's API — "currently on CPU" and "queued to run" are both `RUNNABLE`. The JVM doesn't expose the distinction.

---

## `sleep`, `join`, daemon threads

**`Thread.sleep(ms)`** — current thread pauses for at least `ms` milliseconds. Throws `InterruptedException` (a checked exception — you must handle it). `sleep` does not release any locks the thread holds.

**`t.join()`** — current thread waits for `t` to finish. `t.join(2000)` waits at most 2 seconds. Useful when `main` needs to wait for workers before exiting.

**Daemon threads** — background threads that don't keep the JVM alive. Set with `t.setDaemon(true)` *before* `start()`. The JVM exits as soon as all non-daemon threads finish, even if daemons are still running. Use for housekeeping (metrics flushers, log shippers, cache evictors); never for work that must complete (a daemon doing a DB write may be killed mid-statement).

---

## Concurrency vs parallelism

**Concurrency** — multiple tasks make progress over the same period. Could be on one CPU core (the OS time-slices between them) or many. It's about *structure*: the program is organized into independent tasks.

**Parallelism** — multiple tasks execute *literally at the same instant* on different CPU cores. Requires hardware support.

A single-core machine can be concurrent but not parallel. Java's thread API doesn't distinguish — `start()` gives you concurrency; whether you also get parallelism depends on cores and OS scheduling.

---

## Common pitfalls

- **Calling `run()` instead of `start()`.** The code runs but on the wrong thread. Symptom: no concurrency at all, despite "having threads."
- **Forgetting to handle `InterruptedException`.** Swallowing it (`catch (InterruptedException e) {}`) loses the cancellation signal. At minimum, re-set the flag: `Thread.currentThread().interrupt();`
- **Daemon thread doing critical work.** JVM shuts down → daemon is killed mid-write. Make threads that *must* finish non-daemon (the default).
- **Assuming `Thread.sleep(100)` sleeps exactly 100ms.** It sleeps *at least* 100ms. On a busy system, much more.
- **Naming nothing.** Threads default to `Thread-0`, `Thread-1`. In a thread dump that's useless. Always name worker threads (`new Thread(task, "report-builder")`).

---

## Code examples

1. `RunnableVsThread.java` — same task, both creation styles, side by side.
2. `StartVsRun.java` — proves `run()` doesn't spawn a thread; `start()` does.
3. `LifecycleStates.java` — observe `NEW → RUNNABLE → TIMED_WAITING → TERMINATED`.
4. `JoinCoordination.java` — `main` waits for workers via `join()`.
5. `SleepIsNotPrecise.java` — measure actual sleep duration vs requested.
6. `DaemonExitsWithJvm.java` — daemon thread loop gets killed when `main` returns.

---

## Try this yourself

1. In `StartVsRun.java`, change `start()` to `run()` and confirm both prints come from the `main` thread.
2. In `DaemonExitsWithJvm.java`, comment out `setDaemon(true)` and observe the JVM no longer exits — the worker loop keeps it alive forever.
3. In `JoinCoordination.java`, remove the `join()` calls. The `main` thread may print "all done" before the workers print their results.

---

## Self-check

1. You call `t.run()` on a `Thread`. The body executes. What did you lose by not calling `t.start()`?
2. A web request handler spawns a thread to write an audit log, calls `setDaemon(true)`, then returns 200 to the client. Why is this dangerous?
3. `Thread.sleep(50)` returns. Are you guaranteed exactly 50ms elapsed, at most 50ms, or at least 50ms? Why?
