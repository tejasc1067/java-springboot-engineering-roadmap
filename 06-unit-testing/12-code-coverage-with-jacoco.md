# Code Coverage with JaCoCo

Coverage measures how much of your code the tests actually executed. JaCoCo is the standard tool for it in the Java world. This topic shows how to generate a coverage report, how to read it — and, just as important, why a high coverage number does *not* mean your code is well tested.

---

## The problem this solves

You've written tests. Did they actually exercise the code you think they did? It's easy to leave a whole branch — an `else`, an error path, a tier — with no test touching it, and never notice. Coverage tooling runs your tests with an agent watching, then reports exactly which lines and branches ran and which didn't. It turns "I think this is tested" into "here's the line that isn't."

## How it works

### Add the JaCoCo plugin

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

Two pieces:
- **`prepare-agent`** attaches a Java agent that records which code runs during the tests.
- **`report`** (bound to the `test` phase) turns that recording into an HTML report after the tests finish.

### Run it and open the report

```
mvn test
```

Then open the generated file in a browser:

```
target/site/jacoco/index.html
```

You get a table per package and class. Click into a class and JaCoCo shows your source with each line colored:

- **Green** — fully executed by some test.
- **Yellow** — partially covered: the line has branches (an `if`, a `? :`) and only *some* were taken.
- **Red** — never executed.

### Line coverage vs branch coverage

These are different numbers and the difference matters:

- **Line coverage** — was this line executed at all?
- **Branch coverage** — for a line with a decision (`if`, `? :`, `&&`), were *both* the true and false paths taken?

A line can be green for *line* coverage but yellow for *branch* coverage. Consider:

```java
return orderCents >= 10000 ? 20 : 15;
```

A single test with `orderCents = 10000` executes that line (line coverage: covered) but only takes the `>= true` path. The `15` path is never run — branch coverage is 50% on that line. Branch coverage is the stricter, more useful number, because the bugs hide in the path you *didn't* take.

### The demo's deliberate gap

`LoyaltyDiscount` has four behaviors: GOLD-large, GOLD-small, SILVER, and unknown-tier. The tests cover three and **deliberately skip SILVER**. The report makes the gap obvious — the class lands at 5 of 6 lines and 5 of 6 branches covered, and the `SILVER` branch and its `return 10;` show red. Nothing *failed*; the tests are all green. Coverage isn't about pass/fail — it's about *what you forgot to test*. That red line is the report earning its keep: it points straight at the missing test.

## The trap: coverage is not correctness

This is the part people get wrong. **100% coverage does not mean the code works.** Coverage only proves a line *ran* — not that you *checked* what it did. This test gives 100% coverage of `feeFor` and verifies almost nothing:

```java
@Test
void coversEverythingProvesNothing() {
    calc.feeFor(1000);
    calc.feeFor(9999);
    calc.feeFor(-1);   // wrapped to not throw, say
    // no assertions at all
}
```

Every line ran, so coverage is 100% — and the test would pass even if `feeFor` returned wrong numbers, because it asserts nothing. Coverage measures *execution*, not *verification*. A suite can be 100% covered and catch zero bugs.

So treat coverage as a **gap-finder, not a goal**:

- *Low* coverage is a reliable bad sign: code with 30% coverage definitely has untested logic.
- *High* coverage is not a reliable good sign: it can hide weak assertions.
- Use the report to find the *red* — the branch nobody tested — and ask "should there be a test here?" Often yes (a real case); sometimes no (a trivial getter).

Chasing a coverage *percentage* leads to padding tests that execute code without checking it — which makes the number go up and the suite no better. Chase *meaningful tests for untested behavior*; let the percentage follow.

## What to aim for

There's no magic number, and teams that mandate "must hit 85%" usually get gamed tests. A healthier stance: cover the *behavior that matters* (logic, branches, error paths — everything topics 05–06 talked about), don't sweat coverage of trivial code (plain getters, generated methods), and watch the *trend* — coverage dropping on a change often means new logic arrived without a test.

## Common pitfalls

- **Treating the percentage as the goal.** It rewards executing code, not verifying it. Use it to find gaps, not to grade yourself.
- **Writing assertion-free tests to bump the number.** 100% covered + 0 assertions = false confidence. Worse than honest 60%.
- **Ignoring branch coverage** and looking only at line coverage. The untaken branch is exactly where bugs live.
- **Forgetting the report is regenerated each run.** It lives under `target/` (gitignored). Re-run `mvn test` after changing tests; don't read a stale report.
- **Failing the build on a coverage threshold too early.** JaCoCo *can* fail the build below a threshold (a `check` rule). Useful on mature projects; on a young one it mostly blocks you. Add it deliberately, not reflexively.

---

## Try this yourself

1. Add the missing SILVER test: `assertThat(policy.percentFor("SILVER", 100)).isEqualTo(10);`. Re-run `mvn test`, reopen the report, and watch the class reach 100% — and the red line turn green.
2. Write the "covers everything, asserts nothing" test from above against `LoyaltyDiscount`. Confirm coverage is high but the test would pass even if you broke the return values. This is the trap made concrete.
3. Look at the ternary `orderCents >= 10000 ? 20 : 15`. Remove the `goldGetsFifteenPercentOnSmallOrders` test and see that line drop from full to partial branch coverage, even though it's still "covered" by line.

## Self-check

1. What's the difference between line coverage and branch coverage, and why is branch coverage more informative?
2. Explain how a class can have 100% coverage and still be badly tested.
3. The report shows one class at 45% and another at 95%. What does each number reliably tell you, and what does neither tell you?

---

## Code examples

In reading order:

- `coverage-demo/src/main/java/com/example/LoyaltyDiscount.java` — four branches, one left untested on purpose.
- `coverage-demo/src/test/java/com/example/LoyaltyDiscountTest.java` — three passing tests that leave the SILVER branch red in the report.

Run `mvn test`, then open `target/site/jacoco/index.html`.
