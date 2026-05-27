# What Makes a Good Test

You now know the mechanics: assertions, lifecycle, mocks. This topic is about judgment — the difference between a test suite that helps you and one that slows you down. A bad test passes when the code is broken, or fails when nothing's wrong, or is so tangled nobody dares change it. A good test fails for exactly one clear reason and tells you what.

---

## The problem this solves

A test suite is code you have to maintain forever. Written well, it's a safety net that lets you change code fearlessly. Written badly, it's a second codebase that breaks constantly for the wrong reasons, until the team starts ignoring failures or deleting tests to make the build green. The mechanics are easy; the discipline below is what keeps a suite worth having.

## Arrange-Act-Assert

Structure every test in three visual blocks: set up the inputs (Arrange), call the thing under test (Act), check the result (Assert).

```java
@Test
void appliesPercentOff() {
    DiscountCalculator calc = new DiscountCalculator();   // Arrange

    long result = calc.applyPercentOff(1000, 20);         // Act

    assertThat(result).isEqualTo(800);                    // Assert
}
```

The blank lines aren't decoration — they make the test scannable. A reader sees instantly what's input, what's the action, and what's expected. If your "Act" is hard to spot, the test is doing too much.

## One logical concept per test

A test should check *one behavior*. Not one assertion necessarily — several assertions about the same behavior are fine — but one *reason to fail*.

```java
// Bad: two unrelated behaviors. If it fails, which one broke?
@Test
void orderWorks() {
    assertThat(order.totalCents()).isEqualTo(3500);
    order.clear();
    assertThat(order.isEmpty()).isTrue();
}

// Good: two focused tests, each fails for one reason.
@Test void totalSumsTheLineItems() { ... }
@Test void clearEmptiesTheOrder() { ... }
```

When a focused test fails, its *name* tells you what broke before you read a line of code.

## Name the behavior, not the method

The test name is the first thing you read when it fails — in the CI log, you often read *only* the name. Make it a sentence about behavior:

```java
void test1()                              // useless
void applyPercentOff()                    // just the method name - says nothing
void rejectsPercentOver100()              // good: says what should happen
void zeroPercentLeavesPriceUnchanged()    // good: names the case and the expectation
```

A reader should understand what broke from the name alone, without opening the test.

## Tests must be independent

Each test must pass on its own, in any order, regardless of which others ran. JUnit's fresh-instance-per-test (topic 04) helps, but you can still break independence with shared static state, files, or order assumptions:

```java
static List<String> shared = new ArrayList<>();   // danger: leaks between tests

@Test void a() { shared.add("x"); assertThat(shared).hasSize(1); }
@Test void b() { shared.add("y"); assertThat(shared).hasSize(1); }  // fails if a() ran first
```

If a test only passes when another runs first, you have a hidden dependency that will surface as a baffling failure the day the order changes. Each test arranges everything it needs.

## Tests must be deterministic

Same code, same result, every run. The usual sources of non-determinism:

- **The clock.** `LocalDate.now()` in the code under test → inject a `Clock` (topic 07).
- **Randomness.** `new Random()` with no seed → inject the random source, or seed it.
- **Real I/O.** Network, filesystem, database → mock it (topic 08).
- **Iteration order** of a `HashMap`/`HashSet` → assert with order-independent matchers (`containsExactlyInAnyOrder`).

A test that passes 95% of the time is a **flaky test**, and flaky tests are poison: people learn to re-run the build until it's green, which means they're now ignoring failures — including real ones. A flaky test is worse than no test.

## No logic in tests

The test is supposed to be the *simple, obviously-correct* check on the complicated code. The moment a test contains an `if`, a loop, or a calculation, it can have its own bugs — and now you'd need to test your test.

```java
// Bad: the test recomputes the expected value with the same logic it's testing.
@Test
void discount() {
    long expected = 1000 - (1000 * 20 / 100);   // if this formula is wrong, so is the test
    assertThat(calc.applyPercentOff(1000, 20)).isEqualTo(expected);
}

// Good: a hardcoded expected value a human verified once.
@Test
void discount() {
    assertThat(calc.applyPercentOff(1000, 20)).isEqualTo(800);
}
```

Write the expected value as a literal you worked out by hand. If you're tempted to compute it, that's a sign you don't actually know what the answer should be — figure it out, then hardcode it.

## The FIRST checklist

A handy acronym for all of the above:

- **F**ast — milliseconds, so you run them constantly.
- **I**ndependent — any order, no shared state.
- **R**epeatable — same result every time, anywhere (no clock/network/random).
- **S**elf-validating — it asserts pass/fail; you never eyeball output.
- **T**imely — written with the code (ideally just before — that's topic 11).

## Common pitfalls

- **Asserting nothing.** The test runs code and checks nothing, so it can never fail. Every test asserts.
- **Testing the mock, not the code.** If a test only stubs a method then verifies that same stub was called, it tests Mockito, not your logic. Assert on a *result* your code produced.
- **Brittle assertions on incidental detail.** Asserting an exact `toString`, a full log message, or `HashMap` order makes tests fail on harmless changes. Assert the meaningful thing.
- **Giant setup nobody understands.** If arranging a test takes 40 lines, the class under test likely has too many dependencies. Listen to that pain (topic 07).
- **Copy-paste tests that drift.** Five near-identical tests where one has a subtly wrong expected value nobody noticed. Use parameterized tests (topic 06) instead.

---

## Try this yourself

1. Find a test you (or this module) wrote and rename it to a full behavior sentence. Notice how much the name alone now tells you.
2. Take a test with two unrelated assertions and split it into two named tests. Break the code and confirm the *names* point you at the failure.
3. Find any place a test recomputes its expected value and replace it with a hand-verified literal.

## Self-check

1. Why is a flaky test considered worse than having no test at all?
2. What's wrong with putting an `if` or a loop inside a test?
3. Your colleague's test passes alone but fails in the full suite. What two causes would you check first, and which earlier topic addresses each?
