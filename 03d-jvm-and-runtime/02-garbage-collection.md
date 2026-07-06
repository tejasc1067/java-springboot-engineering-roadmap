# 02 — Garbage Collection

Garbage collection reclaims unreachable objects so the heap doesn't fill up. The JVM does it automatically — you never call `free()`. But the *when* and *how* of collection determine whether your service has 1ms p99 latency or 500ms latency spikes during peak traffic.

This topic covers: the generational hypothesis the JVM is built around, what minor vs major GC really do, the four production collectors (Parallel, G1, ZGC, Shenandoah), and how to read the GC logs.

---

## The core idea: the weak generational hypothesis

Decades of measurement: **most objects die young**. A method allocates a `String`, a `List`, an `Optional`, returns — and they're all immediately garbage. Long-lived objects (caches, session state, the application context) are rare in comparison.

If most objects die young, you should:

1. Allocate them in a separate, small region.
2. Collect that region often, very cheaply (because almost everything in it is dead).
3. Move the few survivors to a different region you collect rarely.

That's the **generational heap**.

---

## Heap layout

```
+-------------------------+  +-------------------------+
|       Young             |  |        Old              |
|  Eden | Surv0 | Surv1   |  |    (Tenured)            |
+-------------------------+  +-------------------------+
```

- **Eden** — where every new object starts.
- **Survivor 0 / 1** — two equal-sized spaces. Each minor GC copies survivors between them.
- **Old** (tenured) — objects that survived enough minor GCs (default: ~15) get *promoted* here.

An object's life:

1. `new Foo()` → allocated in Eden.
2. Minor GC: if reachable, copy to a survivor space.
3. More minor GCs: keep flipping between survivor spaces; each time, age++.
4. After ~15 cycles or when survivors are too full → promoted to Old.
5. Sits in Old until a major (or mixed/full) GC.

---

## Minor GC (Young generation)

Triggered when Eden fills up.

- Walks the GC roots (stack, statics) into the young generation only.
- **Copying collector**: live objects are copied to the empty survivor space; everything left in Eden + the old survivor is freed in bulk.
- Tiny pause (often <10ms), because the young generation is small and mostly dead.
- Frequent — happens many times per minute in a busy service.

This is cheap. You don't usually worry about minor GCs.

---

## Major / Full GC (Old generation)

Triggered when Old fills up (or as part of a mixed cycle).

- Must walk the *entire* heap.
- Old generation is much bigger and mostly *live* — so much more work.
- Historically: **stop-the-world** pause that can run hundreds of milliseconds or even seconds.

This is what causes latency spikes. Modern collectors exist mostly to reduce this pause.

---

## The collectors

The JVM ships with several. You pick one with a flag.

| Collector | Flag | Pause goal | Notes |
|-----------|------|-----------|-------|
| **Parallel** | `-XX:+UseParallelGC` | none — optimizes throughput | Default before Java 9. Good for batch jobs. Long stop-the-world pauses. |
| **G1** (Garbage-First) | `-XX:+UseG1GC` | "soft target" via `-XX:MaxGCPauseMillis=200` | Default since Java 9. Divides heap into regions; collects the regions with the most garbage first. Mixed minor + partial major work. |
| **ZGC** | `-XX:+UseZGC` | sub-10ms pauses on heaps up to TB | Concurrent — most work happens while the app runs. Default for low-latency services. Java 15+. |
| **Shenandoah** | `-XX:+UseShenandoahGC` | sub-10ms pauses | Red Hat alternative to ZGC. Also concurrent. |
| **Serial** | `-XX:+UseSerialGC` | n/a | Single-threaded. Only for tiny apps / containers with <100MB heap. |

**For a backend service in 2026**, the realistic choices are G1 (default, well-tuned) or ZGC (when p99 latency matters more than throughput).

---

## What "stop the world" means

Even concurrent collectors pause briefly. During a stop-the-world phase, *every application thread is parked* — request handling, scheduled tasks, all of it. From the outside, your service is unresponsive for that pause duration.

For a 250ms-budget API endpoint, a 200ms GC pause means most requests during that pause time out. The point of choosing G1 or ZGC is to make those pauses too small to notice.

---

## Reading GC logs

Enable detailed logging:

```
-Xlog:gc*=info:file=gc.log:time,level,tags
```

A typical G1 line looks like:

```
[2026-05-23T14:00:01.234+0000][info][gc,start] GC(42) Pause Young (Normal) (G1 Evacuation Pause)
[2026-05-23T14:00:01.246+0000][info][gc]       GC(42) Pause Young (Normal) (G1 Evacuation Pause) 1234M->456M(2048M) 12.345ms
```

What to read:

- `Pause Young` vs `Pause Full` — minor vs full collection.
- `1234M->456M` — heap usage before → after. If after-GC heap doesn't drop much, you're not actually freeing — investigate leaks.
- `(2048M)` — total heap.
- `12.345ms` — the pause duration. Watch this distribution.

Plot pause time over time. Spikes correlate with traffic load, allocation pressure, or actual leaks.

---

## Tuning, briefly

Topic 10 has the production details. For now, the four levers:

1. **Heap size** — `-Xms` (initial) and `-Xmx` (max). Set them equal in containers to avoid resize pauses.
2. **Pause target** — `-XX:MaxGCPauseMillis=200` (G1's soft goal).
3. **Collector** — `-XX:+UseG1GC` (or `+UseZGC` for low latency).
4. **Logging** — `-Xlog:gc*=info:file=gc.log` so you can actually see what's happening.

Don't tune blind. Run the service under realistic load, log GC, look at the numbers, then change one thing.

---

## What you cannot do

- **Force GC.** `System.gc()` is a hint. The JVM may ignore it. In production, **never call it** — you may pin a long full GC at the worst moment.
- **Predict pause times exactly.** You set goals; the JVM tries.
- **Skip GC.** Even with ZGC, there are pauses, just very short ones.

---

## Common pitfalls

- **Allocating large temporary objects in hot paths.** Pushes them straight into Old (G1 calls these "humongous" — anything >50% of a region). Old fills, full GC fires.
- **`-Xms` much smaller than `-Xmx`.** JVM ramps up over many GC cycles, causing pauses early in process life. Set them equal.
- **Running with the default collector when latency matters.** G1 (the default) is a good general-purpose choice, but if your p99 spikes correlate with full GC, look at ZGC.
- **No GC logs.** When latency spikes happen, you have nothing to look at. Always log.
- **Tuning blind.** Increasing `-Xmx` because someone said "more memory = fewer GCs". Larger heap can mean longer full-GC pauses.

---

## Code examples

1. `AllocationPressureMinorGc.java` — allocate fast, observe minor GCs happening.
2. `ObservingGcWithBeans.java` — read GC counts and times from `GarbageCollectorMXBean`.
3. `MeasureGcPause.java` — allocate large objects, time the longest pause main observes.
4. `HumongousAllocation.java` — single huge allocation goes straight to Old in G1.
5. `SystemGcIsAHint.java` — `System.gc()` may or may not free; show before/after.

Each example prints which collector is active. Run with different `-XX:+UseG1GC` / `-XX:+UseZGC` / `-XX:+UseParallelGC` flags to see the behavior change.

---

## Try this yourself

1. Run `AllocationPressureMinorGc.java` with `-Xlog:gc` and `-Xmx128m`. Count the minor GCs. Then with `-Xmx512m`. Far fewer.
2. Run `MeasureGcPause.java` with `-XX:+UseParallelGC`, then with `-XX:+UseG1GC`. Compare the longest pause.
3. Run `ObservingGcWithBeans.java` and watch counts climb in real time.

---

## Self-check

1. Why is a minor GC almost always cheap, while a full/major GC is expensive — what's the size and liveness difference between the regions they collect?
2. Your service has p50 latency of 5ms and p99.9 of 800ms. GC logs show occasional 600ms `Pause Full` entries. What's the most likely diagnosis, and what's a candidate fix?
3. Why is calling `System.gc()` discouraged in production?
