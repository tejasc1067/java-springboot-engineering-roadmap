# 11 — Concurrent Collections

Once threads touch the same collection, the rules change. `ArrayList`, `HashMap`, and friends are **not thread-safe** — concurrent modification can corrupt them, lose data, or throw `ConcurrentModificationException` at random. This topic covers the modern toolkit: `ConcurrentHashMap`, `CopyOnWriteArrayList`, blocking queues, and what each is for.

Concurrency in depth lives in module [03c-concurrency](../03c-concurrency/). This one focuses specifically on **collections under multi-threaded access**.

---

## The problem this solves

Two threads both want to update a shared `HashMap`. Naively, one of them will corrupt the internal bucket structure, occasionally crashing the JVM or silently losing entries. The textbook fix `synchronizedMap` works but serializes *every* operation — even reads. Concurrent collections do better: they let multiple readers (and often multiple writers) proceed in parallel.

---

## Three families

| Family | Used for |
|--------|----------|
| `ConcurrentHashMap`, `ConcurrentSkipListMap`, `ConcurrentSkipListSet` | High-throughput shared maps/sets |
| `CopyOnWriteArrayList`, `CopyOnWriteArraySet` | Read-mostly lists/sets (think listener registries) |
| `BlockingQueue` implementations (`ArrayBlockingQueue`, `LinkedBlockingQueue`, `SynchronousQueue`, `LinkedTransferQueue`) | Producer/consumer pipelines |

Then the legacy options to know about but rarely use: `Collections.synchronizedList/Map/Set` (whole-collection lock), `Hashtable`, `Vector`.

---

## `ConcurrentHashMap`

The workhorse. Designed from scratch for concurrent access. Highlights:

- **Lock striping** — internally divides the bucket array into segments and locks only the segment being modified. Reads usually don't lock at all.
- **Atomic operations**: `putIfAbsent`, `compute`, `computeIfAbsent`, `merge` — each runs as one unit, no separate check-then-act.
- **Weakly consistent iterators** — iterating doesn't throw `ConcurrentModificationException`, but you may see entries that were added after iteration started, or miss ones added during it. Acceptable for monitoring loops; check explicitly if you need a snapshot.
- **No null keys or values.** Trying to put one throws NPE. (Reason: `get(k)` returning null is ambiguous if null is a valid value.)

Typical bug it fixes:

```java
// BROKEN — racy on plain HashMap, and even on synchronizedMap
if (!map.containsKey(k)) {
    map.put(k, computeValue(k));
}

// FIXED — single atomic operation
map.computeIfAbsent(k, this::computeValue);
```

`computeIfAbsent` runs the function only if absent, and inserts the result atomically. No window for another thread to slip in.

---

## `CopyOnWriteArrayList`

Every mutation (`add`, `remove`, `set`) copies the entire backing array. Reads access the current array without any lock at all — they're as fast as `ArrayList`.

- Reads: O(1), no synchronization, no CME.
- Writes: O(n), expensive.
- Iterators see a stable snapshot — won't change mid-iteration. No CME, ever.

Use cases:

- Read-mostly, rarely written: listener / observer lists.
- Iteration must not see mutations.

Don't use it as a "thread-safe ArrayList" for write-heavy workloads — you'll pay an O(n) copy per write.

---

## `BlockingQueue` for producer/consumer

The right way to hand work from one thread to another. Operations:

| Operation | Blocks? | Time-bound? |
|-----------|---------|-------------|
| `add(e)` / `remove()` | no | throws if full / empty |
| `offer(e)` / `poll()` | no | returns false / null if full / empty |
| `put(e)` / `take()` | **yes** | waits forever |
| `offer(e, t, u)` / `poll(t, u)` | **yes** | bounded wait |

Common implementations:

- `ArrayBlockingQueue(capacity)` — bounded, fair option available.
- `LinkedBlockingQueue` — optionally bounded; common default.
- `SynchronousQueue` — capacity zero; producer hands directly to consumer. Used by `Executors.newCachedThreadPool`.
- `LinkedTransferQueue` — modern, lock-free, supports `transfer` (block until consumer takes it).

The classic producer/consumer becomes a few lines once you have a `BlockingQueue`:

```java
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);

// producer
queue.put(task);                                     // blocks if full

// consumer
Task t = queue.take();                               // blocks if empty
```

No `wait` / `notify` plumbing. The queue handles it.

---

## `Collections.synchronizedList` — when, and what it doesn't give you

Wraps a list so every method holds a single intrinsic lock.

```java
List<User> shared = Collections.synchronizedList(new ArrayList<>());
shared.add(u);                       // each call locks the wrapper
```

Limitations:

- Compound operations are **not atomic**: `if (!shared.contains(u)) shared.add(u);` is two locked calls, with a window between them.
- Iteration **must be hand-synchronized**:
  ```java
  synchronized (shared) {
      for (User u : shared) { ... }
  }
  ```
  Otherwise CME under concurrent modification.

If you find yourself doing both compound operations and iteration, `ConcurrentHashMap` / `CopyOnWriteArrayList` are usually the cleaner answer.

---

## Common pitfalls

- **`synchronizedMap` for "thread safety" then ignoring iteration locking.** CME during iteration. Either lock externally or switch to `ConcurrentHashMap`.
- **`getOrDefault` + `put` on `ConcurrentHashMap`.** Not atomic. Use `compute`, `merge`, or `computeIfAbsent`.
- **`CopyOnWriteArrayList` for write-heavy workloads.** Every write is O(n). Use `Collections.synchronizedList` (with external iteration locks) or restructure to a `ConcurrentHashMap`-backed set.
- **`BlockingQueue.put` without a timeout in a service that should fail fast.** Use `offer(e, timeout, unit)` so a stuck consumer doesn't pile producer threads forever.
- **Assuming `ConcurrentHashMap.size()` is precise.** It's a best-effort estimate during concurrent updates. For exact counting, use `mappingCount()` or freeze the map.

---

## Code examples

1. `HashMapRaceCorruption.java` paired with `ConcurrentHashMapFixed.java` — observe the unsynchronized HashMap losing entries (or worse), then the fix.
2. `ComputeIfAbsentAtomic.java` — the canonical "compute if missing" pattern.
3. `CopyOnWriteListenerRegistry.java` — read-mostly listener list with safe iteration.
4. `BlockingQueueProducerConsumer.java` — handing work between threads via `LinkedBlockingQueue`.
5. `SynchronizedListIterationCme.java` paired with `SynchronizedListIterationLocked.java` — why wrapping isn't enough during iteration.

---

## Try this yourself

1. In `HashMapRaceCorruption.java`, run it several times. Note the size varies between runs (and sometimes the JVM hangs in an infinite loop — that's a real `HashMap` corruption bug). Then run `ConcurrentHashMapFixed.java` — size is exactly the expected total every time.
2. In `BlockingQueueProducerConsumer.java`, set the queue capacity to 1. Watch how the producer waits while the consumer drains.
3. In `CopyOnWriteListenerRegistry.java`, time 100k reads vs 100k writes. Confirm reads are essentially free and writes are O(n).

---

## Self-check

1. Why is `if (!map.containsKey(k)) map.put(k, v)` unsafe even with `Collections.synchronizedMap`? What's the one-line atomic equivalent on `ConcurrentHashMap`?
2. When is `CopyOnWriteArrayList` the right choice, and when is it the wrong one?
3. A producer enqueues tasks; a consumer drains them. Which `BlockingQueue` method should the consumer call to wait for work, and what happens if the queue is empty?
