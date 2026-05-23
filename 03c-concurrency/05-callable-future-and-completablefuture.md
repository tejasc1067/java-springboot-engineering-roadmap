# 05 — Callable, Future, and CompletableFuture

`Runnable` runs work, but it can't return a value and can't throw checked exceptions. `Callable<T>` does both. When you submit a `Callable` to an executor you get a `Future<T>` — a handle on a result that may not be ready yet.

`Future` (Java 5) is the foundation. `CompletableFuture` (Java 8) builds on top with chaining, composition, and timeouts — the API you actually use for non-blocking pipelines in modern backend code.

---

## The problem this solves

A request handler needs three things from three different services: a user profile from auth, a recommendation list from ML, and a feature flag from config. Sequentially each takes 100ms, so the handler takes 300ms. Done in parallel they take ~100ms total — but you need a way to express "kick all three off, then combine the results" without blocking three threads on `get()`.

That is what `Future` started and `CompletableFuture` finished.

---

## `Callable<T>` vs `Runnable`

```java
Runnable r = () -> System.out.println("no return value");        // void run()
Callable<Integer> c = () -> 7 * 6;                                // T call() throws Exception
```

Differences:

| | `Runnable` | `Callable<T>` |
|---|------------|---------------|
| Return type | `void` | `T` |
| Throws checked exceptions | no | yes (`throws Exception`) |
| Submit returns | `Future<?>` (always `null`) | `Future<T>` |

Both are functional interfaces — use lambdas.

---

## `Future<T>`

```java
ExecutorService pool = Executors.newFixedThreadPool(2);
Future<String> f = pool.submit(() -> { Thread.sleep(200); return "hello"; });

// Do other work...

String value = f.get();                                  // blocks until ready
String value2 = f.get(500, TimeUnit.MILLISECONDS);       // blocks up to 500ms; throws TimeoutException
boolean cancelled = f.cancel(true);                      // try to interrupt the running task
boolean done = f.isDone();
```

What `Future` is missing:

- **No way to attach a callback.** You can only poll or block.
- **No composition.** Can't say "run B with A's result".
- **No combination.** Can't say "wait for A and B and then do C".
- **Limited exception handling.** Exceptions surface only when you call `get()`.

That's why `CompletableFuture` exists.

---

## `CompletableFuture<T>` — the modern API

A `CompletableFuture` is a `Future` plus a fluent pipeline. Three things to know:

1. **How to start one.** `CompletableFuture.supplyAsync(...)` runs a supplier on the common ForkJoinPool (or one you pass).
2. **How to chain.** `thenApply`, `thenAccept`, `thenRun`, `thenCompose`.
3. **How to combine.** `thenCombine`, `allOf`, `anyOf`.

### Starting

```java
CompletableFuture<String> a = CompletableFuture.supplyAsync(() -> fetchUser(123));
// optional second arg: your own executor instead of the common pool
CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> fetchUser(123), myPool);
```

If you don't pass an executor, the work runs on `ForkJoinPool.commonPool()` — a JVM-wide pool sized to your CPU count. Fine for CPU-light work, bad for blocking I/O (you'll starve other library code sharing it). For production I/O work, **always pass your own executor**.

### Chaining (one input → one output)

```java
CompletableFuture<Integer> chain = CompletableFuture
    .supplyAsync(() -> "hello world")
    .thenApply(String::length)               // String -> Integer
    .thenApply(n -> n * 2);                   // Integer -> Integer
chain.get();    // 22
```

- `thenApply(fn)` — transform the value (synchronous, on the thread that completes the previous stage).
- `thenApplyAsync(fn)` — same, but reschedule on the pool. Use when the transform itself is slow.
- `thenAccept(consumer)` — receive value, return `Void`.
- `thenRun(runnable)` — fires after the previous stage completes; doesn't see the value.
- `thenCompose(fn)` — like `flatMap`: the function itself returns a `CompletableFuture`. Use when chaining async calls.

### Composing (async returns another async)

```java
CompletableFuture<User> userF = lookupUser(id);
CompletableFuture<Profile> profileF = userF.thenCompose(u -> lookupProfile(u));
```

Without `thenCompose` you'd get `CompletableFuture<CompletableFuture<Profile>>`. `thenCompose` flattens it.

### Combining (two inputs → one output)

```java
CompletableFuture<String> nameF  = supplyAsync(() -> fetchName(id));
CompletableFuture<Integer> ageF  = supplyAsync(() -> fetchAge(id));
CompletableFuture<String> combined = nameF.thenCombine(ageF, (n, a) -> n + " is " + a);
```

Both source futures run concurrently. `thenCombine` fires when both are done.

### Waiting on many

```java
CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);
all.get();                                                   // blocks until all complete
List<String> results = Stream.of(f1, f2, f3).map(CompletableFuture::join).toList();

CompletableFuture<Object> first = CompletableFuture.anyOf(f1, f2, f3);   // first to finish
```

`allOf` returns `CompletableFuture<Void>` — it doesn't carry the results. You access them by joining the individual futures *after* `allOf` completes.

---

## Exception handling

```java
CompletableFuture<String> safe = CompletableFuture
    .supplyAsync(() -> { throw new RuntimeException("boom"); })
    .exceptionally(ex -> "fallback")                          // recover with default
    .thenApply(s -> s.toUpperCase());                         // continues normally
```

- `exceptionally(fn)` — handle a failure; returns a recovery value.
- `handle((value, ex) -> ...)` — handle both success and failure in one place. Always called.
- `whenComplete((value, ex) -> ...)` — observe the outcome without changing it. Often used for logging.

If you ignore exceptions and just call `get()` on a failed future, you'll get an `ExecutionException` wrapping the cause.

---

## Timeouts

```java
CompletableFuture<String> withTimeout = someFuture
    .orTimeout(2, TimeUnit.SECONDS)               // fail if not done in 2s
    .exceptionally(ex -> "default if timed out");

CompletableFuture<String> orValue = someFuture
    .completeOnTimeout("default", 2, TimeUnit.SECONDS);  // succeed with default
```

Both added in Java 9. They're the cleanest way to enforce a deadline on an async chain.

---

## Blocking vs non-blocking

The point of `CompletableFuture` is to *not* block. As soon as you call `get()` you have a thread waiting for a result, which is exactly what threads were too valuable for.

- Inside the pipeline, use callbacks (`thenApply`, `thenCompose`, etc.).
- Block only at the boundary — typically once, where the request handler must return the final response.

A common smell: chains that look async but call `.get()` in every step. That's blocking work hidden under an async API.

---

## Common pitfalls

- **Using `commonPool()` for blocking I/O.** Saturates the JVM-wide pool. Always pass your own executor for I/O work.
- **`get()` instead of `join()`** in lambdas — `get()` throws checked exceptions which break the lambda's signature. `join()` throws unchecked. Use `join()` inside chains.
- **Ignoring `exceptionally`.** A failure silently propagates to the end of the chain. If nothing observes it, you have an invisible bug.
- **`thenApply` for slow transforms.** Runs on the previous stage's thread. If that's the request thread, you're back to blocking. Use `thenApplyAsync`.
- **Forgetting `thenCompose`.** Returning a future from `thenApply` yields a nested future. `thenCompose` flattens.
- **`allOf` and then assuming the results are inside it.** They're not — `allOf` returns `Void`. Join each future after.

---

## Code examples

1. `RunnableVsCallable.java` — show what `Runnable` can't do that `Callable` can.
2. `FutureGetAndCancel.java` — block, timeout, and cancel a `Future`.
3. `CompletableFutureChain.java` — `thenApply` / `thenCompose`.
4. `CompletableFutureCombine.java` — `thenCombine` running two suppliers in parallel.
5. `CompletableFutureAllOf.java` — fan-out to N async calls, collect results.
6. `CompletableFutureExceptionally.java` — recovery with `exceptionally` and `handle`.
7. `CompletableFutureTimeout.java` — `orTimeout` and `completeOnTimeout`.

---

## Try this yourself

1. In `CompletableFutureCombine.java`, time the run. Two 200ms suppliers should finish in ~200ms, not 400ms — proof of parallelism.
2. In `CompletableFutureExceptionally.java`, remove the `exceptionally` step and call `.get()`. Observe the `ExecutionException` wrapping the cause.
3. In `CompletableFutureAllOf.java`, change one of the suppliers to throw. Watch `allOf().join()` propagate the exception.

---

## Self-check

1. You have a chain `supplyAsync(a).thenApply(b).thenApply(c)`. `b` takes 500ms of CPU work. Why might that block your caller, and which method would you switch `b` to?
2. What's the difference between `thenApply` and `thenCompose`, and when do you need the second?
3. `CompletableFuture.allOf(f1, f2)` completes. How do you get the values out of `f1` and `f2`?
