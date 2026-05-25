# 01 — Java 8 Features Overview

Java 8 (released March 2014) was the biggest single language release since generics. It added lambdas, the Stream API, `Optional`, default methods on interfaces, a new date/time API, and `CompletableFuture` — and reshaped how Java is written. Almost every modern Spring/Hibernate/JUnit API assumes you have these tools.

This topic is the **map** for topics 02–04 in this sub-module. It introduces each feature briefly, shows the before/after style, and points to the deep-dive topic. Don't try to memorize everything here; use it as the index.

---

## The problem Java 8 solved

Before Java 8, processing a list of users to find adult names looked like this:

```java
List<String> names = new ArrayList<>();
for (User u : users) {
    if (u.getAge() >= 18) {
        names.add(u.getName());
    }
}
Collections.sort(names);
```

Six lines, all imperative — you tell the JVM exactly *how* to iterate, branch, and collect. The actual intent ("adult users' names, sorted") is buried.

After Java 8:

```java
List<String> names = users.stream()
    .filter(u -> u.getAge() >= 18)
    .map(User::getName)
    .sorted()
    .collect(Collectors.toList());
```

The intent is the code. Anonymous-class noise is gone (`new Predicate<User>() { boolean test(User u) {...} }` would have been the pre-8 equivalent). And the runtime is free to optimize the pipeline.

---

## The features

| Feature | What it gives you | Deep dive |
|---------|------------------|-----------|
| **Lambda expressions** | Pass behavior as data with concise syntax | Topic 02 |
| **Functional interfaces** + `java.util.function.*` | `Predicate`, `Function`, `Consumer`, `Supplier`, `BiFunction` — the lambda target types | Topic 02 |
| **Method references** | `User::getName` shorthand for `u -> u.getName()` | Topic 02 |
| **Stream API** | Declarative pipelines over collections, arrays, generators | Topic 03 |
| **Collectors** | `toList`, `toMap`, `groupingBy`, `joining` — terminal reducers | Topic 03 |
| **`Optional<T>`** | Explicit "value or absent" type | Topic 04 |
| **Default methods on interfaces** | Add methods to an interface without breaking implementers | this topic |
| **Static methods on interfaces** | Utility methods belong with the type | this topic |
| **`java.time` (JSR-310)** | Immutable date/time API that replaces `Date`/`Calendar` | this topic |
| **`CompletableFuture`** | Composable async pipelines | covered in [03c topic 05](../03c-concurrency/05-callable-future-and-completablefuture.md) |

The first six are the ones you reach for daily. The rest matter when they matter.

---

## Imperative vs declarative

This is the mindset shift, not a syntax change.

- **Imperative** — describe the steps: "create empty list; loop; if condition; append; next."
- **Declarative** — describe the result: "names of adult users, sorted."

The same code in both styles produces the same answer. Declarative is shorter, harder to introduce bugs into (no off-by-one loop variables), and lets the runtime optimize (parallel streams, lazy evaluation). It is also harder to debug when the pipeline goes wrong — you can't drop a breakpoint between operators the way you can between statements.

Use the style that fits the situation. A short transformation reads beautifully as a stream; a complex algorithm with branches and early returns is clearer imperative.

---

## Default methods on interfaces

Before Java 8, adding a method to an interface broke every implementer. Java 8 fixed this by letting an interface ship a *default* implementation:

```java
interface Collection<E> {
    boolean add(E e);                       // abstract; existing
    default boolean removeIf(Predicate<? super E> filter) {   // new in Java 8
        Iterator<E> it = iterator();
        boolean removed = false;
        while (it.hasNext()) if (filter.test(it.next())) { it.remove(); removed = true; }
        return removed;
    }
}
```

This is how `Collection.removeIf`, `List.sort`, `Map.getOrDefault`, `Iterable.forEach`, and dozens of other "new" methods got added to existing interfaces in Java 8+ without breaking the world.

A class implementing the interface can override the default, or accept it. If a class inherits a default from two interfaces with conflicting signatures, you must override and choose.

---

## Static methods on interfaces

Utility methods that *belong* with the type, but don't need an instance:

```java
public interface Comparator<T> {
    int compare(T a, T b);
    static <T> Comparator<T> naturalOrder() { return (Comparator) Comparators.NaturalOrderComparator.INSTANCE; }
    static <T, U extends Comparable<? super U>> Comparator<T> comparing(Function<? super T, ? extends U> f) { /*...*/ }
}
```

Before Java 8, these lived in awkward `Comparators` utility classes. Now they're on the type itself.

---

## The new date/time API

`java.util.Date` was a disaster: mutable, thread-unsafe, confusing month numbering (`January = 0`), no timezone separation. Java 8 added `java.time` (JSR-310, inspired by Joda-Time), which is **immutable**, **thread-safe**, and **clear**.

| Type | What it represents | Example |
|------|--------------------|---------|
| `LocalDate` | A date — no time, no zone | `2026-05-23` |
| `LocalTime` | A time — no date, no zone | `14:30:00` |
| `LocalDateTime` | Date + time, no zone | `2026-05-23T14:30:00` |
| `Instant` | A point on the timeline (UTC) | `2026-05-23T18:30:00Z` |
| `ZonedDateTime` | `Instant` with a zone for human display | `2026-05-23T14:30-04:00[America/New_York]` |
| `Duration` | A span of time, exact (e.g. seconds) | `PT2H30M` |
| `Period` | A span of dates (e.g. months, years) | `P1Y2M` |

Rule of thumb:

- **Storing a timestamp in a database** → `Instant`.
- **A user's birthday** → `LocalDate`.
- **An appointment with a timezone** → `ZonedDateTime`.

Use `java.util.Date` only if you must interop with old code, and convert to `Instant` immediately.

---

## Performance awareness

Java 8 features aren't free. Specifically:

- **Streams allocate.** A pipeline creates intermediate stream objects, captures lambdas as classes (with `invokedynamic`-cached instances), and may box primitives if you don't use `IntStream`/`LongStream`.
- **Lambdas may capture.** A lambda referencing a local variable becomes a class with that variable as a field. In a tight loop, each invocation allocates.
- **Parallel streams have setup cost** and share the common `ForkJoinPool` (see [03d topic 04](../03d-jvm-and-runtime/04-java-performance-optimization.md) and topic 03 in this sub-module).

For 99% of code this is invisible. For the few hot paths it matters, prefer plain loops and primitive arrays.

---

## Common pitfalls

- **Using `parallelStream()` reflexively.** It uses the JVM-wide common pool. One slow `forEach` blocks every other parallel stream in the process. Topic 24.
- **`Optional` as a method parameter or field.** `Optional` is for return types — see topic 04.
- **`java.util.Date` in new code.** Use `java.time` types.
- **Default methods to add state.** Default methods can call other methods on the interface, but interfaces still can't hold state. Putting "default fields" via `static Map<This, T>` is a memory leak.
- **Method reference to a stateful method.** `list.stream().map(MyClass::transform)` where `transform` reads instance state — fine, but the order of evaluation in parallel pipelines is undefined.

---

## Code examples

1. `ImperativeVsDeclarative.java` — same task, both styles, side by side.
2. `DefaultMethodOnInterface.java` — adding a method to an interface without breaking existing implementers.
3. `StaticMethodOnInterface.java` — utility on the type.
4. `JavaTimeBasics.java` — `Instant`, `LocalDate`, `ZonedDateTime`, `Duration` — the common operations.
5. `OldVsNewDateApi.java` — show why `java.util.Date` is dangerous and `Instant` is not.

---

## Try this yourself

1. Take `ImperativeVsDeclarative.java`'s declarative pipeline. Break it: change `filter` to `filter(u -> u.getAge() < 18)`. Run. Notice the result naturally describes itself; the imperative version requires reading the loop.
2. In `JavaTimeBasics.java`, parse the string `"2026-05-23"` into a `LocalDate`, add 6 months, convert to an `Instant` at midnight UTC, print.
3. In `OldVsNewDateApi.java`, modify the shared `Date` from a second thread and observe the corruption.

---

## Self-check

1. Before Java 8, adding a method to `Collection` would have broken every implementer in the world. What Java 8 feature let the JDK team add `removeIf` to it safely, and how does it work?
2. A user's birthday is `1995-07-04`. Which `java.time` type would you store it as, and why is it wrong to use `LocalDateTime`?
3. Your colleague writes `users.parallelStream().forEach(this::sendEmail)`. Name two reasons this might be a bad idea.
