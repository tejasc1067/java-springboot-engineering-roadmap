# 01 — Java Memory Management

The JVM hides memory allocation behind `new`, but it doesn't hide *where* objects live or *when* they die. Understanding the runtime memory layout — heap, stack, metaspace — and the reference graph that drives garbage collection is what separates "Java compiles and runs" from "Java runs in production under load without leaking, crashing, or stuttering."

This topic covers the regions. Topic 19 covers garbage collection — when and how the JVM reclaims unreachable objects. Topic 20 covers the rest of the JVM (class loading, JIT, bytecode).

---

## The JVM memory map (Java 8+)

| Region | What's in it | Per-thread or shared | Sized by |
|--------|--------------|---------------------|----------|
| **Heap** | All Java objects, arrays | Shared | `-Xms`, `-Xmx` |
| **Stack** | Method call frames: locals, return address, partial expressions | Per thread | `-Xss` |
| **Metaspace** | Class metadata (bytecode, method tables, constant pools) | Shared | `-XX:MaxMetaspaceSize` (default: unlimited, native memory) |
| **PC Register** | Address of the current bytecode instruction | Per thread | tiny, fixed |
| **Native method stack** | C/C++ call frames for JNI calls | Per thread | OS-managed |

Pre-Java 8 there was a fixed-size **PermGen** in the heap. Java 8 replaced it with **Metaspace** in native memory — no more `OutOfMemoryError: PermGen space` from dynamic class loading.

---

## Heap vs stack — what goes where

```java
void process(int userId) {            // userId lives on the stack
    User u = new User(userId);        // `u` is a REFERENCE on the stack; the User OBJECT is on the heap
    String name = u.getName();        // `name` is a reference on the stack; the String chars are on the heap
}
```

**Primitives** (`int`, `long`, `boolean`, etc.) declared as local variables sit directly on the thread's stack. They vanish when the method returns.

**Objects** are always allocated on the heap. The local variable holds a *reference* (a pointer) to it. Multiple references can point to the same object. The object lives until no references reach it.

When `process` returns, the stack frame is popped. Both `u` and `name` (the references) disappear. The objects they pointed to are now candidates for GC if nothing else references them.

**Exception: escape analysis.** The JIT may prove that an object never escapes a method and allocate it on the stack instead. You don't control this; it's an optimization.

---

## The reference graph

Every object reachable from a **GC root** is alive. Unreachable objects are eligible for collection.

GC roots include:

- Local variables (stack references) in any live thread.
- Static fields of loaded classes.
- Active JNI references.
- Synchronization monitors held.

Reachability is transitive. If a `static Map<String, User> cache` holds a reference to `User`, and `User` holds a reference to `Account`, all are reachable.

This is why "memory leaks" in Java are usually **unintended reachability** — a `static` collection that keeps growing, an event listener never unregistered, a `ThreadLocal` in a pool worker.

---

## Reference strengths

`java.lang.ref` lets you participate in the reachability decision:

| Type | When is the target GC-able? |
|------|----------------------------|
| Strong (the default) | Only when no strong references remain. |
| `SoftReference<T>` | When the JVM is running low on memory. Used for memory-sensitive caches. |
| `WeakReference<T>` | At the next GC cycle if no strong references remain. |
| `PhantomReference<T>` | Never reachable via `get()`. Used to schedule cleanup after the object is finalized. |

Practical use:

- **Soft** — caches that should shrink under pressure (less common with `Caffeine`/Guava these days).
- **Weak** — `WeakHashMap` for "associate metadata with an object, but don't pin it alive."
- **Phantom** — replacing `finalize()` (which is deprecated for removal) for native resource cleanup.

---

## Memory leaks in Java — three patterns

Despite GC, the JVM happily leaks memory if you keep references alive.

**1. Growing static collection.**

```java
static final Map<String, byte[]> cache = new HashMap<>();
public void serve(String key, byte[] value) { cache.put(key, value); }   // never removes
```

The map is a GC root. Every entry is pinned forever. Heap grows until OOM.

**2. ThreadLocal in a pool.** (See [03c topic 06](../03c-concurrency/06-concurrency-advanced.md).) Pool workers live forever; values set via `ThreadLocal.set` stay until `remove()`.

**3. Unregistered listener.**

```java
emitter.addListener(this);   // emitter holds a ref to `this`, even after `this` should be gone
```

Anything that owns a reference to your object pins it. Always provide a `removeListener` symmetric to every `addListener`.

---

## `OutOfMemoryError` — the variants

The single message hides several distinct failure modes. The actual message text tells you which:

| Message | Cause | Fix |
|---------|-------|-----|
| `Java heap space` | Heap full — objects pile up | Fix the leak; or `-Xmx` higher |
| `GC overhead limit exceeded` | GC ran but freed <2% — thrashing | Same — usually a leak |
| `Metaspace` | Too many classes loaded (often dynamic proxy / classloader leaks) | Investigate; or `-XX:MaxMetaspaceSize` |
| `unable to create native thread` | OS thread limit reached | Pool threads instead of `new Thread()` per task |
| `Direct buffer memory` | `ByteBuffer.allocateDirect` exceeded `-XX:MaxDirectMemorySize` | Cap, or stop leaking direct buffers |

When you see `Java heap space`, the **fix is almost always to find the leak**, not to raise `-Xmx`.

---

## `StackOverflowError`

Thrown when the thread's stack runs out — usually unbounded recursion. Default stack is ~512KB to 1MB; you can raise it with `-Xss`, but if you're really blowing 1MB of stack, raising it just delays the inevitable. Make the algorithm iterative.

---

## Diagnostic tools

For finding what's actually on the heap in production:

- **`jcmd <pid> GC.heap_info`** — current heap usage.
- **`jcmd <pid> GC.class_histogram`** — count and bytes per class. Find what's piling up.
- **`jcmd <pid> GC.heap_dump <file>`** — full heap dump for offline analysis.
- **Eclipse MAT / VisualVM** — analyze a `.hprof` heap dump. Find leak suspects, retained sets.

Topic 31 covers JVM tuning and monitoring in detail.

---

## Common pitfalls

- **Assuming Java can't leak.** It can. Anywhere a reference outlives the object's useful life is a leak.
- **Raising `-Xmx` to "fix" an OOM.** Buys time; doesn't solve the leak. The bug will return at a higher water mark.
- **Confusing heap and stack.** Primitives on the stack are not "thread-safe magic." Objects shared across threads are on the shared heap, and need synchronization.
- **Calling `System.gc()` to force collection.** It's a hint, not a command. Production: don't.
- **Relying on `finalize()`.** Deprecated and unreliable. Use `try-with-resources` and `Cleaner` for native cleanup.

---

## Code examples

1. `HeapVsStack.java` — see local primitives discarded vs objects on the heap that persist via references.
2. `StaticCollectionLeak.java` — a "cache" with no eviction, watch heap grow.
3. `WeakReferenceCleared.java` — `WeakReference` releases its target on the next GC.
4. `StackOverflowDemo.java` — unbounded recursion blows the stack; bounded depth shows where.
5. `OutOfMemoryHeap.java` — allocate until the heap dies; print the actual error message.
6. `GarbageVsLeak.java` — garbage (reachable then unreachable) vs leak (always reachable).

---

## Try this yourself

1. In `StaticCollectionLeak.java`, run with `-Xmx32m`. Watch how quickly it OOMs. Then add a `cache.remove(...)` after each use — the OOM goes away.
2. In `WeakReferenceCleared.java`, replace `WeakReference` with a plain field. The object never gets collected, even after GC runs.
3. In `OutOfMemoryHeap.java`, run with `-Xmx64m` and read the exact OOM message printed.

---

## Self-check

1. A local `User u = new User()` is declared inside a method. Where does the `u` variable live, and where does the `User` object live? When `u` goes out of scope, what happens to each?
2. Your service slowly grows in heap usage over days and eventually dies with `Java heap space`. Doubling `-Xmx` makes it die two days later instead. Is that a fix, or a symptom?
3. You add an event listener in a constructor and forget to remove it. The object is no longer used by your own code, but it's not collected. Why?
