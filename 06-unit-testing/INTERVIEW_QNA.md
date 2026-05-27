# Interview Q&A — Module 06 (Unit Testing)

Questions a backend interviewer might ask about unit testing. Each answer is the kind of response that shows actual understanding, not a one-line definition.

---

## Q1 — What makes a test a *unit* test, as opposed to just "a test"?

A unit test exercises one small piece of code — typically a single class, often a single method — **in isolation**, with no real I/O: no database, no network, no filesystem, no real clock. "In isolation" is the defining property. Its collaborators are replaced with test doubles so the only thing that can make the test fail is a bug in the unit itself.

That isolation buys three things:

- **Speed** — no connections to open, so thousands of tests run in seconds and you run them on every change.
- **Pinpoint failures** — when a unit test fails, the bug is in that unit, not "maybe the database was down."
- **Determinism** — same input, same result, every run, because nothing external varies.

The contrast: an integration test deliberately includes real pieces (your code + a real database) to catch a different class of bug — a wrong SQL query, a mapping mistake — that a unit test, by design, can't see. Neither is "better"; they catch different bugs at different costs.

---

## Q2 — Mock, stub, spy, fake, dummy — what's the difference, and when do you use each?

They're all "test doubles" (stand-ins for real collaborators), distinguished by what they do:

- **Dummy** — passed only to satisfy a parameter; never actually used. A placeholder.
- **Stub** — returns canned answers you program: "when asked for user 1, return Ada." Used to *drive* the code down a path.
- **Mock** — a stub that also *records calls*, so you can verify it was invoked as expected. Used when the *interaction* is the behavior under test.
- **Spy** — wraps a *real* object; methods run for real unless you override them. Used when you want mostly-real behavior but need to control one piece.
- **Fake** — a working but simplified implementation (an in-memory `Map`-backed repository). Used when a stub would need too much setup.

Mockito's `mock(...)`/`@Mock` is both stub and mock — you can program returns and verify calls. In practice people say "mock" loosely; the precise terms matter when reasoning about *what a test actually proves*. A test that only stubs and never verifies is checking state; one that verifies is checking behavior.

The judgment part: mock collaborators that do real work (repositories, gateways); use real objects for values (records, DTOs); never mock the class under test; prefer a fake over a mock when the same stub setup repeats across many tests.

---

## Q3 — Why is calling `new SomeDependency()` inside a method a testability problem, and what's the fix?

Because the method *decides for itself* what to depend on, a test has no way to substitute something else. If `placeOrder` does `new SmtpGateway()` internally, a unit test can't stop it from trying to send real email — there's no seam to inject a fake. Same with `LocalDateTime.now()` (you can't control "now") or a `static` singleton (you can't replace it).

The fix is **dependency injection**, usually via the constructor: the class accepts its collaborators as parameters instead of creating them. Now production passes the real implementation and a test passes a double. Depending on an *interface* rather than a concrete type makes the substitution clean.

This is also the conceptual root of frameworks like Spring: once every class takes its dependencies in the constructor, *something* has to create and wire them all, and a DI container automates exactly that. So "design for testability" and "design for Spring" turn out to be the same discipline — Spring is just automated constructor injection.

---

## Q4 — How do you unit-test code whose behavior depends on the current date or time?

You don't let the code read the clock directly. `LocalDate.now()` / `Instant.now()` make the result depend on *when* the test runs, which is the textbook flaky test.

Inject a `java.time.Clock` and have the code read time through it: `LocalDate.now(clock)` instead of `LocalDate.now()`. In production you wire a real clock (`Clock.systemUTC()`); in a test you wire a frozen one:

```java
Clock march1 = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneId.of("UTC"));
```

Now "today" is whatever the test says, the assertion is exact, and the test gives the same result forever. The same principle applies to other non-deterministic sources: inject the `Random` (or seed it), and mock anything that does I/O. A unit test must be *repeatable* — same result every run, on any machine, on any date.

---

## Q5 — What is a flaky test, why is it so harmful, and how do you fix one?

A flaky test passes sometimes and fails sometimes without any code change. Common causes: dependence on the real clock, unseeded randomness, real network/filesystem calls, reliance on `HashMap`/`HashSet` iteration order, or hidden state shared between tests so the result depends on run order.

It's harmful out of proportion to its frequency because it **destroys trust in the whole suite**. When a build goes red and people's first instinct is "just re-run it," they've started ignoring failures — and now a *real* regression that turns a test red gets re-run and shrugged off too. One flaky test can neutralize an entire suite as a safety net.

Fixing it means removing the source of nondeterminism, not adding retries: inject a `Clock` for time, seed or inject randomness, mock I/O, use order-independent assertions (`containsExactlyInAnyOrder`), and ensure each test sets up its own state (fresh instance per test, no shared statics). Retrying a flaky test hides the flakiness; it doesn't fix it.

---

## Q6 — When would you use `verify(...)` instead of `assertThat(...)`?

`assertThat` checks **state** — a value the code produced and returned. `verify` checks **an interaction** — that the code called a collaborator in a particular way.

Use `verify` when the behavior under test *is* the interaction, and especially when there's no return value to assert on. If `placeOrder` is supposed to persist the order, the only way to know it happened is `verify(orders).save(order)` — the method might return `void`, or return something unrelated to whether the save occurred. Likewise `verify(gateway, never()).send(...)` is how you prove a message was *not* sent on a rejection path; there's no return value that captures "nothing happened."

The caution: don't `verify` everything. Over-verification couples the test to the implementation, so harmless refactors break tests even when behavior is unchanged. Verify the interactions that genuinely *are* the contract (the save happened / didn't); assert on returned state for the rest.

---

## Q7 — What does "80% code coverage" actually tell you? Is 100% the goal?

Coverage tells you what fraction of lines (or branches) the tests *executed*. That's a one-directional signal:

- **Low coverage is reliably bad.** 30% coverage means most of the code has no test touching it — there's definitely untested logic.
- **High coverage is not reliably good.** Coverage measures *execution*, not *verification*. A test that calls every line but asserts nothing yields 100% coverage and catches zero bugs.

So 100% is not the goal, and mandating a percentage tends to produce padding tests that run code without checking it — the number goes up, the suite gets no better. The right use is as a **gap-finder**: open the report, look for the red (untested) branches, and ask "is this a real case that needs a test?" Branch coverage is more informative than line coverage, because the bug usually hides in the path you *didn't* take (the `else`, the boundary). Aim to cover behavior that matters; don't chase coverage of trivial getters; watch the trend — a drop on a change often means new logic arrived without a test.

---

## Q8 — What is the test pyramid, and why does its shape matter?

It's a guideline about the *proportion* of test types: many fast unit tests at the base, fewer integration tests in the middle, a handful of slow end-to-end tests at the top.

The shape is driven by cost and reliability. A unit test costs milliseconds, needs nothing running, and fails only for the reason you care about. An end-to-end test costs seconds, needs the whole system up, and breaks for incidental reasons (a slow environment, a flaky network) that have nothing to do with the bug. If you invert the pyramid — mostly end-to-end tests — the suite becomes slow and flaky, people stop running it, and it stops protecting anything.

The nuance is that each layer catches bugs the others *can't*: a unit test will never catch a broken SQL query (it doesn't run SQL), and an end-to-end test that does catch it is expensive and vague about the cause. So you catch what you can at the cheap layer and reserve the expensive layers for what genuinely needs the whole system — integration of real pieces, true user flows.

---

## Q9 — What is TDD, and what's the point of writing the test before the code?

TDD (Test-Driven Development) is the cycle: write a small failing test (**red**), write the simplest code that makes it pass (**green**), then clean up with the tests protecting you (**refactor**) — repeating in minute-scale loops.

Writing the test first does two things that writing it after doesn't. First, the test describes the **requirement** rather than the implementation: when you test after, you tend to read your own code and assert that it does what it already does — bugs included. Second, **seeing the test fail first** proves the test can actually fail; a test you've only ever seen pass might be asserting nothing or testing the wrong thing. As a side effect, coverage isn't bolted on later — code only exists because a test demanded it.

TDD isn't mandatory or all-or-nothing. It shines for logic with clear rules (calculators, validation, parsing) and is awkward for exploratory or UI-wiring work. A pragmatic high-value habit even for non-TDDers: when fixing a bug, write a failing test that reproduces it *first*, then fix it — now the bug can never silently return.

---

## Q10 — A test passes when run alone but fails in the full suite. How do you diagnose it?

This is the signature of **broken test isolation** — the test's result depends on something left behind by another test. Two causes to check first:

1. **Shared mutable state.** A `static` field, a singleton, a file, a database row, or a system property mutated by one test and read by another. The fix is to remove the sharing: rebuild state in `@BeforeEach` (JUnit gives a fresh test instance per method) and never lean on `static` mutable fields.
2. **Order dependence.** Test B only passes if test A ran first and set something up. JUnit doesn't guarantee order, so this surfaces the day the order changes. The fix is that every test arranges everything it needs and assumes nothing about predecessors.

To confirm, run the failing test in isolation (passes), then with the suite (fails), then bisect — run it after each other test to find which one pollutes the state. The underlying lesson (topic 04 / topic 10) is that tests must be **independent**: any order, any subset, same result.
