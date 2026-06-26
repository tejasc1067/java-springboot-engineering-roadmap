# Validating `@PathVariable` and `@RequestParam` with `@Validated`

Topic 08 surfaced the per-field errors for `@Valid @RequestBody` failures. But `@Valid` only validates the **body** — a `@PathVariable @Min(1) Long id` or a `@RequestParam @Max(100) int size` is ignored unless you do one more thing. This topic adds class-level `@Validated`, explains why the failure arrives as a *different* exception (`ConstraintViolationException`, not `MethodArgumentNotValidException`), and folds both into the one `fieldErrors` contract the rest of the module uses. Module 09 topic 10 named this exact gap: "Module 11 unifies the response body for both."

---

## The problem this solves

`GET /api/books/-5` and `GET /api/books?size=100000` are bad requests, but nothing stops them by default:

```java
@GetMapping("/{id}")
public Book one(@PathVariable @Min(1) Long id) {   // the @Min looks like it does something...
    return store.get(id);
}
```

It compiles. It runs. And it happily accepts `id = -5` — the `@Min(1)` does **nothing**. Constraint annotations on method parameters are inert unless the surrounding bean is wired for method-parameter validation. So the negative id flows into your store, the oversized `size` flows into your query, and you find out later (a 500 from the database, or a query that tries to page a million rows).

`@Valid` does not fix this. `@Valid` is the *body* trigger — it tells Spring to validate the object bound from `@RequestBody`. Path variables and query params are bound differently, and they need a different switch.

## How it works

Two pieces, both shown in `BookController`:

1. Annotate the **controller class** with `@Validated` — Spring's `org.springframework.validation.annotation.Validated`, **not** `jakarta.validation.Valid`. This tells Spring to wrap the bean in a proxy that runs method-parameter constraints.
2. Put the constraints directly on the parameters.

```java
@RestController
@RequestMapping("/api/books")
@Validated                                   // <-- the switch. Without it, the @Min/@Max below are dead.
public class BookController {

    @GetMapping("/{id}")
    public String one(@PathVariable @Min(1) Long id) {
        return "book " + id;
    }

    @GetMapping
    public String page(@RequestParam @Min(1) @Max(100) int size) {
        return "page of " + size;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@Valid @RequestBody CreateBookRequest request) {   // body still uses @Valid
        return "created " + request.title();
    }
}
```

With `@Validated` present, `GET /api/books/0` no longer reaches the method body. The constraint check fails first, and Spring throws — which brings us to the part that trips people up.

## The key surprise — it's a *different* exception

A failed body validation (`@Valid @RequestBody`) throws **`MethodArgumentNotValidException`**. A failed param validation (`@Min` on a `@PathVariable` under `@Validated`) throws **`jakarta.validation.ConstraintViolationException`**. Same intent — "input is invalid" — two unrelated exception types, because they come from two different layers:

| Failure | Exception | Where it comes from |
|---------|-----------|---------------------|
| `@Valid @RequestBody` body | `MethodArgumentNotValidException` | Spring MVC's data binding layer |
| `@Min` on `@PathVariable`/`@RequestParam` | `ConstraintViolationException` | the bean validation layer (Hibernate Validator), via the `@Validated` proxy |

This matters because of *who handles each*. Your advice extends `ResponseEntityExceptionHandler` (topic 05). That base class already knows about `MethodArgumentNotValidException` — it's a Spring MVC exception, so there's a hook (`handleMethodArgumentNotValid`) you can override. It knows **nothing** about `ConstraintViolationException`, because that type lives in `jakarta.validation` and is not a Spring MVC exception. There is no hook for it. Left alone, it falls through to the generic fallback and the client gets a 500 — for what is plainly a 400.

So you handle it explicitly with a plain `@ExceptionHandler`.

## Unifying both into one `fieldErrors` shape

The goal is that a client writes **one** error parser. Whether the bad data was in the path, the query string, or the body, the response looks the same:

```json
{
  "title": "Validation failed",
  "status": 400,
  "fieldErrors": [ { "field": "size", "message": "must be less than or equal to 100" } ]
}
```

`GlobalExceptionHandler` produces that from both sources. The body case overrides the framework hook:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    body.setTitle("Validation failed");
    body.setProperty("timestamp", Instant.now());
    List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(),
                              "message", String.valueOf(fe.getDefaultMessage())))
            .toList();
    body.setProperty("fieldErrors", fieldErrors);
    return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
}
```

The param case adds the missing handler — note it's a normal `@ExceptionHandler`, because the base class won't catch this one for us:

```java
@ExceptionHandler(ConstraintViolationException.class)
public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
    ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Validation failed", request);
    List<Map<String, String>> fieldErrors = ex.getConstraintViolations().stream()
            .map(v -> {
                String path = v.getPropertyPath().toString();          // e.g. "page.size"
                String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                return Map.of("field", field, "message", v.getMessage());
            })
            .toList();
    p.setProperty("fieldErrors", fieldErrors);
    return p;
}
```

The one nuisance is the `field` name. A `ConstraintViolation`'s `getPropertyPath()` is the *method-and-parameter* path — for the `page(int size)` method it reads `page.size`, not `size`. The substring after the last `.` recovers the parameter name. (For the body case, `MethodArgumentNotValidException` already gives you `fe.getField()` = `"title"` cleanly, because it's reporting on a bound object's fields.)

`ParamValidationTest` proves the unification: `/api/books/0`, `?size=999`, and a `{"title":""}` POST each assert `status().isBadRequest()`, `$.title == "Validation failed"`, and a one-element `$.fieldErrors` array with a `field` and a `message`. Three different exceptions internally, one shape on the wire.

> Parameter names like `size` survive to the path only because Spring Boot compiles with `-parameters` by default. If you saw `arg0` instead, that flag is missing — but in a standard Boot build it's on, so the demo reports `size` and `id`.

---

## When to use it / when not to

**Use `@Validated` + param constraints** for cheap, declarative guards on the *shape* of an id or a paging param — `@Min(1)` on an id, `@Max(100)` on a page size, a `@Pattern` on a slug. It rejects garbage at the edge, before a single line of your logic runs.

**Don't reach for it** when the check is a *business rule* ("this book can't be borrowed because it's already lent out"). Bean validation answers "is this value structurally acceptable"; it can't answer questions that require looking at other data. Those belong in the service as a thrown domain exception (topic 07, and topic 12 draws the line explicitly). A `@Min(1)` on an id says "ids start at 1"; whether *book 7* exists is a lookup, not a constraint.

## Common pitfalls

- **Forgetting `@Validated` on the class.** The most common one: you put `@Min(1)` on the `@PathVariable`, test a `0`, and it sails through with a 200. The annotation is inert without the class-level `@Validated`. (And it must be Spring's `@Validated`, not `@Valid` — `@Valid` on a path variable does nothing here.)
- **Expecting `ConstraintViolationException` to be handled by `ResponseEntityExceptionHandler` automatically.** It isn't — it's not a Spring MVC exception, so the base class has no hook. Without your explicit `@ExceptionHandler`, it's a 500. This is the whole reason the param handler is written by hand.
- **Reporting the raw property path as the field.** Leaving `field` as `"page.size"` instead of `"size"` confuses clients and breaks a uniform contract with the body case (which reports `"title"`). Trim to the last path segment.
- **`@Min` on a primitive that defaults.** `@RequestParam int size` with no value sent is a *missing parameter* (a `MissingServletRequestParameterException`, 400 from the framework) — not a constraint violation. Make it `@RequestParam(required = false) Integer size` if "absent" should mean "use a default", and validate the default elsewhere; otherwise the two failure modes look different to the client.
- **Putting `@Validated` on the class but the constraint on the wrong layer.** `@Validated` triggers method-parameter validation on *that bean's* public methods. If your constraints are on a service method instead, you need `@Validated` on the service (topic 12), not only the controller.
- **Assuming both exceptions yield the same default status.** They happen to both map to 400 here, but only because you wrote it that way. Out of the box, the param case is a 500. The uniform 400 is a property of your advice, not a freebie.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/09-validating-path-and-query-params/param-validation-demo`.

1. Run `mvn -q spring-boot:run`, then hit all three failure modes and confirm they look identical:
   ```bash
   curl -i http://localhost:8080/api/books/0                 # 400, fieldErrors[0].field == "id"
   curl -i "http://localhost:8080/api/books?size=999"        # 400, fieldErrors[0].field == "size"
   curl -i -X POST http://localhost:8080/api/books \
        -H "Content-Type: application/json" -d '{"title":""}' # 400, fieldErrors[0].field == "title"
   ```
2. **Break it and watch the test fail.** Remove `@Validated` from `BookController`. Run `mvn -q test`. The two param tests now fail: `/api/books/0` returns 200 (the `@Min` is dead) instead of 400. Put it back.
3. **Feel the 500.** Keep `@Validated`, but comment out the entire `handleConstraintViolation` method in `GlobalExceptionHandler`. Re-run `curl -i http://localhost:8080/api/books/0` — it's now a 500, because nothing catches `ConstraintViolationException`. That's exactly the gap the explicit handler fills. Restore it.
4. Change the `field`-trimming line to return the raw `path`, re-run the param curl, and watch the field become `one.id` / `page.size`. Revert.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`CreateBookRequest.java`** — the POST body record with `@NotBlank title`; its failure is the `MethodArgumentNotValidException` case.
3. **`BookController.java`** — `@Validated` on the class; `@Min`/`@Max` on the path and query params, plus the `@Valid @RequestBody` POST.
4. **`GlobalExceptionHandler.java`** — overrides `handleMethodArgumentNotValid` (body) **and** adds `@ExceptionHandler(ConstraintViolationException.class)` (params), both emitting one `fieldErrors` shape.
5. **`ParamValidationTest.java`** — asserts `/api/books/0`, `?size=999`, and a blank-title POST all return 400 with the same `fieldErrors` contract.

## Self-check

1. You added `@Min(1)` to a `@PathVariable Long id` and a request with `id=0` still returns 200. What single annotation is missing, and on what?
2. A body-validation failure and a param-validation failure throw two different exceptions. Name both, and explain why only one of them is handled by `ResponseEntityExceptionHandler` out of the box.
3. A `ConstraintViolation`'s property path for `page(@RequestParam int size)` is `page.size`. What does the client want to see as the `field`, and how does the handler get there?
