# Common Mistakes — Module 06 (Unit Testing)

Real bugs people hit when writing tests. Each entry: what someone wrote, what went wrong, why, and how to fix it.

---

## 1. A test that asserts nothing

**What someone wrote:**

```java
@Test
void placeOrder() {
    OrderService service = new OrderService(users, orders);
    service.placeOrder(1, new Order("A-1", 500));
}
```

**What went wrong:** The test is green. It stayed green after a refactor that made `placeOrder` return the wrong order and skip the save entirely. Nobody noticed for weeks.

**Why:** There's no assertion. The test runs the code and throws the result away, so the only way it can fail is if the code *throws*. Wrong return values, missing side effects — all invisible.

**Fix:** Every test must check something. Assert the return value (`assertThat(result)...`) and/or verify the interaction (`verify(orders).save(order)`). If you can't think of anything to assert, you don't yet know what the code is supposed to do.

---

## 2. Tests depend on each other through shared state

**What someone wrote:**

```java
static List<String> created = new ArrayList<>();

@Test void createsUser()   { created.add("ada"); assertThat(created).hasSize(1); }
@Test void createsSecond() { created.add("bob"); assertThat(created).hasSize(1); }
```

**What went wrong:** Run alone, each passes. Run together, `createsSecond` fails (`expected size 1 but was 2`) — or passes or fails depending on order. After someone reordered the file, CI went red with no code change.

**Why:** A `static` field is shared across every test instance. State from one test leaks into the next, so the result depends on run order — which JUnit does not guarantee.

**Fix:** No shared mutable state. Use an instance field rebuilt in `@BeforeEach` (topic 04 — JUnit gives a fresh instance per test). Each test arranges everything it needs and assumes nothing about what ran before.

---

## 3. Non-deterministic test (the real clock)

**What someone wrote:**

```java
@Test
void sendsReminderOnTheFirst() {
    assertThat(service.sendDueReminder("ada@x.com")).isTrue();   // service reads LocalDate.now()
}
```

**What went wrong:** The test passed the day it was written (the 1st) and failed every other day. The author marked it `@Disabled` "until I figure it out," and the behavior went untested.

**Why:** The code under test reads the real system clock, so the result depends on the calendar date the test runs. A test whose outcome depends on *when* it runs is flaky by construction.

**Fix:** Inject a `Clock` (topic 07) and fix it in the test: `Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneId.of("UTC"))`. Same for `Random` (seed or inject it) and any I/O (mock it). A unit test must give the same result every run, everywhere.

---

## 4. Mocking the value object instead of building it

**What someone wrote:**

```java
User ada = mock(User.class);
when(ada.active()).thenReturn(true);
when(ada.id()).thenReturn(1L);
```

**What went wrong:** Three lines of mock setup to describe a user, and a later test broke mysteriously because `ada.name()` returned `null` (unstubbed) and the code under test logged it.

**Why:** `User` is a record — a plain value. Mocking it replaces *every* method with a stub and makes the object lie about anything you didn't stub.

**Fix:** Build the real thing: `new User(1, "Ada", "ada@x.com", true)`. Mock *collaborators that do work* (repositories, gateways), not values you can simply construct.

---

## 5. Mocking the class under test

**What someone wrote:**

```java
OrderService service = mock(OrderService.class);
when(service.placeOrder(anyLong(), any())).thenReturn(new Order("A-1", 500));
assertThat(service.placeOrder(1, order).id()).isEqualTo("A-1");
```

**What went wrong:** The test passes and proves nothing — it asserts that the mock returns what you told it to return. `OrderService`'s real logic never ran.

**Why:** You mocked the very thing you're supposed to be testing. The test exercises Mockito, not `OrderService`.

**Fix:** Build the real `OrderService` and mock only its *dependencies*: `new OrderService(mockUsers, mockOrders)`. Test the real object; fake the things around it.

---

## 6. Catching the exception instead of asserting it

**What someone wrote:**

```java
@Test
void rejectsBadPercent() {
    try {
        calc.applyPercentOff(1000, 150);
    } catch (IllegalArgumentException e) {
        // expected
    }
}
```

**What went wrong:** Someone removed the validation from `applyPercentOff`. No exception was thrown — and the test *still passed*, because the `try` block just completed normally.

**Why:** A `try/catch` with no `fail()` after the call passes whether or not the exception is thrown. The "test" can never fail.

**Fix:** Use `assertThrows(IllegalArgumentException.class, () -> calc.applyPercentOff(1000, 150))` (JUnit) or `assertThatThrownBy(...)` (AssertJ). These fail when no exception is thrown. (Topic 05.)

---

## 7. Mixing argument matchers with raw values

**What someone wrote:**

```java
verify(gateway).send("ada@x.com", anyString());
```

**What went wrong:** Runtime failure: `org.mockito.exceptions.misusing.InvalidUseOfMatchersException`. The test errors before it can check anything.

**Why:** Once you use a matcher (`anyString()`) for one argument, Mockito can't tell a literal apart from a matcher for the others. All arguments must be matchers or all must be literals.

**Fix:** Wrap the literal in `eq(...)`: `verify(gateway).send(eq("ada@x.com"), anyString())`. (Topic 09.)

---

## 8. `when(spy.method())` runs the real method

**What someone wrote:**

```java
GreetingService spy = spy(new GreetingService());
when(spy.loadFromDatabase()).thenReturn("cached");   // oops
```

**What went wrong:** `loadFromDatabase()` actually executed during the stub setup — hit the database, threw, or had a side effect — even though the whole point was to *avoid* calling it.

**Why:** `when(spy.loadFromDatabase())` evaluates `spy.loadFromDatabase()` first to pass its result to `when(...)`. On a spy, that calls the real method.

**Fix:** Use the `doReturn` form, which never calls the real method: `doReturn("cached").when(spy).loadFromDatabase();`. With spies, always `doReturn`/`doThrow`. (Topic 09.)

---

## 9. Over-stubbing → `UnnecessaryStubbingException`

**What someone wrote:**

```java
when(users.findById(99)).thenReturn(Optional.empty());
when(orders.save(any())).thenReturn(null);   // never reached on this path

assertThatThrownBy(() -> service.placeOrder(99, order))
        .isInstanceOf(IllegalArgumentException.class);
```

**What went wrong:** `org.mockito.exceptions.misusing.UnnecessaryStubbingException` on the `orders.save` line.

**Why:** `MockitoExtension` is strict: this path rejects the unknown user before ever calling `save`, so the `save` stub is dead code. Strict stubbing flags it so stale stubs don't pile up and mislead readers about what the test covers.

**Fix:** Stub only what the path under test actually calls. Delete the `orders.save` stub from the "unknown user" test. (Topic 08.)

---

## 10. `@BeforeAll` on a non-static method

**What someone wrote:**

```java
@BeforeAll
void loadFixtures() { ... }   // not static
```

**What went wrong:** `org.junit.platform...` error: "`@BeforeAll` method must be static" — the whole test class fails to run.

**Why:** In the default per-method lifecycle, `@BeforeAll` runs before any instance exists, so it must be `static`.

**Fix:** Make it `static`, or — only if you genuinely need instance state shared across the class — add `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`, which lets `@BeforeAll` be non-static (at the cost of the automatic per-test isolation). (Topic 04.)

---

## 11. Reading 100% coverage as "well tested"

**What someone wrote:**

```java
@Test
void exercisesEverything() {
    calc.feeFor(1000);
    calc.feeFor(9999);
    calc.feeFor(5000);
    // no assertions
}
```

**What went wrong:** JaCoCo reported 100% coverage of `feeFor`. The team relaxed. A bug that returned the wrong fee shipped — the "test" asserted nothing, so it passed anyway.

**Why:** Coverage measures whether a line *executed*, not whether you *checked* the result. 100% coverage with no (or weak) assertions catches no bugs.

**Fix:** Treat coverage as a gap-finder, not a goal. Add real assertions; use the report to find *untested branches*, not to chase a percentage. (Topic 12.)

---

## 12. Asserting on incidental detail (brittle tests)

**What someone wrote:**

```java
assertThat(order.toString())
        .isEqualTo("Order[id=A-1, amountCents=500]");
```

**What went wrong:** A teammate added a field to the `Order` record. Dozens of unrelated tests went red — not because behavior changed, but because `toString()` now reads differently.

**Why:** The test pinned an *incidental* representation (`toString` format) instead of the *behavior* it cared about. Incidental details change for harmless reasons.

**Fix:** Assert the meaningful thing: `assertThat(order.id()).isEqualTo("A-1")` and `assertThat(order.amountCents()).isEqualTo(500)`. Never assert a full `toString`, a complete log line, or `HashMap` iteration order unless that exact format *is* the behavior under test.

---

## 13. The Mockito "self-attaching" warning mistaken for a failure

**What someone saw:**

```text
Mockito is currently self-attaching to enable the inline-mock-maker.
This will no longer work in future releases of the JDK...
```

**What went wrong:** A developer on Java 21 saw this during `mvn test`, assumed the build was broken, and spent an afternoon "fixing" passing tests.

**Why:** It's a *warning*, not an error. On Java 21+ Mockito loads a Java agent at runtime, and recent JDKs warn about dynamic agent loading. Tests still pass.

**Fix:** Ignore it while learning. To silence it on a real project, register Mockito as an agent in the Surefire `argLine` (Mockito's docs show the snippet). Don't mistake a yellow warning for a red failure — check the `BUILD` line and the test counts. (Topic 08.)

---

## 14. A test class (or `@Test`) in `src/main/java`

**What someone wrote:** Put `OrderServiceTest.java` under `src/main/java/com/example/` "to keep it next to the class."

**What went wrong:** The production jar shipped with test classes inside it, and JUnit/Mockito leaked onto the runtime classpath. A `NoClassDefFoundError` appeared in production when the test classes referenced test-scoped libraries that weren't deployed.

**Why:** Only `src/test/java` is compiled with test-scoped dependencies and excluded from the artifact. Test code in `src/main/java` is treated as production code.

**Fix:** Tests live in `src/test/java`, mirroring the package of the class they test. That's how they reach package-private members *and* stay out of the shipped jar. (Topic 02.)
