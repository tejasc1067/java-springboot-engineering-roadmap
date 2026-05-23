# Module 03b — Modern Java & Functional

The second of four sub-modules covering advanced Java. This one covers the **Java 8+ functional toolkit**: lambdas, the Stream API, `Optional`, and the language features (default methods on interfaces, `java.time`) that came with the 2014 release.

You'll use these tools every day in modern Spring/Hibernate codebases. They also underpin 03c (concurrency) — `CompletableFuture` chains are built from lambdas — which is why this sub-module comes before 03c in the recommended reading order.

## Audience

You've completed 03a. You're comfortable with generics and the collections framework. Now you're ready to write *declarative* Java — describing what should happen instead of how.

## Prerequisites

- 03a complete, or equivalent comfort with generics, collections, and `Comparable`/`Comparator`.

## Topics (in reading order)

1. [Java 8 features overview](01-java-8-features.md)
2. [Functional interfaces and lambdas](02-functional-interfaces-and-lambda.md)
3. [Stream API](03-stream-api.md)
4. [Optional API](04-optional-api.md)

## Companion files

- [COMMON_MISTAKES.md](COMMON_MISTAKES.md) — bugs specific to lambdas, streams, and `Optional`.
- [INTERVIEW_QNA.md](INTERVIEW_QNA.md) — interview questions on functional Java.

## What you should be able to do after this sub-module

- Write a stream pipeline that reads as the *intent*, not the *iteration*.
- Choose between `thenApply` and `thenApplyAsync` (this matters in 03c).
- Use `Optional` where it earns its keep, and avoid it where it adds ceremony.
- Read modern Java/Spring code without stopping to puzzle out lambda syntax.

## Where to next

→ **[03c — Concurrency](../03c-concurrency/)**. Multithreading, synchronization, executors, futures, the modern locks API. `CompletableFuture` here will lean on what you learned in topic 03 (streams) and topic 02 (functional interfaces).

## Folder structure

```text
03b-modern-java-and-functional/
├── README.md
├── INTERVIEW_QNA.md
├── COMMON_MISTAKES.md
├── 01-java-8-features.md … 04-optional-api.md
└── CODE_EXAMPLES/
    ├── 01-java-8-features/
    └── ...
```
