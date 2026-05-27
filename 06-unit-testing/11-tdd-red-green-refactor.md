# TDD: Red, Green, Refactor

Test-Driven Development flips the usual order: you write the failing test *first*, then write just enough code to pass it, then clean up. This topic walks through building one small feature that way, so you can feel the rhythm. TDD isn't mandatory, but doing it a few times changes how you think about code — and it guarantees every line you write is covered by a test.

---

## The problem this solves

Writing tests *after* the code has two quiet failure modes. First, you tend to test what you *built* rather than what was *required* — you read your own code and assert it does what it does, bugs included. Second, "I'll add tests later" often becomes "never," because the code already works and tests feel like a chore.

TDD removes both. The test comes first, so it describes the *requirement*, not the implementation. And the code doesn't exist until a test demands it, so coverage isn't something you bolt on afterward — it's a side effect of how the code was written.

## The cycle

```
RED      Write a small test for one behavior. Run it. It fails
         (or doesn't compile) - the feature isn't there yet.
GREEN    Write the simplest code that makes it pass. Not the
         elegant version - the simplest. Run tests: all green.
REFACTOR Now that tests protect you, clean up the code (and the
         test) without changing behavior. Run tests: still green.
```

Then repeat for the next behavior. Small loops — minutes, not hours.

The hardest discipline for beginners is the RED step. **You must see the test fail first.** A test you've never seen fail might be passing for the wrong reason (asserting nothing, testing the wrong thing). The failure is proof the test can actually catch a problem.

## A worked example: shipping fees

The requirement: orders below a threshold pay a flat shipping fee; orders at or above it ship free; negative totals are an error. Let's build `ShippingFeeCalculator.feeFor(orderTotalCents)` test-first.

### Cycle 1 — flat fee for small orders

**Red.** Write the test before the class exists:

```java
@Test
void chargesFlatFeeForSmallOrders() {
    assertThat(new ShippingFeeCalculator().feeFor(1000)).isEqualTo(599);
}
```

This doesn't even compile — there's no `ShippingFeeCalculator`. That's a valid RED. Create the class with the *simplest* thing that compiles and passes:

**Green.**

```java
public class ShippingFeeCalculator {
    public long feeFor(long orderTotalCents) {
        return 599;   // simplest thing that passes the one test we have
    }
}
```

Yes, it always returns 599. That's *correct for every test that exists right now*. TDD says don't write more than the tests demand — the next test will force the real logic. Run it: green.

### Cycle 2 — free shipping for large orders

**Red.**

```java
@Test
void shipsFreeWellAboveTheThreshold() {
    assertThat(new ShippingFeeCalculator().feeFor(9999)).isZero();
}
```

Run it: fails — we always return 599, but expected 0. Good, RED. Now the hardcoded answer no longer works, so we write actual logic:

**Green.**

```java
public long feeFor(long orderTotalCents) {
    return orderTotalCents >= 5000 ? 0 : 599;
}
```

Both tests green.

### Cycle 3 — pin the boundary

What about *exactly* 5000? The requirement said "at or above," but it's worth a test so nobody breaks it later. This is where you nail down the edge (topic 05):

**Red → Green.**

```java
@Test
void thresholdIsInclusiveAtExactly5000() {
    ShippingFeeCalculator calc = new ShippingFeeCalculator();
    assertThat(calc.feeFor(4999)).isEqualTo(599);
    assertThat(calc.feeFor(5000)).isZero();
}
```

It passes immediately — `>= 5000` already handles it. That's fine: the test still has value as a *guard*. If someone later "tidies" `>=` into `>`, this test catches it. (Try it: change `>=` to `>` and watch this test go red.)

### Cycle 4 — reject bad input

**Red.**

```java
@Test
void rejectsNegativeTotal() {
    assertThatThrownBy(() -> new ShippingFeeCalculator().feeFor(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be negative");
}
```

Fails — right now `feeFor(-1)` cheerfully returns 599. Add the guard:

**Green.**

```java
public long feeFor(long orderTotalCents) {
    if (orderTotalCents < 0) {
        throw new IllegalArgumentException("orderTotalCents must not be negative");
    }
    return orderTotalCents >= 5000 ? 0 : 599;
}
```

### Refactor

All four tests are green, so now — and only now — clean up. The magic numbers `5000` and `599` aren't self-explanatory; name them:

```java
private static final long FREE_SHIPPING_THRESHOLD_CENTS = 5000;
private static final long FLAT_FEE_CENTS = 599;

public long feeFor(long orderTotalCents) {
    if (orderTotalCents < 0) {
        throw new IllegalArgumentException("orderTotalCents must not be negative");
    }
    return orderTotalCents >= FREE_SHIPPING_THRESHOLD_CENTS ? 0 : FLAT_FEE_CENTS;
}
```

Run the tests one last time: still green. The refactor changed the code's *shape*, not its *behavior*, and the tests prove it. That's the whole payoff — you can improve code freely because the tests will catch any behavior change. The committed `tdd-demo` is exactly this final state.

## When to use TDD (and when it's awkward)

- **Great for:** logic with clear rules — calculators, validation, parsing, state machines. Anywhere you can state "given X, expect Y" up front.
- **Awkward for:** exploratory work where you don't yet know the design, or UI/wiring where the "assertion" is fuzzy. Many people explore first, then add tests, then TDD the next change. That's fine — TDD is a tool, not a religion.
- **Always worth:** seeing the test fail once before trusting it, whether or not you wrote it first.

## Common pitfalls

- **Skipping RED.** Writing test and code together and never seeing the test fail. Then you don't know the test can fail. Run it red first, even briefly.
- **Writing too much code in GREEN.** Implementing the whole feature to pass one small test. You lose the tight loop and write untested branches. Do the minimum; let the next test pull more out of you.
- **Refactoring with red tests.** "Refactor" means *behavior-preserving* cleanup — it's only safe when everything's green. If tests are red, you're fixing, not refactoring.
- **Treating TDD as all-or-nothing.** You don't have to TDD an entire project to benefit. TDD the next bug fix: write a failing test that reproduces the bug, then fix it. Now it can never silently come back.

---

## Try this yourself

1. Delete `ShippingFeeCalculator.java` and rebuild it from the tests, one cycle at a time. Watch each test go red, then make it green. This is the only way to really feel TDD.
2. Add a new requirement test-first: orders over 20000 cents get free *express* shipping (a new return value or method). Red, green, refactor.
3. Find a small bug in any earlier topic's code (or introduce one). Write a failing test that reproduces it *first*, then fix it. Notice the test now guards against that bug forever.

## Self-check

1. Why does TDD insist you see the test fail *before* writing the code?
2. In cycle 1 we returned a hardcoded `599`. Why is that acceptable — even correct — at that moment?
3. What has to be true about your tests before you're allowed to call a change a "refactor"?

---

## Code examples

In reading order:

- `tdd-demo/src/main/java/com/example/ShippingFeeCalculator.java` — the final state after all four cycles.
- `tdd-demo/src/test/java/com/example/ShippingFeeCalculatorTest.java` — the four tests, in the order they were written.

Run with `mvn test`. To experience TDD, delete the production class and bring back one test at a time.
