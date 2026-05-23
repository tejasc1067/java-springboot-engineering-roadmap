# 04 — Java Performance Optimization

Performance work in Java is *measurement first*. The hardware is fast, the JIT is smart, the GC is fast — most "optimizations" you do without measuring either don't help or make things worse. This topic covers the things that actually matter in backend services, and shows the measurements that justify each one.

The rule: don't optimize what you haven't profiled. The corollary: don't write deliberately slow code "because we'll optimize later" either. The right default is straightforward code; the optimizations that matter come from data.

---

## Where backend time actually goes

A typical backend request spends its time in (roughly this order):

1. **I/O wait** — database queries, downstream HTTP, disk. Often 80–95% of latency.
2. **Serialization** — JSON parse/write, ORM mapping.
3. **Allocations and GC pressure** — every request creates objects; pressure adds up.
4. **Lock contention** — shared state under load.
5. **Pure computation** — usually a small slice.

This is why "make this loop 2x faster" usually doesn't help: the loop is a few percent of the request, and 2x of "a few percent" is invisible.

The first three are where wins typically come from in real services.

---

## Measure first

Three tools, in order of precision:

| Tool | What it tells you |
|------|------------------|
| **Application metrics (Micrometer, Prometheus)** | Where time goes per endpoint at the millisecond level. The first place to look. |
| **JFR (Java Flight Recorder)** | Per-thread CPU + allocations + GC + I/O sampled at low overhead. Built into the JDK. `-XX:StartFlightRecording=...` |
| **async-profiler / JMH** | Pinpoint CPU hotspots (flame graphs) or write rigorous microbenchmarks. |

`System.nanoTime()` differences in a `main()` are not a benchmark. They include JIT warmup, GC, OS scheduling — they tell you almost nothing. For a *method*-level number you trust, use JMH.

---

## CPU-bound vs I/O-bound

A workload's character determines what optimizations help:

| | CPU-bound | I/O-bound |
|---|----------|-----------|
| Where time goes | Cycles | Wait |
| Pool size | ~ cores | Much higher ([03c topic 04](../03c-concurrency/04-executor-framework.md)) |
| Helps | Better algorithm, vectorization, less allocation | Caching, batching, async I/O |
| Doesn't help much | More threads | Smarter algorithm |

Don't blindly add threads to a CPU-bound workload — past `cores` threads, you're just paying for context switches.

---

## Reduce allocation pressure

Allocation in modern JVMs is cheap (bump pointer in TLAB). The cost is collection: every object in young generation that survives one minor GC costs copy work; large objects go straight to Old and pressure the heavyweight collector.

Concrete wins:

- **Reuse buffers and arrays** for hot paths. `ByteBuffer.allocate(8192)` once and clear/reuse per request is much cheaper than allocating per call.
- **Don't autobox in hot loops.** `Map<Integer, Integer>` boxes everything; for hot counters consider `Int2IntMap` from Eclipse Collections / fastutil, or just `int[]`.
- **Avoid `Optional` on the hot path.** Every `Optional.of(x)` is an extra allocation. Fine for API design; not great for an inner loop.
- **Stream the output**, don't build a huge in-memory buffer. `OutputStreamWriter` over the response is better than building a 5MB `StringBuilder`.

Confirm with JFR's "Allocation" view that the rate actually drops.

---

## String construction

```java
String s = "";
for (String part : parts) s = s + part;     // O(n^2) allocations
```

Concatenation in a loop creates a new `String` (and an intermediate `StringBuilder`) every iteration. Use a `StringBuilder` explicitly:

```java
StringBuilder sb = new StringBuilder(parts.size() * 16);
for (String part : parts) sb.append(part);
String s = sb.toString();
```

The compiler turns `a + b + c` into a single `StringBuilder` automatically. The trap is *across iterations*: each loop iteration is its own statement, and the compiler can't fold them.

Side note: since Java 9, `+` on Strings uses `invokedynamic` and may be even more efficient than a manual `StringBuilder` for fixed-shape concatenations. For variable-loop concatenation, manual `StringBuilder` still wins.

---

## Picking the right collection

This is the single highest-leverage micro-optimization in real code:

- **Lookup-heavy** → `HashMap` / `HashSet`. O(1).
- **Iterate then forget** → `ArrayList`. O(1) iteration; cache-friendly.
- **Frequent inserts/removes at both ends** → `ArrayDeque`. Faster than `LinkedList` in practice.
- **Sorted access** → `TreeMap` / `TreeSet`. O(log n).
- **Concurrent access** → `ConcurrentHashMap` ([03a topic 11](../03a-java-language-depth/11-concurrent-collections.md)).

If iteration is hot and you can size the collection in advance, **pre-size**: `new HashMap<>(expectedSize / 0.75f + 1)` skips rehashing.

`LinkedList` is almost never the right choice. Every node is a heap allocation; cache locality is terrible; the only place it wins is `removeFirst()` / `removeLast()` on a deque — and `ArrayDeque` is faster there too.

---

## Primitives over wrappers

```java
long sum = 0;                              // 8 bytes on the stack
Long sum = 0L;                             // boxed, heap allocation, hash, sync header...
```

`Long` is roughly 16–24 bytes plus indirection. In a million-element accumulator the difference is real. For collections:

- `Map<Long, Long>` boxes both keys and values.
- `long[]` and primitive specialized collections (Eclipse Collections, fastutil) skip boxing entirely.

Stream API: `mapToInt` / `mapToLong` / `mapToDouble` exist precisely so you don't box in stream pipelines.

---

## Avoid unnecessary synchronization

Every `synchronized` block costs memory barriers and forbids parallelism. If two threads never touch the same instance, the lock is dead weight.

Patterns:

- **No shared state** is the cheapest concurrency strategy.
- **Thread confinement** ([03c topic 06](../03c-concurrency/06-concurrency-advanced.md)): per-request local objects don't need locks.
- **Immutable objects** ([03c topic 03](../03c-concurrency/03-thread-safety-and-concurrency-problems.md)): safe to share with no synchronization.
- **`ConcurrentHashMap`** instead of `Collections.synchronizedMap` — far less contention.

---

## Caching

A 10ms DB query that runs 1000 times/second is 10 seconds of latency per second. If 90% of those queries are repeats, caching the result saves you 9 seconds/sec — equivalent to 9 CPU cores.

What to remember:

- **Bound the cache size**. Unbounded = memory leak.
- **Bound the entry TTL**. Stale data is a bug source.
- **Pick the right library**. Caffeine is the modern standard for in-process caches.
- **Don't cache mutable shared state without invariants** — invalidation is the hard part.

---

## Batching

When the per-call overhead is high (network round-trip, transaction setup), batching dominates:

```java
for (User u : users) repo.save(u);          // N transactions
repo.saveAll(users);                         // 1 transaction
```

Same applies to HTTP, Kafka producers, log writers. Batching cuts per-call overhead and amortizes network latency.

---

## Common pitfalls

- **Optimizing without profiling.** "I'll switch this to a `LinkedList` for faster removes" — then never measuring that this code wasn't on the hot path anyway.
- **Microbenchmarking with `System.nanoTime`.** No warmup, no JIT, no dead-code elimination protection. Use JMH.
- **`+` concatenation in a loop.** O(n²) in allocation work.
- **Calling `size()` in a loop condition where size doesn't change.** Cache it. (Doesn't matter for `ArrayList`; matters when `size()` is expensive — e.g., counted iterators.)
- **Premature parallelStream.** Default `parallelStream` uses the shared `ForkJoinPool.commonPool`. One slow task starves everyone. For real parallelism, hand work to your own executor.
- **Logging in the hot path.** Even with the right log level, the *argument formatting* runs. Use `log.debug("user {} did {}", id, action)` (parameterized) not `log.debug("user " + id + " did " + action)`.

---

## Code examples

1. `StringConcatVsBuilder.java` — same N concatenations both ways, time them.
2. `BoxingCostInMap.java` — `Map<Long, Long>` vs `long[]` accumulator.
3. `PresizeHashMap.java` — rehash cost when you don't pre-size.
4. `MicrobenchmarkPitfall.java` — naive timing vs same code with JIT warmup; same workload reports very different "speeds".
5. `CachingHotLookup.java` — uncached vs `HashMap`-cached repeated lookups.
6. `ParameterizedLogging.java` — string-built log message wastes time even at INFO when the level is WARN.

---

## Try this yourself

1. In `StringConcatVsBuilder.java`, try N = 1000, 10_000, 100_000. The concat version's time scales roughly quadratically.
2. In `BoxingCostInMap.java`, add `-XX:+PrintGCDetails`. The Map version causes far more GC than the array version.
3. In `MicrobenchmarkPitfall.java`, swap the order of the two measurements. The first one always looks slower — proof that naive nanoTime "benchmarks" measure something other than what you think.

---

## Self-check

1. Your endpoint takes 200ms. You shave a `for` loop from 8ms to 2ms. What's the new endpoint latency, and where should you have looked first?
2. Why is `Map<Long, Long>` slower and more memory-hungry than a `long[]` for a counter keyed by index?
3. You write a benchmark: `long t = System.nanoTime(); doIt(); System.out.println(System.nanoTime() - t);`. Name two specific reasons this number is misleading.
