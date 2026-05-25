# 03 — Stream API

A `Stream<T>` is a pipeline of values you process with declarative operators — `filter`, `map`, `sorted`, `collect`. It's not a data structure (it doesn't store anything). It's a recipe that runs once a terminal operation kicks it off.

This topic covers the pipeline lifecycle, the operators you'll actually use, the `Collectors` toolkit, primitive streams, and the parallel-stream caveats that bite people in production.

---

## The pipeline lifecycle

```java
List<String> names = users.stream()                  // SOURCE
    .filter(u -> u.getAge() >= 18)                   // intermediate
    .map(User::getName)                              // intermediate
    .sorted()                                        // intermediate
    .collect(Collectors.toList());                   // TERMINAL
```

Three parts:

1. **Source.** Where the values come from. `Collection.stream()`, `Arrays.stream(arr)`, `Stream.of(a, b, c)`, `IntStream.range(0, 100)`, `Files.lines(path)`, `Stream.generate(...)`.
2. **Intermediate operations.** Each returns a new stream. They are **lazy** — nothing happens yet.
3. **Terminal operation.** Triggers the pipeline. Examples: `collect`, `forEach`, `count`, `reduce`, `findFirst`, `anyMatch`, `toArray`.

A stream can be consumed **exactly once**. After the terminal operation runs, calling another operation throws `IllegalStateException`.

---

## Lazy evaluation

Intermediate operations don't execute when you write them. They execute when the terminal runs, and **per element**, not per operator.

```java
Stream.of(1, 2, 3, 4, 5)
    .peek(n -> System.out.println("filter input: " + n))
    .filter(n -> n % 2 == 0)
    .peek(n -> System.out.println("map input: " + n))
    .map(n -> n * 10)
    .findFirst();
```

You might expect "filter all, then map all." Instead each element flows through the whole pipeline before the next one starts, and `findFirst` short-circuits as soon as it has one result. Real output:

```
filter input: 1
filter input: 2
map input: 2
```

This is why streams can process infinite sources (`Stream.iterate`, `Stream.generate`) — laziness plus short-circuit terminals.

---

## Intermediate operations

| Operation | What it does |
|-----------|------------|
| `filter(Predicate)` | Keep elements that match. |
| `map(Function)` | Transform each element. |
| `flatMap(Function)` | Each element becomes a stream; flatten them. |
| `distinct()` | Drop duplicates (uses `equals`). |
| `sorted()` / `sorted(Comparator)` | Sort. Buffers the whole stream. |
| `limit(n)` | First n elements. |
| `skip(n)` | Skip first n. |
| `peek(Consumer)` | Side effect (debug, log) — does not modify the element. |
| `takeWhile(Predicate)` (Java 9+) | Take while true, stop at first false. |
| `dropWhile(Predicate)` (Java 9+) | Skip while true. |

`flatMap` is the one that confuses beginners. Use it whenever your transform "explodes" each input into multiple outputs:

```java
List<List<Integer>> nested = List.of(List.of(1,2), List.of(3,4));
List<Integer> flat = nested.stream()
    .flatMap(List::stream)
    .collect(Collectors.toList());
// [1, 2, 3, 4]
```

---

## Terminal operations

| Operation | Returns |
|-----------|---------|
| `collect(Collector)` | Whatever the collector builds — list, map, string, count |
| `forEach(Consumer)` | nothing; side effect |
| `count()` | `long` |
| `reduce(...)` | A single accumulated value |
| `findFirst()` / `findAny()` | `Optional<T>` |
| `anyMatch` / `allMatch` / `noneMatch` | `boolean` |
| `toArray()` / `toArray(IntFunction)` | array |
| `min(Comparator)` / `max(Comparator)` | `Optional<T>` |

`reduce` deserves a sentence: it's the primitive behind everything else, and you'll occasionally need it directly when no `Collector` fits.

```java
int totalAge = users.stream().mapToInt(User::getAge).sum();        // primitive shortcut
int productOfIds = users.stream().map(User::getId).reduce(1, (a, b) -> a * b);
```

---

## `Collectors` — the terminal toolkit

`java.util.stream.Collectors` is most of why `collect(...)` is so useful.

```java
// Most common:
List<String> list   = stream.collect(Collectors.toList());
Set<String> set     = stream.collect(Collectors.toSet());
Map<Long, User> byId = users.stream().collect(Collectors.toMap(User::getId, u -> u));

// String building:
String csv = stream.collect(Collectors.joining(", "));
String html = stream.collect(Collectors.joining(", ", "<ul>", "</ul>"));

// Grouping:
Map<Department, List<User>> byDept = users.stream()
    .collect(Collectors.groupingBy(User::getDepartment));

// Counting per group:
Map<Department, Long> sizeByDept = users.stream()
    .collect(Collectors.groupingBy(User::getDepartment, Collectors.counting()));

// Partitioning (boolean predicate -> two-bucket map):
Map<Boolean, List<User>> adultSplit = users.stream()
    .collect(Collectors.partitioningBy(u -> u.getAge() >= 18));
```

`toMap` has a sharp edge: if two elements share a key, it throws `IllegalStateException` ("Duplicate key"). The 3-arg form takes a merge function: `toMap(keyFn, valueFn, (a, b) -> a)` keeps the first.

Java 16+ has `Stream.toList()` — a shortcut that returns an **unmodifiable** `List<T>`. Use it when you don't need a mutable list (which is most of the time).

---

## Primitive streams

`Stream<Integer>` boxes every value. For hot paths, use the primitive specializations:

```java
IntStream.rangeClosed(1, 100).sum();
LongStream.of(1L, 2L, 3L).average();
DoubleStream.of(1.5, 2.5).max();
```

Switch between primitive and reference streams with `mapToInt`, `mapToObj`:

```java
int total = users.stream().mapToInt(User::getAge).sum();    // primitive
List<String> names = IntStream.range(0, 10)
    .mapToObj(i -> "user-" + i)
    .collect(Collectors.toList());
```

---

## Parallel streams — and why to be careful

```java
list.parallelStream().filter(...).map(...).reduce(...);
```

Operations run in parallel on the **JVM-wide common `ForkJoinPool`**. That's the issue:

- The common pool has roughly `Runtime.availableProcessors() - 1` workers. Total. For your *whole* JVM.
- Every library that uses parallel streams or CompletableFuture without an executor shares it.
- One slow task starves every other parallel stream in the process.
- Inside a server framework you usually don't know what else is using it.

**Guidelines:**

- Use parallel streams for **pure, CPU-bound work** on a **large** dataset where the gain outweighs overhead.
- Never use parallel streams for **I/O** — you'll block the shared pool.
- The reduction function must be **associative**: `(a op b) op c == a op (b op c)`. Otherwise parallel results differ.
- If you need parallel I/O work, hand it to your own `ExecutorService` and use `CompletableFuture` instead.

In practice, most backend code is sequential. Use `parallelStream` only when you've measured a benefit.

---

## Common pitfalls

- **Reusing a stream.** Streams are one-shot. After a terminal, the stream is gone.
- **Side effects inside `map` or `filter`.** These should be pure. If you mutate shared state, parallel execution corrupts it.
- **`toMap` without a merge function on possibly-duplicate keys.** Throws at runtime. Always pass a merge if duplicates are possible.
- **Forgetting that `sorted()` buffers everything.** It can't emit anything until the whole stream is collected. For infinite streams or huge data, it explodes memory.
- **`parallelStream` for I/O.** Blocks the common pool, kills other parallel work in the JVM.
- **Using `Stream` where a `for` is clearer.** Five operations chained is often less readable than a six-line loop. Choose what the next reader will understand.

---

## Code examples

1. `StreamLifecycle.java` — source → intermediate → terminal, with `peek` to prove laziness and short-circuit.
2. `FilterMapCollect.java` — the bread-and-butter pipeline.
3. `FlatMapNested.java` — why `map` gives `Stream<Stream<T>>` and `flatMap` flattens.
4. `CollectorsToolkit.java` — `toMap`, `groupingBy`, `partitioningBy`, `joining`, `counting`.
5. `ReduceFromScratch.java` — `reduce` building sum, max, and concatenation.
6. `PrimitiveStreams.java` — `IntStream` saves boxing.
7. `ParallelStreamHazard.java` — race condition with a shared `ArrayList`; same pipeline fixed via thread-safe collector.

---

## Try this yourself

1. In `StreamLifecycle.java`, replace `findFirst()` with `count()`. Observe how every element flows through the pipeline now — short-circuit is gone.
2. In `ParallelStreamHazard.java`, run the broken version several times. Count the result size each run. It varies — race condition. The fixed version is stable.
3. In `CollectorsToolkit.java`, replace `toMap(...)` to deliberately produce duplicate keys without a merge function. Watch the `IllegalStateException`.

---

## Self-check

1. Why does `stream.filter(...).map(...).findFirst()` typically run faster than you'd expect on a long source — what's happening operationally?
2. You write `users.stream().collect(Collectors.toMap(User::getEmail, u -> u))`. Two users share an email. What happens, and how do you fix it cleanly?
3. Your colleague switches a service method from `stream()` to `parallelStream()` "for speed." Name two reasons this can make things worse, not better.
