# Assertions: Built-in, Then AssertJ

An assertion is the line in a test that decides pass or fail. This topic covers JUnit's built-in assertions — which you'll see everywhere — and then AssertJ, the fluent assertion library that reads like English and gives far better failure messages.

---

## The problem this solves

A test without an assertion proves nothing: it runs your code and throws the answer away. The assertion is where you say "and the answer had better be *this*." Get good at assertions and your tests become precise; get sloppy and they pass even when the code is wrong.

There are two styles in the Java world, and you'll meet both:

- **JUnit's built-in assertions** (`assertEquals`, `assertTrue`, ...) — always available, no extra dependency.
- **AssertJ** (`assertThat(x).isEqualTo(y)`) — an extra library, but it reads better and its failure messages tell you *much* more. It's what `spring-boot-starter-test` bundles, so it's the de facto standard on Spring projects.

## How it works

### JUnit's built-in assertions

All static methods on `org.junit.jupiter.api.Assertions`:

```java
assertEquals(3500, order.totalCents());   // expected first, actual second
assertTrue(order.totalCents() > 0);
assertFalse(order.isEmpty());
assertNull(maybeNull);
assertNotNull(order.items());
assertThrows(IllegalArgumentException.class, () -> ...);   // covered in topic 05
```

One genuinely useful one is `assertAll`. Normally the first failing assertion stops the test, so if three things are wrong you only see the first. `assertAll` runs every check and reports all the failures together:

```java
assertAll(
    () -> assertEquals("BOOK", item.sku()),
    () -> assertEquals(2, item.quantity()),
    () -> assertEquals(3000, item.lineTotalCents())
);
```

These are fine. The friction shows up with anything richer than two scalar values — collections, objects, "contains", "starts with". That's where AssertJ wins.

### AssertJ: fluent assertions

One static import — `org.assertj.core.api.Assertions.assertThat` — and every assertion starts with `assertThat(theThingYouHave)` and then reads left to right:

```java
assertThat(order.totalCents()).isEqualTo(3500);
assertThat(order.isEmpty()).isFalse();
assertThat(order.items()).hasSize(2);
assertThat(order.totalCents()).isPositive();
```

The win is collections and objects. Suppose you want "the order has two items, and their SKUs are BOOK then PEN." With JUnit that's a loop and several `assertEquals`. With AssertJ:

```java
assertThat(order.items())
        .hasSize(2)
        .extracting(OrderItem::sku)
        .containsExactly("BOOK", "PEN");
```

`extracting` pulls one field out of every element; `containsExactly` checks the values *and order*. Other everyday ones: `contains`, `containsExactlyInAnyOrder`, `isEmpty`, `startsWith`, `hasSize`, `allMatch`.

### Why people switch: the failure message

This is the real reason teams adopt AssertJ. Say a list should have 2 items but has 3.

JUnit's `assertEquals(2, order.items().size())`:

```
expected: <2> but was: <3>
```

You know a number is wrong, but not *which item* is unexpected.

AssertJ's `assertThat(order.items()).hasSize(2)`:

```
Expected size: 2 but was: 3 in:
  [OrderItem[sku=BOOK, quantity=2, unitPriceCents=1500],
   OrderItem[sku=PEN, quantity=5, unitPriceCents=100],
   OrderItem[sku=MUG, quantity=1, unitPriceCents=900]]
```

It prints the actual contents, so you immediately see the stray `MUG`. Better messages mean less time debugging the test itself.

## When to use which

- **Either is fine for "two values are equal."** Use whichever the codebase already uses; consistency beats preference.
- **Reach for AssertJ** for collections, strings (`contains`, `startsWith`), objects with several fields, or anywhere a good failure message will save you time.
- **This module uses AssertJ from here on**, because it's what you'll see in Spring projects (module 08) and the messages teach you more when a test fails.

## Common pitfalls

- **`assertEquals` argument order.** Expected first, actual second. Swap them and the test still passes/fails correctly, but the failure message is a confusing lie ("expected 3 but was 2" when you meant the opposite).
- **`assertThat` from the wrong package.** AssertJ is `org.assertj.core.api.Assertions.assertThat`. JUnit and Hamcrest also have an `assertThat`. Import the AssertJ one or the fluent methods won't be there.
- **Asserting on `toString()` instead of the object.** `assertEquals("BOOK x2", item.toString())` breaks the moment you tweak `toString`. Assert on the actual fields (`item.sku()`, `item.quantity()`), not on a formatted string.
- **Comparing floating-point with `assertEquals(0.3, a + b)`.** `0.1 + 0.2 != 0.3` in floating point. Use a tolerance (`assertEquals(0.3, x, 0.0001)`) or, better, avoid floating-point money entirely — these examples use whole cents (`long`) for that reason.

---

## Try this yourself

1. In `AssertJAssertionsTest`, add a third item (`new OrderItem("MUG", 1, 900)`) but leave the `hasSize(2)` assertion. Run `mvn test` and read AssertJ's failure message — notice it prints every item.
2. Rewrite the `assertAll` block from `JUnitAssertionsTest` using a single AssertJ chain on the `item`. Which reads better to you?
3. Add an assertion that the SKUs contain `"BOOK"` but in *any* order, using `containsExactlyInAnyOrder`.

## Self-check

1. What does `assertAll` do that a sequence of plain `assertEquals` calls does not?
2. Give one concrete case where AssertJ's failure message tells you more than JUnit's, and say what extra information you get.
3. Why do these examples store money as `long` cents instead of `double` dollars?

---

## Code examples

In reading order:

- `assertions-demo/src/test/java/com/example/JUnitAssertionsTest.java` — the built-in assertions, including `assertAll`.
- `assertions-demo/src/test/java/com/example/AssertJAssertionsTest.java` — the same checks fluently, plus `extracting`/`containsExactly` on a collection.

Run with `mvn test`.
