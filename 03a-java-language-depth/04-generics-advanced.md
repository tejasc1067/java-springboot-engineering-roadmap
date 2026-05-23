# 04 — Generics Advanced (Wildcards, Bounds, PECS)

Topic 03 covered the basics — `Box<T>`, generic methods, type erasure. This one covers what trips most people up: **wildcards** (`?`), **bounded types** (`T extends Number`), and the **PECS** rule (Producer Extends, Consumer Super) that tells you when to use which.

You need this the moment you write a method that has to accept "a list of Numbers or any subtype" — which, in real code, is very often.

---

## The problem this solves

Suppose you write:

```java
static double sumAll(List<Number> nums) {
    double total = 0;
    for (Number n : nums) total += n.doubleValue();
    return total;
}
```

Then you call it:

```java
List<Integer> ints = List.of(1, 2, 3);
sumAll(ints);                          // ← compile error
```

Wait — `Integer` extends `Number`, so why doesn't this work? Because **`List<Integer>` is NOT a subtype of `List<Number>`**. Generic types are *invariant* by default. The fix is wildcards.

---

## Why generics are invariant

If `List<Integer>` were a subtype of `List<Number>`, this would compile:

```java
List<Integer> ints = new ArrayList<>();
List<Number> nums = ints;              // (hypothetical — actually illegal)
nums.add(3.14);                        // adding a Double through the Number view
Integer i = ints.get(0);               // boom: it's actually a Double
```

To prevent that disaster, Java makes generic types invariant: `List<Integer>` and `List<Number>` are unrelated types, even though `Integer extends Number`. Wildcards give you back a *safe* way to relax this restriction.

---

## Upper-bound wildcard: `? extends T`

"A list of `T` or any subtype." You can **read** from it, but you can't **write** to it (except `null`).

```java
static double sumAll(List<? extends Number> nums) {
    double total = 0;
    for (Number n : nums) total += n.doubleValue();
    return total;
}

sumAll(List.of(1, 2, 3));              // List<Integer> ✓
sumAll(List.of(1.0, 2.0));             // List<Double>  ✓
sumAll(List.of(1L, 2L, 3L));           // List<Long>    ✓
```

The compiler knows every element is *some* `Number`, so reading is safe (`Number n = nums.get(0)`). But it can't let you `nums.add(...)` — the actual list might be `List<Integer>`, in which case adding a `Double` would corrupt it.

**Use case: producers.** When a method receives data from the parameter and only reads from it.

---

## Lower-bound wildcard: `? super T`

"A list of `T` or any supertype." You can **write** `T` (or any subtype) into it, but you can't safely **read** anything more specific than `Object`.

```java
static void addIntegers(List<? super Integer> dest) {
    dest.add(1);
    dest.add(2);
    dest.add(3);
}

List<Integer> ints     = new ArrayList<>();
List<Number>  nums     = new ArrayList<>();
List<Object>  objects  = new ArrayList<>();

addIntegers(ints);     // ✓
addIntegers(nums);     // ✓
addIntegers(objects);  // ✓
```

The compiler knows the list holds at least `Integer` (or some ancestor of it), so adding `Integer` is safe. But reading? You'd only get an `Object` — too lossy to be useful.

**Use case: consumers.** When a method puts data into the parameter.

---

## PECS: Producer Extends, Consumer Super

That's the rule, and it's the one piece of mnemonics worth memorizing.

- If the parameter **produces** data for your method to read → `? extends T`.
- If the parameter **consumes** data your method writes → `? super T`.
- If it does both → just use `T` (no wildcard).

The canonical example is `Collections.copy`:

```java
public static <T> void copy(List<? super T> dest, List<? extends T> src) {
    //                       ^^^^^^^^^^^^^^^^      ^^^^^^^^^^^^^^^^^
    //                       consumer (writes T)    producer (reads T)
}
```

Read it once. Internalize it. PECS appears everywhere in standard library signatures — `Stream`, `Comparator`, `Function`, etc.

---

## Unbounded wildcard: `?`

`List<?>` means "list of *something*, but I don't care what." You can read elements only as `Object`, and you can't add anything except `null`.

```java
static void printSize(List<?> list) {
    System.out.println("size: " + list.size());
}
```

When is this better than `List<Object>`? Because `List<Object>` is invariant — only `List<Object>` satisfies it. `List<?>` accepts `List<String>`, `List<Integer>`, anything.

Default to `List<?>` when you really mean "any list and I won't add to it." Otherwise pick a bound.

---

## Bounded type parameters: `<T extends Number>`

Sometimes you want a *type parameter* with a constraint, not just a wildcard. Use `<T extends Bound>` in the method/class declaration.

```java
static <T extends Number> double sumStrict(List<T> nums) {
    double total = 0;
    for (T n : nums) total += n.doubleValue();
    return total;
}
```

Difference from `? extends Number`:

- `List<? extends Number>` — caller can pass any `List<Subtype>`, but **inside the method, all elements are just "some unknown subtype of Number."** You can't refer to the concrete type.
- `List<T> where T extends Number` — `T` is a *named* type. You can use it in the signature (`T first = list.get(0)`), return it, store it.

Use a named type parameter when the method's signature talks about the type (e.g. returning a `T`). Use `? extends` when you only read elements.

You can have multiple bounds with `&`: `<T extends Number & Comparable<T>>` — must extend `Number` *and* implement `Comparable<T>`.

---

## Type erasure consequences here

Topic 03 showed that the JVM erases type parameters. With wildcards, that means:

- You can't write `new T[10]`, `new ArrayList<T>[10]`, etc.
- `if (list instanceof List<String>)` won't compile — only `List<?>` works as an `instanceof` check.
- The compiler inserts casts when you call `list.get(0)` in generic code. Most of the time you never see this; occasionally a runtime `ClassCastException` from inserted casts is your only clue.

This is why generics are sometimes called "compile-time type checking only."

---

## Common pitfalls

- **`List<Object>` as a "universal" type.** It only matches `List<Object>` exactly, not `List<String>`. Use `List<?>` if you mean "any list."
- **Trying to `add` into `List<? extends T>`.** Compiler refuses. The wildcard means you don't know the exact type — adding could corrupt it. Use `? super T` for that.
- **PECS reversed.** Easy to swap. Re-derive it: if your method *reads* the parameter, the parameter *produces* — `extends`. If it *writes*, the parameter *consumes* — `super`.
- **Wildcard in a return type.** `List<? extends T> doStuff()` — the caller now has the same write restriction. Usually you mean to return a concrete `List<T>` instead.
- **Capture conversion confusion.** Sometimes you'll get errors like "capture of ? extends Number cannot be converted." The fix is usually a helper method with a real type parameter (`<T extends Number> void help(List<T> list)`).

---

## Code examples

1. `InvarianceProof.java` — `List<Integer>` is not a `List<Number>`; compile error explained.
2. `UpperBoundProducer.java` — `? extends Number` for reading.
3. `LowerBoundConsumer.java` — `? super Integer` for writing.
4. `PecsCopy.java` — the canonical `copy(dest, src)` with both wildcards.
5. `BoundedTypeParameter.java` — `<T extends Number>` named parameter, used in return type.
6. `UnboundedWildcard.java` — `List<?>` for "any list, read-only."

---

## Try this yourself

1. In `UpperBoundProducer.java`, try to add an `Integer` to the `List<? extends Number>` parameter. Read the compile error — it's PECS biting you correctly.
2. In `LowerBoundConsumer.java`, try to read an element from `List<? super Integer>` and assign to `Integer`. Read the compile error. Now assign to `Object` — works.
3. Convert `PecsCopy.java` to use no wildcards (`List<T> dest, List<T> src`). Find at least one realistic caller that no longer compiles. That call site is exactly why PECS exists.

---

## Self-check

1. Why is `List<Integer>` not a subtype of `List<Number>`? What concrete bug would assigning one to the other allow?
2. State the PECS rule. Apply it to a method `Collections.addAll(Collection<? super T> c, T... elements)` — which role does the parameter play?
3. When would you prefer a bounded type parameter `<T extends Number>` over `? extends Number`?
