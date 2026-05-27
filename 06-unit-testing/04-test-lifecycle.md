# The Test Lifecycle

JUnit gives every test a clean starting point and runs setup/teardown code around it. Understanding *when* that code runs — and why each test gets a fresh instance of the test class — is what keeps your tests independent of each other.

---

## The problem this solves

Most tests need some setup: build the object under test, maybe load some sample data. If you copy that setup into every test method, the file gets noisy and the setup drifts out of sync. And if two tests share the same object, one test's leftovers can make another test pass or fail for the wrong reason — a bug that's maddening to track down because the test only fails *when run with others*.

JUnit's lifecycle annotations solve both: put shared setup in one place, and get a guaranteed-clean state before each test.

## How it works

There are four lifecycle hooks:

| Annotation | Runs | Typical use |
|------------|------|-------------|
| `@BeforeAll` | once, before the first test in the class | expensive one-time setup (rare in unit tests) |
| `@BeforeEach` | before *every* test method | build a fresh object under test |
| `@AfterEach` | after *every* test method | cleanup (close something you opened) |
| `@AfterAll` | once, after the last test in the class | one-time teardown |

The execution order for a class with two tests:

```
@BeforeAll                 (once)
  @BeforeEach              (test 1)
    test 1
  @AfterEach
  @BeforeEach              (test 2)
    test 2
  @AfterEach
@AfterAll                  (once)
```

In code:

```java
private ShoppingCart cart;

@BeforeEach
void freshCartBeforeEachTest() {
    cart = new ShoppingCart();   // every test starts from an empty cart
}

@Test
void addingOneItem() {
    cart.add("BOOK");
    assertThat(cart.size()).isEqualTo(1);
}

@Test
void addingTwoItems() {
    cart.add("BOOK");
    cart.add("PEN");
    assertThat(cart.size()).isEqualTo(2);   // not 3 - the other test's BOOK isn't here
}
```

### The key idea: a fresh instance per test

By default JUnit creates a **brand-new instance of the test class for every test method**. So instance fields can't leak between tests — each test sees them freshly initialized. That's why `addingTwoItems` sees a cart with exactly 2 items, not 3: the `BOOK` added in `addingOneItem` was on a *different `ShoppingCartLifecycleTest` object* that's already been thrown away.

This is also why `@BeforeAll` and `@AfterAll` must be `static` in the default lifecycle: they run when there is no instance (before the first one is created, after the last is discarded).

The demo proves this with a counter field, `beforeEachRuns`. It's incremented in `@BeforeEach`, and *both* tests assert it equals `1` — never `2` — because each test runs on its own fresh instance where the counter started at zero.

### Changing the default: `@TestInstance(PER_CLASS)`

You can tell JUnit to reuse one instance for the whole class with `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`. Then `@BeforeAll`/`@AfterAll` may be non-static. This is occasionally handy, but it means instance state *can* leak between tests, so you give up the automatic isolation. Default (a fresh instance per method) is the right choice almost always — reach for `PER_CLASS` only when you have a specific reason.

## When to use which hook

- **`@BeforeEach`** is the one you'll use constantly: build the object under test here.
- **`@BeforeAll`/`@AfterAll`** are rare in *unit* tests, because unit tests shouldn't have expensive shared setup (no database to start). You'll see them more in integration tests.
- **`@AfterEach`** only when a test opens something that must be closed. Pure unit tests usually have nothing to tear down.

## Common pitfalls

- **Relying on test execution order.** JUnit does not promise to run tests top-to-bottom. If test B only passes because test A ran first and left some state, you have a hidden dependency that will break the day the order changes. Each test must set up everything it needs (that's what `@BeforeEach` is for).
- **`@BeforeAll` on a non-static method (default lifecycle).** JUnit will refuse to run it (or error). Make it `static`, or switch the class to `@TestInstance(PER_CLASS)`.
- **Doing setup in the constructor instead of `@BeforeEach`.** It usually works (new instance per test), but `@BeforeEach` is the conventional, readable place, and it runs *after* JUnit has fully wired the instance (which matters once extensions like Mockito's are involved).
- **Heavy work in `@BeforeEach`.** It runs before every single test. If it's slow, your whole suite is slow. Keep per-test setup cheap.

---

## Try this yourself

1. Add a `private int counter = 0;` field, increment it inside each `@Test`, and assert it's `1` in both. It always is — proof of the fresh instance. Now move the increment into a static field and watch the behavior change.
2. Add a third test that asserts the cart is empty at the start (`assertThat(cart.size()).isZero()`). It passes because `@BeforeEach` rebuilt the cart — comment out the line inside `@BeforeEach` and watch tests start interfering with each other.
3. Add `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` to the class and make `@BeforeAll` non-static. Confirm it now compiles and runs.

## Self-check

1. Why must `@BeforeAll` be `static` in the default lifecycle?
2. Two tests pass when run alone but one fails when both run together. What lifecycle mistake should you suspect first?
3. What does "a fresh instance per test" protect you from, concretely?

---

## Code examples

In reading order:

- `lifecycle-demo/src/test/java/com/example/ShoppingCartLifecycleTest.java` — all four hooks with print statements to show order, plus a counter field that proves each test runs on a fresh instance.

Run with `mvn test`.
