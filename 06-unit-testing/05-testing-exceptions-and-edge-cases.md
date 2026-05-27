# Testing Exceptions and Edge Cases

Most bugs don't live in the happy path — they live at the edges (zero, the maximum, empty, null) and in how code reacts to bad input. This topic shows how to assert that a method throws the right exception, and how to think about which edge cases are worth a test.

---

## The problem this solves

It's tempting to write one test for the case you had in mind ("20% off 1000 is 800") and call it done. But the code that computes that correctly might still:

- divide by zero, overflow, or return a nonsense value at `0%` or `100%`,
- silently accept `150%` or a negative price and produce garbage,
- throw the *wrong* exception, or no exception, when it should reject input.

A method is only as trustworthy as its behavior at the edges. And you can't assert "it throws" with `assertEquals` — you need a different tool, because the whole point is that the call *doesn't return normally*.

## How it works

### Asserting an exception — the wrong way first

The naive approach is a try/catch:

```java
@Test
void rejectsBadPercent() {
    try {
        calc.applyPercentOff(1000, 150);
        fail("should have thrown");   // if we get here, no exception was thrown
    } catch (IllegalArgumentException expected) {
        // ok
    }
}
```

This works but is clumsy and easy to get subtly wrong (forget the `fail()` and the test passes even when nothing throws). Both JUnit and AssertJ give you something cleaner.

### JUnit: `assertThrows`

```java
IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> calc.applyPercentOff(1000, 150));

assertThat(ex.getMessage()).contains("between 0 and 100");
```

You pass the expected exception type and a lambda that runs the code. `assertThrows` fails the test if the code *doesn't* throw, or throws a different type. It *returns* the caught exception, so you can then assert on its message or fields.

### AssertJ: `assertThatThrownBy`

```java
assertThatThrownBy(() -> calc.applyPercentOff(1000, -5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 0 and 100");
```

Same idea, one fluent chain — type and message together. Use whichever your codebase favors; this module leans AssertJ for the readability.

### Don't just check the type — check the message (sometimes)

`isInstanceOf(IllegalArgumentException.class)` proves *something* was rejected, but a class that throws `IllegalArgumentException` for *every* bad input would pass that check even when it rejects the wrong thing. Asserting on part of the message (`hasMessageContaining("between 0 and 100")`) confirms it rejected for the *right reason*. Don't assert the whole message word-for-word, though — that breaks every time someone rewords it. Match the meaningful fragment.

## Thinking about edge cases

For any method, ask: what are the *boundaries* and the *special values*?

- **Numbers:** zero, negative, the maximum allowed, one past the maximum, overflow. Here: `0%` (no change), `100%` (free), `101%` and `-1%` (rejected).
- **Collections/strings:** empty, one element, null. ("Empty cart total is 0", "null input throws".)
- **Ranges:** test *just inside* and *just outside* each end. If valid is `0..100`, test `0`, `100` (inside) and `-1`, `101` (outside). Off-by-one bugs hide exactly here.

You don't need every possible value — that's infinite. You need a *representative* from each class of behavior: one normal value, each boundary, and each kind of rejection. The demo does exactly this: one ordinary discount, the `0`/`100` boundaries, and the two rejection paths.

When you find yourself writing many near-identical edge-case tests that differ only in the input and expected output, that's the signal for a parameterized test — the next topic.

## Common pitfalls

- **Catching the exception instead of asserting it.** A bare `try { ... } catch (Exception e) {}` with no `fail()` makes the test pass whether or not the exception is thrown. Use `assertThrows`/`assertThatThrownBy`.
- **Asserting the exception type but never the reason.** `isInstanceOf(IllegalArgumentException.class)` alone can pass for the wrong rejection. Add a message check when the *reason* matters.
- **Wrapping too much code in the lambda.** Put only the call that should throw inside `assertThrows`. If you include setup that could also throw, you can't tell which line threw.
- **Only testing the happy path.** The case you had in mind is the one least likely to be broken. The edges are where the bugs are; if you only test the obvious case, your test gives false confidence.
- **Word-for-word message assertions.** `hasMessage("percentOff must be between 0 and 100, got 150")` breaks on any reword. Match a stable fragment with `hasMessageContaining`.

---

## Try this yourself

1. Add tests for the boundaries `applyPercentOff(1000, 1)` and `applyPercentOff(1000, 99)`. Then add `101` and confirm it throws. You've now pinned both sides of the valid range.
2. Change the validation in `DiscountCalculator` from `> 100` to `>= 100` (a deliberate off-by-one bug). Which of your tests catches it? If none do, you're missing a boundary test at exactly `100`.
3. Add a `MAX` overflow case: what does `applyPercentOff(Long.MAX_VALUE, 50)` do? Write a test for whatever you decide the correct behavior is.

## Self-check

1. Why can't you assert "this throws an exception" with `assertEquals`?
2. When is checking the exception's *message* worth it, and when is checking only its *type* enough?
3. You're testing a method valid for inputs `1..10`. Which specific input values would you test, and why those?

---

## Code examples

In reading order:

- `exceptions-demo/src/test/java/com/example/DiscountCalculatorTest.java` — happy path, the `0%`/`100%` edges, and both ways (`assertThrows` and `assertThatThrownBy`) of asserting rejected input.

Run with `mvn test`.
