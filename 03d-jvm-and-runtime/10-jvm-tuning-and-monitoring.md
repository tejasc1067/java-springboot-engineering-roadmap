# 10 — JVM Tuning and Monitoring

A backend service in production needs JVM-level visibility. When the API gets slow at 3am, "looks fine in dev" is not enough. You need: heap usage, GC pause history, thread dumps, allocation rate, lock contention. This topic covers the **observation** layer (what to look at, with which tool) and the **tuning** layer (which flags actually matter, when to change them).

Topics 18-19 covered *what* GC does. Topic 32 covers production architecture concerns (cold start, backpressure, pool sizing). This one is the bridge: the actual flags and tools you reach for.

---

## The four JVM levers

In order of how often you'll touch them:

1. **Heap size** — `-Xms`, `-Xmx`.
2. **Garbage collector** — `-XX:+UseG1GC`, `-XX:+UseZGC`, `-XX:+UseParallelGC`.
3. **GC tuning targets** — pause goal, region size, heap occupancy thresholds.
4. **Logging and observability** — `-Xlog:gc*=info:file=gc.log`.

Most services in 2026 ship with G1 (the default since Java 9), a heap that matches the container limit, and GC logging enabled. That covers 95% of production needs.

---

## Heap sizing

```
-Xms2g   -Xmx2g
```

**Set `-Xms` equal to `-Xmx`.** Three reasons:

1. The JVM never has to grow the heap → no GC pauses during resize.
2. The OS commits the full heap up-front → no surprise page-faults under load.
3. In containers, this matches the cgroup limit, so the kernel doesn't surprise-kill the process.

For containerized services, also use `-XX:MaxRAMPercentage=75` (or whatever fits your sidecars). The JVM reads the cgroup limit and sizes accordingly.

How big? **Big enough to hold the working set with a buffer for GC.** Too small → constant collection. Too large → infrequent but huge collections (especially with old-school collectors). For most APIs: 1-4GB.

---

## Picking a collector

| Collector | Choose it when |
|-----------|----------------|
| **G1** (default) | Most services. Heap 4MB-16GB. Want predictable pause times. |
| **ZGC** | Latency-sensitive. Heap 100MB-TB. Need sub-10ms pauses even on huge heaps. |
| **Shenandoah** | Like ZGC but the Red Hat path. Similar use case. |
| **Parallel** | Throughput batch jobs where pauses don't matter. Rare in modern microservices. |
| **Serial** | Tiny apps. Containers with <100MB heap. |

```
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200          # soft target for G1
```

`MaxGCPauseMillis` is a *goal*, not a guarantee. G1 will try to keep pauses under this by collecting in smaller chunks.

For ZGC:

```
-XX:+UseZGC
-Xmx16g
```

ZGC has no `MaxGCPauseMillis` — pauses are already sub-10ms by design. The tradeoff: more CPU spent on concurrent collection, slightly lower throughput than Parallel.

---

## GC logging

Without logs, every latency spike is a mystery.

```
-Xlog:gc*=info:file=gc.log:time,level,tags:filecount=5,filesize=10m
```

Breaking that down:

| Part | What |
|------|------|
| `-Xlog:gc*=info` | All GC tags at info level. |
| `:file=gc.log` | Write to a file. |
| `:time,level,tags` | Decorations on each line. |
| `:filecount=5,filesize=10m` | Keep 5 rotated 10MB files. |

When investigating: look for `Pause Full` entries. Those are the expensive ones. A frequent `Pause Full` (say, more than a few per hour) usually means heap is undersized or you have a leak.

---

## Diagnostic tools — what each is for

### `jcmd` — the swiss army knife

Modern, supersedes most of the old tools:

```
jcmd <pid> GC.heap_info                    # heap usage right now
jcmd <pid> GC.class_histogram              # what's piled up, by class
jcmd <pid> GC.heap_dump /tmp/heap.hprof    # full heap dump
jcmd <pid> Thread.print                    # thread dump
jcmd <pid> VM.flags                        # which JVM flags are in effect
jcmd <pid> JFR.start duration=60s filename=profile.jfr   # start a JFR recording
```

`jcmd` is in every modern JDK; no extra install.

### `jstack` — thread dump only

```
jstack <pid>
```

Output: every live thread with its stack trace. What to look for:

- **Deadlocks** — listed at the bottom of the output.
- **Many threads in `parking to wait for ... LockSupport`** — pool waiting for work, normal.
- **Many threads in your code blocked on the same monitor** — lock contention.
- **A thread stuck in a `Socket.read`** — slow downstream.

### `jmap` — heap dump (legacy; prefer `jcmd GC.heap_dump`)

```
jmap -dump:format=b,file=heap.hprof <pid>
```

Then open the file in **Eclipse MAT** or **VisualVM** to find leak suspects.

### JFR — Java Flight Recorder

Built-in, low-overhead production profiler. Records CPU, allocations, GC, I/O, locks, exceptions — everything — to a file you can open in **JDK Mission Control**.

```
java -XX:StartFlightRecording=duration=2m,filename=startup.jfr ...
# or attach at runtime:
jcmd <pid> JFR.start duration=60s filename=current.jfr
```

JFR is what you reach for when "the service is slow but I don't know why." Open the recording, look at the hot methods, the allocation hot spots, the GC pauses, all in one view.

### Health endpoints (Micrometer, Spring Actuator)

Production services expose `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`. The metrics include heap usage, GC counts, thread states, request latency — pushed to Prometheus/Grafana for alerting.

In application code, you read these via `ManagementFactory`:

```java
MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
long used = mem.getHeapMemoryUsage().getUsed();

ThreadMXBean threads = ManagementFactory.getThreadMXBean();
long[] deadlocked = threads.findDeadlockedThreads();   // null if none
```

---

## Reading a thread dump

When you have one, look in order:

1. **"Java-level deadlock"** section, if present, is decisive — the JVM detected a cycle.
2. **Count threads in `BLOCKED` state** — they're waiting on a `synchronized` lock. If many block on the same monitor, you've got contention.
3. **Threads named after your business logic** vs **library/framework names**. Application threads in unexpected states usually point to the bug.
4. **`RUNNABLE` threads** in your code consuming CPU — open the stack, see what they're computing.

A useful trick: take *three* thread dumps 10 seconds apart. Threads that are in the same stack frame all three times are stuck (or doing very long work). Threads that move through different frames are healthy.

---

## What to alert on

For an API service, set alarms on:

- **Heap usage > 80% after GC.** Approaching OOM territory.
- **Full GC count > N per minute.** Heap pressure or memory leak.
- **GC pause time P99 > 200ms** (or whatever your SLO allows).
- **Live thread count climbing without bound.** Leak somewhere.
- **CPU saturation > 80% sustained.** Either undersized or genuine load issue.
- **Class count climbing.** Metaspace leak (dynamic class generation, classloader leak).

Don't alert on absolute counts (`heap used > 1.5G`) — those become wrong on every scaling change. Alert on percentages and rates.

---

## Common pitfalls

- **Default `-Xmx` in containers.** Pre-Java 10 the JVM ignored cgroups; even now `-Xmx` of "whatever the host has" is wrong inside a 2GB container. Set explicitly or use `MaxRAMPercentage`.
- **No GC logs.** When a latency spike happens, you have no data. Always log.
- **Tuning by changing many flags at once.** Change one. Measure. Confirm. Repeat. Otherwise you can't tell which flag did what.
- **Calling `System.gc()` to "see if it helps."** Pins a full-GC pause at the worst moment. Don't.
- **Treating thread dumps as one-shot.** Take multiple, compare.
- **Using `jstat` / `jconsole` in production.** They work but they're noisy; JFR with reasonable settings has lower impact for the same data.

---

## Code examples

1. `JvmRuntimeInfo.java` — print Java version, heap settings, GC names, active flags.
2. `HeapUsageWatcher.java` — sample heap usage every 200ms while allocating; print used/max.
3. `DetectDeadlock.java` — deliberately deadlock two threads, use `ThreadMXBean.findDeadlockedThreads` to detect.
4. `GcCountAndTime.java` — read `GarbageCollectorMXBean` totals before and after an allocation burst.
5. `ThreadDumpProgrammatic.java` — produce a thread dump from inside the JVM, formatted like `jstack`.

---

## Try this yourself

1. Run `HeapUsageWatcher.java` with `-Xmx128m` and watch GC kick in as the heap fills. Rerun with `-Xmx1g` and see how rarely it fires.
2. In `DetectDeadlock.java`, comment out the detection call. Run with `jstack <pid>` from another terminal. Compare the JVM's deadlock report with `findDeadlockedThreads`.
3. With any example running, in another terminal: `jcmd <pid> VM.flags` and read the resolved flag values. Add `-XX:+PrintFlagsFinal` to see hundreds more.

---

## Self-check

1. You see a `Pause Full` in the GC log lasting 800ms. Name two pieces of data you'd look at next, and what each would tell you.
2. Why is setting `-Xms` equal to `-Xmx` standard practice for backend services?
3. A service has consistent 200ms p99 latency but 5s p99.9 latency. The 5s coincides with a `Pause Full` in `gc.log`. What's the most likely fix, and what's a more conservative diagnostic step first?
