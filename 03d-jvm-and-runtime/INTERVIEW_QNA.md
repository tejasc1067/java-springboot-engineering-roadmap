# Interview Q&A — Module 03d (JVM, Runtime & Production)

The JVM, performance, reflection, annotations, serialization, networking, and production-engineering questions you'll meet in mid-level Java backend interviews.

---

## JVM, memory, performance

### Q1. Heap vs stack — describe what lives where, and what happens at method return.

**A.**

- **Stack** — per-thread. Each method call pushes a frame: parameters, locals, partial results, return address. Frame pops on return. Primitive locals live entirely on the stack.
- **Heap** — shared across threads. Every Java *object* and array. Subject to GC.

`User u = new User()` — `u` (the reference) is a 4- or 8-byte slot in the stack frame; the `User` object's fields are in the heap.

On return, the stack frame is gone. `u` (the reference) is gone. The `User` object lives until nothing reachable from a GC root references it.

Exception: the JIT may do **escape analysis** and decide a particular object doesn't escape the method; it then stack-allocates the object (no heap involvement). You don't control this; it's an optimization.

---

### Q2. Generational GC — what's the hypothesis it's built on, and why does it pay off?

**A.** The **weak generational hypothesis**: most objects die young. Decades of profiling backed this — a method allocates a `String`, a `List`, an `Optional`, returns, and they're all garbage.

If most objects die young, segregate:

1. **Young generation** — small, collected often, by *copying* live objects to a survivor space. Dead bytes freed in bulk because there are few survivors.
2. **Old generation** — bigger, collected rarely, by *marking and compacting*.

Result: most GC work happens on the cheap, in a small region, in milliseconds. Expensive old-gen work is infrequent.

G1, ZGC, and Shenandoah are all generational with different mechanisms.

---

### Q3. Minor vs Major / Full GC — what's the cost difference?

**A.**

- **Minor GC** (young generation) — walks GC roots into young only. Copies live objects to a survivor. Young is small and mostly dead, so the work is small. Pause typically <10ms.
- **Major / Full GC** (whole heap, or at least old generation) — walks the entire reachability graph. Old gen is much bigger and mostly live, so the work is much bigger. Pause can be hundreds of ms to seconds with old-school collectors; sub-10ms with ZGC.

In production tail-latency terms: minor GCs are noise. Full GCs are the events that show up in p99.9.

---

### Q4. ZGC vs G1 — when would you choose ZGC?

**A.** ZGC is designed for **latency** at the cost of some throughput.

- ZGC pauses are sub-10ms regardless of heap size (tested up to TB-scale heaps).
- G1 has soft-target pause goals (`MaxGCPauseMillis`) — usually hits 100-300ms but spikes happen, especially on large heaps.

Choose ZGC when:

- p99/p99.9 latency tail matters (most APIs).
- Heap is large (10GB+).
- You can afford slightly more CPU spent on concurrent GC.

Choose G1 when:

- Defaults are fine.
- Throughput beats tail-latency (batch jobs).
- Heap is modest (a few GB).

For most modern microservices the answer is "start with G1; switch to ZGC if p99 tails hurt."

---

### Q5. What does `OutOfMemoryError: Java heap space` actually mean, and is raising `-Xmx` a fix?

**A.** The JVM couldn't allocate an object because the heap was full and GC freed too little.

Two possible causes:

1. **Genuine working-set size exceeds heap.** A cache is correctly sized to be large; the heap was too small. Fix: raise `-Xmx`.
2. **Memory leak.** Something is keeping references alive forever. Fix: find the leak.

The difference: under (1), heap-after-GC stays roughly flat over time. Under (2), heap-after-GC trends upward day over day.

Raising `-Xmx` for case (2) is the "buys you days, not a fix" anti-pattern. Take a heap dump (`jcmd <pid> GC.heap_dump`), open in Eclipse MAT, find the dominator, remove the reference.

---

### Q6. JIT cold start — why is the first 30 seconds of a new JVM slow?

**A.** The JIT runs the interpreter until methods become hot. Interpretation is significantly slower than C2-compiled code (5-10x for typical loops). HotSpot's tiered compilation gets methods to C1 quickly, then to C2 once they're truly hot — but that takes thousands of invocations.

Practical impacts:

- New pods after autoscaling are slow for the warm-up window.
- Serverless / Lambda cold starts are dominated by this.
- Microbenchmarks that don't warm up measure the interpreter, not the steady-state code.

Mitigations: a startup warm-up routine that exercises the hot paths; readiness probes that gate traffic until warm; AOT compilation (GraalVM native-image) which skips JIT entirely at the cost of some peak throughput.

---

## Reflection, annotations, serialization

### Q7. Why is `@Retention(RUNTIME)` so frequently overlooked?

**A.** The default retention is `CLASS`. An annotation with default retention is **present in the bytecode** but **invisible to `getAnnotation` / `isAnnotationPresent`** at runtime. Frameworks that scan for the annotation won't find it.

The bug presents as: "I added `@MyAnno` but my framework isn't picking it up." Hours of debugging follow. The fix is one line on the annotation declaration.

Every annotation a framework reads at runtime needs `@Retention(RetentionPolicy.RUNTIME)`. No exceptions.

---

### Q8. What's the security history of Java native serialization, and why does it not affect JSON?

**A.** `ObjectInputStream.readObject(bytes)` instantiates *whichever class is named in the byte stream*, invoking its `readObject`, `readResolve`, and constructor logic. Crafted input can trigger chains of method calls through classpath libraries (Apache Commons Collections being the most famous) that end in `Runtime.exec`.

This is what made the 2015-2016 wave of Java RCE vulnerabilities possible. Every application that deserialized untrusted Java-native bytes was a potential RCE.

JSON parsers (Jackson, Gson) construct a **specific target type the caller passes in** (`mapper.readValue(json, User.class)`). They don't reconstruct arbitrary types from the bytes. That single design choice eliminates the entire attack class.

Jackson does have its own "polymorphic typing" feature that brings the problem back if enabled globally. Don't enable it unless you understand the risk.

---

### Q9. A reflection call is slower than a direct call. By how much, and why?

**A.** Roughly **10-100x slower** for a single call, dropping closer to 5-10x when the `Method`/`Field` is cached and the JIT has had time to settle.

Reasons:

- Method handle lookup and security checks on every invocation (mitigated by caching).
- Argument boxing — primitives become `Integer`, `Long`, etc.
- The JIT can't inline the target the same way it can inline a direct call.

In production frameworks: scan and cache `Method`/`Field` objects once at startup; for hot paths, generate bytecode (cglib, Hibernate's bytecode enhancement) so the reflective lookup happens once.

For your own code: don't reflect on the hot path. If you must, cache the `Method`.

---

## Networking, NIO, production

### Q10. Thread-per-connection vs NIO selector — what's the scaling difference?

**A.** Thread-per-connection: each accepted socket is handed to a dedicated thread. The thread parks on `read()` while waiting. Cost per idle connection: ~1 MB of stack memory plus OS bookkeeping.

10,000 connections at 1 MB stack each = 10 GB. Plus thread-creation cost, plus scheduler overhead, plus context-switch noise. Practical ceiling: a few thousand connections per JVM.

NIO selector: one thread blocks on `selector.select()`, waking up only when one or more channels are ready. Per connection: a small `SocketChannel` and its registration — kilobytes, not megabytes. The single thread reads from ready channels, hands work off to a small worker pool, and goes back to selecting.

Practical ceiling: tens of thousands of connections per thread. Netty, Vert.x, Akka, WebFlux all run on this model.

---

### Q11. When a service has a latency spike, what's the diagnostic order?

**A.** In order:

1. **Check metrics first.** CPU saturated? Heap full? Thread count climbing? GC time growing? Inbound rate spike?
2. **If CPU saturated:** take a profile (JFR or async-profiler). Look at the hot methods.
3. **If memory pressure:** heap dump (`jcmd <pid> GC.heap_dump`). Open in MAT. Find dominators.
4. **If neither:** take three thread dumps 10s apart. Threads stuck in the same frame all three times are the suspect. Threads in `BLOCKED` on the same monitor → lock contention.
5. **If everything looks fine internally:** check downstream. Slow DB? Slow API call? Saturated connection pool?

The "everything looks fine internally" path is common; the slowness often turns out to be downstream contention that backs up into your service.

---

### Q12. Backpressure — what is it and what does Java provide?

**A.** Backpressure is the principle that a slow consumer must be able to **signal** an upstream producer to slow down. Without it, the upstream keeps producing into a queue that grows unbounded, eventually OOM or extreme latency.

Java's tools:

- **Bounded queues + `RejectedExecutionHandler`.** The standard `ThreadPoolExecutor` with `ArrayBlockingQueue(N)` and `CallerRunsPolicy` — when full, the producer (calling thread) runs the task itself, slowing the producer.
- **Reactive Streams API** (Java 9 `java.util.concurrent.Flow`). Subscriber calls `request(n)`; publisher emits at most `n` items. Project Reactor and RxJava implement this.
- **HTTP 429 / 503.** API-level: tell the client "back off."
- **Circuit breakers** (Resilience4j). Failure-driven: after N errors in a window, fail fast.

The opposite — silently queueing without bound — is the classic cause of cascading failure.

---

### Q13. p99 is fine but p99.9 is bad. Where do you look?

**A.** The 1-in-1000 events. Most likely:

1. **GC pauses.** A full GC every few minutes will show up as huge p99.9 spikes without touching p99. Check `gc.log`.
2. **JIT compilation events.** The JIT pauses to compile hot methods. Less common than GC at this scale.
3. **Lock contention spikes** — most requests don't hit the contended path; the unlucky 0.1% do.
4. **Slow downstream** — a 1-in-1000 DB query that goes through a slow index.
5. **TCP retransmits** — networking-level noise that p99 averages out but p99.9 catches.

p99.9 is the metric that tells you the system has a tail. Hiding it (by reporting averages) is how teams ship "fast" services that customers complain about.
