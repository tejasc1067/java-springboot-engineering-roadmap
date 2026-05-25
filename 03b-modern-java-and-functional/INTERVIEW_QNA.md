# Interview Q&A — Module 03b (Modern Java & Functional)

Lambdas, streams, `Optional`, default methods. Each answer requires understanding, not vocabulary.

---

### Q1. What makes an interface a "functional interface"? What does `@FunctionalInterface` add?

**A.** A functional interface is **any interface with exactly one abstract method**. Default methods, static methods, and methods overriding `Object`'s methods (`equals`, `hashCode`, `toString`) don't count toward the abstract-method count.

`Runnable` (one abstract method `run`) has always been a functional interface, even before Java 8 — but lambdas weren't a thing then, so you couldn't target it with one.

`@FunctionalInterface` adds **compile-time enforcement**: if anyone later adds a second abstract method, compile fails. It's a contract pin — useful, optional, doesn't change behavior.

---

### Q2. Why does `parallelStream` use a single JVM-wide thread pool, and what's the implication?

**A.** It uses `ForkJoinPool.commonPool()` — a static pool sized to `Runtime.availableProcessors() - 1`. Every parallel stream and every `CompletableFuture` without a custom executor shares it.

Implications:

- One slow `parallelStream` (especially I/O-bound work) ties up workers; every other parallel stream in the JVM slows down.
- You don't know what other libraries in the process use it.
- The pool can't be sized per-workload.

For production parallelism, hand work to your own `ExecutorService` and use `CompletableFuture` instead. `parallelStream` is appropriate for CPU-bound, embarrassingly parallel work on large collections that you control end-to-end.

---

### Q3. When NOT to use `Optional`.

**A.** Don't use `Optional` as:

- **A field** — adds allocation per access, serialization frameworks struggle.
- **A method parameter** — callers can still pass `null`, you gained nothing.
- **A collection element** — `List<Optional<T>>` is almost always wrong; use `List<T>` and exclude nulls.
- **A return type for collections** — return an empty list instead of `Optional<List<T>>`.

Do use `Optional`:

- As a method return type for a single value that might be absent (a finder-style method).
- Inside fluent transformation chains (`map`, `flatMap`, `orElseGet`).

The point of `Optional` is the explicit type signature. If the signature isn't where the absence shows up, `Optional` adds ceremony without buying anything.

---

### Q4. `orElse` vs `orElseGet` — when does the difference matter?

**A.** `orElse(default)` evaluates its argument eagerly — always, even when the `Optional` is present. `orElseGet(supplier)` only invokes the supplier when empty.

```java
user.orElse(loadFromDb())              // ALWAYS hits the DB
user.orElseGet(() -> loadFromDb())     // hits the DB only when needed
```

Matters whenever the default is expensive. Style-guide-level rule: use `orElseGet` for anything that isn't a literal or a constant.

---

### Q5. Why does `stream.filter(...).map(...).findFirst()` typically run faster than you'd expect on a long source — what's happening operationally?

**A.** **Lazy evaluation + short-circuit.** Intermediate operators (`filter`, `map`) don't execute until a terminal operator triggers them, and they fire **per element**, not per operator. `findFirst` is short-circuiting — as soon as one element passes the whole pipeline, the rest of the source is never inspected.

For a million-element source where the first match is at index 3: only 3 elements flow through `filter`, of which 1 flows through `map`. The other 999,997 never touch the pipeline.

This is also why streams can process infinite sources (`Stream.iterate`, `Stream.generate`) — only the short-circuit terminal lets the program terminate.

---

### Q6. You write `users.stream().collect(Collectors.toMap(User::getEmail, u -> u))`. Two users share an email. What happens, and how do you fix it cleanly?

**A.** The 2-argument `toMap` throws `IllegalStateException("Duplicate key ...")` at runtime. Crashes the request.

Fix with the 3-argument form: `toMap(keyFn, valueFn, mergeFn)`. Pick a merge strategy:

- `(a, b) -> a` — keep the first.
- `(a, b) -> b` — keep the last.
- `(a, b) -> mostRecent(a, b)` — domain-specific tiebreaker.

If you actually wanted multiple values per key, you wanted `groupingBy(User::getEmail)` returning `Map<String, List<User>>`, not `toMap`.
