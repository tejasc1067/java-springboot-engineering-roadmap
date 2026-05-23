# Module 03d — JVM, Runtime & Production

The fourth and final sub-module covering advanced Java. This one is about **what happens after `javac`** — the runtime that turns your bytecode into a service that handles traffic, allocates memory, gets garbage-collected, talks over networks, and (when something goes wrong) gets diagnosed by you at 3am.

Coverage: JVM memory & GC, JVM internals (class loading, JIT, bytecode), performance, reflection, annotations, serialization, networking, NIO, JVM tuning & monitoring, production-engineering mindset.

## Audience

You've completed 03a, 03b, and 03c. You can write modern Java with confidence. Now you need to understand the runtime so that when production gets weird, you have somewhere to look.

This is the sub-module that separates a Java *developer* from a Java *backend engineer*. The depth here is intentionally heavier than strictly required for getting hired — much of it becomes load-bearing the first time you're on-call.

## Prerequisites

- 03a, 03b, 03c complete.
- Comfort using your terminal (you'll run `jcmd`, `jstack`, profile-flag JVMs).

## Topics (in reading order)

### Memory & GC

1. [Java memory management](01-java-memory-management.md)
2. [Garbage collection](02-garbage-collection.md)
3. [JVM internals (class loading, JIT, bytecode)](03-jvm-internals.md)
4. [Performance optimization](04-java-performance-optimization.md)

### Framework foundations

5. [Reflection API](05-reflection-api.md)
6. [Annotations](06-annotations.md)
7. [Serialization](07-serialization.md)

### Networking & I/O

8. [Java networking basics (sockets, TCP, UDP)](08-java-networking-basics.md)
9. [Java NIO (buffers, channels, selectors)](09-java-nio.md)

### Production engineering

10. [JVM tuning and monitoring](10-jvm-tuning-and-monitoring.md)
11. [Production JVM concepts](11-production-jvm-concepts.md)

## Companion files

- [COMMON_MISTAKES.md](COMMON_MISTAKES.md) — memory leaks, reflection traps, serialization vulnerabilities, networking pitfalls, JVM tuning mistakes.
- [INTERVIEW_QNA.md](INTERVIEW_QNA.md) — JVM, reflection, serialization, networking, production-engineering questions.

## Depth note

Topics **07** (serialization), **08** (networking basics), **09** (Java NIO), **10** (JVM tuning), and **11** (production JVM concepts) go **deeper than strictly necessary** for entry-level Java backend work. Modern services rely on frameworks (Spring's `RestTemplate`/`WebClient`, Jackson, Netty) that wrap most of this. Skim these topics first; come back when you encounter the related production work.

Topics **01-04** (memory, GC, JVM internals, performance) and **05-06** (reflection and annotations) are foundational and worth full study.

## What you should be able to do after this sub-module

- Diagnose a production latency spike using GC logs, thread dumps, and JFR.
- Recognize a memory leak, take a heap dump, and find the dominator.
- Choose `-Xmx`, a collector, and a thread-pool size based on the workload.
- Understand how Spring discovers your `@Service` beans (reflection + annotations).
- Recognize the classic Java native-serialization RCE risk and avoid it.
- Talk about latency percentiles and backpressure with the engineers running the service.

## Where to next

→ **Module 04** ([JDBC and database](../04-jdbc-and-database/)). Persistence is where backend-specific thinking begins: connection pools, transactions, parameterized SQL, the relationship between your service and the database it can't live without.

## Folder structure

```text
03d-jvm-and-runtime/
├── README.md
├── INTERVIEW_QNA.md
├── COMMON_MISTAKES.md
├── 01-java-memory-management.md … 11-production-jvm-concepts.md
└── CODE_EXAMPLES/
    ├── 01-java-memory-management/
    └── ...
```
