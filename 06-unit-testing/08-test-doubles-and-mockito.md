# Test Doubles and Mockito Basics

When the class you're testing depends on a database, an email server, or another service, you don't want the *real* one in a unit test. You replace it with a stand-in you control — a "test double." In topic 07 you wrote one by hand. Mockito generates them for you. This topic covers the vocabulary and the three things you'll do constantly: create a mock, stub it, and verify it.

---

## The problem this solves

`OrderService.placeOrder` looks up a user (in a database) and saves an order (in a database). To unit-test its *logic* — "reject unknown users, reject inactive users, save valid orders" — you do not want a real database: it would be slow, need setup, and turn a logic test into an integration test.

In topic 07 you solved this by writing a `RecordingGateway` by hand. That works, but writing a fake class for every interface gets old fast, especially when you need to control *return values* per test ("this time `findById` returns Ada; that time it returns nothing"). Mockito automates exactly that.

## The vocabulary of test doubles

"Test double" is the umbrella term (like "stunt double"). The kinds, from simplest to richest:

| Term | What it is |
|------|-----------|
| **Dummy** | An object passed only to fill a parameter; never actually used. |
| **Stub** | Returns canned answers you program: "when asked for user 1, return Ada." |
| **Mock** | A stub that *also records calls*, so you can verify it was called as expected. |
| **Spy** | Wraps a *real* object; calls run for real unless you override them (topic 09). |
| **Fake** | A working but simplified implementation (e.g. an in-memory repository). Your `RecordingGateway` was a fake. |

Mockito's `mock(...)` / `@Mock` gives you something that's a stub *and* a mock — you can program return values *and* verify calls. In practice people say "mock" for all of it; the precise terms matter mostly in interviews and when reasoning about what a test is really checking.

## How it works

### Step 1 — turn on Mockito for the test class

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock UserRepository users;
    @Mock OrderRepository orders;
    ...
}
```

`@ExtendWith(MockitoExtension.class)` tells JUnit to let Mockito process the class. Each `@Mock` field is then filled with a fresh mock before every test. (Add the `mockito-junit-jupiter` dependency — it brings `mockito-core` and this extension.)

### Step 2 — build the object under test with the mocks

```java
@BeforeEach
void setUp() {
    service = new OrderService(users, orders);
}
```

This is the constructor injection from topic 07: we hand the mocks to the real `OrderService`. (Mockito can also do this wiring for you with `@InjectMocks OrderService service;` — handy, but building it yourself is clearer while you're learning, and it keeps the link to "dependencies go in the constructor" visible.)

### Step 3 — stub: program what the mocks return

```java
when(users.findById(1)).thenReturn(Optional.of(ada));
when(orders.save(order)).thenReturn(order);
```

Read it as "*when* this method is called with these arguments, *then return* this." An unstubbed method on a mock returns a harmless default (`null`, `0`, `false`, an empty `Optional`) — Mockito never calls the real implementation (there isn't one).

### Step 4 — act, then verify

```java
Order result = service.placeOrder(1, order);

assertThat(result).isEqualTo(order);   // assert the return value (state)
verify(orders).save(order);            // verify the interaction happened (behavior)
```

Two different kinds of check:

- **`assertThat`** checks a *value* — what came back.
- **`verify`** checks an *interaction* — that `orders.save(order)` was actually called. Some methods return nothing (`void`); for those, verifying the call is the *only* way to know they ran.

### Verifying a call did *not* happen

```java
verify(orders, never()).save(any());
```

For the "unknown user" and "inactive user" tests, the order must *not* be saved. `never()` asserts the call count is zero. `any()` is an argument matcher meaning "any argument" — covered next topic.

## Strict stubbing: only stub what you use

`MockitoExtension` is *strict* by default: if you stub something and the test never uses it, it fails with `UnnecessaryStubbingException`. That's a feature — it stops stale stubs from accumulating and lying about what the test covers. It's why the "unknown user" test stubs only `users.findById` and *not* `orders.save`: the save is never reached, so stubbing it would be flagged as unnecessary.

## A note on the "self-attaching" warning

On Java 21 and newer, running Mockito prints:

```
Mockito is currently self-attaching to enable the inline-mock-maker.
This will no longer work in future releases of the JDK...
```

Your tests still pass — this is a warning, not an error. It happens because Mockito loads a Java agent at runtime to do its bytecode tricks, and recent JDKs are tightening that. The clean fix (for later) is to register Mockito as an agent in the Surefire config; for learning, the warning is safe to ignore. It's noted here so it doesn't alarm you when you see it.

## When to mock and when not to

- **Mock collaborators that do I/O or are slow/external:** repositories, gateways, other services. That's the whole point — isolate your logic from theirs.
- **Don't mock value objects or types you don't own.** Don't mock `String`, `List`, `Optional`, or a record like `User` — just create real ones. Mocking a `User` to make `active()` return `true` is absurd when `new User(1, "Ada", "ada@example.com", true)` says it plainly.
- **Don't mock the class under test.** You're testing `OrderService` for real; only its *dependencies* are doubles.

## Common pitfalls

- **`when(...)` on a `void` method.** `when` needs a return value. For void methods use `doThrow(...).when(mock).method()` or just `verify` that it was called.
- **Over-stubbing.** Stubbing methods the code path never calls → `UnnecessaryStubbingException`. Stub only what this test exercises.
- **Verifying everything.** If you `verify` every single call, the test breaks whenever you refactor internals, even when behavior is unchanged. Verify the interactions that *matter* (the save happened / didn't); assert on return values for the rest.
- **Mocking a record/value and being surprised it's all nulls.** A mocked `User` returns `null`/`0`/`false` for everything unless stubbed. Use a real `User`.
- **Forgetting `@ExtendWith(MockitoExtension.class)`.** Then `@Mock` fields stay `null` and you get a `NullPointerException` before the test even starts.

---

## Try this yourself

1. Add a test where `findById` returns an active user but `orders.save` is stubbed to return an order with a generated id (`new Order("SAVED-1", order.amountCents())`). Assert the returned id is `"SAVED-1"`. This shows the stub *shaping* the result.
2. Replace the manual `new OrderService(users, orders)` with `@InjectMocks OrderService service;` and delete the `@BeforeEach`. Confirm the tests still pass — then decide which version you find clearer.
3. Add a stub you never use (e.g. `when(orders.save(any())).thenReturn(null)`) to the "unknown user" test and watch `UnnecessaryStubbingException` appear. Remove it.

## Self-check

1. What's the difference between a *stub* and a *mock*, and which does Mockito's `@Mock` give you?
2. When would you use `verify(...)` instead of `assertThat(...)`? Give a case where `verify` is the *only* option.
3. Why is it a mistake to mock the `User` record instead of constructing a real one?

---

## Code examples

In reading order:

- `mockito-basics-demo/src/main/java/com/example/OrderService.java` — the class under test and its two repository interfaces.
- `.../src/test/java/com/example/OrderServiceTest.java` — `@Mock`, `when().thenReturn()`, `verify()`, `verify(never())`, and strict stubbing in action.

Run with `mvn test`.
