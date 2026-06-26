# `@ExceptionHandler` in a controller — status *and* body, one controller at a time

`@ResponseStatus` (topic 02) gives you the status line and nothing else. `@ExceptionHandler` gives you a method that runs when a chosen exception is thrown — so you control the status, the headers, and the body. This topic covers the method-local form: an `@ExceptionHandler` written inside a controller, which handles exceptions from *that controller only*. Seeing that scope limit firsthand is what motivates the global advice in topic 04.

---

## The problem this solves

You want a missing book to return a 404 **with a body** the client can read:

```json
{ "status": 404, "detail": "book 999 not found", "instance": "/api/books/999" }
```

`@ResponseStatus` can't do the body. A `try`/`catch` in every method can, but it's repeated noise. `@ExceptionHandler` is the middle ground: write the mapping once per controller, as a method.

---

## How it works

An `@ExceptionHandler` method lives inside a `@RestController`. It names the exception type(s) it handles. When a handler method in the same controller throws that exception, Spring calls the `@ExceptionHandler` method instead of propagating:

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping("/{id}")
    public Book one(@PathVariable Long id) {
        Book book = store.get(id);
        if (book == null) {
            throw new BookNotFoundException(id);
        }
        return book;
    }

    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleNotFound(BookNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Book not found");
        return problem;
    }
}
```

Three things to notice:

- **The return type is `ProblemDetail`** — Spring 6's RFC 7807 body type (topic 05 covers it fully). Returning it sets `Content-Type: application/problem+json` and serializes the fields. The status comes from `forStatusAndDetail(...)`. You could also return `ResponseEntity<ProblemDetail>` for header control, or any object plus a `@ResponseStatus` on the handler method.
- **The handler method receives the exception**, so it can read `ex.getMessage()` and build a body from it.
- **`BookNotFoundException` no longer needs `@ResponseStatus`.** The handler decides the status now. (If both are present, the `@ExceptionHandler` wins for this controller.)

The demo proves the body shows up: `GET /api/books/999` returns 404 with `detail: "book 999 not found"`, and the test asserts both the status and the `detail` field via `jsonPath`.

## Handling several exceptions in one method

One `@ExceptionHandler` can list multiple types, useful when they map to the same response:

```java
@ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
public ProblemDetail handleBadState(RuntimeException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
}
```

The parameter type must be a supertype of all the listed exceptions (here `RuntimeException`).

---

## The scope limit — this is the whole point

An `@ExceptionHandler` declared in a controller handles exceptions **only from that controller**. The demo includes a second controller, `ReportController`, that also throws `BookNotFoundException` — but has no handler of its own. The test shows the difference:

- `GET /api/books/999` → **404 with a body** (the `BookController` handler ran).
- `GET /api/reports/999` → **500** (no handler in `ReportController`; `BookNotFoundException` here has no `@ResponseStatus`, so it falls through).

Same exception, two different outcomes, because the handler is local. In an app with twenty controllers, you'd either copy this handler into all twenty (and update twenty places when the contract changes) or — far better — lift it into a `@RestControllerAdvice` that applies to all of them. That's topic 04.

---

## When to use it / when not to

**Use a controller-local `@ExceptionHandler` when:**
- The handling is genuinely specific to one controller — a translation that makes no sense elsewhere.
- You're prototyping and haven't built the global advice yet.

**Lift it to `@RestControllerAdvice` (topic 04) when:**
- More than one controller throws the exception (almost always true for domain exceptions like "not found").
- You want one place that defines the error contract for the whole app.

In practice, real codebases keep *almost everything* in advice and use controller-local handlers only for the rare controller-specific case.

---

## Common pitfalls

- **Expecting it to apply app-wide.** It doesn't — it's local. Throwing the same exception from another controller skips this handler. (Topic 04.)
- **Returning a plain `String` or `Map` and wondering why the status is still 200.** The handler's return value is the *body*; the status defaults to 200 unless you set it — via `ProblemDetail.forStatus(...)`, a `ResponseEntity`, or `@ResponseStatus` on the handler method. Forgetting the status is the most common bug here.
- **Catching too broad a type.** `@ExceptionHandler(Exception.class)` in a controller swallows *everything* thrown there, including bugs you'd rather see as a 500. Handle specific types.
- **Two handlers that both match.** If a controller has `@ExceptionHandler(RuntimeException.class)` and `@ExceptionHandler(BookNotFoundException.class)`, Spring picks the most specific (the `BookNotFoundException` one) for a `BookNotFoundException`. Know the rule so you don't accidentally shadow a specific handler with a broad one.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/03-exceptionhandler-in-a-controller/exceptionhandler-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```bash
   curl -i http://localhost:8080/api/books/999     # 404 WITH a body (detail: book 999 not found)
   curl -i http://localhost:8080/api/reports/999   # 500 — no handler in ReportController
   ```
2. Copy the `handleNotFound` method into `ReportController`. Re-run the second curl — now it's a 404 too. You just saw why duplication hurts: two copies to maintain. Delete it again.
3. Remove the status from the handler (return `ex.getMessage()` as a `String` with no `@ResponseStatus`). Re-run — the status is now 200 with the message as the body. Status is not automatic for handler methods.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — the domain record.
3. **`BookNotFoundException.java`** — plain `RuntimeException` now; the handler owns the status.
4. **`BookController.java`** — the controller with its local `@ExceptionHandler` returning a `ProblemDetail`.
5. **`ReportController.java`** — throws the same exception but has *no* handler, proving the scope limit.
6. **`ExceptionHandlerTest.java`** — asserts 404+body for the handled case, 500 for the unhandled one.

## Self-check

1. What can an `@ExceptionHandler` method control that `@ResponseStatus` cannot?
2. A handler method returns `ex.getMessage()` (a `String`) and you set no status anywhere. What status does the client get, and why?
3. You have an `@ExceptionHandler(BookNotFoundException.class)` in `BookController`. Another controller throws `BookNotFoundException`. What status does *that* controller's client get, and what's the fix?
</content>
