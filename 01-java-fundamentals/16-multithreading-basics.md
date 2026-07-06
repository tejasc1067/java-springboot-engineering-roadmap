# 16 — Multithreading Basics

A thread is a separate path of execution inside your program. Multiple threads let your program do several things at once: handle many requests in a web server, compute in the background while UI stays responsive, fan out work in parallel.

This topic stays at the basics — creating threads, sleeping, joining, and the race conditions you get when multiple threads touch the same data. Module 03c covers the modern concurrency tools (Executors, CompletableFuture, virtual threads). For now: the language-level primitives.

A warning: concurrent code is hard. The bugs are non-deterministic, hard to reproduce, and embarrassingly common. Treat this topic carefully.

---

## Creating a thread

Two ways. The second is preferred.

**Extend `Thread`:**

```java
class MyThread extends Thread {
    @Override public void run() {
        System.out.println("hi from " + getName());
    }
}

MyThread t = new MyThread();
t.start();              // creates an OS thread and calls run()
```

**Implement `Runnable` (preferred):**

```java
Runnable task = () -> System.out.println("hi from " + Thread.currentThread().getName());

Thread t = new Thread(task);
t.start();
```

Why `Runnable` over `extends Thread`?

- A class extending `Thread` has burned its single-inheritance slot.
- Logically, *what to run* (the task) is separable from *how to run it* (the thread).
- `Runnable` plays nicely with `ExecutorService` and the rest of modern concurrency APIs.

Always call `start()`, not `run()`. Calling `run()` directly just executes the body on the current thread — no new thread is created.

---

## Sleep and join

`Thread.sleep(ms)` pauses the current thread for that many milliseconds. Throws a checked `InterruptedException`.

```java
Thread.sleep(1000);   // pause 1 second
```

Use it for demos and deliberate delays. Don't use it as a coordination mechanism — for "wait for thread B to finish," use `join`.

`thread.join()` makes the current thread wait until `thread` finishes.

```java
Thread t = new Thread(() -> heavyWork());
t.start();
// ... do other work ...
t.join();      // wait for t to finish before continuing
System.out.println("t done");
```

`join` also throws `InterruptedException`.

---

## Shared mutable state and race conditions

The trouble starts when two threads write to the same variable.

```java
class Counter {
    int count = 0;
    void increment() { count++; }
}

Counter c = new Counter();
Runnable task = () -> {
    for (int i = 0; i < 100_000; i++) c.increment();
};
Thread a = new Thread(task);
Thread b = new Thread(task);
a.start(); b.start();
a.join(); b.join();
System.out.println(c.count);   // expected 200,000 — actual: less, randomly
```

Why? `count++` is not one operation. It's:
1. Read the current value of `count`.
2. Add 1.
3. Write back to `count`.

If thread A reads `5`, thread B reads `5` before A writes back `6`, B also writes `6`. One increment is lost. This is a **race condition**.

---

## `synchronized`

`synchronized` makes a method or block atomic with respect to other synchronized code on the same object. Only one thread at a time can be inside.

```java
class Counter {
    int count = 0;
    synchronized void increment() {
        count++;     // now safe
    }
}
```

Or as a block, with a specific lock object:

```java
class Counter {
    int count = 0;
    final Object lock = new Object();

    void increment() {
        synchronized (lock) {
            count++;
        }
    }
}
```

A `synchronized` method on a non-static method locks on `this`. A `synchronized` static method locks on the class.

Costs:
- Contention slows things down: threads queue up waiting for the lock.
- Holding multiple locks invites deadlock (thread A holds lock X, waits for lock Y; thread B holds Y, waits for X).
- It's surprisingly easy to forget to synchronize *all* the methods that touch the shared field, leaving a hole.

`AtomicInteger` (in `java.util.concurrent.atomic`) is a single-purpose, lock-free alternative for counters and similar:

```java
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet();
```

For collections, `ConcurrentHashMap` and `CopyOnWriteArrayList` are thread-safe by design.

---

## When to reach for threads (and when not to)

- **Reach for threads** when you have genuinely independent work that can run in parallel — handling separate requests in a server, running background jobs, fanning out parallel calls to external services.
- **Don't reach for threads** "to make code faster" without measuring. Synchronization, context switches, and cache effects often eat the gains. A single-threaded program that finishes in 100 ms beats a four-threaded one that finishes in 200 ms because of overhead.

In modern Java code you'll almost never create `Thread` objects directly. You'll use an `ExecutorService`, `CompletableFuture`, or (Java 21+) virtual threads. Module 03c covers these. The reason we still teach `Thread` first: every higher-level API is built on these primitives, and the failure modes are the same.

---

## Common pitfalls

- **Calling `run()` instead of `start()`.** Runs synchronously on the current thread. No parallelism, no error message.
- **Race conditions on shared variables.** The example above. Always synchronize, use atomics, or use thread-safe collections.
- **`Thread.sleep` for coordination.** Fragile and slow. Use `join`, `CountDownLatch`, or higher-level APIs.
- **Catching and swallowing `InterruptedException`.** When a thread is interrupted, you should generally re-throw, or set the interrupted flag back (`Thread.currentThread().interrupt()`). Eating the interrupt makes the thread unresponsive to cancellation.
- **Starting too many threads.** Each thread costs memory (typically 0.5–1 MB stack). 100 threads is OK. 10,000 platform threads probably isn't. Use a thread pool.
- **Assuming order.** Thread scheduling is up to the OS. Output from two concurrent threads can interleave in any order on each run.

---

## Code examples

1. `ThreadCreation.java` — both styles side-by-side (`extends Thread` and `Runnable`).
2. `SleepAndJoin.java` — `Thread.sleep` and `Thread.join` in a tiny demo.
3. `RaceConditionBroken.java` paired with `SynchronizedFixed.java` — two threads incrementing a counter; the broken one loses increments, the fixed one doesn't.
4. `AtomicCounter.java` — the simpler `AtomicInteger` alternative to `synchronized`.

---

## Try this yourself

1. Run `RaceConditionBroken.java` a few times. Notice the result varies. Replace the field type with `AtomicInteger` (or apply `synchronized`) and confirm it always prints 200,000.
2. In `SleepAndJoin.java`, comment out the `.join()`. Watch main exit before the worker finishes. Put it back. The worker is now guaranteed to complete first.
3. Make `ThreadCreation.java` start 10 threads in a loop, each printing its name. Note the order of output across runs — it's not the order you started them in.

---

## Self-check

1. Why does calling `t.run()` directly defeat the purpose of creating a thread?
2. Two threads both increment a shared `int count` 100,000 times. Why is the final value usually less than 200,000?
3. You make a method `synchronized` but it only updates one shared field. What's the cost vs. using `AtomicInteger.incrementAndGet()`?
