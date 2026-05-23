# Module 03a — Java Language Depth

The first of four sub-modules that together cover advanced Java. This one focuses on the **language and the type system** — exceptions, generics, the collections framework. The other three (03b, 03c, 03d) cover modern syntax, concurrency, and JVM internals.

## Audience

You've completed modules 01-02 (Java fundamentals + OOP). You can read and write Java classes confidently. This sub-module deepens your grasp of the type system and the standard library — the foundation for everything in 03b-03d.

If you're a career-switcher landing here from another language, this is your starting point for the Java-specific knowledge a backend role assumes.

## Prerequisites

- Java syntax (variables, loops, methods, classes).
- OOP (inheritance, interfaces, polymorphism — module 02).
- Java 17 installed.

## Topics (in reading order)

1. [Exception handling](01-exception-handling.md)
2. [Custom exceptions & best practices](02-custom-exceptions-and-best-practices.md)
3. [Generics](03-generics.md)
4. [Generics advanced (PECS, type erasure)](04-generics-advanced.md)
5. [Collections framework overview](05-collections-framework-overview.md)
6. [List and ArrayList](06-list-and-arraylist.md)
7. [LinkedList and Vector](07-linkedlist-and-vector.md)
8. [Set and hashing](08-set-and-hashing.md)
9. [Map and HashMap internals](09-map-and-hashmap-internals.md)
10. [Comparable vs Comparator](10-comparable-vs-comparator.md)
11. [Concurrent collections](11-concurrent-collections.md)

## Companion files

- [COMMON_MISTAKES.md](COMMON_MISTAKES.md) — concrete bugs with code, symptoms, and fixes for this sub-module.
- [INTERVIEW_QNA.md](INTERVIEW_QNA.md) — interview questions whose answers require understanding, not vocabulary.

## What you should be able to do after this sub-module

- Design exception hierarchies that aid debugging instead of obscuring it.
- Read and write generic APIs using PECS correctly.
- Pick the right collection for a given access pattern (lookup-heavy, iteration-heavy, sorted, concurrent).
- Recognize and fix the classic `equals`/`hashCode` and `HashMap`-under-concurrency bugs.

## Where to next

→ **[03b — Modern Java & Functional](../03b-modern-java-and-functional/)**. Lambdas, streams, and `Optional`. The functional toolkit you'll need before 03c (concurrency) because `CompletableFuture` examples use lambdas heavily.

## Folder structure

```text
03a-java-language-depth/
├── README.md
├── INTERVIEW_QNA.md
├── COMMON_MISTAKES.md
├── 01-exception-handling.md … 11-concurrent-collections.md
└── CODE_EXAMPLES/
    ├── 01-exception-handling/
    │   ├── FirstExample.java
    │   └── ...
    └── ...
```

Every Java file under `CODE_EXAMPLES/` is fully self-contained. From any topic's directory:

```text
javac *.java
java SomeExample
```
