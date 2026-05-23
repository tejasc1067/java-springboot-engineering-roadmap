# 02 — Custom Exceptions and Best Practices

Built-in exceptions like `IllegalArgumentException` and `IOException` describe *what kind of thing* went wrong at a technical level. Custom exceptions describe *what failed in your domain*: `UserNotFoundException`, `InsufficientFundsException`, `OrderAlreadyShippedException`. The right custom exceptions make your code self-documenting and your error handling much sharper at boundaries (REST controllers, message handlers, retry logic).

This topic is about designing them well — not just "extend `RuntimeException` and add a message."

---

## The problem this solves

Imagine a controller that handles three error cases: the user doesn't exist, the user is locked, and the user lacks permission. If your service throws `RuntimeException("user not found")`, `RuntimeException("user locked")`, and `RuntimeException("forbidden")`, the controller is forced to inspect the message string — which is fragile, untestable, and breaks at the first typo or translation.

With three custom exception types, the controller does `catch (UserNotFoundException) → 404`, `catch (UserLockedException) → 423`, `catch (UnauthorizedException) → 403`. Same logic, but driven by types, not strings.

---

## How to define a custom exception properly

Minimum: a class extending the right base, with at least the constructors that take `(String message)` and `(String message, Throwable cause)`. The cause constructor lets you chain low-level exceptions (see [topic 01](01-exception-handling.md)).

```java
public class InsufficientFundsException extends RuntimeException {

    private final String accountId;
    private final double requested;
    private final double available;

    public InsufficientFundsException(String accountId, double requested, double available) {
        super(String.format("account %s: requested %.2f, only %.2f available",
                            accountId, requested, available));
        this.accountId = accountId;
        this.requested = requested;
        this.available = available;
    }

    public String getAccountId()   { return accountId; }
    public double getRequested()   { return requested; }
    public double getAvailable()   { return available; }
}
```

Note what's happening:
- The constructor takes *structured* data (the account ID, the numbers) — not just a pre-built string.
- The exception builds a human message from that data, but also exposes the fields. The controller layer can do `e.getAvailable()` to put a number in the JSON response, without parsing the message.
- Extends `RuntimeException` — the modern default. Use `Exception` only if you genuinely want to force callers to handle it (see [topic 01](01-exception-handling.md) for the trade-off).

---

## Should it be checked or unchecked?

Quick rule (same as topic 01, applied here):

- **Unchecked** for things the caller can't reasonably recover from in the calling method itself — most business errors fall here. Examples: `UserNotFoundException`, `InsufficientFundsException`, `InvalidStateException`. The handler is usually far away (the controller, a retry policy), not the immediate caller.
- **Checked** when the immediate caller *must* decide what to do — typically I/O or infrastructure boundaries where the caller might retry, fall back, or give up with explicit handling.

When in doubt: unchecked. Industry has moved that way for two decades.

---

## Hierarchy: organize them, don't just dump them at the root

A flat list of 40 unrelated `RuntimeException` subclasses becomes unmanageable. Group them under abstract bases:

```text
ApplicationException (abstract, extends RuntimeException)
├── ValidationException
│    ├── InvalidEmailException
│    └── InvalidPriceException
├── NotFoundException
│    ├── UserNotFoundException
│    └── OrderNotFoundException
└── BusinessRuleException
     ├── InsufficientFundsException
     └── OrderAlreadyShippedException
```

Now a controller can do:

```java
catch (NotFoundException e)       → 404
catch (ValidationException e)     → 400
catch (BusinessRuleException e)   → 422
catch (ApplicationException e)    → 500 with controlled details
```

One catch per *category*, not per specific subclass. New exceptions slot in without touching the controller.

---

## Centralized handling at the boundary

In a Spring Boot app, the cleanest pattern is `@ControllerAdvice` translating exceptions to HTTP responses at the edge. The service layer stays clean — it just throws the right domain exception. The controller doesn't try/catch at all.

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> handle(NotFoundException e) {
        return ResponseEntity.status(404).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorBody> handle(ValidationException e) {
        return ResponseEntity.status(400).body(new ErrorBody(e.getMessage()));
    }
}
```

This pattern is module-13 (Spring Boot) territory — but the design idea applies anywhere there's a boundary: a message handler, a CLI's main loop, a Kafka consumer's error topic logic.

---

## Common pitfalls

- **`UserException` as the only custom type.** Too generic — callers can't distinguish "not found" from "locked." Refactor into a hierarchy as soon as you have more than two distinct failure modes.
- **Storing pre-built messages, not structured data.** `new UserNotFoundException("user 42 not found")` is fine for logs but useless for JSON responses. Take the ID as a field, build the message from it.
- **Subclassing `Exception` reflexively.** Then every layer needs `throws UserNotFoundException`. Now the throws clauses leak through the entire stack. Default to `RuntimeException` unless you have a reason.
- **Forgetting the `(message, cause)` constructor.** First time you wrap a low-level exception you'll wish you had it. Always add both constructors.
- **Throwing custom exceptions from libraries you're publishing.** Library users now depend on your exception hierarchy. Be deliberate about what you expose. Internal helpers can be `RuntimeException` directly.

---

## Code examples

1. `BasicCustomException.java` — minimal unchecked exception with `(message)` and `(message, cause)` constructors.
2. `StructuredFieldsException.java` — exception carrying typed fields, not just a message.
3. `HierarchyDesign.java` — abstract base + grouped subclasses, demonstrating one catch per category.
4. `CheckedDomainException.java` — when you do want checked: forcing caller acknowledgement at a boundary.
5. `MessageOnlyAntiPattern.java` paired with `TypedExceptionProper.java` — message-string parsing vs. typed catches.

---

## Try this yourself

1. In `HierarchyDesign.java`, add a new exception class `EmailAlreadyTakenException` under the right parent. Notice how the existing catches handle it without changes.
2. In `StructuredFieldsException.java`, extract a field like `requested` into the JSON-style output. Now imagine doing the same by parsing the message string — feel why structured fields win.
3. In `CheckedDomainException.java`, change the exception's base from `Exception` to `RuntimeException`. Watch the `throws` clauses disappear from every caller. Decide whether that's an improvement or a loss of safety.

---

## Self-check

1. You're designing exceptions for an order service: order not found, order already shipped, payment declined. Sketch the hierarchy. Which catches would a controller declare?
2. Why is storing structured fields (account ID, amounts) on the exception better than only storing a pre-built message?
3. Give one reason to prefer a checked custom exception, and one reason to prefer unchecked. Which would you default to for a `UserNotFoundException` and why?
