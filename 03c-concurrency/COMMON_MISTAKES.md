# Common Mistakes — Module 03c (Concurrency)

The real concurrency bugs — visibility, atomicity, deadlock, executor misuse, `ThreadLocal` leaks. Each entry has code that reproduces the bug and the one-line fix.

---

## 1. Calling `run()` instead of `start()` (topic 01)

```java
new Thread(task).run();    // runs on the caller. No new thread.
```

Looks like a thread, isn't a thread. Symptom: no concurrency at all.

**Fix.** `start()`. `run()` is the body; `start()` schedules a new OS thread that calls `run()` for you.

---

## 2. `count++` "fixed" with `volatile` (topic 03)

```java
volatile int count = 0;
count++;                  // still racy
```

`volatile` gives you visibility, not atomicity. `count++` is read-add-write — three steps.

**Fix.** `AtomicInteger.incrementAndGet()` for low contention; `LongAdder.increment()` for high contention.

---

## 3. Holding a lock across a slow downstream call (topics 02, 03)

```java
synchronized (lock) {
    httpClient.send(req);     // takes 500ms when downstream is slow
    // every other thread is parked for that 500ms
}
```

One slow downstream call cascades into your entire service freezing.

**Fix.** Don't hold locks across I/O. Acquire local state, release the lock, then call out. Or use `ReentrantLock.tryLock(timeout)` so contention has a bounded wait.

---

## 4. Deadlock from inconsistent lock order (topic 03)

```java
// thread 1
synchronized (accountA) { synchronized (accountB) { transfer(a, b); } }
// thread 2
synchronized (accountB) { synchronized (accountA) { transfer(b, a); } }
```

AB-BA cycle. Both threads stuck forever.

**Fix.** Always acquire locks in a consistent global order. For accounts, sort by id: `Account first = a.id < b.id ? a : b; Account second = ...`.

---

## 5. `Executors.newFixedThreadPool(N)` for an API endpoint (topic 04)

`newFixedThreadPool` uses an **unbounded** `LinkedBlockingQueue`. Under sustained overload, the queue grows until OOM.

**Fix.** Use `new ThreadPoolExecutor(...)` with `ArrayBlockingQueue(cap)` and an explicit `RejectedExecutionHandler` (`CallerRunsPolicy` is often the right choice).

---

## 6. Scheduled task throws once, silently stops forever (topic 04)

```java
scheduler.scheduleAtFixedRate(() -> {
    doWork();              // throws NPE on one tick
}, 0, 1, TimeUnit.MINUTES);
// next tick never fires
```

`ScheduledExecutorService` silently cancels a task that throws. You don't get a stack trace; the job just stops.

**Fix.** Wrap the body in `try { ... } catch (Throwable t) { log.error("scheduled job failed", t); }`. Every recurring task you write.

---

## 7. `CompletableFuture.supplyAsync` for I/O on the common pool (topic 05)

```java
CompletableFuture.supplyAsync(() -> httpClient.fetch(url));   // uses ForkJoinPool.commonPool
```

The common pool has ~ `cores - 1` threads. Your blocking HTTP call eats one of them. Run enough of these concurrently and you stall every parallel stream and `CompletableFuture` in the JVM.

**Fix.** Pass your own executor: `supplyAsync(..., myIoPool)`. Configure `myIoPool` sized for I/O work.

---

## 8. `ThreadLocal` set, never `remove()`d in a pool (topic 06)

```java
public void handle(Request r) {
    userContext.set(r.user());        // forgot the finally{}
    process(r);
}
```

Pool worker survives. Next request lands on the same worker. `userContext.get()` returns the previous user. Customer A sees Customer B's data in production.

**Fix.** `try { userContext.set(user); ... } finally { userContext.remove(); }`. Always. In every framework-managed thread pool.
