# Common mistakes — Exception Handling and Validation

Real bugs from real Spring Boot codebases, each with the broken code, what goes wrong, and the fix. These are the ones that reach production, not typos.

---

## 1. Letting a missing-record lookup become a 500

```java
@GetMapping("/{id}")
public Book one(@PathVariable Long id) {
    return repository.findById(id).orElseThrow();   // throws NoSuchElementException
}
```

**What goes wrong:** `orElseThrow()` with no argument throws `NoSuchElementException`, which has no status mapping, so the client gets `500 Internal Server Error`. The client did nothing wrong — they asked for a book that isn't there — but the status says *your server* broke. Worse, 5xx tells clients "retry," so they hammer you with requests that will never succeed.

**Fix:** throw a domain exception the advice maps to 404.

```java
return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
// GlobalExceptionHandler maps BookNotFoundException -> 404
```

---

## 2. Forgetting `@Valid`, so validation silently never runs

```java
@PostMapping
public ResponseEntity<Void> create(@RequestBody CreateBookRequest req) {   // no @Valid
    service.create(req);
    return ResponseEntity.status(201).build();
}
```

**What goes wrong:** the `@NotBlank`, `@Size`, `@Email` annotations on `CreateBookRequest` are present but **never evaluated**. `{"title": "", "isbn": "x"}` sails through to your service and database. The code compiles, the endpoint works, and nothing tells you validation isn't happening — until garbage is in your database.

**Fix:** add `@Valid` to the parameter. Without it, the constraints are decoration.

```java
public ResponseEntity<Void> create(@Valid @RequestBody CreateBookRequest req) { ... }
```

Always test with a deliberately invalid request so a missing `@Valid` fails a test, not production.

---

## 3. `@NotNull` on a String when you meant `@NotBlank`

```java
public record CreateBookRequest(@NotNull String title) {}
```

**What goes wrong:** `@NotNull` only rejects `null`. `{"title": ""}` and `{"title": "   "}` both pass — you've "validated" a required field that's still effectively empty.

**Fix:** for required strings, use `@NotBlank` (not null, not empty, not whitespace-only). Reserve `@NotNull` for non-string objects, and `@NotEmpty` for collections/strings where whitespace is acceptable but emptiness isn't.

```java
public record CreateBookRequest(@NotBlank String title) {}
```

---

## 4. An advice that doesn't extend `ResponseEntityExceptionHandler`, so error shapes are inconsistent

```java
@RestControllerAdvice
public class GlobalExceptionHandler {     // does NOT extend ResponseEntityExceptionHandler
    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleNotFound(...) { ... }   // pretty ProblemDetail
}
```

**What goes wrong:** your *domain* errors return a nice `ProblemDetail`, but Spring's own exceptions — malformed JSON (`HttpMessageNotReadableException`), wrong method (405), body validation (`MethodArgumentNotValidException`) — bypass your advice and come back in a *different* shape. A client now has to parse two error formats from the same API.

**Fix:** extend `ResponseEntityExceptionHandler`. It already has handlers for every Spring MVC exception (returning `ProblemDetail`), and lets you override hooks like `handleMethodArgumentNotValid` to add your `fieldErrors`.

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler { ... }
```

---

## 5. Catching `Exception` and swallowing it

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handle(Exception ex) {
    return ResponseEntity.ok("something happened");   // 200, no log
}
```

**What goes wrong:** two disasters at once. The client gets `200 OK` for a *failure*, so it proceeds as if all is well. And nothing is logged, so the outage is invisible to you — no stack trace, no alert, no way to diagnose.

**Fix:** return a real error status, and always log the cause in the fallback.

```java
@ExceptionHandler(Exception.class)
public ProblemDetail handle(Exception ex, HttpServletRequest req) {
    log.error("Unhandled exception", ex);              // detail to the log
    return problem(INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred.", req);
}
```

---

## 6. Leaking the exception message or stack trace into the response body

```java
@ExceptionHandler(Exception.class)
public ProblemDetail handle(Exception ex, HttpServletRequest req) {
    return problem(INTERNAL_SERVER_ERROR, "Error", ex.getMessage(), req);   // leaks internals
}
```

**What goes wrong:** `ex.getMessage()` for a deep exception often contains a JDBC URL, credentials, SQL with table names, or internal class names. You've just handed an attacker reconnaissance — framework versions for CVE lookup, your schema, your file paths. This is a real, common data leak.

**Fix:** put the real detail in the log; return a generic message (and optionally a correlation id) to the client. Only echo messages *you wrote* (your own domain exceptions), never arbitrary caught exceptions. Also keep `server.error.include-stacktrace: never` in production.

---

## 7. Setting `reason` on `@ResponseStatus` and losing the JSON body

```java
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "book not found")
public class BookNotFoundException extends RuntimeException {}
```

**What goes wrong:** the `reason` attribute makes Spring handle the exception with `HttpServletResponse.sendError(...)`, which routes through the servlet container's error path. Your exception's message is discarded, the `Content-Type` is no longer `application/problem+json`, and you can't attach a body. For a JSON API this is almost never what you want.

**Fix:** omit `reason`. Map the exception in a `@ControllerAdvice`/`@RestControllerAdvice` where you control the body, or use `@ResponseStatus` with `code` only.

---

## 8. Handling body validation but forgetting param validation (or vice versa)

```java
// advice overrides handleMethodArgumentNotValid (body) but has no ConstraintViolationException handler
@GetMapping("/{id}")
public Book one(@PathVariable @Min(1) Long id) { ... }   // controller is @Validated
```

**What goes wrong:** body validation failures return your nice `fieldErrors` shape, but a bad path variable throws `ConstraintViolationException` — a *different* exception that your advice doesn't handle — so it falls to an ugly default (often a 500). The same API gives two different error experiences.

**Fix:** handle both. `MethodArgumentNotValidException` for `@Valid @RequestBody`, `ConstraintViolationException` for `@Validated` params/methods. Map both to the same `fieldErrors` contract.

---

## 9. Returning a handler body but forgetting to set the status

```java
@ExceptionHandler(BookNotFoundException.class)
public Map<String, String> handle(BookNotFoundException ex) {
    return Map.of("error", ex.getMessage());   // status defaults to 200
}
```

**What goes wrong:** an `@ExceptionHandler`'s return value is the *body*; the status is **not** inferred from it. With no `@ResponseStatus` on the method and no `ResponseEntity`/`ProblemDetail` carrying a status, the client gets `200 OK` with an error body — the exact "200 with success:false" anti-pattern.

**Fix:** carry the status. Return a `ProblemDetail` built with `forStatus(...)`, return a `ResponseEntity` with an explicit status, or annotate the handler with `@ResponseStatus`.

---

## 10. Validation groups: shared constraints silently skipped

```java
public record BookPayload(
        @Null(groups = OnCreate.class) @NotNull(groups = OnUpdate.class) Long id,
        @NotBlank String title) {}          // title has NO group -> belongs to Default

@PostMapping
public void create(@Validated(OnCreate.class) @RequestBody BookPayload p) { ... }
```

**What goes wrong:** constraints with no `groups` attribute belong to the `Default` group. Validating with only `@Validated(OnCreate.class)` does **not** trigger the `Default` group — so `@NotBlank title` is never checked on create. A blank title slips through.

**Fix:** include `Default` explicitly, or assign shared constraints to every group.

```java
@PostMapping
public void create(@Validated({Default.class, OnCreate.class}) @RequestBody BookPayload p) { ... }
```

---

## 11. `save` instead of `saveAndFlush` when translating a constraint violation

```java
public Book create(Book book) {
    try {
        return repository.save(book);     // INSERT may be deferred to commit
    } catch (DataIntegrityViolationException ex) {
        throw new DuplicateIsbnException(book.getIsbn());
    }
}
```

**What goes wrong:** `save` may not flush the `INSERT` until the transaction commits — *after* this method (and the `try` block) has returned. The `DataIntegrityViolationException` then fires outside the `catch`, escapes untranslated, and the client gets a 500 instead of your 409.

**Fix:** use `saveAndFlush` so the `INSERT` happens inside the `try` and the catch can translate it. (Same flush-timing subtlety as module 10 topic 14.)

---

## 12. Forgetting `spring-boot-starter-validation`

```xml
<!-- pom has spring-boot-starter-web but NOT spring-boot-starter-validation -->
```

```java
public record CreateBookRequest(@NotBlank String title) {}   // annotation present
```

**What goes wrong:** the `jakarta.validation` annotations are on the classpath (transitively), so the code compiles — but **Hibernate Validator** (the implementation that actually runs them) is not present. Constraints silently do nothing. `@Valid` has no effect.

**Fix:** add the starter. It pulls in Hibernate Validator and Boot autoconfigures it.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

## 13. A custom `ConstraintValidator` that rejects `null`

```java
public class IsbnValidator implements ConstraintValidator<ValidIsbn, String> {
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        return value.matches("\\d{13}");   // NullPointerException when value is null
    }
}
```

**What goes wrong:** when the field is `null`, this either NPEs or reports the field as invalid — stealing `@NotNull`'s job and producing a confusing "invalid ISBN" message for a missing value.

**Fix:** treat `null` as valid and let `@NotNull`/`@NotBlank` own nullness. This is the standard convention for all constraints.

```java
public boolean isValid(String value, ConstraintValidatorContext ctx) {
    if (value == null) return true;        // @NotNull's job, not mine
    return value.matches("\\d{13}");
}
```

---

## 14. Throwing `ResponseStatusException` from deep in the service layer

```java
public Book getById(Long id) {
    return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));   // HTTP in the domain
}
```

**What goes wrong:** it works, but it couples your service layer to HTTP. Now the service can't be reused outside a web context (a batch job, a message consumer) without dragging `HttpStatus` along, and the "what status does this map to" decision is scattered across services instead of centralized in the advice.

**Fix:** throw a named domain exception (`BookNotFoundException`) and map it once in the advice. The service speaks the domain; the advice speaks HTTP.

---

## 15. Validating (and binding) the JPA entity instead of a DTO

```java
@PostMapping
public Book create(@Valid @RequestBody Book book) { ... }   // Book is an @Entity
```

**What goes wrong:** two problems. (1) The client can set *any* field on your entity, including `id`, audit fields, or relationships — a mass-assignment risk. (2) Your validation rules and your persistence mapping are now tangled in one class, so a constraint meant for input creeps into your schema and vice versa.

**Fix:** bind and validate a request DTO (`CreateBookRequest`) with only the fields the client may set, then map it to the entity in the service. (Module 09 topic 09 made this case; it matters doubly once validation is involved.)
</content>
