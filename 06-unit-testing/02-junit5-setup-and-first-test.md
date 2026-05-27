# JUnit 5 Setup and Your First Test

JUnit is the library that finds your tests, runs them, and reports which passed. This topic adds it to a Maven project and writes the smallest test that actually proves something.

---

## The problem this solves

In topic 01 you saw the `println`-and-eyeball approach and why it doesn't scale. To replace it you need three things working together:

1. A place to put test code that ships *separately* from your real code (you don't want test classes inside your production jar).
2. A way to mark a method as "this is a test, run it."
3. Something that runs all those methods and tells you `Tests run: 5, Failures: 0`.

Maven and JUnit give you all three. Maven already knows about a test source folder (`src/test/java`) from module 05. JUnit gives you the `@Test` marker. The Surefire plugin (also from module 05) runs them during `mvn test`.

## How it works

### Step 1 — add JUnit to the pom

One dependency, in `test` scope (so it never ships in your production jar — recall scopes from module 05):

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```

`junit-jupiter` is the modern JUnit (often called "JUnit 5" or "Jupiter"). It pulls in everything you need: the API you write against and the engine that runs it.

Surefire — the plugin that runs tests on `mvn test` — needs no special config; it discovers JUnit 5 automatically:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
</plugin>
```

### Step 2 — put production code in `src/main/java`

```java
public class PriceCalculator {
    public long lineTotal(long unitPriceCents, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        return unitPriceCents * quantity;
    }
}
```

### Step 3 — put the test in `src/test/java`, in the *same package*

```java
package com.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceCalculatorTest {

    private final PriceCalculator calculator = new PriceCalculator();

    @Test
    @DisplayName("three items at 250 cents each cost 750 cents")
    void lineTotalMultipliesPriceByQuantity() {
        long total = calculator.lineTotal(250, 3);
        assertEquals(750, total);
    }
}
```

Four things to notice:

- **`@Test`** marks the method as a test. No `@Test`, and JUnit ignores it.
- **`assertEquals(expected, actual)`** — expected value *first*. If they differ, the test fails with a message like `expected: <750> but was: <75>`. If they match, nothing happens and the test passes.
- **Same package, different folder.** The test is in package `com.example` just like the class it tests, but it lives under `src/test/java`. Because it's the same package, the test can reach package-private methods too — handy later.
- **`@DisplayName`** is optional but makes the report readable: you see the sentence, not the method name.

### Step 4 — run it

```
mvn test
```

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`Tests run` is how many ran; `Failures` is how many assertions failed; `Errors` is how many threw an unexpected exception. You want failures and errors at zero. If either is non-zero, `mvn test` exits non-zero and the build fails — which is exactly how a broken change gets stopped before it ships.

### What a failure looks like

Change the expected value to `999` and run again:

```
[ERROR] PriceCalculatorTest.lineTotalMultipliesPriceByQuantity:18
        expected: <999> but was: <750>
[INFO] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

It tells you the test, the line, what it expected, and what it got. That message — not a `println` you have to interpret — is what you'll read every day.

## When to use it / when not to

Use `assertEquals` for any exact-value check. It's the workhorse. In the next topic you'll meet AssertJ, which reads better for anything more complex than "these two values are equal" — but `assertEquals` is perfectly fine and you'll see it in lots of code.

## Common pitfalls

- **Forgetting `@Test`.** A method with no `@Test` annotation simply doesn't run, and you get a false sense of safety ("all green!" — because nothing ran). If your test count is lower than expected, check for a missing `@Test`.
- **Putting the test in `src/main/java`.** It compiles, but now JUnit is on your production classpath and your tests ship in the jar. Tests go in `src/test/java`.
- **`public` everything.** JUnit 5 does not require test classes or methods to be `public`. Default (package-private) is the convention. Old JUnit 4 habits die hard.
- **Arguments to `assertEquals` backwards.** `assertEquals(actual, expected)` still passes/fails correctly, but the failure message reads backwards ("expected 750 but was 999" when you meant the opposite). Expected first.

---

## Try this yourself

1. Add a third test: `lineTotal(0, 5)` should be `0`. Run `mvn test` and watch the count go to 3.
2. Deliberately break `PriceCalculator` (change `*` to `+`) and run the tests. Read the failure message. Then fix it. This is the loop you'll live in: change code, run tests, read failures.
3. Remove the `@Test` annotation from one method and run again. Notice the test count drops and nothing warns you. This is why a missing `@Test` is dangerous.

## Self-check

1. Why does the JUnit dependency use `<scope>test</scope>`? What would go wrong in `compile` scope?
2. What's the difference between a "Failure" and an "Error" in the Surefire summary?
3. The test lives in `src/test/java/com/example/` and the class in `src/main/java/com/example/`. They're in the same package but different folders. Why does that matter?

---

## Code examples

In reading order:

- `first-test-demo/` — a complete minimal Maven project: `PriceCalculator` in `src/main/java`, `PriceCalculatorTest` in `src/test/java`, one test dependency in the pom. Run `mvn test`.
