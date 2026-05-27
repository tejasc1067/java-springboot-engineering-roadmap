# Designing for Testability

Some code is easy to test and some is nearly impossible — and the difference is almost never the test, it's the *design* of the code. This topic shows the one structural change that turns untestable code into testable code: passing dependencies in instead of creating them inside. It's also the reason Spring's dependency injection exists, which you'll meet in module 07.

---

## The problem this solves

You've been testing pure calculators — give an input, check the output. Real code isn't all calculators. It talks to other things: a database, an email server, the system clock. Watch what happens when a method reaches for those things itself:

```java
public boolean sendDueReminder(String user) {
    MessageGateway gateway = new SmtpMessageGateway();   // builds its own collaborator
    LocalDate today = LocalDate.now();                   // reads the real clock
    if (today.getDayOfMonth() == 1) {
        return gateway.send(user, "Your monthly payment is due.");
    }
    return false;
}
```

Now try to unit-test "on the 1st of the month, it sends a reminder." You can't:

- **You can't control the date.** `LocalDate.now()` reads the real system clock. The `getDayOfMonth() == 1` branch only runs on the actual 1st. Your test would pass on one day a month and do nothing useful the rest — a test that depends on the calendar is worse than no test.
- **You can't avoid the real send.** The method builds its own `SmtpMessageGateway` with `new`. There's no way for a test to slip in a fake. Run the test on the 1st and it tries to email a real person.

The code isn't *wrong* — it works in production. It's *untestable*, because it decides for itself what to depend on. The test has no way in.

## How it works: inject the dependencies

The fix is to stop reaching out and start accepting. Take the gateway and the clock as constructor parameters:

```java
public class TestableRefactored {
    private final MessageGateway gateway;
    private final Clock clock;

    public TestableRefactored(MessageGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public boolean sendDueReminder(String user) {
        LocalDate today = LocalDate.now(clock);   // the injected clock, not the system one
        if (today.getDayOfMonth() == 1) {
            return gateway.send(user, "Your monthly payment is due.");
        }
        return false;
    }
}
```

Two specific moves:

1. **Depend on an interface (`MessageGateway`), not a concrete class.** Production passes in `SmtpMessageGateway`; a test passes in a fake.
2. **Make the clock a dependency too.** `java.time.Clock` is a real JDK class built for exactly this. `Clock.fixed(...)` gives you a clock frozen at a chosen instant, so "today" is whatever the test says.

Now the test is in full control:

```java
// A hand-written fake: records sends instead of performing them.
static class RecordingGateway implements MessageGateway {
    final List<String> sent = new ArrayList<>();
    public boolean send(String to, String message) {
        sent.add(to + " | " + message);
        return true;
    }
}

@Test
void sendsReminderOnTheFirstOfTheMonth() {
    RecordingGateway gateway = new RecordingGateway();
    Clock march1 = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneId.of("UTC"));
    TestableRefactored service = new TestableRefactored(gateway, march1);

    boolean sent = service.sendDueReminder("ada@example.com");

    assertThat(sent).isTrue();
    assertThat(gateway.sent).containsExactly("ada@example.com | Your monthly payment is due.");
}
```

The date is fixed, no real email is sent, and the assertion is exact and stable. Same logic, completely testable — only the *shape* changed.

## Test doubles, by hand (for now)

`RecordingGateway` is a **test double**: a stand-in for a real dependency that you control. We wrote it by hand here so you can see there's no magic — it's just a class implementing the interface, recording what it was asked to do. In topic 08, Mockito generates these for you so you don't have to write a `RecordingGateway` for every dependency. But the *enabling condition* is what you did here: the code accepts its dependencies, so something fake can be passed in.

## The smells that make code untestable

When you can't figure out how to test something, look for these. Each one means "this code decides its own dependencies, so a test can't get in":

- **`new SomeService()` inside a method** that does real work (I/O, sending, persisting). Inject it instead.
- **`LocalDateTime.now()` / `Instant.now()` / `new Random()`** read directly. Inject a `Clock` (or the random source). Anything that changes by itself between runs makes assertions impossible.
- **`static` calls to things that do work** (a static `Database.query(...)`, a singleton's `getInstance().doThing()`). Statics can't be substituted in a plain unit test.
- **Reading config or environment directly** (`System.getenv(...)`) deep inside logic. Pass the value in.

The pattern behind all of them: **a class should be given what it needs, not go get it.** Given things can be faked; fetched things can't.

## Why this is the bridge to Spring

If every class takes its dependencies in the constructor, *someone* has to create them all and wire them together. In these examples that someone is `main` (in production) or the test (in tests). In a real app with hundreds of classes, doing that by hand is tedious. That wiring job — "create the objects, hand each one its dependencies" — is exactly what a **dependency injection container** does, and that's what Spring is at its core (module 07). You're learning the manual version now so the framework version makes sense later: Spring isn't magic, it's automated constructor injection.

## When not to over-do it

Injecting dependencies is for *collaborators that do work* — gateways, repositories, clocks. Don't inject value objects or pure helpers that have no side effects and don't vary (a `List`, a `BigDecimal`, a stateless formatter you can just `new`). Injecting everything "to be safe" makes constructors enormous and code harder to read. Inject the things a test needs to *control or observe*; build the rest normally.

## Common pitfalls

- **Injecting the dependency but then ignoring it** and still calling `new`/`now()` inside. Easy to do in a half-finished refactor. The test will catch it (the fake won't be used).
- **Field injection instead of constructor injection.** Setting dependencies via setters or reflection makes it possible to construct a half-wired object. Constructor injection makes the dependency required and the object always valid — prefer it.
- **Reaching for `Clock` only after a flaky test bites you.** If logic depends on the current time, inject the clock from the start; retrofitting it under a deadline is painful.
- **Mocking the clock with a string-matched "now."** Use `Clock.fixed(...)` and `LocalDate.now(clock)` — a real, supported idiom — rather than hacks.

---

## Try this yourself

1. In `TestableRefactored`, add a test for `2026-02-01` (also a 1st) to confirm the branch fires for any month, not just March.
2. Try to write a passing, non-flaky test for `HardToTestBroken` without changing it. You can't — write down exactly where you get stuck. That stuck feeling is the lesson.
3. Add a second dependency to `TestableRefactored`: an injected `int reminderDayOfMonth` (default 1). Inject it, and write a test that the reminder fires on the 15th when configured that way.

## Self-check

1. Name the two things in `HardToTestBroken` that make it untestable, and the one change that fixes both.
2. Why does depending on an *interface* (`MessageGateway`) rather than a concrete class matter for testing?
3. How is the manual wiring you do in these tests related to what Spring will do for you in module 07?

---

## Code examples

In reading order (broken first, then the fix):

- `testability-demo/src/main/java/com/example/MessageGateway.java` — the interface that becomes the seam.
- `.../SmtpMessageGateway.java` — the "real" implementation (throws, to prove it must not run in a test).
- `.../HardToTestBroken.java` — builds its own gateway and reads the real clock; can't be unit-tested.
- `.../TestableRefactored.java` — same logic, dependencies injected via the constructor.
- `.../HardToTestBrokenTest.java` — the doomed test attempt, `@Disabled`, documenting *why* it can't work.
- `.../TestableRefactoredTest.java` — a hand-written fake gateway + a fixed `Clock`; the test that the refactor makes possible.

Run with `mvn test`.
