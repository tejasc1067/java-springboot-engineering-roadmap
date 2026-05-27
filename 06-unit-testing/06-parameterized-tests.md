# Parameterized Tests

When you find yourself writing the same test five times with only the numbers changed, a parameterized test lets you write it once and feed it a table of inputs. JUnit runs it once per row and reports each as its own test.

---

## The problem this solves

In topic 05 you tested a discount at `0%`, `20%`, and `100%`. Written out, that's three almost-identical methods:

```java
@Test void zero()    { assertThat(calc.applyPercentOff(1000, 0)).isEqualTo(1000); }
@Test void twenty()  { assertThat(calc.applyPercentOff(1000, 20)).isEqualTo(800); }
@Test void hundred() { assertThat(calc.applyPercentOff(1000, 100)).isEqualTo(0); }
```

The structure is duplicated; only the numbers differ. Add a fourth case and you copy-paste a fourth method. The logic-vs-data is tangled, and a typo in the copy-paste is easy to miss.

A parameterized test separates the *check* (written once) from the *data* (a table). Adding a case becomes adding a row.

## How it works

Replace `@Test` with `@ParameterizedTest` and add a *source* annotation that supplies the data. The method takes parameters; JUnit calls it once per data item.

### `@ValueSource` — one simple parameter

```java
@ParameterizedTest
@ValueSource(ints = {0, 1, 50, 99, 100})
void everyPercentInRangeIsAccepted(int percent) {
    assertThat(calc.applyPercentOff(1000, percent)).isBetween(0L, 1000L);
}
```

Runs five times, once per int. Good for "this whole set of values should all behave the same way."

### `@CsvSource` — several parameters per case

```java
@ParameterizedTest
@CsvSource({
        "1000,   0, 1000",
        "1000,  20,  800",
        "1000, 100,    0"
})
void discountIsComputedCorrectly(long price, int percent, long expected) {
    assertThat(calc.applyPercentOff(price, percent)).isEqualTo(expected);
}
```

Each row is one case; the comma-separated values map to the method's parameters in order. This is the most common form — a little table of input→expected.

### `@MethodSource` — when CSV isn't enough

CSV only holds simple values (numbers, strings). When you need real objects, or computed inputs, supply a `Stream<Arguments>` from a static method:

```java
static Stream<Arguments> priceScenarios() {
    return Stream.of(
            arguments(2500L, 50, 1250L),
            arguments(100L, 99, 1L),
            arguments(0L, 50, 0L)
    );
}

@ParameterizedTest
@MethodSource("priceScenarios")
void discountFromAMethodSource(long price, int percent, long expected) {
    assertThat(calc.applyPercentOff(price, percent)).isEqualTo(expected);
}
```

### `@EnumSource` — once per enum constant

```java
@ParameterizedTest
@EnumSource(value = OrderStatus.class, names = {"NEW", "PAID"})
void newAndPaidOrdersCanBeCancelled(OrderStatus status) {
    assertThat(status.canBeCancelled()).isTrue();
}
```

Handy for exhaustively covering an enum: one test for the constants that should behave one way, another for the rest.

### What the report looks like

Each input is reported as a separate test. So `@CsvSource` with three rows shows three tests, and if the `100%` row fails you see *that row* named in the output — not just "the test failed." The Surefire test count will be higher than the number of methods, which is exactly what you want: failures point at the specific input.

## When to use it / when not to

- **Use it** when several cases share the same logic and differ only in data: input→output tables, "all these values are valid," boundary sweeps, enum coverage.
- **Don't use it** when the cases need *different* assertions or different setup. Forcing distinct behaviors into one parameterized method with `if`s in the body makes it unreadable — write separate `@Test` methods instead.
- **Keep the data readable.** A `@CsvSource` row should be obvious at a glance. If you need a comment to explain what a row means (like the integer-division case in the demo), add one.

## Common pitfalls

- **Using `@Test` and `@ParameterizedTest` together,** or `@ParameterizedTest` with no source. The first makes JUnit unsure how to run it; the second runs zero times. A parameterized test needs exactly one (or more) source annotations and no `@Test`.
- **Parameter order not matching the CSV columns.** `@CsvSource("1000, 20, 800")` with method `(int percent, long price, long expected)` silently maps `1000`→percent. Keep the column order and parameter order in sync.
- **Putting logic in the test body to handle different rows.** If row 1 needs `isEqualTo` and row 2 needs `isThrownBy`, they're not the same test — split them.
- **`@MethodSource` method not `static`** (in the default test lifecycle) or name misspelled in the annotation string. Either gives a confusing "no tests found / method not found" error.

---

## Try this yourself

1. Add two rows to the `@CsvSource`: `"500, 50, 250"` and `"1, 100, 0"`. Run `mvn test` and watch the test count rise by two.
2. Add a deliberately wrong row (`"1000, 20, 999"`) and read how the failure names the exact row that failed. Then remove it.
3. Convert the boundary tests from topic 05 (`0`, `1`, `99`, `100`, `101`) into a single `@ValueSource` for the valid ones and a separate test asserting `101` throws.

## Self-check

1. What's the advantage of `@CsvSource` over five copy-pasted `@Test` methods, beyond fewer lines?
2. When would you reach for `@MethodSource` instead of `@CsvSource`?
3. You have five cases that share inputs but need *different* assertions. Is a parameterized test the right tool? Why or why not?

---

## Code examples

In reading order:

- `parameterized-demo/src/test/java/com/example/DiscountCalculatorParameterizedTest.java` — `@ValueSource`, `@CsvSource`, `@MethodSource`, and `@EnumSource` over the discount calculator and the `OrderStatus` enum.

Run with `mvn test`.
