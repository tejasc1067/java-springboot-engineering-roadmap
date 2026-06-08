# Validation preview — `@Valid` and Bean Validation

`@RequestBody` trusts the client. A naive controller accepts `{"username": "", "age": -3}` and tries to store it. Bean Validation (the JSR-380 standard, implemented by Hibernate Validator) is the layer that says no. This topic is the **preview** — enough to reject bad input with a 400. Module 11 covers custom validators, error-response shaping with `@ControllerAdvice`, and field-level error messages.

---

## The problem this solves

Without validation:

```java
@PostMapping
public UserResponse create(@RequestBody CreateUserRequest req) {
    return service.create(req);
}
```

A request like `{"username": "", "email": "not-an-email"}` reaches `service.create(...)` and either fails much later (when the database NOT NULL constraint complains) or, worse, silently stores garbage.

With validation:

```java
@PostMapping
public UserResponse create(@Valid @RequestBody CreateUserRequest req) { ... }
```

…plus jakarta.validation annotations on the DTO:

```java
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 30) String username,
        @NotBlank @Email             String email,
        @NotNull  @Min(13) @Max(120) Integer age) {}
```

Spring runs the validations **before** your method body. On failure, it returns `400 Bad Request` automatically with a body listing every failed constraint. Your method only ever sees valid input.

---

## How it works

### 1. Add the starter

Validation is not in `spring-boot-starter-web` by default. Add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

That brings Hibernate Validator (the reference implementation) plus its dependencies. No Java config needed; Spring Boot autoconfigures the validator.

### 2. Annotate the DTO

`jakarta.validation.constraints.*` has the standard set:

| Annotation        | Applies to       | Means |
|-------------------|------------------|-------|
| `@NotNull`        | any object        | not null. Allows empty strings and empty collections. |
| `@NotBlank`       | strings only      | not null, not empty, not whitespace-only. |
| `@NotEmpty`       | strings, collections, arrays, maps | not null, length > 0. Allows whitespace strings. |
| `@Size(min, max)` | strings, collections, arrays, maps | size within range. |
| `@Min(n)` / `@Max(n)` | numbers       | value within range. |
| `@Email`          | strings           | basic email shape. |
| `@Pattern(regexp = "...")` | strings  | matches the regex. |
| `@Past` / `@Future` | dates and times | before/after the present. |
| `@Positive` / `@PositiveOrZero` / `@Negative` | numbers | sign constraint. |

You can stack annotations: `@NotBlank @Size(min = 8) String password`.

### 3. Add `@Valid` to the parameter

`@Valid` (also from `jakarta.validation`) tells Spring to actually *run* the validations. Without `@Valid`, the annotations on the DTO are ignored.

```java
@PostMapping
public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) { ... }
```

For nested objects (a DTO that contains another DTO), add `@Valid` on the field as well:

```java
public record CreateOrderRequest(
        @NotBlank String customer,
        @Valid @NotNull Address shippingAddress) {}        // nested validation
```

---

## What the default 400 body looks like

When validation fails, Spring throws `MethodArgumentNotValidException`. Its default handler returns a `ProblemDetail` JSON response (Spring 6+'s RFC 7807 implementation):

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/users"
}
```

That's it by default — the response **does not include per-field details**. The validation errors are in `MethodArgumentNotValidException.getBindingResult()`, but Spring's default handler doesn't put them in the response body. You will see them only in the server log.

This is the gap module 11 fills with a `@ControllerAdvice` that maps `MethodArgumentNotValidException` to a richer body listing every failed field. For now, the 400 is enough to know "the client sent bad input" — the *what* is in the logs.

---

## 400 vs 422 — Spring's default

Spring Boot 3.x returns **400** for `@Valid` failures. Recall topic 08: some teams prefer 422 to distinguish "well-formed but semantically wrong" from "broken JSON." To switch to 422:

```yaml
spring:
  webmvc:
    problemdetails:
      enabled: true
```

…and then either override the handler or set `@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)` on your exception advice. Stay with 400 in this module; the conversation belongs in module 11.

---

## Validating path variables and query parameters

`@Valid` only handles the request body. To validate `@PathVariable` and `@RequestParam`, two pieces are needed:

1. Annotate the **class** with `@Validated` (from Spring, *not* `@Valid`).
2. Put constraints directly on the parameter:

```java
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    @GetMapping("/{id}")
    public UserResponse one(@PathVariable @Min(1) Long id) { ... }
}
```

Validation failures now throw `ConstraintViolationException` (a different exception from the body case), and Spring handles it with another default 400. Module 11 unifies the response body for both.

---

## Common pitfalls

- **Forgetting `@Valid`.** The constraint annotations are there, but Spring never runs them. Easy to miss because the code compiles and the controller still serves successfully — it just doesn't validate. Always check with a deliberately invalid request as part of testing.
- **`@NotNull` on a `String` when you mean `@NotBlank`.** `@NotNull` accepts `""`. `@NotBlank` is what you almost always want for required strings.
- **Forgetting `spring-boot-starter-validation`.** With only `-web`, the annotations exist on the classpath via `jakarta.validation-api` (a transitive of Boot's metadata) but Hibernate Validator isn't there — so validations silently don't run. Add the starter.
- **`@Valid` on path variables.** Doesn't work without class-level `@Validated`. The annotation is from the wrong package for parameter-level validation.
- **Returning your own custom error body without testing it under load.** A naive `@ControllerAdvice` that logs every field name can leak schema information. Module 11 covers safe error responses.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/10-validation-preview/validation-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```
   # all fields valid -> 201
   curl -i -X POST http://localhost:8080/api/users \
        -H "Content-Type: application/json" \
        -d '{"username":"alice","email":"alice@example.com","age":30}'

   # blank username -> 400
   curl -i -X POST http://localhost:8080/api/users \
        -H "Content-Type: application/json" \
        -d '{"username":"","email":"alice@example.com","age":30}'

   # bad email -> 400
   curl -i -X POST http://localhost:8080/api/users \
        -H "Content-Type: application/json" \
        -d '{"username":"alice","email":"not-an-email","age":30}'

   # too young -> 400
   curl -i -X POST http://localhost:8080/api/users \
        -H "Content-Type: application/json" \
        -d '{"username":"alice","email":"alice@example.com","age":5}'
   ```

2. Look at the server log for the failed cases. The constraint message ("must not be blank", "must be a well-formed email address") is in the WARN line. The response body does not include it (yet — module 11).

3. Remove the `@Valid` annotation from the controller method. Repeat the bad requests. Notice they now return 201 — validation isn't running.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`CreateUserRequest.java`** — DTO with constraint annotations.
3. **`UserController.java`** — `@Valid @RequestBody`.
4. **`UserControllerTest.java`** — happy path + four invalid inputs, each asserting 400.

## Self-check

1. Without `@Valid` on the parameter, do the `@NotBlank` annotations on the DTO have any effect?
2. Which annotation accepts `""` (empty string): `@NotNull`, `@NotBlank`, `@NotEmpty`, or `@Size(min=1)`?
3. The default 400 body from Spring Boot 3 doesn't include the per-field errors. Where do they live, and what do you need to add to surface them to the client?
