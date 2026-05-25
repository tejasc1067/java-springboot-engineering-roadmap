# Common Mistakes — Module 03b (Modern Java & Functional)

Real bugs you'll hit with lambdas, streams, and `Optional`.

---

## 1. `parallelStream` on the request thread (topic 03)

```java
public Response handle(Request r) {
    return r.items.parallelStream().map(this::lookup).collect(toList());
}
```

`parallelStream` uses `ForkJoinPool.commonPool` — shared across every parallel stream in the JVM. Under load, your `lookup` blocks the pool; other parallel streams freeze. The "speedup" is invisible or negative.

**Fix.** For I/O work, use `CompletableFuture` with your own pool (covered in [03c topic 05](../03c-concurrency/05-callable-future-and-completablefuture.md)). Reserve `parallelStream` for CPU-bound, embarrassingly parallel pure work on large collections.

---

## 2. Side effects inside a stream `map` or `filter` (topic 03)

```java
List<Integer> out = new ArrayList<>();
data.stream().filter(x -> x > 0).forEach(out::add);     // racy on parallelStream
data.parallelStream().forEach(out::add);                // ArrayList isn't thread-safe
```

Streams may be parallel; `ArrayList.add` is not thread-safe; size of `out` ends up random.

**Fix.** Use a `Collector`. `data.stream().filter(x -> x > 0).collect(toList())` is correct under both serial and parallel execution because Collectors produce per-thread containers and merge them at the end.

---

## 3. `toMap` with possibly-duplicate keys, no merge function (topic 03)

```java
users.stream().collect(Collectors.toMap(User::getEmail, u -> u));   // throws IllegalStateException on a collision
```

Two users with the same email → runtime crash.

**Fix.** Pass a merge function: `toMap(User::getEmail, u -> u, (a, b) -> a)` (keep the first). Or `groupingBy` if you actually wanted a list per key.

---

## 4. Reusing a stream (topic 03)

```java
Stream<String> s = list.stream().filter(...);
s.count();
s.forEach(...);          // IllegalStateException: stream has already been operated upon
```

Streams are single-shot.

**Fix.** Restart the pipeline: `list.stream().filter(...)...`.

---

## 5. `Optional` as a method parameter (topic 04)

```java
public List<User> search(Optional<String> filter) { ... }
```

Callers still might pass `null`. The signature is uglier without preventing the bug.

**Fix.** Overload (`search()` and `search(String filter)`) or accept `String` and treat `null`/empty as "no filter."

---

## 6. `orElse` with an expensive default (topic 04)

```java
user.orElse(loadDefaultUserFromDb());        // always loads, even when present
```

Method arguments evaluate eagerly. The DB call happens whether you need it or not.

**Fix.** `user.orElseGet(this::loadDefaultUserFromDb)`. Supplier is invoked only when empty.

---

## 7. `isPresent + get` instead of `map + orElse` (topic 04)

```java
if (u.isPresent()) return u.get().getName();
else return "unknown";
```

This is `if (u != null) u.x()` with extra steps. You haven't gained anything from `Optional`.

**Fix.** `return u.map(User::getName).orElse("unknown");` — declarative, no `get`, no `isPresent`.
