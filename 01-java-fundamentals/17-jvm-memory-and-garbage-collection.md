# 17 — JVM Memory and Garbage Collection

Java doesn't make you call `free()` like C. Objects you've stopped using are reclaimed automatically by the **garbage collector** (GC). Memory management still has rules, though — knowing where things live, when they're collected, and how memory leaks happen in a "garbage-collected" language matters in production.

This topic isn't a JVM internals course (that comes in module 03). It's enough mental model to debug a real memory issue and to read GC logs without panicking.

---

## JVM memory regions

Your running JVM splits memory into a few areas. The ones you care about as a backend developer:

```text
┌────────────────────────────────────────────────────────────┐
│                       JVM Memory                           │
│                                                            │
│  ┌──────────────┐    ┌────────────────────────────────┐   │
│  │   STACKS     │    │            HEAP                 │   │
│  │              │    │                                 │   │
│  │  (one per    │    │  Young Gen:                     │   │
│  │   thread)    │    │  ┌────┐ ┌──────┐ ┌──────┐       │   │
│  │              │    │  │Eden│ │ S0   │ │ S1   │       │   │
│  │  local vars  │    │  └────┘ └──────┘ └──────┘       │   │
│  │  refs        │    │                                 │   │
│  │  method      │    │  Old Gen:                       │   │
│  │  frames      │    │  ┌─────────────────────────┐    │   │
│  │              │    │  │ long-lived objects      │    │   │
│  │              │    │  └─────────────────────────┘    │   │
│  └──────────────┘    └────────────────────────────────┘   │
│                                                            │
│  ┌────────────────────────────────────────────────────┐   │
│  │  Metaspace: loaded class metadata, static fields   │   │
│  └────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────┘
```

- **Stack** — one per thread. Holds local variables, method-call frames, and references. Fast, automatically cleaned up when a method returns.
- **Heap** — shared across threads. Holds all the objects you create with `new`. This is what the GC manages. Typically split into a "Young" generation (where new objects go) and an "Old" generation (where objects that survive several GCs end up).
- **Metaspace** — class metadata, static fields, method bytecode. Outside the regular heap. Usually grows slowly and is bounded.

When you see `OutOfMemoryError: Java heap space`, the heap is exhausted. `OutOfMemoryError: Metaspace` means too many classes are being loaded (often a sign of a classloader leak in long-running apps).

---

## What lives where

```java
void doStuff() {
    int x = 5;                   // x is a local variable — STACK
    int[] arr = new int[10];     // arr (reference) on STACK; the int[] itself on HEAP
    Person p = new Person();     // p (reference) on STACK; Person object on HEAP
}                                // method returns: x, arr, p are gone from stack
                                 // the int[] and Person are now unreferenced → eligible for GC
```

A common confusion: primitives in *fields* live wherever the enclosing object lives. So a `Person` object on the heap has its `int age` field on the heap too — the primitive isn't "in the stack" just because it's a primitive.

---

## How GC decides what to collect

A heap object is **eligible for garbage collection** when there's no longer any way to reach it from a "root" (a stack reference, a static field, etc.).

```java
Person p = new Person();    // p references the object; not eligible
p = null;                    // no more references; eligible
```

Or:

```java
Person p1 = new Person();
Person p2 = p1;
p1 = null;                   // still reachable through p2; NOT eligible
p2 = null;                   // now eligible
```

Or, more realistically:

```java
void load() {
    Person p = new Person();
    // do something with p
}    // local p disappears; the Person is now unreferenced
```

You don't tell the GC *when* to run. It picks its own moments — when the young generation fills up, when allocation pressure rises, etc. `System.gc()` is a suggestion, not a command, and you should almost never call it.

---

## Memory leaks in a garbage-collected language

"Garbage collected" doesn't mean leak-free. A leak in Java is **an unintentionally-retained reference** — something keeps pointing at an object you no longer need, so the GC can't reclaim it. Most production memory bugs fall into a few patterns:

**Static collections that grow forever:**

```java
class CacheBad {
    static Map<String, User> cache = new HashMap<>();
    // We add to it but never remove. In a long-running server, this grows
    // unbounded. The "static" anchors it forever.
}
```

**Listeners or callbacks never unregistered:**

```java
button.addListener(myListener);
// myListener is held by the button, even after we're done with it.
```

**Caches without an eviction policy.** Use `WeakHashMap` (the JDK's), or `Caffeine` (a library), or limit size explicitly.

**Long-lived references to short-lived state.** A background scheduler holding onto every request it ever processed.

The fix is always "remove the reference when you no longer need the thing."

---

## Stack memory and `StackOverflowError`

Each thread gets a stack — typically 512KB to 1MB by default. Method calls push frames onto it. Excessive recursion blows it.

```java
void recurse(int n) {
    recurse(n + 1);   // no base case
}
// StackOverflowError after a few thousand levels deep
```

Different from heap memory. `StackOverflowError` is about call depth, not number of objects.

---

## OutOfMemoryError

`OutOfMemoryError` (OOM) means the JVM couldn't allocate more memory. The most common variants:

- **`Java heap space`** — the heap is full. Most common in real apps. Causes: too many objects, leak, undersized heap. Fix: investigate with a heap dump, raise `-Xmx` if the demand is legitimate.
- **`GC overhead limit exceeded`** — the GC is spending almost all its time collecting and reclaiming almost nothing. Usually a leak.
- **`Metaspace`** — too many classes loaded. Classloader leaks (common in app-server-redeploy scenarios) or generated proxy explosion.

You'll usually meet the first one. The fix is rarely "raise the heap size" — it's "find what's accumulating."

---

## Tuning, briefly

The two flags worth knowing today:

```bash
java -Xms512m -Xmx2g MyApp
```

`-Xms` sets the initial heap, `-Xmx` sets the max heap. Most production servers set both to the same value so the heap doesn't have to grow and shrink at runtime.

Modern Java picks reasonable defaults. Don't tune until you have a measured problem.

---

## Common pitfalls

- **`static` references holding state forever.** Innocent-looking caches, registries, listeners. Anything `static` that grows needs an eviction strategy.
- **Forgetting to close resources (file handles, sockets, DB connections).** These hold both Java-heap memory AND OS resources. The OS one usually bites first. Try-with-resources fixes it (topic 15).
- **Calling `System.gc()` "to free memory."** It doesn't help. The GC decides when. Stops the world for nothing.
- **Confusing stack overflow with out-of-memory.** Deep recursion → `StackOverflowError`. Too many live objects → `OutOfMemoryError`. Different cause, different fix.
- **Holding huge objects (like big maps) in `static` fields "for performance."** They sit in old-gen and never get cleaned. Make sure the size is bounded.

---

## Code examples

1. `StackVsHeapAgain.java` — visualize where things live, who survives a method return.
2. `GcEligibility.java` — show an object becoming unreachable; suggest a GC; observe that it ran.
3. `MemoryLeakSimulation.java` — a static collection that grows forever. Watch heap usage climb. (Contrast with a bounded version.)
4. `StackOverflowDemo.java` — unbounded recursion → StackOverflowError. Caught and printed.

---

## Try this yourself

1. Run `MemoryLeakSimulation.java` with `-Xmx64m`. It crashes faster the smaller the heap. Try `-Xmx256m` and see how long it takes. Then run the bounded version — confirm it never crashes.
2. In `GcEligibility.java`, change the test object's class to print from its `finalize()` method (deprecated, but instructive — see the comment). Run with `-verbose:gc` to see GC activity.
3. Investigate your own JVM defaults: `java -XX:+PrintFlagsFinal -version | grep -i heapsize`. Note what max heap your default JVM picks.

---

## Self-check

1. You have `Person p = new Person(); p = null;`. Is the Person object guaranteed to be collected immediately?
2. A static `Map<String, User> cache` in a long-running web server has 50 million entries. Why is this a leak, given that Java has a garbage collector?
3. Your service crashes with `StackOverflowError`, not `OutOfMemoryError`. What does that tell you about the cause?
