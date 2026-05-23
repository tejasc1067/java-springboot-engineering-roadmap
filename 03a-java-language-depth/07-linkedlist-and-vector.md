# 07 — `LinkedList`, `ArrayDeque`, and `Vector`

`LinkedList` and `Vector` come up in interviews more often than in code. This topic explains what each is, why `ArrayDeque` has quietly replaced both as the right choice for almost every "I want a queue / stack / linked list" use case, and why `LinkedList` is far less useful than its reputation suggests.

If you've been reaching for `LinkedList` because someone said "use it for lots of inserts" — read this first.

---

## The problem this solves

The interfaces `Queue` (FIFO), `Deque` (double-ended), and `Stack`-style LIFO need *some* implementation. There are two reasonable strategies:

1. **Linked nodes** (`LinkedList`) — each element is its own object with `prev`/`next` pointers.
2. **Circular array** (`ArrayDeque`) — one array, two index pointers (head and tail) that wrap around.

Both give O(1) operations at both ends. But the array-backed version wins on cache locality, memory overhead, and constant factors — by a wide margin. `LinkedList` won when memory was cheap and CPUs didn't have caches. That's not the world anymore.

---

## How `LinkedList` works

Each element is a node:

```text
[prev | value | next] ↔ [prev | value | next] ↔ [prev | value | next]
   ↑                                                ↑
  head                                             tail
```

- `addFirst`, `addLast`, `removeFirst`, `removeLast` — O(1).
- `get(i)` — walks from the nearer end. **O(n)**, even though `LinkedList` implements `List`.
- `add(i, e)` — walks to index `i` (O(n)), then inserts (O(1) once positioned). Total: O(n).

The "fast insert in the middle" claim *only* applies if you already have an `Iterator` at the position. `linkedList.add(5000, x)` is O(n) — same as `arrayList.add(5000, x)`, just for a different reason.

### The hidden cost: memory

Each `LinkedList` node is a separate object: ~24 bytes of object header, two reference fields (~16 bytes), plus the actual element. On a 64-bit JVM, that's roughly **48 bytes per element of overhead**, plus the element itself. An `ArrayList<Integer>` of 1M ints uses ~24 MB. A `LinkedList<Integer>` of 1M ints uses ~72 MB. And every iteration is a pointer chase — terrible for CPU cache.

---

## `ArrayDeque` — the better default

`ArrayDeque` is a circular array. Two index pointers mark the head and tail; they wrap around the array. It implements `Deque`, so it's both a queue and a stack.

```java
Deque<String> stack = new ArrayDeque<>();
stack.push("A");                          // top
stack.push("B");
stack.push("C");
System.out.println(stack.pop());          // C
System.out.println(stack.pop());          // B

Deque<String> queue = new ArrayDeque<>();
queue.offer("A");                         // back
queue.offer("B");
queue.offer("C");
System.out.println(queue.poll());         // A (front)
```

- `addFirst`, `addLast`, `removeFirst`, `removeLast`, `peek` — all amortized O(1).
- No null elements allowed (you'd lose the "queue is empty" sentinel).
- Cache-friendly: it's one contiguous array.

When should you use `LinkedList` instead? Honestly: rarely. Two cases come to mind:

1. You need a list that implements `Deque` *and* you'll frequently take iterators and modify around them. `LinkedList` iterators are stable across some structural modifications.
2. You're already inside an API that mandates `LinkedList`. (Doesn't happen in modern code.)

Otherwise: `ArrayList` for a list, `ArrayDeque` for a queue or stack.

---

## `Stack` (the legacy class) — don't use it

Java has a `Stack` class. It extends `Vector` (synchronized), exposes the `Vector` API (so you can mutate via `set(index, value)` — defeats the point), and is slow because of synchronization.

Modern stack code:

```java
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1);
stack.push(2);
int top = stack.pop();
```

That's the canonical pattern, recommended in `Stack`'s own Javadoc.

---

## `Vector` and `Hashtable` — also legacy

`Vector` is `ArrayList`'s synchronized older sibling — every public method holds a lock. Same for `Hashtable` vs. `HashMap`.

Two problems:

- **Coarse-grained locking.** Every operation locks the whole structure, even reads. Heavy concurrent workloads get worse, not better.
- **The "synchronized" guarantees are weaker than people think.** `vector.contains(x)` is atomic, but `if (vector.contains(x)) vector.add(y)` is not — another thread can race between the two calls.

If you need thread-safe collections, use what topic 11 covers: `ConcurrentHashMap`, `CopyOnWriteArrayList`, etc. For locked access to a regular list, `Collections.synchronizedList()` is at least honest about what it gives you.

---

## Quick decision table

| What you want | Use |
|---------------|-----|
| Indexed list, append/scan | `ArrayList` |
| Stack | `ArrayDeque` (via `push`/`pop`) |
| Queue (FIFO) | `ArrayDeque` (via `offer`/`poll`) |
| Double-ended queue | `ArrayDeque` |
| Thread-safe map | `ConcurrentHashMap` (topic 11) |
| Thread-safe list, read-heavy | `CopyOnWriteArrayList` (topic 11) |
| Anywhere `LinkedList` was suggested | usually `ArrayDeque` or `ArrayList` |
| Anywhere `Vector`/`Hashtable`/`Stack` was suggested | use the modern equivalent |

---

## Common pitfalls

- **Using `LinkedList` for "fast middle insert"** without realizing the walk-to-position is O(n) too.
- **`stack` (lowercase, `java.util.Stack`)** in new code. Even Oracle says use `ArrayDeque`.
- **`Vector` for "thread safety."** Its per-method locking doesn't make compound operations safe. And it's slower for single-threaded use than `ArrayList`.
- **`LinkedList.get(i)` in a loop.** O(n) per call → O(n²) loop. Use an iterator: one walk through.
- **Storing nulls in `ArrayDeque`.** `null` is the empty-marker; the API will reject it. `LinkedList` allows nulls, which can be useful or a source of confusion.

---

## Code examples

1. `ArrayDequeAsStack.java` — modern stack pattern.
2. `ArrayDequeAsQueue.java` — modern queue pattern (FIFO).
3. `LinkedListGetIsLinear.java` — proving `LinkedList.get(i)` is O(n) by timing.
4. `LinkedListVsArrayListIterate.java` — same iteration, very different times.
5. `VectorIsLegacy.java` — `Vector`'s synchronization doesn't help compound operations.

---

## Try this yourself

1. In `LinkedListGetIsLinear.java`, change the indices accessed and watch the time grow with index. Now run the same on `ArrayList` — flat.
2. In `ArrayDequeAsQueue.java`, switch the implementation to `LinkedList`. Same code, both work — that's the point of programming to `Deque`.
3. In `VectorIsLegacy.java`, run with one thread vs. eight. Note that `Vector` *does not* speed up — its lock serializes everything. (Topic 11 shows actual concurrent collections.)

---

## Self-check

1. `LinkedList` is famous for "fast insertion." Under what specific condition is that true, and when is it actually O(n)?
2. Why is `ArrayDeque` preferred over `LinkedList` for stack/queue use cases?
3. `Vector.contains(x)` is atomic. Why is `if (vector.contains(x)) vector.add(y)` still unsafe under concurrency?
