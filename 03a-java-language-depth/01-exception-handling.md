# 01 — Exception Handling (Deep Dive)

Module 01 covered the mechanics — `try`, `catch`, `finally`, `throw`, `throws`. This topic is about the judgment calls: chaining causes, translating exceptions across layers, suppressed exceptions in try-with-resources, the real cost of `throw`, and when checked vs. unchecked is the right call.

If you've never seen `try` / `catch` before, go back to module 01 topic 12 first.

---

## The problem this solves

A REST endpoint calls a service, which calls a repository, which calls JDBC. JDBC throws `SQLException`. What does the endpoint return? "500 Internal Server Error" with no detail leaks the database schema. A 400 misleads the client. And if you just `catch (Exception e) {}` and return `null`, you've created a silent bug.

Exception handling at scale is about three things: **preserving the original cause**, **translating to the right abstraction at each layer**, and **deciding what to surface to the caller**.

---

## Exception chaining: never lose the cause

When you catch an exception and throw a new one, **pass the original as the cause**. Otherwise the stack trace stops at the wrapping line — you lose where the actual failure happened.

```java
// BAD — original SQLException is gone
try {
    repo.save(user);
} catch (SQLException e) {
    throw new UserSaveException("could not save user");
}

// GOOD — chain it
try {
    repo.save(user);
} catch (SQLException e) {
    throw new UserSaveException("could not save user", e);
}
```

The stack trace for the second version shows both exceptions, separated by `Caused by:`. That's how you find the actual SQL error two layers down.

Every custom exception should expose a `(String, Throwable)` constructor for this reason.

---

## Exception translation across layers

A common rule: **catch low, throw high**. Each layer translates exceptions into something the next layer up understands.

```text
JDBC layer       throws SQLException                (low-level, technology-specific)
Repository       catches → throws DataAccessException (data abstraction)
Service          catches → throws UserNotFoundException, etc. (domain)
Controller       catches → returns HTTP 404         (transport)
```

Why bother? The controller shouldn't know that the database is SQL. If you migrate to MongoDB, only the repository changes. The service still throws `UserNotFoundException`, the controller still returns 404. Spring's `DataAccessException` hierarchy is built exactly on this idea.

---

## Suppressed exceptions (try-with-resources)

`try-with-resources` closes resources for you. But what if both the `try` body *and* the `close()` throw? In old `finally` code, the close exception would mask the original — disaster for debugging.

Java 7+ keeps both. The original exception propagates; the close exception is attached as a **suppressed** exception.

```java
try (var conn = openConnection()) {
    conn.executeBadQuery();   // throws SQLException ←  primary
}                              // close() also throws ← suppressed onto the SQLException

// Stack trace prints:
//   Exception in thread "main" java.sql.SQLException: bad query
//     ...
//     Suppressed: java.sql.SQLException: connection close failed
//     ...
```

Access them programmatically with `Throwable.getSuppressed()`. If you write `finally { resource.close(); }` by hand instead of try-with-resources, you have to call `Throwable.addSuppressed()` yourself — easy to forget, which is why try-with-resources is the strict default.

---

## Checked vs. unchecked: when each is right

Module 01 explained the mechanical difference. The judgment call is *when to use which*.

**Use checked when** the caller can plausibly recover and you want to *force* them to acknowledge it. Classic example: `IOException` reading a config file — caller might retry, fall back to defaults, or fail fast with a clear message.

**Use unchecked when** the error indicates a programming bug or an unrecoverable state. Classic example: `IllegalArgumentException` on a negative price — the caller passed invalid input; no try/catch will fix that.

Modern Java (Spring, JPA, most newer libraries) leans heavily unchecked. The argument: checked exceptions force `throws` clauses up every call chain, polluting signatures. In practice most "I can't recover here" code just rethrows. Unchecked exceptions let callers handle errors at the level that *can* recover — usually the request boundary — instead of every layer in between.

A reasonable default for your own code: **unchecked, with a custom exception type per error category**.

---

## The real cost of throwing

Throwing an exception is expensive. The JVM has to capture the stack trace at the moment of throw — walking up the frames, recording line numbers. This is fine for genuine error paths but disastrous if you use exceptions for control flow.

```java
// SLOW — throws and catches once per non-integer in a 10M-string list
for (String s : strings) {
    try {
        nums.add(Integer.parseInt(s));
    } catch (NumberFormatException ignored) { }
}
```

Two fixes: validate first (`s.matches("-?\\d+")`), or use a parse-with-result API. Either way, **don't pay stack-trace tax for an expected outcome**.

(For genuinely rare errors — once per request, not once per element — the cost is negligible. Don't optimize prematurely; just don't write the loop above.)

---

## When to catch vs. let it propagate

The fact that you *can* catch an exception doesn't mean you should. Catch when you can:

1. **Recover** (retry, fall back, return a sensible default).
2. **Translate** to a more meaningful exception for callers (see translation above).
3. **Add context** (rethrow with extra info: the user ID, the file path).
4. **Cross a boundary** that callers don't understand the original type at (e.g. controller boundary turning exceptions into HTTP responses).

If none of those apply, let it propagate. A `catch (Exception e) { log.error(e); throw e; }` that adds nothing is just noise — and it puts the log line at a confusing place in the stack.

---

## Common pitfalls

- **Swallowing without logging.** `catch (Exception e) {}` — the silent killer. Hours of debugging await. Minimum: log with stack trace and the operation context.
- **Catching `Throwable`.** Catches `OutOfMemoryError`, `StackOverflowError`, your own assertions. You can't sensibly recover from these. Catch `Exception` at most, and usually something more specific.
- **Logging *and* rethrowing.** Now the same error appears twice in the logs at different stack depths. Pick one: either handle it (log + don't rethrow) or propagate it (rethrow + don't log). Log at the top boundary only.
- **`throw new RuntimeException(e.getMessage())`.** Two bugs: lost the cause, *and* you copied only the message — the inner stack is gone. Use `throw new RuntimeException("context", e)`.
- **`finally` that throws.** A `throw` inside `finally` replaces any in-flight exception. The original error vanishes. Don't throw from `finally` unless you genuinely mean to.

---

## Code examples

1. `ExceptionChaining.java` — wrapping with cause vs. without; reading `Caused by:` in the stack trace.
2. `TryWithResourcesAndSuppressed.java` — what happens when both `try` body and `close()` throw.
3. `ExceptionTranslation.java` — a 3-layer (jdbc → repo → service) translation chain.
4. `CheckedVsUncheckedChoice.java` — same operation, two designs, when each makes sense.
5. `ExceptionAsControlFlowSlow.java` paired with `ValidateFirstFast.java` — the perf cost and the fix.
6. `FinallySwallowsOriginal.java` — the bug where `finally` discards an in-flight exception.

---

## Try this yourself

1. In `ExceptionChaining.java`, comment out the `, e` cause argument. Run it and compare the stack trace — note how everything below the wrap line disappears.
2. In `TryWithResourcesAndSuppressed.java`, switch from try-with-resources to a manual `finally { close(); }` and observe that the close exception now masks the body exception.
3. Run `ExceptionAsControlFlowSlow.java` and `ValidateFirstFast.java` with the same input list of 1 million strings. Time them. The difference should be roughly 10–100×.

---

## Self-check

1. You catch a `SQLException` in your repository and want to throw a `RepositoryException`. Write the line that preserves the original cause. Why does omitting the cause hurt debugging?
2. A `try-with-resources` block's body throws `SQLException`, and the resource's `close()` also throws. Which exception does the caller see? How do they retrieve the other one?
3. Give one concrete case where a checked exception is the right choice, and one where unchecked is the right choice. Explain the reasoning in one sentence each.
