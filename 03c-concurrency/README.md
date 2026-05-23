# Module 03c — Concurrency

The third of four sub-modules covering advanced Java. This one is **the concurrency module**: threads, synchronization, executors, futures, and the modern `java.util.concurrent.locks` and `java.util.concurrent.atomic` toolkits.

Concurrency bugs are the hardest production bugs you'll ever debug — visibility problems that work on your laptop but corrupt data under load, deadlocks that lock up your service at 3am, race conditions that pass tests and fail in prod. The goal of this sub-module is to make those bugs *recognizable* before they happen.

## Audience

You've completed 03a and 03b. You're comfortable with lambdas and `Function`/`Predicate`/`Consumer` — `CompletableFuture` will use them heavily.

If you're a Java developer who's only ever worked with single-threaded code (the request handler is "just a method"), this is where the change in worldview happens.

## Prerequisites

- 03a complete (collections, especially `ConcurrentHashMap` from [03a topic 11](../03a-java-language-depth/11-concurrent-collections.md)).
- 03b complete (lambdas — required for `CompletableFuture`).

## Topics (in reading order)

1. [Multithreading basics](01-multithreading-basics.md)
2. [Thread synchronization](02-thread-synchronization.md)
3. [Thread safety & concurrency problems](03-thread-safety-and-concurrency-problems.md)
4. [Executor framework](04-executor-framework.md)
5. [Callable, Future, CompletableFuture](05-callable-future-and-completablefuture.md)
6. [Advanced concurrency (locks, atomics, semaphores)](06-concurrency-advanced.md)

## Companion files

- [COMMON_MISTAKES.md](COMMON_MISTAKES.md) — race conditions, visibility bugs, deadlocks, executor pitfalls.
- [INTERVIEW_QNA.md](INTERVIEW_QNA.md) — the classic concurrency interview ground.

## What you should be able to do after this sub-module

- Reproduce a race condition on demand and write the fix.
- Recognize the AB-BA deadlock pattern in code review.
- Pick the right thread-pool configuration for a workload (CPU-bound vs I/O-bound).
- Compose async work with `CompletableFuture` chains without blocking.
- Diagnose a deadlock in production using `jstack` or `ThreadMXBean`.

## Where to next

→ **[03d — JVM, Runtime & Production](../03d-jvm-and-runtime/)**. Memory management, garbage collection, JVM internals, reflection (the foundation of Spring), annotations, serialization, networking, NIO, and production tuning.

## Folder structure

```text
03c-concurrency/
├── README.md
├── INTERVIEW_QNA.md
├── COMMON_MISTAKES.md
├── 01-multithreading-basics.md … 06-concurrency-advanced.md
└── CODE_EXAMPLES/
    ├── 01-multithreading-basics/
    └── ...
```

A few examples coordinate two or more files (e.g., `MultiThreadedEchoServer.java`'s client + server, deadlock demos with timing-sensitive sleeps). Those are noted in the relevant markdown.
