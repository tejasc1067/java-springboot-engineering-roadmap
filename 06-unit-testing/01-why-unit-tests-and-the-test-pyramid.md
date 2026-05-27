# Why Unit Tests Exist, and the Test Pyramid

A unit test is code that runs your code, checks the answer, and fails loudly when the answer is wrong. This topic is about *why* you'd write code to test code instead of just running the program and looking — and where unit tests sit among the other kinds of tests.

---

## The problem this solves

Here's how you've been checking your work so far:

```java
public static void main(String[] args) {
    PriceCalculator calc = new PriceCalculator();
    System.out.println(calc.lineTotal(250, 3));   // I expect 750
}
```

You run it, you see `750`, you nod, you move on. This has three problems that get worse every week:

1. **You have to read the output and remember what's correct.** Was it supposed to be 750 or 75000? Next month you won't remember.
2. **You only check the thing you're looking at right now.** You change `lineTotal`, eyeball it, ship it — and don't notice you broke `applyDiscount`, which also calls it. Nobody re-runs every `main` by hand.
3. **It doesn't scale.** With three classes you can eyeball everything. With three hundred, you can't. Bugs slip through not because they're hard, but because nobody re-checked the boring parts.

A unit test fixes all three. It writes down "input 250 and 3 should give 750" *as code*. The computer checks it, not your memory. It runs in under a second, so you run all of them on every change. And "all of them" can be thousands of checks across the whole codebase.

```java
@Test
void lineTotalMultipliesPriceByQuantity() {
    assertEquals(750, new PriceCalculator().lineTotal(250, 3));
}
```

Same check as the `println` — but now it's permanent, automatic, and it *fails the build* if someone breaks it. That last part is the whole point: a test that can't fail isn't testing anything.

## What "unit" means

A **unit test** checks one small piece of code — usually one class, often one method — *in isolation*, without a database, network, file, or clock. "In isolation" is the key word. If your test needs a running database to pass, it's not a unit test; it's an integration test (slower, flakier, tests more at once).

Why insist on isolation?

- **Speed.** No database connection, no HTTP call. A thousand unit tests run in a few seconds, so you run them constantly.
- **Pinpoint failures.** If a unit test fails, the bug is in *that* unit. You're not wondering whether the database was down or the network blipped.
- **Determinism.** Same input, same result, every time. A test that depends on the real clock or a real network sometimes passes and sometimes fails — that's a *flaky* test, and flaky tests get ignored, which makes them worthless.

Some code can't be tested in isolation without help — a method that creates its own database connection, or calls `LocalDateTime.now()`. That's not a testing problem, it's a *design* problem, and topic 07 is entirely about fixing it.

## The test pyramid

Unit tests are not the only kind of test. The usual mental model is a pyramid, widest at the bottom:

```
        /\        End-to-end (E2E): drive the whole running app
       /  \       like a user would. Few. Slow. Catch "the pieces
      /----\      don't fit together" bugs.
     /      \     Integration: a few real pieces together — your code
    /        \    + a real database, or two services talking. Some.
   /----------\   Medium speed. Catch "my SQL is wrong" bugs.
  /            \  Unit: one class, in isolation, no I/O. Many.
 /--------------\ Fast. Catch "my logic is wrong" bugs.
```

The shape is a recommendation about *proportion*: have many fast unit tests, fewer integration tests, and only a handful of slow end-to-end tests. The reason is cost. A unit test costs milliseconds and never flakes. An end-to-end test costs seconds, needs the whole system running, and breaks for reasons that have nothing to do with the bug you care about. If you flip the pyramid — mostly slow, flaky end-to-end tests — your test suite becomes something people dread running, and a test suite nobody runs protects nothing.

This doesn't mean unit tests are "better." Each layer catches bugs the others can't. A unit test will never catch "my SQL query has a typo" because a unit test doesn't run SQL. The pyramid says: catch what you *can* at the cheap layer, and reserve the expensive layers for what genuinely needs them.

## What this module covers, and what it doesn't

This module is **only the bottom layer**: pure unit tests, no I/O.

- **Covered here:** testing plain Java classes — calculators, services, validation logic — with their dependencies faked out (topic 08).
- **Not here (module 08):** integration tests with Spring — starting the app, hitting a real endpoint, querying a real (test) database. That needs Spring's test support, so it waits until you know Spring.

We're starting at the bottom because the bottom is where most of your bugs actually are (logic mistakes), the tests are cheapest to write, and the skills carry straight into the integration layer later.

## When unit tests pull their weight, and when they don't

**Worth a unit test:** anything with logic — calculations, branching, validation, parsing, edge cases (empty list, negative number, null). This is where unit tests shine and where bugs hide.

**Not worth a unit test:** a plain getter/setter, or a method that does nothing but pass a call straight through to a library. There's no logic to get wrong, so a test just restates the code and breaks every time you rename something. Testing trivial code isn't diligence; it's noise that makes the suite slower and more annoying to maintain.

The honest rule: **test behavior that could plausibly be wrong.** If you can't imagine the test ever catching a real bug, don't write it.

## Common pitfalls

- **A test with no assertion.** It calls the method, nothing checks the result, it always "passes." It tests nothing. Every test must assert something.
- **Testing the framework instead of your code.** Don't write a test that checks `ArrayList.add` works. It does. Test *your* logic.
- **Making unit tests hit a real database "to be thorough."** Now they're slow and flaky, and you stop running them. Keep units isolated; put database checks in integration tests.
- **Chasing a coverage percentage instead of testing behavior.** 100% coverage with weak assertions catches nothing. Covered in topic 12.

---

## Try this yourself

1. Take the last small program you wrote with a `main` that printed something you checked by eye. Write down, in plain English, the three inputs you'd want to be sure about and what the correct output is for each. That list is your first test suite — topic 02 turns it into code.
2. For a web app you use (email, a shopping site), name one bug a *unit* test could catch and one bug only an *end-to-end* test could catch. Notice they're different kinds of bug.

## Self-check

1. In your own words, what makes a test a *unit* test specifically — as opposed to just "a test"?
2. The test pyramid says "many unit tests, few end-to-end tests." What's the practical reason, in terms of speed and flakiness?
3. Give one example of code that is **not** worth unit-testing, and say why.
