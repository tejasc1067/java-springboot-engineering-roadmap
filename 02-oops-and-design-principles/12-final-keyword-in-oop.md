# 12 — The `final` Keyword

`final` means "this can't change after it's set." Java applies the keyword in three places — variables, methods, and classes — each with a different effect.

Used well, `final` makes your design intentions clear and prevents whole classes of bugs. Used badly, it's just noise. Knowing when to apply it is a real skill.

---

## `final` on a variable

The variable can be assigned exactly once.

```java
final int MAX_RETRIES = 3;
MAX_RETRIES = 5;     // compile error
```

Common uses:

- **Constants:** `static final int MAX_PAGE_SIZE = 100;`
- **Method parameters:** marks "this parameter won't be reassigned inside the method." Mostly a documentation hint.
- **Local variables:** rarely necessary, sometimes used in lambdas (see below).
- **Instance fields:** the most important use — see the immutability discussion below.

---

## `final` on a reference: a critical subtlety

A `final` reference can't be reassigned, but the **object it points to can still be mutated**:

```java
final List<String> names = new ArrayList<>();
names.add("Alice");    // ← this is fine
names = new ArrayList<>();    // ← this is the error
```

`final` locks the *variable*, not the *value behind it*. This is one of the most-asked Java interview questions, and the source of many "wait, why is this still mutable?" surprises.

For true immutability, you need a final field **AND** an immutable type (or immutable wrapper) inside it:

```java
final List<String> names = List.of("Alice", "Bob");   // List.of returns an unmodifiable list
names.add("Carol");                                    // throws at runtime
```

This shows up again in topic 13 (immutability), where it's the heart of the topic.

---

## `final` instance fields: the strongest pattern

When all instance fields are `final`, an object's state is locked at construction time:

```java
class Point {
    private final int x;
    private final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    int getX() { return x; }
    int getY() { return y; }
}
```

No setter exists. Once a `Point` is built, its coordinates can never change. The class is immutable (assuming we don't expose mutable references — see topic 13).

This is the **default** you should reach for. Mutable state should be a deliberate choice, not the unthinking default.

---

## `final` on a method

Marks the method as **non-overridable** by subclasses.

```java
class Account {
    final void close() {           // subclasses cannot override
        System.out.println("closing account");
    }
}

class SavingsAccount extends Account {
    @Override
    void close() { ... }    // compile error — close() is final
}
```

Use cases:

- The method's logic is critical and a subclass override would be a bug (e.g. security checks).
- The method is part of a template-method workflow (topic 08) and changing its order would break the algorithm.
- You're documenting "this is final by design" so future readers don't try to subclass it.

In day-to-day code, `final` methods are uncommon. Use them when you have a real reason.

---

## `final` on a class

The class **cannot be extended**.

```java
final class String {        // (this is exactly what java.lang.String is)
    // ...
}
```

Use cases:

- **Value types** that need consistent behavior — `String`, `Integer`, `LocalDate`, etc. all `final`.
- **Security boundaries** where a malicious subclass could break invariants.
- **Public API stability** — if you don't intend the class to be extended, marking it `final` communicates that and lets you change internals freely.

A class with all-`final` fields plus the class itself `final` is the gold standard of "this is a value object" — the next topic (13) covers this in depth.

---

## "Effectively final" (lambdas and inner classes)

Lambdas and anonymous classes can only reference *local* variables from the enclosing scope if those variables are `final` or **effectively final** (never reassigned after initialization).

```java
String greeting = "Hello";
Runnable r = () -> System.out.println(greeting + " world");
// greeting = "Hi";    // ← would break the lambda above

r.run();
```

If you uncomment the reassignment, the compiler will complain that `greeting` is no longer "effectively final" and the lambda can't use it. The variable doesn't have to be declared `final`, but if you reassign it, the compiler will tell you.

This is why local variables in lambdas don't need to be explicitly `final` — the compiler infers it.

---

## When to use `final` — guidelines

- **Instance fields: default to `final`.** Make them mutable only when there's a real reason (state that changes over the object's life).
- **Method parameters: rarely worth the noise.** Some styles use it everywhere; modern Java conventions usually don't.
- **Local variables: only if you're documenting an intent.** "Effectively final" already protects you for lambdas.
- **Methods: when override would be a bug** — security, ordering, contract enforcement.
- **Classes: when extension wasn't designed for** — most "leaf" classes in your app should probably be `final`.

The general principle: `final` says "I designed this to not change. Don't change it." Use it to express design decisions, not as decoration.

---

## Common pitfalls

- **Thinking `final` makes an object immutable.** It locks the variable, not the contents. See `FinalReferenceStillMutable.java`.
- **Marking everything `final` reflexively.** `final` on local variables and parameters is mostly noise unless your team has a strong style for it.
- **`final` on a single field while the rest are mutable.** Inconsistent — either the class is immutable or it isn't. Don't half-do it.
- **Forgetting that `final` classes can't be mocked easily.** If you `final` a class, popular test libraries like Mockito need extra setup to mock it. Worth knowing.

---

## Code examples

1. `FinalVariable.java` — local, parameter, and instance-field uses.
2. `FinalReferenceStillMutable.java` — the classic surprise.
3. `FinalMethodCannotOverride.java` — what the compiler says when you try.
4. `FinalClassCannotExtend.java` — same, for classes.
5. `EffectivelyFinalInLambda.java` — lambdas captured locals.

---

## Try this yourself

1. In `FinalReferenceStillMutable.java`, change the field to hold an `int` instead of a `List`. Now `final` actually does prevent change. Why?
2. In `FinalClassCannotExtend.java`, remove `final` from the class. Subclass it. Compare.
3. In `EffectivelyFinalInLambda.java`, reassign the captured variable after the lambda. Read the compile error.

---

## Self-check

1. `final List<String> names = new ArrayList<>(); names.add("X");` — does this compile? Why?
2. When would you mark a method `final`? Give one realistic scenario.
3. Why are `String`, `Integer`, and `LocalDate` all declared `final`?
