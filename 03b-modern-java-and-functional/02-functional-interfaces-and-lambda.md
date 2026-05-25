# 02 — Functional Interfaces and Lambdas

A **lambda** is a way to write a piece of behavior — a function — inline, as a value. A **functional interface** is the type Java uses to hold that value. The two go together: every lambda you write has a functional-interface target type the compiler picks based on context.

This topic covers the five core functional interfaces in `java.util.function` (Predicate, Function, Consumer, Supplier, BiFunction), method references, and the rules that govern lambda scoping.

---

## The problem this solves

Before Java 8, "pass a piece of behavior to a method" meant anonymous inner classes:

```java
button.addActionListener(new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println("clicked");
    }
});
```

Six lines of ceremony for one line of intent. With a lambda:

```java
button.addActionListener(e -> System.out.println("clicked"));
```

The compiler infers everything: the target type (`ActionListener`), the method being implemented (`actionPerformed`), the parameter type (`ActionEvent`).

---

## Functional interfaces — the contract

A functional interface is an interface with **exactly one abstract method**. That's it. `default` methods and `static` methods don't count.

```java
@FunctionalInterface
interface Greeting {
    void sayHello(String name);             // the SAM (single abstract method)
    default void sayHelloTwice(String n) { sayHello(n); sayHello(n); }
}
```

`@FunctionalInterface` is optional but recommended — it tells the compiler "verify this stays single-method." If someone adds a second abstract method later, compile fails.

The `Runnable` and `Comparator<T>` interfaces have existed since Java 1.0 and 1.2 respectively, and they qualify as functional interfaces purely because they happen to have one abstract method. Lambdas can target them retroactively — that's why `new Thread(() -> doWork()).start()` works.

---

## Lambda syntax

```java
() -> 42                            // no params, returns int
x -> x * 2                          // one param, no parens needed
(x, y) -> x + y                     // multiple params, parens required
(int x, int y) -> x + y             // explicit types (rarely needed)
x -> { int y = x * 2; return y; }   // block body needs return
```

Rules:

- **One parameter**, type inferred: parens optional.
- **Zero or many parameters**: parens required.
- **Block body** in `{ }`: requires explicit `return` if non-void.
- **Expression body**: the expression is the return value.

The compiler infers types from the target functional interface. `Function<String, Integer> f = s -> s.length();` — the compiler knows `s` is `String` and the return is `Integer`.

---

## The five interfaces you'll use 90% of the time

Located in `java.util.function`.

| Interface | Abstract method | Read as |
|-----------|----------------|---------|
| `Predicate<T>` | `boolean test(T t)` | "is this true of `t`?" |
| `Function<T, R>` | `R apply(T t)` | "turn `T` into `R`" |
| `Consumer<T>` | `void accept(T t)` | "do something with `t`" |
| `Supplier<T>` | `T get()` | "give me a `T`" |
| `BiFunction<T, U, R>` | `R apply(T t, U u)` | "combine `T` and `U` into `R`" |

```java
Predicate<String> isLong   = s -> s.length() > 10;
Function<String, Integer> len = String::length;
Consumer<String> log       = System.out::println;
Supplier<String> nowIso    = () -> Instant.now().toString();
BiFunction<Integer, Integer, Integer> add = Integer::sum;
```

There are also primitive specializations (`IntPredicate`, `LongFunction<R>`, `ToIntFunction<T>`, etc.) that avoid boxing — use them in hot paths.

---

## Method references — the four flavors

A method reference is shorthand for a lambda that does nothing but call an existing method.

| Pattern | Form | Equivalent lambda |
|---------|------|------------------|
| **Static method** | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| **Instance method of a specific object** | `System.out::println` | `x -> System.out.println(x)` |
| **Instance method of an arbitrary object** | `String::length` | `s -> s.length()` |
| **Constructor** | `ArrayList::new` | `() -> new ArrayList<>()` |

The third form (arbitrary instance method) trips people up. `String::length` *isn't* a closure capturing a specific string — it's a `Function<String, Integer>` where the parameter becomes the receiver.

---

## Variable capture rules

A lambda can reference local variables from its enclosing scope **as long as they're effectively final** — declared `final` or never reassigned.

```java
String prefix = "[INFO] ";                 // effectively final
Consumer<String> log = msg -> System.out.println(prefix + msg);   // OK

int counter = 0;
Runnable bad = () -> { counter++; };       // compile error: not effectively final
```

Why: lambdas may execute on another thread or at an unknown later time. Capturing a value (not a reference to a stack slot) is the only safe option. To "mutate captured state," capture a reference to a holder: `AtomicInteger counter = new AtomicInteger(); Runnable r = counter::incrementAndGet;`.

Instance fields and `static` fields **are** mutable from a lambda — the lambda captures `this` (which is effectively final) and goes through it.

---

## Function composition

`Function` and `Predicate` come with composition helpers:

```java
Function<String, String> trim = String::trim;
Function<String, String> upper = String::toUpperCase;
Function<String, String> trimThenUpper = trim.andThen(upper);    // apply trim, then upper

Predicate<String> notEmpty = s -> !s.isEmpty();
Predicate<String> startsA  = s -> s.startsWith("A");
Predicate<String> both     = notEmpty.and(startsA);              // both must be true
Predicate<String> either   = notEmpty.or(startsA);               // either may be true
Predicate<String> notA     = startsA.negate();
```

These let you build small named pieces and compose them, instead of one giant lambda.

---

## Common pitfalls

- **Mutating shared state from a lambda.** Inside a stream `forEach`, every parallel worker hits your shared mutable state. Race condition. Make the lambda pure, or use a thread-safe accumulator.
- **Capturing a reference to a mutable object thinking it'll "freeze."** Effectively-final means the *reference* can't change. The object pointed to can. `List<String> items = new ArrayList<>(); Runnable r = items::clear;` — `items` is effectively final; the lambda still wipes it.
- **Method reference to an overloaded method.** Compiler can't always pick the right overload. Fall back to an explicit lambda.
- **Performance assumption that lambdas are free.** They aren't — they may allocate a class instance on each call site (cached when stateless). Profile before assuming.
- **Throwing checked exceptions from a stream pipeline.** Built-in functional interfaces don't declare checked exceptions. You'll need `try/catch` inside, or a custom interface that declares `throws`.

---

## Code examples

1. `LambdaSyntax.java` — the five syntactic forms in one file.
2. `FiveCoreInterfaces.java` — `Predicate`, `Function`, `Consumer`, `Supplier`, `BiFunction` each with a realistic usage.
3. `MethodReferenceFlavors.java` — the four kinds of method reference, with the equivalent lambda beside each.
4. `EffectivelyFinalCapture.java` — what compiles, what doesn't, and the `AtomicInteger` workaround.
5. `FunctionComposition.java` — `andThen`, `compose`, `Predicate.and/or/negate` building pipelines from parts.

---

## Try this yourself

1. In `LambdaSyntax.java`, write a lambda that takes two ints and returns their max. Try it with and without `return`.
2. In `EffectivelyFinalCapture.java`, replace the `AtomicInteger` workaround with a single-element `int[1]`. It compiles. Why?
3. In `FunctionComposition.java`, build a `Predicate<String>` that matches strings that are not empty AND don't start with "A" AND have length > 3. Do it with composition.

---

## Self-check

1. What single rule makes an interface a "functional interface"? What does `@FunctionalInterface` add on top of that?
2. You have `int total = 0; users.forEach(u -> total += u.getAge());`. The compiler refuses. Explain why, and give the idiomatic fix.
3. `String::length` and `s -> s.length()` look the same. Which one is shorter to read, and what's the technical name for the form on the left?
