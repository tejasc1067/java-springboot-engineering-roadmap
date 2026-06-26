# Common pitfalls — the exception-handling and validation bugs that bite teams

You now have every mechanism this module teaches: the three ways to map an exception, the `ProblemDetail` contract, domain exceptions, and the full validation toolkit. This capstone is a catalog of the bugs people hit *while wiring those together* — each one a real symptom, its cause, and the one-line fix. Most are silent: the code compiles, the app boots, and the bug only shows up when bad input arrives in production. Three of them are runnable as before/after in the demo, so you can watch the failure and the fix.

---

## The problem this solves

Almost every bug in this catalog has the same shape: **something that looks wired up isn't actually running.** A `@NotBlank` that never fires. A handler that catches an exception and throws away the evidence. An advice that reshapes your domain errors but not Spring's. They don't crash — they quietly do the wrong thing, which is worse, because you find out from a customer instead of a stack trace. The fix is almost always small; the skill is *recognizing the symptom*.

---

## Pitfall 1: forgetting `@Valid`

**Symptom:** the DTO is covered in `@NotBlank` / `@Email` / `@Size`, but bad input gets a `201 Created` anyway. Validation appears to do nothing.

**Cause:** the constraint annotations on the DTO are inert metadata. `@Valid` on the `@RequestBody` parameter is what tells Spring to *run* them. Without it, Spring binds the body and calls your method — no validation step.

```java
// BROKEN — constraints present, but never executed
@PostMapping("/without-valid")
public CreateUserRequest create(@RequestBody CreateUserRequest req) { ... }   // {"username":""} -> 201

// FIX — @Valid runs the constraints before the body
@PostMapping("/with-valid")
public CreateUserRequest create(@Valid @RequestBody CreateUserRequest req) { ... }   // {"username":""} -> 400
```

This is the demo's headline pitfall. `UserController` has both endpoints; the test `missingValid_acceptsBadInput_returns201` asserts the broken one returns 201 for blank input — the bug, written as a *passing* test — and `withValid_rejectsBadInput_returns400WithFieldErrors` asserts the fix returns 400 with a `fieldErrors` array. The lesson is that "it compiled and returned 201" is not evidence validation works. Test with a deliberately invalid request.

---

## Pitfall 2: `@NotNull` where you meant `@NotBlank`

**Symptom:** a required `String` field rejects `null` but happily accepts `""` and `"   "`. Empty usernames slip into the database.

**Cause:** `@NotNull` only forbids `null`. On a `String` you almost always want `@NotBlank` (not null, not empty, not whitespace-only). `@NotEmpty` sits between them — it forbids `""` but *allows* `"   "`.

```java
public record CreateUserRequest(
        @NotNull  String username,   // BROKEN: "" passes
        @NotBlank String email) {}   // FIX: "" and "   " both rejected
```

Rule of thumb: `@NotBlank` for required text, `@NotNull` for required objects/numbers, `@NotEmpty` for collections you want non-empty. The demo's DTO uses `@NotBlank` deliberately.

---

## Pitfall 3: catching `Exception` and swallowing it

**Symptom:** an operation fails, but the client gets `200 OK` and there's no log line. An outage you can't see, because everything *reports* healthy.

**Cause:** a `try/catch (Exception)` that returns a success value (or logs nothing) on failure. The exception is consumed; the advice never runs; the failure is now invisible.

```java
// BROKEN — the charge threw, the client is told "ok", nothing is logged
@GetMapping("/swallow")
public String charge() {
    try { return chargeCard(); }
    catch (Exception ex) { return "ok"; }   // failure vanishes
}

// FIX — don't catch what you can't handle; let it reach the advice
@GetMapping("/propagate")
public String chargeProperly() { return chargeCard(); }   // -> 500, cause logged
```

The demo's `PaymentController` has both. The test `swallowingHandler_hidesFailure_returns200Ok` proves the lie — a 200 with body `"ok"` on a charge that threw — and `propagatingHandler_surfacesFailure_returns500Generic` proves the fix surfaces a real 500. The rule: catch only to *add value* (translate to a domain exception, add context). If you can't recover, let it propagate. If you must catch a broad type, **log it and re-throw** — never swallow.

---

## Pitfall 4: an over-broad handler masking real errors

**Symptom:** a bug that should be a 500 you investigate comes back as a clean 400 (or worse, a 200), and you never notice it in your dashboards.

**Cause:** handler precedence is **most-specific-exception-wins**, and a too-broad handler set to the wrong status quietly catches things you meant to handle elsewhere. `@ExceptionHandler(Exception.class)` returning a 400 will swallow a `NullPointerException` from your own bug and report it as the client's fault.

```java
// DANGEROUS — every unexpected exception now looks like a client error
@ExceptionHandler(Exception.class)
public ProblemDetail handleAll(Exception ex) {
    return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);   // a server bug reported as 400
}
```

The fallback in the demo's `GlobalExceptionHandler` does the opposite of the trap: it is the most *general* handler, returns a **500** (a server fault is a server fault), and always logs. Specific handlers (`ConstraintViolationException`, the `MethodArgumentNotValid` override) win over it for the cases they name. Keep your catch-all as a genuine last resort that returns 500 and logs — not a 400 that hides bugs.

---

## Pitfall 5: advice that doesn't extend `ResponseEntityExceptionHandler`

**Symptom:** your domain errors return a nice `ProblemDetail`, but malformed JSON, a wrong HTTP method (405), or a missing parameter come back in a *different* shape — Spring's default body. The client can't write one error parser.

**Cause:** Spring raises framework errors (`HttpMessageNotReadableException`, `HttpRequestMethodNotSupportedException`, `MethodArgumentNotValidException`, …) before your `@ExceptionHandler` methods see them. The only clean way to reshape those into your envelope is to extend `ResponseEntityExceptionHandler` and override its hooks.

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {   // the `extends` is load-bearing
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(...) { ... }  // now body-validation matches your shape
}
```

The demo's advice extends it and overrides `handleMethodArgumentNotValid` to add the `fieldErrors` array. Drop the `extends` and that override stops overriding anything — body-validation failures revert to Spring's default body, and your contract is inconsistent across domain vs framework errors. (Topic 05 covers the override hooks in full.)

---

## Pitfall 6: confusing `ConstraintViolationException` with `MethodArgumentNotValidException`

**Symptom:** body validation returns your clean 400, but a bad path variable or query param returns an ugly default — or vice versa. You handled one validation exception and forgot the other.

**Cause:** they come from two different code paths.

| Failure | Exception | Triggered by |
|---------|-----------|--------------|
| `@Valid @RequestBody` | `MethodArgumentNotValidException` | body binding |
| `@Min` etc. on `@PathVariable`/`@RequestParam` (class is `@Validated`) | `jakarta.validation.ConstraintViolationException` | method-parameter validation |

Handle **both**, mapping them to the same envelope:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(...) { ... }   // body case

@ExceptionHandler(ConstraintViolationException.class)
public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, ...) { ... }   // param case
```

The demo proves they converge: `paramValidation_returnsSameShapeAsBody` sends `GET /api/users/0` (violating `@Min(1)`), gets a `ConstraintViolationException`, and asserts the response is a 400 with a `fieldErrors` array and `title: "Validation failed"` — the same shape the body case produces. (Topics 08 and 09 cover each path on its own.)

---

## Pitfall 7: leaking `ex.getMessage()` or a stack trace in the response

**Symptom:** an error body contains a connection string, a SQL fragment, internal class names, or a full stack trace — readable by anyone, including an attacker.

**Cause:** a fallback handler that does `setDetail(ex.getMessage())` for an arbitrary deep exception, or `server.error.include-stacktrace: always`. The message of an exception thrown five layers down often embeds infrastructure details you never meant to expose.

```java
// BROKEN — copies whatever a deep library put in the message
return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error", ex.getMessage(), request);

// FIX — generic message to the client; the real cause goes to the log only
log.error("Unhandled exception", ex);
return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
        "An unexpected error occurred.", request);
```

The demo's fallback does the fix: it logs the cause and returns `"An unexpected error occurred."` The test `propagatingHandler_surfacesFailure_returns500Generic` asserts the original message (`"payment gateway timed out"`) is **absent** from the body — the security boundary written as a test. Set `detail` only from messages *you* wrote (your domain exceptions), never from caught framework exceptions. (Topic 06 is the full treatment.)

---

## Pitfall 8: validation silently doesn't run — wrong starter, or validating the entity

**Symptom:** `@Valid` is present, the constraints look right, and bad input is *still* accepted. Identical to pitfall 1, but `@Valid` is there.

**Cause:** one of two things. Either `spring-boot-starter-validation` is missing — the `jakarta.validation` annotations are on the classpath (so the code compiles), but **Hibernate Validator isn't**, so there's no engine to run them. Or you put `@Valid` on a JPA entity used as the request body, and the constraints you care about live on a DTO you bypassed.

```xml
<!-- FIX for the missing-engine case -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Always validate a **DTO**, never the entity directly (a DTO lets create and update validate differently, and keeps persistence annotations off your request contract — topic 10). And confirm the starter is on the pom: the demo's `pom.xml` includes it, which is why its `@NotBlank`s actually fire.

---

## Pitfall 9: returning a handler body without setting the status

**Symptom:** your `@ExceptionHandler` returns a tidy error body, but the status line says `200 OK`. The client's "is this an error?" check (which keys off the status) treats the failure as success.

**Cause:** a handler's return value is the *body*; the status is **not** inferred from it. A plain `String`, `Map`, or record returned from a handler defaults to 200 unless you set the status explicitly.

```java
// BROKEN — 200 with an error body
@ExceptionHandler(BookNotFoundException.class)
public Map<String, String> handle(BookNotFoundException ex) { return Map.of("error", ex.getMessage()); }

// FIX — any of these sets the status
@ExceptionHandler(BookNotFoundException.class)
public ProblemDetail handle(BookNotFoundException ex) {            // ProblemDetail carries its own status
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
}
// or: @ResponseStatus(HttpStatus.NOT_FOUND) on the handler, or return a ResponseEntity
```

Returning `ProblemDetail` (as every handler in this module does) sidesteps the trap because the status is baked into `forStatusAndDetail(...)`. (Topic 03 demonstrates the 200-instead-of-404 failure directly.)

---

## Try this yourself

The demo project is `CODE_EXAMPLES/13-common-pitfalls/pitfalls-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```bash
   curl -i -X POST http://localhost:8080/api/users/without-valid \
        -H "Content-Type: application/json" -d '{"username":"","email":""}'   # 201 — the bug (no @Valid)
   curl -i -X POST http://localhost:8080/api/users/with-valid \
        -H "Content-Type: application/json" -d '{"username":"","email":""}'   # 400 + fieldErrors — the fix
   curl -i http://localhost:8080/api/users/0          # 400, ConstraintViolationException, SAME shape
   curl -i http://localhost:8080/api/payments/swallow   # 200 "ok" — failure hidden
   curl -i http://localhost:8080/api/payments/propagate # 500, generic body; secret only in the log
   ```
2. **Break it and watch the test fail.** Add `@Valid` to `createWithoutValid` in `UserController`. Re-run `mvn -q test`: `missingValid_acceptsBadInput_returns201` now fails, because the endpoint correctly returns 400 — proof the test was pinning the *pitfall*. Revert.
3. Open `GlobalExceptionHandler` and delete `extends ResponseEntityExceptionHandler` (and the `@Override`). The file no longer compiles, because `handleMethodArgumentNotValid` overrides nothing — exactly pitfall 5: without the base class there's no hook to reshape framework errors. Revert.
4. In the fallback, change the detail to `ex.getMessage()`. Re-run the tests: `propagatingHandler_surfacesFailure_returns500Generic` fails because `"payment gateway timed out"` is now in the body. You just felt the security control (pitfall 7) catch a regression. Revert.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`CreateUserRequest.java`** — DTO using `@NotBlank` (not `@NotNull`), so `""` is rejected (pitfall 2).
3. **`UserController.java`** — the `@Valid`-vs-no-`@Valid` pair (pitfall 1) plus a `@Min(1)` path variable (the `ConstraintViolationException` path, pitfall 6).
4. **`PaymentController.java`** — the swallow-vs-propagate pair (pitfall 3).
5. **`GlobalExceptionHandler.java`** — extends `ResponseEntityExceptionHandler` (pitfall 5); handles body *and* param validation in one shape (pitfall 6); a 500-and-log fallback that never leaks (pitfalls 4, 7).
6. **`PitfallsTest.java`** — pins each pitfall and its fix: 201-for-bad-input, 400-with-fieldErrors, unified param shape, 200-swallow vs 500-propagate, and the secret-absent assertion.

## Self-check

1. Your DTO has `@NotBlank` on every field and the endpoint returns 201 for `{"username":""}`. Name the two distinct causes (one needs a parameter annotation, one needs a dependency) and how you'd tell them apart.
2. Why does a handler that returns a `Map` with an `"error"` key still send `200 OK`, and what are two ways to fix the status?
3. A bad request body and a bad path variable throw two different exceptions. Name both, say which annotation on the controller is required for the path-variable one to fire, and explain why you handle both in the advice.
