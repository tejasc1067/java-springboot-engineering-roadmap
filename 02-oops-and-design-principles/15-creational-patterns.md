# 15 — Creational Patterns: Singleton, Factory Method, Builder

A **design pattern** is a named, reusable answer to a problem that keeps coming up in object-oriented code. In 1994 four authors (the "Gang of Four", GoF) catalogued 23 of them in three families: **creational** (how objects get created), **structural** (how objects are combined — topic 16), and **behavioral** (how objects divide up work — topic 17).

This topic covers the three creational patterns you will meet every week as a backend engineer. Each one exists because plain `new SomeClass(...)` goes wrong in a specific way — and each section shows that way going wrong first.

---

## The problem this solves

`new` is the simplest way to create an object, and usually the right one. It stops being enough when:

- there must be **exactly one** of something, shared by the whole program (a config loader, a connection pool) — but `new` happily makes as many as you ask for;
- the caller shouldn't have to know **which class** to create — `new EmailNotifier()` hard-codes the choice into every caller;
- the object has **many parameters**, several of the same type — `new UserAccount("bob@x.com", "Bob", true, false, false)` compiles whichever order you get the booleans in.

Singleton, factory method, and builder are the standard fixes for those three, in that order.

---

## Singleton — exactly one instance

### How it works

Make the constructor `private` so no one else can call `new`, and hand out one shared instance from a static method:

```java
class ConfigLoader {
    private static ConfigLoader instance;

    private ConfigLoader() { /* read the config file - slow */ }

    static ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }
}
```

### Broken: the lazy singleton isn't single

That `getInstance()` is a **check-then-act**: check `instance == null`, then create. With one thread it's fine. With several threads arriving together, they can *all* pass the check before any of them has finished creating the object.

`SingletonBroken.java` starts 10 threads at the same moment and counts how many `ConfigLoader` objects get built:

```text
Threads that called getInstance(): 10
ConfigLoader objects created:      10
The "single" instance was created 10 times.
```

On a web server, every request runs on its own thread (module 08), so this is not a theoretical problem. Each copy might have read the config at a different moment, and code that relies on "there's only one" silently breaks.

### Fixed: let the JVM guarantee it

The simplest thread-safe singleton in Java is an `enum` with one value:

```java
enum ConfigLoader {
    INSTANCE;

    ConfigLoader() { /* read the config file */ }

    String get(String key) { ... }
}

ConfigLoader.INSTANCE.get("db.url");
```

The JVM creates enum values exactly once, when the enum class is first used, and guarantees that class initialization happens on one thread while others wait. `SingletonSafe.java` runs the same 10-thread race and asserts exactly one instance, every run.

Two other fixes you'll see in older code:

- **Holder class:** a `private static class Holder { static final ConfigLoader INSTANCE = new ConfigLoader(); }` — same JVM guarantee, works when you can't use an enum.
- **Double-checked locking:** `synchronized` plus a second `null` check. It only works if the field is `volatile`, and people forget. Prefer the enum or the holder.

### When not to write one

A hand-written singleton is a **global variable**: any class can reach it with `ConfigLoader.getInstance()`, nothing in a constructor says it's being used, and tests can't swap it for a fake. That's why, from module 07 on, you will almost never write one. In Spring every bean is a singleton by default — the container creates one instance and *passes it in* through the constructor. You get "exactly one" without the global access. (SOLID's D, topic 14, is the reason that's better.)

---

## Factory method — one place decides which class

### Broken: the decision copied into every caller

When the right implementation depends on input (`"email"` → `EmailNotifier`, `"sms"` → `SmsNotifier`), the naive code puts a `switch` wherever a notifier is needed. `NotificationSwitchBroken.java` has two such places. SMS support was added to one and not the other:

```java
class BillingReport {
    int monthlyCost(String channel, int messages) {
        Notifier notifier = switch (channel) {
            case "email" -> new EmailNotifier();
            default -> throw new IllegalArgumentException("unknown channel " + channel); // forgot "sms"
        };
        ...
```

```text
SMS to +44 7700 900123: Your order shipped
Billing report crashed: unknown channel sms
```

It compiled. It passed every test that only checked email. It crashed the first time finance ran the report.

### Fixed: a factory method

Move the decision into **one** static method, and have callers ask that method instead of calling `new`:

```java
interface Notifier {
    String send(String to, String message);
    int costInCents();

    static Notifier forChannel(String channel) {
        return switch (channel) {
            case "email" -> new EmailNotifier();
            case "sms" -> new SmsNotifier();
            default -> throw new IllegalArgumentException("unknown channel " + channel);
        };
    }
}

Notifier.forChannel("sms").send(to, "Your order shipped");
```

Adding WhatsApp is now a one-line change in one file, and every caller gets it. (Topic 14's Open/Closed principle goes one step further and removes even that `switch` — in Spring you'd register each notifier as a bean and look it up in a map.)

### You already use factory methods

`StaticFactoryMethods.java` shows three JDK factory methods and the things they can do that a constructor can't:

| Factory method | What it does that `new` can't |
|---|---|
| `Integer.valueOf(127)` | **Reuses** a cached object — `valueOf(127) == valueOf(127)` is `true`. (For 128 it's `false`, which is why you compare `Integer`s with `equals`.) |
| `List.of("a", "b")` | **Hides the real class** — you get a JDK-internal immutable list, and the JDK can change which class without breaking you. |
| `LocalDate.of(2026, 2, 30)` | **Has a name and validates** — throws `Invalid date 'FEBRUARY 30'` instead of building a nonsense date. |

Spring's container is, at heart, one giant factory. Its root interface is literally called `BeanFactory` (module 07).

---

## Builder — many parameters, set by name

### Broken: the telescoping constructor

A constructor with many parameters of the same type is a trap. The compiler checks types, not meaning:

```java
UserAccount(String email, String displayName, boolean admin, boolean emailVerified, boolean locked)

// Author meant: emailVerified = true. Position 3 is admin.
new UserAccount("bob@example.com", "Bob", true, false, false);
```

`TelescopingConstructorBroken.java`:

```text
bob.emailVerified = false
bob.admin         = true
A sign-up form just created an administrator. Nothing failed.
```

("Telescoping" is the name for the usual cure-that-isn't: adding more constructors, each with one more parameter, until nobody knows which to call.)

### Fixed: a builder

A builder is a small helper object with one named method per value. You set what you need, then call `build()`:

```java
UserAccount bob = UserAccount.builder("bob@example.com")   // required value: can't be forgotten
        .displayName("Bob")
        .emailVerified(true)                               // named: can't be swapped
        .build();                                          // validates, then creates
```

What `BuilderPattern.java` adds over the constructor:

- **Every value is named** at the call site, so swapping two booleans is impossible.
- **Optional values have safe defaults** — `admin` is `false` unless someone says otherwise.
- **Required values go in `builder(...)`**, so forgetting one is a compile error, not a `null`.
- **`build()` validates once** — `builder("not-an-email").build()` throws before any object exists.
- **The result is immutable** (topic 13): `UserAccount` has a `private` constructor and only `final` fields; the builder is the only way in.

### You'll use builders constantly

`StringBuilder`, the JDK HTTP client's `HttpRequest.newBuilder()`, `RestClient.builder()` (module 13) and `ResponseEntity.ok().header(...).body(...)` (module 09) are all builders. Many teams generate builders with Lombok's `@Builder` instead of writing them by hand — same pattern, less typing.

---

## The other two creational patterns

The GoF list has two more. You'll recognise them more than write them:

- **Abstract Factory** — a factory for a *family* of related objects that must match (a `PostgresDialect` that creates Postgres-flavoured connection, query, and type-mapper objects together). In application code it's rare; in frameworks (JDBC drivers, Hibernate dialects) it's everywhere.
- **Prototype** — create new objects by copying an existing one instead of calling a constructor. Java's `clone()` was the old way and has well-known problems (topic 10); today you copy explicitly (a copy constructor, or a record's `with`-style method). Spring borrows the name for its *prototype scope*: a new bean instance every time one is requested (module 07, topic 06).

---

## When to use them / when not to

| Pattern | Use it when | Don't use it when |
|---|---|---|
| Singleton | Never by hand in Spring code — let the container make beans singletons. Outside Spring: a genuinely process-wide resource, written as an `enum`. | You just want "one shared object" in an app that has a DI container. |
| Factory method | The class to create depends on input or config, and more than one caller needs the decision. | There's one implementation and one caller — `new` is clearer. |
| Builder | 4+ parameters, especially several of the same type or many optional ones. | 2–3 required parameters of different types — a constructor or a record is simpler. |

Patterns cost code. A builder for a two-field class is noise; a factory with one product is ceremony. Reach for a pattern when you can name the bug it prevents in *this* code.

---

## Common pitfalls

- **Lazy singleton without thread safety.** `if (instance == null) instance = new X();` creates duplicates under concurrency. Use an `enum` (or a holder class).
- **Singletons holding mutable state.** One shared instance + fields that change = every thread sees everyone else's changes. Keep singletons (and Spring beans) stateless, or make the state thread-safe.
- **A builder that doesn't validate.** If `build()` just copies fields, you've added a class and kept every bug. Check required values and invariants in `build()`.
- **A factory that returns `null` for unknown input.** The caller gets a `NullPointerException` somewhere far away. Throw `IllegalArgumentException` with the bad value in the message.

---

## Code examples

Read and run in this order — each `Broken` file is followed by its fix:

1. `SingletonBroken.java` — 10 threads race the lazy `getInstance()` and create 10 "singletons".
2. `SingletonSafe.java` — the same race against an `enum` singleton; asserts exactly one instance.
3. `NotificationSwitchBroken.java` — the "which class?" `switch` copied into two callers; one forgets SMS and crashes.
4. `NotificationFactory.java` — the decision moved into `Notifier.forChannel(...)`; both callers work.
5. `StaticFactoryMethods.java` — JDK factory methods: `Integer.valueOf` caching, `List.of` hiding its class, `LocalDate.of` validating.
6. `TelescopingConstructorBroken.java` — two swapped booleans turn a new user into an admin.
7. `BuilderPattern.java` — the same account built with named setters, defaults, required values, and validation in `build()`.

Run any of them from the repo root:

```bash
java 02-oops-and-design-principles/CODE_EXAMPLES/15-creational-patterns/SingletonBroken.java
```

---

## Try this yourself

1. In `SingletonBroken.java`, remove the `pause(50)` from the constructor and run it several times. How many instances now? Does that make the code correct, or just the bug harder to see?
2. In `NotificationFactory.java`, add a `WhatsAppNotifier` costing 2 cents. Count the files and methods you had to change. Do the same in `NotificationSwitchBroken.java`.
3. In `BuilderPattern.java`, make `displayName` required: move it into `builder(...)` as a second parameter. What happens to every call site that forgot it?

## Self-check

1. Two threads call `getInstance()` on a lazy singleton at the same time. Walk through, step by step, how both end up creating an instance. Why does an `enum` singleton not have this problem?
2. Give two things `Integer.valueOf(5)` can do that `new Integer(5)` can't, and explain why `Integer.valueOf(128) == Integer.valueOf(128)` is `false`.
3. A colleague's constructor takes `(String, String, boolean, boolean, int, int)`. What bug is waiting to happen, and what would `build()` in a builder add beyond named parameters?
4. Spring beans are singletons by default, yet Spring code almost never contains a singleton class. What does the container do instead, and why is that easier to test?
