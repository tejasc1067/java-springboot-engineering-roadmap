# Designing an error contract — stability, and what never to leak

You now have the mechanism (`@RestControllerAdvice`) and the format (`ProblemDetail`). This topic is about *policy*: what fields your errors always carry, what they must never carry, and why the "never" list is a security boundary, not a style preference. A good error contract is one a client can depend on and an attacker learns nothing from.

---

## The problem this solves

Two failures, opposite directions:

- **Too little.** A bare `ProblemDetail` with `detail: null` tells the client nothing actionable. They file a support ticket instead of fixing their request.
- **Too much.** `include-stacktrace: always` (topic 01) tells the client — and anyone sniffing the response — your framework versions, package layout, and SQL. They use it to find a known CVE in one of those versions.

The contract threads between them: enough for the client to self-correct, nothing that describes your server's internals.

---

## The contract — a stable shape on every error

Pick a shape and apply it to *every* error path. The one this module uses, built on `ProblemDetail` plus two custom properties:

```json
{
  "type": "https://api.example.com/problems/book-not-found",
  "title": "Book not found",
  "status": 404,
  "detail": "book 999 not found",
  "instance": "/api/books/999",
  "timestamp": "2026-06-26T12:00:00.000Z"
}
```

- The **five RFC fields** (topic 05). `type` lets clients branch by problem kind; `detail`/`instance` are occurrence-specific.
- **`timestamp`** — when the error happened, so a client (or you, reading logs) can correlate it with a log line.
- **`fieldErrors`** — added for validation responses (topic 08): an array of `{ field, message }`. Absent on non-validation errors.

The key discipline is *consistency*: a 404, a 409, and a 400 all come back with the same envelope, so the client writes one error parser. The demo centralizes this in the advice with a small helper so every handler produces the same shape without copy-paste:

```java
private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setTitle(title);
    p.setInstance(URI.create(request.getRequestURI()));
    p.setProperty("timestamp", Instant.now());
    return p;
}
```

Every handler calls `problem(...)`. One place defines the envelope; changing it changes every error at once.

---

## The "never leak" list — this is security, not style

An error body is attacker-readable. These must **never** appear in it:

| Never include | Why it's dangerous |
|---------------|--------------------|
| **Stack traces** | Reveal framework + library versions (→ known CVEs), internal class names, package structure, and the exact line that failed. |
| **SQL or query fragments** | `SQLException` messages often echo the query and table/column names — a map of your schema, and a hint for SQL-injection probing. |
| **Internal class / bean names** | `com.acme.billing.internal.LegacyChargeProcessor` tells an attacker how your system is built. |
| **Raw exception messages from deep layers** | They frequently embed file paths, host names, connection strings, or credentials in connection URLs. |
| **Whether a resource exists, when that itself is sensitive** | Sometimes a 404 (instead of 403) is correct *precisely to avoid* confirming a record exists. |

The rule in one sentence: **the body tells the client what *they* can fix; it never describes what your server *is*.** The real exception detail belongs in your logs (private, access-controlled), reached via the fallback handler's `log.error("...", ex)`. The client gets a generic message and, ideally, a correlation id (`traceId`) they can quote to support — which you then look up in the logs.

The demo proves the boundary: an endpoint throws an exception whose message contains a fake "secret" (`jdbc:postgresql://prod-db:5432 password=hunter2`). The fallback handler logs that full message but returns only `"An unexpected error occurred."` The test asserts the secret is **absent** from the response body. That assertion is the security control, written as a test.

---

## A correlation id beats a stack trace

Instead of leaking internals, give the client a handle:

```java
String traceId = UUID.randomUUID().toString();
log.error("Unhandled exception [traceId={}]", traceId, ex);   // full detail in the log
problem.setProperty("traceId", traceId);                       // opaque handle in the response
```

Now a user can report "traceId abc-123" and you can find the exact stack trace in your logs — without ever putting it on the wire. In a real system the trace id usually comes from your tracing framework (module 19); here it's a `UUID`.

---

## 400 vs 422 — make the call once, in the contract

Module 09 topic 08 framed the debate; the contract is where you settle it. Spring's default for `@Valid` failures is **400**. Whichever you choose (400 for "your input is wrong" or 422 for "well-formed but semantically invalid"), apply it uniformly and document it in the contract. The only wrong answer is mixing them arbitrarily. This module stays on Spring's default of 400 for validation failures.

---

## Common pitfalls

- **Different shapes for different errors.** A pretty `ProblemDetail` for your domain errors and the raw default body for framework errors (the topic 05 trap). Clients can't write one parser. Extend `ResponseEntityExceptionHandler` and route everything through your helper.
- **Leaking via `detail`.** Setting `detail` to `ex.getMessage()` for a *deep* exception copies whatever that library put in the message — possibly a connection string. Set `detail` from messages *you* wrote (your domain exceptions), not from arbitrary caught exceptions.
- **`include-stacktrace: on_param`.** This returns the stack trace whenever the request has `?trace=true`. Attackers know this flag. Use `never` in production.
- **No timestamp or correlation id.** When a user reports an error, you have nothing to find it by. Add at least a timestamp; ideally a trace id.
- **Documenting the happy path only.** Clients integrate against your error shape too. If it's not in your OpenAPI doc (module 09 topic 13), they discover it by breaking things in production.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/06-designing-an-error-contract/contract-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```bash
   curl -i http://localhost:8080/api/books/999    # 404, full contract: type/title/detail/instance/timestamp
   curl -i http://localhost:8080/api/books/leak    # 500, generic body — the "secret" is NOT here
   ```
   Then look at the server log for the second call: the secret message *is* there. Body clean, log complete.
2. Edit the fallback to `setDetail(ex.getMessage())`. Re-run the leak call — now the secret is in the response. Run the test; it fails. Revert. You just felt the security control catch a regression.
3. Add a `traceId` property to the fallback and confirm the same id appears in both the response body and the log line.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** / **`BookNotFoundException.java`** — domain.
3. **`BookController.java`** — a normal not-found path plus a `/leak` endpoint that throws an exception carrying a fake secret.
4. **`GlobalExceptionHandler.java`** — the `problem(...)` helper that stamps every error with `instance` + `timestamp`; a fallback that logs the secret but returns a generic body.
5. **`ErrorContractTest.java`** — asserts the contract fields are present, and asserts the secret is absent from the 500 body.

## Self-check

1. State the one-sentence rule for what belongs in an error body versus what doesn't.
2. An attacker reads a stack trace you leaked. Name two concrete things they now know that help them attack you.
3. Your fallback handler needs to stay debuggable without leaking. What do you put in the *log*, and what (minimal) handle do you put in the *response* so the two can be connected later?
</content>
