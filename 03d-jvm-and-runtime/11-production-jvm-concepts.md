# 11 — Production JVM Concepts

This is the closing topic for module 03. By now you know the language, the standard library, the concurrency model, the memory model, and the tools. Production is where that knowledge becomes load-bearing: a service must stay fast, stay up, and recover from things going wrong.

This topic is about the system-level concerns: latency vs throughput, cold start, thread and connection pools, backpressure, bottlenecks, and the observability mindset that ties them together. Less new API, more frame of reference for the decisions you'll make on any non-trivial service.

---

## Latency vs throughput

Two different goals; tuning one usually hurts the other.

- **Latency** — time to handle a single request. The metric end-users feel. Measured at percentiles (p50, p95, p99, p99.9), not averages.
- **Throughput** — requests handled per second. The metric capacity planning cares about.

A batch report job optimizes throughput; you don't care if one row takes 50ms or 500ms as long as the whole report finishes fast. A latency-sensitive API optimizes p99; one slow request is a bad experience even if the average is fine.

Tuning consequences:

| Goal | Bigger thread pool helps | Bigger heap helps | Concurrent GC helps |
|------|--------------------------|-------------------|---------------------|
| Throughput | Yes (more parallelism) | Yes (fewer GCs total) | Less (steals throughput for shorter pauses) |
| Latency p99 | Up to a point (then contention bites) | Mixed (long full GC bad) | Yes (sub-10ms pauses) |

The single most useful production graph: latency *percentiles* over time, with deployment markers. Averages hide the tail. Tail latency is what wakes you up.

---

## JIT warm-up and cold start

A freshly-started JVM is running the interpreter. Bytecode hasn't been profiled yet; nothing is C2-compiled. The same code that takes 200µs in steady state can take 5ms during the first minute. This is the **cold start** problem.

It bites three places:

- **Containers that autoscale.** Each new pod is slow for ~30 seconds.
- **Serverless / Lambdas.** Container starts on each cold request; latency is awful.
- **Deployments.** The new instance carries traffic immediately if your load balancer doesn't know better.

Mitigations:

1. **Health-check readiness gates.** Don't accept traffic until a synthetic warm-up has run.
2. **Run a warm-up routine on startup.** Call the hottest paths a few thousand times in a `@PostConstruct`.
3. **AOT / native compilation.** GraalVM native-image compiles ahead of time; no warm-up needed, faster start, lower memory — at the cost of slower peak throughput than a fully-JIT'd JVM and stricter constraints (no reflection without config, etc.).
4. **CDS** (Class Data Sharing) and **AppCDS** speed up class loading on every start.

Topic 03 covers the JIT mechanics; topic 10 covers the flags.

---

## Pool sizing

Two pools matter most: **threads** (for handling requests) and **connections** (DB, HTTP, Redis, downstream services). Both can be the wrong size.

### Thread pool

The classic formula:

```
threads ≈ cores * (1 + waitTime / serviceTime)
```

- CPU-bound work: ~cores or cores+1.
- I/O-bound work: much higher; threads spend most time blocked.

For an API where each request waits 90ms on a DB and computes 10ms, on 8 cores: ~80 threads. Measure your actual numbers — guesses are usually wrong by 3-10x.

**Symptoms of wrong size:**

| Too small | Too large |
|-----------|-----------|
| Queue depth grows; requests time out | Threads idle in contention |
| CPU under-utilized while clients wait | Context-switching overhead dominates |

### Connection pool

If the DB has a connection limit of 100 and you run 20 service instances, each instance's pool max should be **≤ 5**. Otherwise the first few instances exhaust the limit and the rest fail.

Symptoms of pool starvation:

- Latency cliff: most requests fast, some incredibly slow (waiting for a connection).
- "Cannot acquire connection" errors in logs.
- Thread dump shows handlers parked in `HikariCP.getConnection`.

HikariCP (the modern JDBC pool) defaults are sensible; for most services you tune `maximumPoolSize` and `connectionTimeout`.

---

## Backpressure

When the system can't keep up, what should happen? The wrong answer: queue indefinitely. The right answer: **push the slowness upstream**, so the caller knows to slow down.

Common patterns:

- **Bounded queues with `CallerRunsPolicy`** ([03c topic 04](../03c-concurrency/04-executor-framework.md)) — the producer pays the cost when the pool is full.
- **HTTP 503 / 429** — the API tells callers "back off."
- **Circuit breakers** (Resilience4j, Hystrix) — after N failures, fail fast for a window so the downstream can recover.
- **Reactive streams** — Project Reactor / RxJava have explicit `request(n)` flow control built in.

Without backpressure, slowness compounds: the queue fills, requests time out, callers retry, queue grows, latency rises further, OOM. The classic **cascading failure**.

---

## Bottleneck analysis — what's actually slow

When a service is slow, the bottleneck is one of:

1. **CPU saturation.** `top` shows the process at 100% across all cores. Profile (JFR) → see what's hot.
2. **Memory / GC pressure.** Heap usage stays high after GC; pause times climb. Topic 19.
3. **Lock contention.** Thread dump shows many threads in `BLOCKED` on the same monitor. Topic 13/14/17.
4. **I/O wait.** Threads parked in `Socket.read` or `getConnection`. Pool or downstream is the problem.
5. **GC + I/O combination.** Slow downstream → threads pile up → heap fills → GC pressure → everything slows.

The diagnostic order:

1. Look at metrics. Is CPU saturated? Heap full? Thread count climbing?
2. If CPU: take a CPU sample (JFR or async-profiler), look at flame graph.
3. If memory: heap dump, look at retained sizes.
4. If neither: thread dump. What are most threads doing?

---

## The observability mindset

Three pillars:

- **Metrics** — numeric time-series (Prometheus): request rate, latency percentiles, error rate, heap, GC, threads, queue depths. Cheap; aggregate.
- **Logs** — structured events with context (request ID, user, action). Verbose; per-event.
- **Tracing** — span graphs across services (Zipkin / Jaeger / OTel). Shows where in a multi-service chain time went.

A service without these is a black box. When it breaks at 3am you have no way in. Setting them up is part of "production-ready," not a separate feature.

Three things a service should expose on day one:

1. **`/health`** — readiness + liveness for load balancers.
2. **`/metrics`** — Prometheus scrape target.
3. **Structured logs** with at least a request ID so you can correlate.

---

## Scalability — three axes

When the service can't handle more load:

- **Vertical** — bigger machine. Easy, fast, has a ceiling.
- **Horizontal** — more instances. Requires stateless services (or sticky sessions / shared state).
- **Architectural** — async, queues, splitting hot paths from cold. Hardest, most lasting.

The right answer depends on the workload. A CPU-bound report generator might just need a bigger machine. An I/O-bound API scales horizontally. A monolith that's outgrown its envelope needs architectural change.

---

## Common pitfalls

- **One global thread pool for everything.** A slow batch job fills the queue, latency-sensitive requests sit waiting. Separate pools per workload class.
- **No backpressure.** Queues grow without bound. Latency cliffs. OOM.
- **Tuning by intuition.** Always profile first. The wrong fix often hurts.
- **No GC logs, no metrics.** When the page fires, you have nothing to look at. Set up observability *first*.
- **Optimizing the wrong layer.** Saved 20µs in a method that runs 1k times/sec when the DB call takes 50ms. Nothing changed at the user level.
- **Cold start ignored in deployments.** New pod takes traffic before the JIT warms up; users see latency spikes on every rollout.
- **Single global `ExecutorService` with `commonPool`.** One slow library blocks all parallel streams in the process.

---

## Code examples

This topic is less about new APIs and more about understanding. The examples below use what 03c and the earlier 03d topics covered to demonstrate production patterns.

1. `MeasureLatencyPercentiles.java` — generate request timings, compute p50/p95/p99/p99.9. The right way to look at latency.
2. `WarmupVsSteadyState.java` — same workload measured cold and after warmup; see the cold-start gap.
3. `BackpressureCallerRuns.java` — bounded pool with `CallerRunsPolicy` slowing the producer when overloaded.
4. `SeparatePoolsByWorkload.java` — separate batch and request pools; show one doesn't starve the other.
5. `BottleneckSnapshot.java` — read live JMX beans (CPU load, GC, heap, threads) — the same data you'd feed into Prometheus.

---

## Try this yourself

1. In `MeasureLatencyPercentiles.java`, deliberately inject a 1-in-1000 slow request. Watch p99.9 spike while p99 stays clean. That's the tail.
2. In `BackpressureCallerRuns.java`, switch to `AbortPolicy`. Observe submissions throw under load — that's how the producer learns to back off.
3. In `BottleneckSnapshot.java`, generate load (allocate, contend on a lock) in another thread; see the metrics shift.

---

## Self-check

1. A service has a p50 of 5ms and a p99 of 50ms. The average is reported as 12ms. Why is the average a misleading number to optimize against?
2. You add 20 instances of a service. Each opens a HikariCP pool with `maximumPoolSize=20` to a database that allows 100 connections. What goes wrong, and what's the formula for the right per-instance limit?
3. The same service runs much slower for the first 30 seconds after a deploy. Name two reasons, and the two mitigations corresponding to each.
