# Mockito in Depth

Topic 08 covered the everyday loop: mock, stub, verify. This topic adds the tools you reach for when "the method returned the right thing" isn't enough — when you need to match arguments loosely, count calls, inspect *what* was passed, simulate failures, or keep a real object mostly real.

---

## Argument matchers

A bare `verify(gateway).send("ada@example.com", "Order A-1 confirmed")` checks the *exact* arguments. Often you don't care about all of them — you want "called with this email, and *any* message." That's a matcher:

```java
verify(gateway).send(eq("ada@example.com"), anyString());
```

- `anyString()`, `anyLong()`, `any()`, `anyList()` — match any value of that type.
- `eq(value)` — match exactly this value.

**The one rule that trips everyone up:** if you use a matcher for *any* argument, you must use a matcher for *every* argument. You cannot mix a raw value with a matcher:

```java
verify(gateway).send("ada@example.com", anyString());      // COMPILES, FAILS at runtime
verify(gateway).send(eq("ada@example.com"), anyString());  // correct: wrap the literal in eq()
```

The first throws `InvalidUseOfMatchersException` because Mockito can't tell a literal apart from a matcher once matchers are in play. Wrap literals in `eq(...)`.

## Verifying call counts

`verify(mock)` means "exactly once." To check other counts:

```java
verify(gateway, times(3)).send(anyString(), eq("Sale today"));  // exactly 3 times
verify(gateway, never()).send(eq("nobody@x.com"), anyString()); // zero times
verify(gateway, atLeastOnce()).send(anyString(), anyString());  // 1 or more
```

`times(n)`, `never()`, `atLeast(n)`, `atMost(n)`, `atLeastOnce()`. Counts matter when the *number* of interactions is the behavior under test — "broadcast to three recipients sends three messages."

## ArgumentCaptor: inspect what was passed

Sometimes the argument is *built inside* the method, and you want to assert on it. Verifying `send(eq("ada@example.com"), anyString())` confirms *a* message was sent, but not *what* it said. An `ArgumentCaptor` grabs the actual value so you can assert on it:

```java
@Captor ArgumentCaptor<String> messageCaptor;

new OrderNotifier(gateway).confirm("ada@example.com", "A-42");

verify(gateway).send(eq("ada@example.com"), messageCaptor.capture());
assertThat(messageCaptor.getValue()).isEqualTo("Order A-42 confirmed");
```

`capture()` sits in the `verify` call where the argument goes; afterwards `getValue()` (or `getAllValues()` for multiple calls) gives you what was actually passed. Use it when *the content of the argument* is the thing you care about — a formatted message, a built-up domain object, an event being published.

## thenThrow: test the error path

Your code should handle a dependency failing — the database is down, the gateway times out. You can't make the *real* thing fail on demand, but you can make a mock do it:

```java
when(gateway.send(anyString(), anyString()))
        .thenThrow(new RuntimeException("SMTP server down"));

assertThatThrownBy(() -> new OrderNotifier(gateway).confirm("ada@example.com", "A-1"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("SMTP server down");
```

This is how you test "what does my code do when its dependency blows up?" — a path that's otherwise almost impossible to trigger. For a `void` method, use `doThrow(...).when(mock).voidMethod()` (you can't wrap a void call in `when`).

## Spy: a real object with a method or two overridden

A `mock` is hollow — every method returns a default unless you stub it. A `spy` wraps a *real* object — every method runs for real unless you override it. Use it when you want most of an object's real behavior but need to control one piece:

```java
GreetingService service = spy(new GreetingService());
doReturn("Hi, ").when(service).prefix();   // override prefix() only

assertThat(service.greet("Ada")).isEqualTo("Hi, Ada");  // greet() still runs for real
verify(service).prefix();
```

Note the `doReturn(...).when(service).prefix()` form, **not** `when(service.prefix()).thenReturn(...)`. On a spy the second form would *actually call* the real `prefix()` while setting up the stub — sometimes harmless, sometimes a real side effect. With spies, always use `doReturn/doThrow`.

Spies are powerful but a bit of a smell: needing to override part of an object often means it's doing two jobs and should be split. Reach for a spy when you genuinely can't, not as a first move.

## Don't over-mock

The biggest Mockito mistake isn't a syntax error — it's mocking too much:

- **Don't mock what you don't own carelessly.** Mocking a third-party library's class ties your test to *your assumption* of how it behaves. If that assumption is wrong, the test passes and production breaks. Prefer a thin interface you own, and mock that.
- **Don't mock value objects.** Records, DTOs, `String`, `List` — build real ones.
- **Don't verify every interaction.** Over-verification makes tests fail on harmless refactors. Verify the interactions that *are* the behavior; assert return values for the rest.
- **If a test needs five mocks and ten stubs**, the class under test probably has too many dependencies. The painful test is telling you something about the design.

## Common pitfalls

- **Mixing matchers and raw values** in one call → `InvalidUseOfMatchersException`. Wrap literals in `eq()`.
- **`when(spy.method())` on a spy** calls the real method during stubbing. Use `doReturn(...).when(spy).method()`.
- **`when(...)` on a `void` method** doesn't compile. Use `doThrow(...).when(mock).method()`.
- **Capturing without verifying.** An `ArgumentCaptor` only fills during a `verify` (or a stubbed call). `getValue()` before any captured call throws.
- **Asserting on a captor when zero calls happened.** `getValue()` needs exactly one captured value; none (or many) throws. Use `getAllValues()` when several calls are expected.

---

## Try this yourself

1. In `verifyCanCountCalls`, change `broadcast` to send to four recipients and watch `times(3)` fail with a clear "wanted 3, was 4" message. Fix the count.
2. Add a test using `ArgumentCaptor` with `getAllValues()` to assert that `broadcast` sent to the recipients *in order*.
3. Make `gateway.send` return `false` for one recipient (use `thenReturn(true, false, true)` to return different values on successive calls) and assert `broadcast` reports 2 delivered, not 3.

## Self-check

1. Why does `verify(gateway).send("ada@example.com", anyString())` fail, and what's the fix?
2. When does an `ArgumentCaptor` tell you something that a matcher in `verify` cannot?
3. What's the difference between a `mock` and a `spy`, and why do spies require `doReturn(...).when(...)` instead of `when(...).thenReturn(...)`?

---

## Code examples

In reading order:

- `mockito-deep-demo/src/test/java/com/example/MockitoInDepthTest.java` — one test per feature: matchers, `times`/`never`, `ArgumentCaptor`, `thenThrow`, and a `spy`.

Run with `mvn test`.
