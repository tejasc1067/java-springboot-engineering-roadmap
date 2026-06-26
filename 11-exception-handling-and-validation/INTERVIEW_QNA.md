# Interview Q&A — Exception Handling and Validation

Each question is the kind you'd actually be asked about a Spring Boot service's error handling. The answers explain not just *what* but *why* and *when* — vocabulary recall won't get you through these.

---

## 1. `@ResponseStatus`, `@ExceptionHandler`, and `@RestControllerAdvice` all map exceptions to responses. When do you use each?

They're three points on a scale from "simplest, least control" to "most control, app-wide."

- **`@ResponseStatus` on the exception class** sets only the status line, no body. Use it when an exception *always* means one status (`BookNotFoundException` is always 404) and you don't need a custom body. Its limits: no body, static status, and you can't put it on library exceptions you don't own.
- **`@ExceptionHandler` inside a controller** is a method that maps an exception to a status *and* a body — but only for that one controller. Use it for genuinely controller-specific handling. The trap is scope: the same exception from another controller isn't covered.
- **`@RestControllerAdvice`** is `@ControllerAdvice` + `@ResponseBody`: `@ExceptionHandler` methods that apply to *every* controller. This is where real apps centralize their error contract. Almost everything lives here.

The progression matters in interviews: they want to hear that you'd centralize in an advice, and reserve the other two for the narrow cases they fit.

---

## 2. How does Spring decide which `@ExceptionHandler` runs when several could match?

Most-specific-exception-type wins. If you throw `BookNotFoundException` and the advice has handlers for both `BookNotFoundException` and `Exception`, Spring picks the `BookNotFoundException` one because it's closer in the type hierarchy. This is exactly why the standard pattern — a handful of specific handlers plus one `@ExceptionHandler(Exception.class)` fallback — works: the fallback only runs when nothing more specific matches. Controller-local handlers are also consulted before advice-level ones, so a controller can override the global handling for its own quirks. Across multiple advices, you control order with `@Order`.

---

## 3. What is `ProblemDetail` / RFC 7807, and why prefer it over a hand-rolled error DTO?

RFC 7807 (now 9457) is the IETF standard "problem details for HTTP APIs," and `ProblemDetail` (in `org.springframework.http`) is Spring 6's implementation. It defines a standard envelope — `type` (a URI naming the *kind* of problem, for machines), `title` (human summary of the kind), `status`, `detail` (this occurrence), `instance` (the path) — plus arbitrary custom members like `timestamp` or `fieldErrors`. Returning it sets `Content-Type: application/problem+json`, which signals to clients and tooling "this body is an error description."

Prefer it because it's a documented, conventional shape clients already understand, it costs nothing over a custom DTO, and it's the framework default. A bespoke `{"error": "..."}` forces every client to learn your private format. The one thing people forget is `type`: `title` is for humans, `type` lets a client branch on the problem *kind* without string-matching `detail`.

---

## 4. By default, a `@Valid` failure returns a 400 with no per-field detail. How do you surface which fields failed?

The validation failure throws `MethodArgumentNotValidException`, and its `BindingResult` holds the field errors — but Spring's default handler doesn't put them in the body. To surface them, make your advice `extend ResponseEntityExceptionHandler` and override `handleMethodArgumentNotValid`:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    List<Map<String,String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
            .toList();
    body.setProperty("fieldErrors", fieldErrors);
    return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
}
```

Now the client gets `fieldErrors: [{field, message}, ...]` and can highlight the exact inputs to fix.

---

## 5. What's the difference between `MethodArgumentNotValidException` and `ConstraintViolationException`, and when do you get each?

Both are validation failures, but from different mechanisms:

- **`MethodArgumentNotValidException`** comes from `@Valid @RequestBody` — validating a bound *object* (a DTO). It's a Spring MVC exception, so `ResponseEntityExceptionHandler` already knows about it.
- **`ConstraintViolationException`** (from `jakarta.validation`) comes from `@Validated` on the *class* with constraints directly on `@PathVariable`/`@RequestParam`, or on the parameters of a `@Validated` service method. It is *not* a Spring MVC exception, so `ResponseEntityExceptionHandler` doesn't handle it — you must write an explicit `@ExceptionHandler(ConstraintViolationException.class)`.

A complete API handles both and maps them to the same `fieldErrors` shape, so clients see one error format whether the bad input was in the body or the URL.

---

## 6. `@Valid` versus `@Validated` — what's the difference?

`@Valid` is the standard `jakarta.validation` annotation. It triggers validation of a bound object (request body, nested fields) but has **no support for validation groups**.

`@Validated` is Spring's annotation. It does two things `@Valid` can't: (1) it accepts **groups** — `@Validated(OnCreate.class)` — so you can validate the same DTO differently per operation; and (2) placed on a **class** (a controller or a service bean), it enables method-level validation of parameters, which is how `@PathVariable`/`@RequestParam` constraints and `@Validated` service methods work.

Rule of thumb: `@Valid` on the request-body parameter for normal cases; `@Validated` when you need groups or method-level/param validation.

---

## 7. `@NotNull`, `@NotBlank`, `@NotEmpty` — which for what?

- `@NotNull` — only rejects `null`. Allows `""` and empty collections. Use for non-string objects (a required `Integer`, a required nested object).
- `@NotBlank` — strings only: not null, not empty, not whitespace-only. This is what you almost always want for a required text field.
- `@NotEmpty` — strings/collections/arrays/maps: not null and size > 0. Allows a whitespace-only string. Use for "this list must have at least one element."

The classic bug is `@NotNull` on a required `String`: it lets `""` through.

---

## 8. How do you validate the same DTO differently for create versus update?

Validation groups. Define marker interfaces (`OnCreate`, `OnUpdate`), tag constraints with the group they apply to (`@Null(groups = OnCreate.class)` and `@NotNull(groups = OnUpdate.class)` on `id`), and trigger the right group per operation with `@Validated(OnCreate.class)` / `@Validated(OnUpdate.class)` — note `@Validated`, because `@Valid` doesn't do groups.

The gotcha worth raising unprompted: a constraint with no `groups` attribute belongs to the `Default` group, and triggering only a custom group does **not** run `Default`. So `@NotBlank title` (no group) is skipped if you validate with just `@Validated(OnCreate.class)`. Fix by including `Default` explicitly: `@Validated({Default.class, OnCreate.class})`.

---

## 9. How do you write a custom validation constraint, including one that spans multiple fields?

A constraint is two pieces: an annotation and a validator.

- **Field-level**: define `@ValidIsbn` annotated `@Constraint(validatedBy = IsbnValidator.class)` with `message()`, `groups()`, `payload()` members; write `IsbnValidator implements ConstraintValidator<ValidIsbn, String>` and put the logic in `isValid`. Convention: return `true` for `null` and let `@NotNull` own nullness.
- **Cross-field / class-level**: put the annotation on the *type* (`@ValidDateRange record LoanPeriod(...)`), and the validator's `isValid` receives the whole object so it can compare `startDate` and `endDate`. To attach the error to a specific field instead of the whole object, call `context.disableDefaultConstraintViolation()` then `context.buildConstraintViolationWithTemplate(msg).addPropertyNode("endDate").addConstraintViolation()`.

Cross-field validation is the usual reason to go custom — built-in constraints only see one field at a time.

---

## 10. Where should validation live — controller, service, or both? And what's the difference between validation and business rules?

Two distinct things:

- **Input validation** — shape, format, range. Expressible as bean-validation annotations because it needs no external data ("title not blank", "isbn 13 digits", "age between 13 and 120"). This belongs at the **controller boundary** (`@Valid`), so bad input is rejected before any logic runs.
- **Business-rule enforcement** — logic that needs current state/context ("are there enough copies in stock?", "is this account allowed to borrow?"). Bean validation *cannot* express this because an annotation has no access to the database. This belongs in the **service**, enforced by throwing a domain exception (`InsufficientCopiesException` → 409).

You can also enable method-level validation on a `@Validated` service as a defensive guard, but a violation there usually signals a *programming* error (the controller should have validated already), so it often maps to 500 rather than 400. The headline: validate input at the edge, enforce business rules in the service, and don't confuse the two.

---

## 11. What must never appear in an error response body, and why is it a security issue?

Never: stack traces, SQL or query fragments, internal class/bean names, raw messages from deep exceptions (which often embed file paths, hostnames, or connection strings with credentials), and — when existence itself is sensitive — confirmation that a resource exists. The body is attacker-readable. A leaked stack trace reveals framework and library versions (→ known CVEs to try), your package structure, and the failing line; a leaked SQL message maps your schema and hints at injection points.

The rule: the body tells the client what *they* can fix; it never describes what your server *is*. The real detail goes to your logs (private), and you give the client at most a correlation id (`traceId`) they can quote to support, which you then look up. In practice that means: `include-stacktrace: never` in production, a fallback handler that logs the exception but returns a generic message, and setting `detail` only from messages you authored.

---

## 12. Walk through what happens, end to end, when a service throws `BookNotFoundException`.

1. The service does `findById(id).orElseThrow(() -> new BookNotFoundException(id))` — domain language, no HTTP.
2. The controller called the service and does **not** catch it, so the exception propagates out of the controller method.
3. Spring's `DispatcherServlet` catches it and runs its `HandlerExceptionResolver` chain; the `ExceptionHandlerExceptionResolver` finds the matching `@ExceptionHandler` in your `@RestControllerAdvice` (most-specific type wins).
4. `handleNotFound` builds a `ProblemDetail` with status 404, a title, the detail from the exception message, the request path as `instance`, and a `timestamp` property.
5. Because the handler returns a `ProblemDetail`, Spring serializes it as `application/problem+json` with status 404.

The point of the design: the service decided *what went wrong* in domain terms; the advice decided *what HTTP that is*; the controller stayed thin and ignorant of both. That separation is what keeps error handling maintainable as the app grows.
</content>
