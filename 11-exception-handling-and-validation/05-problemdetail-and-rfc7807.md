# `ProblemDetail` and RFC 7807 — a standard shape for error bodies

You've been returning `ProblemDetail` since topic 03 without dwelling on it. This topic is the dwell. `ProblemDetail` is Spring 6's implementation of **RFC 7807** (now RFC 9457), the IETF standard for "a problem details object for HTTP APIs." Using it means your errors have a documented, conventional shape that clients and tools already understand — instead of a bespoke JSON blob every API invents differently. This topic covers the format, how to add your own fields, and the one piece that's easy to miss: making Spring's *own* built-in exceptions use the same shape.

---

## The problem this solves

Every API used to invent its own error JSON: `{"error": "..."}`, `{"message": "...", "code": 42}`, `{"errors": [...]}`. A client integrating with five APIs had to write five error parsers. RFC 7807 standardizes the envelope so there's one shape to parse, with a registered media type (`application/problem+json`) that says "this body is a problem description" without the client guessing.

---

## How it works — the five standard fields

`ProblemDetail` (in `org.springframework.http`) models exactly the RFC's fields:

| Field | Meaning | Example |
|-------|---------|---------|
| `type` | A URI identifying the *kind* of problem. Defaults to `about:blank`. | `https://api.example.com/problems/duplicate-isbn` |
| `title` | A short, human-readable summary of the problem type. Stable across occurrences. | `"Duplicate ISBN"` |
| `status` | The HTTP status code, duplicated in the body for convenience. | `409` |
| `detail` | A human-readable explanation specific to *this* occurrence. | `"a book with ISBN 9780201616224 already exists"` |
| `instance` | A URI identifying the specific occurrence — usually the request path. | `"/api/books"` |

You build one with the static factories:

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
problem.setTitle("Duplicate ISBN");
problem.setType(URI.create("https://api.example.com/problems/duplicate-isbn"));
```

Returning it from an `@ExceptionHandler` makes Spring serialize those fields and set `Content-Type: application/problem+json`. That content type is the signal — a client seeing `application/problem+json` knows to parse the RFC shape, regardless of the status code.

### `type` is the field people skip — and shouldn't

`title` is for humans; `type` is for machines. Two different problems can both be 400, but `type: ".../validation-failed"` versus `type: ".../malformed-json"` lets a client branch on the *kind* of problem without string-matching `detail`. Point `type` at a (even non-resolving) URI you own and document. The demo sets a distinct `type` per problem so you can see the difference.

## Custom properties — extending the shape

The RFC explicitly allows extra members beyond the five standard ones. `ProblemDetail.setProperty(name, value)` adds them:

```java
problem.setProperty("timestamp", Instant.now());
problem.setProperty("traceId", currentTraceId());
```

They serialize as top-level JSON fields alongside the standard ones. This is how topic 06 adds `timestamp` and a `fieldErrors` array — they're just custom properties on the same `ProblemDetail`.

## Making Spring's *built-in* exceptions match

Here's the piece that trips people up. Your own `@ExceptionHandler` returning a `ProblemDetail` always produces `application/problem+json`. But Spring throws plenty of its *own* exceptions before your code runs — `HttpMessageNotReadableException` (malformed JSON body), `HttpRequestMethodNotSupportedException` (405), `MethodArgumentNotValidException` (validation, topic 08), `NoResourceFoundException` (404 for an unknown URL). By default those don't go through your advice, and their body is **not** guaranteed to match your `ProblemDetail` shape.

There are two ways to bring them into the fold:

**Option A — a config flag.** Spring Boot can render its built-in exceptions as `ProblemDetail`:

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

(Note: the property is `spring.mvc.problemdetails`, not `spring.webmvc...`.) This gives you RFC bodies for the framework's exceptions with zero code — but you can't customize them (no `timestamp`, no `fieldErrors`).

**Option B — extend `ResponseEntityExceptionHandler`.** Make your advice extend it:

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleNotFound(BookNotFoundException ex) { ... }
    // your domain handlers as before
}
```

`ResponseEntityExceptionHandler` is a base class with `@ExceptionHandler` methods *already written* for every Spring MVC exception, each returning a `ProblemDetail`. By extending it, your advice now handles those too — and you can override individual hooks (like `handleMethodArgumentNotValid`, topic 08) to inject your custom properties. **This is the option real apps choose**, because it's the only one that lets you add `timestamp`/`fieldErrors` to validation errors. The demo uses it: malformed JSON to `POST /api/books` comes back as a `ProblemDetail` 400, the same shape as your domain errors.

---

## When to use it / when not to

- **Use `ProblemDetail` for every error body in a new API.** It's the modern Spring default and costs nothing over a hand-rolled DTO.
- **Extend `ResponseEntityExceptionHandler`** as soon as you care about the shape of framework-thrown errors (you will, by topic 08).
- **A hand-rolled error DTO is acceptable** only if you're matching an existing non-RFC contract a client already depends on. For greenfield, use `ProblemDetail`.

---

## Common pitfalls

- **Customizing your domain errors but leaving Spring's built-ins on the default shape.** A client then sees two different error formats from the same API — your pretty `ProblemDetail` for a 404, a different blob for malformed JSON. Extend `ResponseEntityExceptionHandler` so everything matches.
- **Setting `reason` on `@ResponseStatus` and expecting a `ProblemDetail`.** That routes through `sendError` and bypasses `ProblemDetail` entirely (topic 02).
- **Forgetting `type`.** It defaults to `about:blank`, which carries no information. Set a real, documented URI per problem kind so clients can branch on it.
- **Putting sensitive data in custom properties.** `setProperty("sqlState", ...)` or `setProperty("stackTrace", ...)` leaks internals. Custom properties are still part of the response body — topic 06's rules apply.
- **Expecting `instance` to fill itself in.** When you build a `ProblemDetail` by hand, `instance` is null unless you set it. Topic 06 wires it to the request path.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/05-problemdetail-and-rfc7807/problemdetail-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```bash
   # domain error -> problem+json with type/title/detail
   curl -i http://localhost:8080/api/books/999

   # malformed JSON body -> Spring's HttpMessageNotReadableException, now ALSO problem+json
   curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" -d '{ broken'
   ```
   Note both responses have `Content-Type: application/problem+json` and the same field shape — that's `ResponseEntityExceptionHandler` at work.
2. Comment out `extends ResponseEntityExceptionHandler`, restart, and re-run the malformed-JSON call. The body shape changes (it's no longer your `ProblemDetail`). That contrast is the lesson.
3. Add `problem.setProperty("traceId", "demo-123")` to a handler and confirm it appears as a top-level field in the JSON.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — the domain record.
3. **`BookNotFoundException.java`** — domain exception.
4. **`BookController.java`** — GET that throws not-found, POST that reads a body (so malformed JSON triggers a built-in exception).
5. **`GlobalExceptionHandler.java`** — `extends ResponseEntityExceptionHandler`; sets `type`, `title`, and a custom `timestamp` property.
6. **`ProblemDetailTest.java`** — asserts both a domain error and a malformed-JSON error are `application/problem+json` with the standard fields.

## Self-check

1. What does the `application/problem+json` content type tell a client, and why is that better than `application/json` for an error?
2. What is the difference in purpose between the `type` field and the `title` field?
3. You customized your domain exception handlers but malformed JSON still returns a differently-shaped body. What's the one change that brings Spring's built-in exceptions into your `ProblemDetail` shape?
</content>
