# Bean validation in depth — surfacing per-field errors, nested objects, collections

Module 09's validation preview (topic 10) stopped at a bare 400: `@Valid` rejected bad input, but the *which field, and why* lived only in the server log. This topic closes that gap. You override the framework's validation hook to put a `fieldErrors` array in the response, then go deeper — validating nested objects and the elements of a collection, where the error paths become `publisher.name` and `tags[0]`. `@Valid`, the constraint annotations, and the `-validation` starter are assumed known from module 09; the new ground is the response body and the deeper validation targets.

---

## The problem this solves

Module 09 left you here. A request fails validation and the client gets:

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Invalid request content." }
```

The client knows *something* was wrong but not *what*. The actual constraint violations — "title must not be blank", "ISBN must be 13 characters" — sit inside `MethodArgumentNotValidException.getBindingResult()`, and Spring's default handler doesn't copy them into the body. A frontend can't highlight the offending field; an API consumer can't fix their request without guessing.

The fix is one overridden method. The contract a client deserves:

```json
{
  "title": "Validation failed",
  "status": 400,
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" },
    { "field": "isbn",  "message": "ISBN must be 13 characters" }
  ]
}
```

---

## How it works

`MethodArgumentNotValidException` is the exception Spring throws when a `@Valid @RequestBody` fails. The framework's `ResponseEntityExceptionHandler` already has a handler for it — that's the bare 400 above. Because our `GlobalExceptionHandler extends ResponseEntityExceptionHandler`, we **override** that handler instead of writing a fresh `@ExceptionHandler`:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    body.setTitle("Validation failed");
    body.setProperty("timestamp", java.time.Instant.now());

    List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(),
                              "message", String.valueOf(fe.getDefaultMessage())))
            .toList();
    body.setProperty("fieldErrors", fieldErrors);

    return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
}
```

The key calls:

- **`ex.getBindingResult().getFieldErrors()`** — the list of every failed field. Each `FieldError` carries `getField()` (the property path) and `getDefaultMessage()` (the resolved constraint message).
- **`body.setProperty("fieldErrors", ...)`** — `ProblemDetail` lets you attach arbitrary JSON members beyond the RFC 7807 standard ones. This is how the array reaches the wire.
- **`handleExceptionInternal(...)`** — the base-class plumbing that writes the body with the right headers and content type. Returning a hand-built `ResponseEntity` would skip the framework's content negotiation; calling the helper keeps us consistent with the rest of `ResponseEntityExceptionHandler`.

The demo's `BookController.create` is deliberately empty of validation logic — `@Valid` runs everything before the body executes, so if the method runs at all, the input is already valid:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public CreateBookRequest create(@Valid @RequestBody CreateBookRequest request) {
    return request;
}
```

`BeanValidationTest.blankTitleAndBadIsbnReturn400WithFieldErrors` posts a blank title and a 3-char ISBN, then asserts the `fieldErrors` array contains both `title` and `isbn`. That assertion failing is exactly what module 09 couldn't deliver.

## Nested validation — `@Valid` has to be on the field too

`CreateBookRequest` holds a `Publisher`, which has its own constraint:

```java
public record Publisher(@NotBlank String name) {}

public record CreateBookRequest(
        @NotBlank String title,
        ...
        @Valid @NotNull Publisher publisher,     // the @Valid here is load-bearing
        List<@NotBlank String> tags) {}
```

The subtlety: **`@Valid` on the field is what makes the nested object's own constraints run.** `@NotNull` checks that `publisher` isn't null; only `@Valid` tells the validator to *descend into* `publisher` and check `name`. Drop the `@Valid` and a `{ "name": "" }` publisher passes — the `@NotBlank` on `Publisher.name` never fires.

When it does fire, the field path is **`publisher.name`** — the validator builds a dotted path from the outer property to the inner one. `BeanValidationTest.blankNestedPublisherNameReportsDottedPath` posts a blank publisher name and asserts a `fieldErrors` entry with `field == "publisher.name"`. That dotted path is why `getField()` (not a bare property name) is the right thing to surface: a client editing a nested form needs the full path to know which input to flag.

## Validating the elements of a collection

A constraint can sit on the *element type* of a collection, not the collection itself:

```java
List<@NotBlank String> tags
```

This reads as "a list whose every element must be non-blank." `@NotBlank` here does **not** constrain the list (it's not "the list must be non-empty" — that would be `@NotEmpty` on the field). It constrains each `String` inside. A blank element at index 1 reports the path **`tags[1]`** — the property name plus the index in brackets. `BeanValidationTest.blankCollectionElementReportsIndexedPath` proves it.

The same applies to a list of objects: `@Valid List<Author> authors` runs each `Author`'s constraints and reports paths like `authors[0].name`. The `@Valid` is again what triggers the descent into each element.

## Customizing the message

Every constraint has a `message` attribute. The default messages ("must not be blank", "size must be between 13 and 13") are fine for developers but often too generic for an API contract. Override per-constraint:

```java
@Size(min = 13, max = 13, message = "ISBN must be 13 characters")
String isbn
```

The string flows straight through `getDefaultMessage()` into the `fieldErrors` array — the test asserts the literal `"ISBN must be 13 characters"` appears. That's the lightweight path. The heavier path — externalizing messages to a `messages.properties` file so Hibernate Validator resolves them per-locale (`{validation.isbn.size}`) — is real i18n and a presentation concern; this module stops at the inline `message` attribute.

---

## Common pitfalls

- **Forgetting `@Valid` on a nested field.** `@NotNull Publisher publisher` checks only that it's present; the inner `@NotBlank name` is silently skipped. Add `@Valid` to descend. This compiles and "works" — it just doesn't validate the nested object.
- **Putting the collection constraint in the wrong place.** `@NotEmpty List<String> tags` means "the list must have at least one element"; `List<@NotBlank String> tags` means "each element must be non-blank." They answer different questions and you sometimes want both: `@NotEmpty List<@NotBlank String> tags`.
- **Writing a fresh `@ExceptionHandler(MethodArgumentNotValidException.class)` instead of overriding.** Because the base class already handles it, your method and the framework's can collide or one can shadow the other. When you extend `ResponseEntityExceptionHandler`, override `handleMethodArgumentNotValid` — don't compete with it.
- **Returning a plain `ResponseEntity` you built by hand from the override.** You lose the content negotiation `handleExceptionInternal` does. Build the `ProblemDetail`, then hand it to `handleExceptionInternal`.
- **Surfacing `getObjectName()` or the rejected value.** `getRejectedValue()` echoes what the client sent — fine for a name, a leak for a password field. Surface `field` + `message`, not the value, unless you've thought about what's in it.
- **Assuming `fieldErrors` is ordered or deduplicated.** The binding result lists violations in no contractual order, and two constraints on one field produce two entries. If a client relies on order, sort before returning.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/08-bean-validation-in-depth/validation-depth-demo`.

1. Run `mvn -q spring-boot:run`, then post the invalid body and read the `fieldErrors` array:
   ```bash
   curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" -d '{
     "title": "", "author": "Martin", "isbn": "123",
     "publisher": { "name": "Addison-Wesley" }, "tags": ["classic"]
   }'
   ```
   You get a 400 with `title` and `isbn` both listed — the body module 09 couldn't produce.
2. Post a blank publisher name (`"publisher": { "name": "" }`) and confirm the field path is `publisher.name`. Then **remove the `@Valid`** from the `publisher` field in `CreateBookRequest`, re-run, and post the same body — now it's a 201, because the nested constraint stopped running. Put `@Valid` back.
3. Run `mvn -q test`. Then break it: change the override to surface `fe.getObjectName()` instead of `fe.getField()`. Watch `blankNestedPublisherNameReportsDottedPath` fail — the object name is `createBookRequest`, not the `publisher.name` path the client needs. Revert.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Publisher.java`** — the nested record with its own `@NotBlank name`.
3. **`CreateBookRequest.java`** — the DTO: scalar constraints, `@Valid` nested `Publisher`, and a `List<@NotBlank String>`.
4. **`BookController.java`** — `@Valid @RequestBody`, returns 201; no validation logic in the body.
5. **`GlobalExceptionHandler.java`** — overrides `handleMethodArgumentNotValid` to add the `fieldErrors` array.
6. **`BeanValidationTest.java`** — 201 happy path; 400 with both failed fields; nested `publisher.name` path; indexed `tags[1]` path.

## Self-check

1. A `@NotNull Publisher` field with no `@Valid` accepts `{ "name": "" }`. Why, and what one annotation fixes it?
2. `List<@NotBlank String> tags` rejects a blank element with the path `tags[0]`. What does `@NotEmpty List<String> tags` reject instead, and how is that a different question?
3. Why override `handleMethodArgumentNotValid` rather than write a new `@ExceptionHandler(MethodArgumentNotValidException.class)` when your advice extends `ResponseEntityExceptionHandler`?
