# `@RestControllerAdvice` — one place to handle every exception

Topic 03 ended with a problem: an `@ExceptionHandler` only covers its own controller, so the same exception got two different responses depending on where it was thrown. `@RestControllerAdvice` fixes that. It's a bean whose `@ExceptionHandler` methods apply to *every* controller in the app. This is where real Spring Boot projects centralize their error contract — one class, every exception, one consistent body. This topic builds the `GlobalExceptionHandler` that the rest of the module extends.

---

## The problem this solves

In an app with twenty controllers, "a missing book is a 404 with this body" should be defined once. Topic 03's controller-local handler would force you to copy it into all twenty, and change twenty places when the contract changes. `@RestControllerAdvice` lets you write it once.

---

## How it works

`@RestControllerAdvice` is `@ControllerAdvice` + `@ResponseBody` — the same "applies to all controllers" mechanism, with return values serialized to the response body (JSON) instead of resolved as view names. You put `@ExceptionHandler` methods on it exactly as you would in a controller; the difference is reach.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleNotFound(BookNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Book not found");
        return problem;
    }

    @ExceptionHandler(DuplicateIsbnException.class)
    public ProblemDetail handleDuplicate(DuplicateIsbnException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate ISBN");
        return problem;
    }

    // Fallback: anything not matched above. Log the real cause, return a generic 500 — never the stack trace.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);   // detail goes to the LOG, not the client
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
```

The demo has the same two controllers as topic 03 — `BookController` and `ReportController` — both throwing `BookNotFoundException`. With the advice in place, the test shows **both** now return 404 with a body. The local handler is gone; the global one covers everyone. `POST /api/books` with an ISBN that already exists throws `DuplicateIsbnException` and comes back 409.

This `GlobalExceptionHandler` is the spine of the module. Topic 05 makes its bodies real `ProblemDetail`s with custom properties; topic 06 designs the contract; topics 08–12 add handlers for validation failures. It all lands in this one class.

---

## Handler precedence — most-specific exception wins

When a thrown exception could match more than one `@ExceptionHandler`, Spring picks the **most specific** by type distance. Throw a `BookNotFoundException` and the advice has both `handleNotFound(BookNotFoundException)` and `handleUnexpected(Exception)` — Spring calls `handleNotFound`, because `BookNotFoundException` is closer to it in the type hierarchy.

This is why the `Exception.class` fallback doesn't swallow your specific cases: it only runs when nothing more specific matches. The rule is reliable enough that the standard pattern is exactly the demo's — a handful of specific handlers plus one `Exception` fallback at the bottom.

## Where advice sits, and ordering across multiple advices

`@ExceptionHandler` methods (whether in a controller or an advice) are invoked by Spring's `ExceptionHandlerExceptionResolver`, one link in the `HandlerExceptionResolver` chain that the `DispatcherServlet` consults. Controller-local handlers are checked before advice-level ones, so a controller can still override the global handling for its own quirks.

You can have **more than one** `@RestControllerAdvice`. Order them with `@Order` (lower runs first). A common split: one advice for domain exceptions, another (lower priority, `@Order(LOWEST_PRECEDENCE)`) as the catch-all. For this module, one advice is enough.

## Scoping an advice

By default an advice applies to *all* controllers. You can narrow it:

```java
@RestControllerAdvice(basePackages = "com.example.api.public")        // only this package
@RestControllerAdvice(assignableTypes = { BookController.class })      // only these controllers
@RestControllerAdvice(annotations = { RestController.class })          // only @RestController beans
```

Useful when a public API and an internal API need different error contracts. Most apps don't need this; one unscoped advice is the norm.

---

## When to use it / when not to

**Use `@RestControllerAdvice` for:** essentially all exception-to-response mapping in a real app. Domain exceptions, validation failures, framework exceptions — all centralized here.

**Use a controller-local `@ExceptionHandler` (topic 03) only when:** the handling is genuinely specific to one controller and would be wrong elsewhere. Rare.

**Use `@ResponseStatus` (topic 02) only when:** you want the status to live on the exception class itself and you don't need a body. Also rare once you have an advice — but harmless, and self-documenting on the exception.

---

## Common pitfalls

- **No `Exception` fallback.** Without one, any exception you didn't explicitly handle falls through to the default 500 with no body. Add a fallback — but log the cause and return a *generic* message; never echo the exception to the client.
- **The fallback logs nothing.** A generic 500 body with no log line means an outage you can't diagnose. Always `log.error("...", ex)` in the fallback so the stack trace lands in your logs (where it's safe) instead of the response (where it isn't).
- **Two advices both handling the same type with no `@Order`.** Behavior is then unspecified. Give them an order, or merge them.
- **Putting business logic in the advice.** The advice translates exceptions to responses. It should not call repositories, send emails, or make decisions. Throw a well-named exception from the service; let the advice do nothing but map it.
- **Forgetting the advice is a bean.** It must be component-scanned (it's in your base package by default). If it lives outside the scanned packages, Spring never registers it and it silently does nothing.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/04-restcontrolleradvice-global-handling/advice-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```bash
   curl -i http://localhost:8080/api/books/999     # 404 (handled globally)
   curl -i http://localhost:8080/api/reports/999   # 404 — SAME handler now covers ReportController
   curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
        -d '{"title":"Dup","author":"X","isbn":"9780201616224"}'   # 409, duplicate ISBN
   ```
   Contrast with topic 03, where `/api/reports/999` was a 500.
2. Add a new endpoint that throws `new ArithmeticException("/ by zero")`. Without adding a handler, hit it — the `Exception` fallback catches it and returns a generic 500. Check the server log: the real exception is there.
3. Temporarily add `@ExceptionHandler(RuntimeException.class)` returning a 418. Throw a `BookNotFoundException`. It still returns 404 — the more specific handler wins. Remove it.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — the domain record.
3. **`BookNotFoundException.java`** / **`DuplicateIsbnException.java`** — the domain exceptions, plain `RuntimeException`s.
4. **`BookController.java`** / **`ReportController.java`** — both throw `BookNotFoundException`; neither has a local handler.
5. **`GlobalExceptionHandler.java`** — the `@RestControllerAdvice`: specific handlers + an `Exception` fallback that logs and returns a generic 500.
6. **`AdviceTest.java`** — asserts both controllers return 404, the POST returns 409, and the fallback returns 500.

## Self-check

1. What two things does `@RestControllerAdvice` combine, and how is its reach different from a controller-local `@ExceptionHandler`?
2. Your advice has handlers for `BookNotFoundException` and for `Exception`. You throw a `BookNotFoundException`. Which runs, and why doesn't the `Exception` handler swallow it?
3. Why must the `Exception` fallback log the exception but *not* put it in the response body?
</content>
