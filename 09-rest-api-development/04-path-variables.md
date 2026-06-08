# Path variables — `@PathVariable`

A path variable is the part of a URL that identifies *which* resource you mean — the `42` in `/api/books/42`. Spring binds those segments to method parameters via `@PathVariable`. This topic covers the four things you will actually do with them: bind one, bind several, let Spring convert types, and constrain the path with a pattern.

---

## The problem this solves

REST URLs name resources, so they need placeholders for the identifier:

```text
GET /api/books/{id}
GET /api/books/{id}/reviews/{reviewId}
GET /api/users/{username}/posts/{slug}
```

Without path variables, you would parse the URL by hand in every controller. `@PathVariable` is the tool that pulls those segments out into typed parameters.

---

## How it works

### One path variable

```java
@GetMapping("/books/{id}")
public Book one(@PathVariable Long id) {
    return store.get(id);
}
```

`{id}` in the path matches the parameter name `id`. Spring converts the matched text (`"42"`) into a `Long` using its built-in converter. If conversion fails (`"hello"` → `Long`), Spring returns `400 Bad Request` by default.

When the parameter name does not match the placeholder, name it explicitly:

```java
@GetMapping("/books/{id}")
public Book one(@PathVariable("id") Long bookId) { ... }
```

Most code uses the implicit form. Match the names and you can drop the explicit `("id")`.

### Several path variables

```java
@GetMapping("/books/{bookId}/reviews/{reviewId}")
public Review oneReview(@PathVariable Long bookId,
                        @PathVariable Long reviewId) { ... }
```

Each placeholder binds to a parameter by name. Order does not matter — Spring matches on name, not position.

### Type conversion

Spring converts path strings to whatever type you declare:

| Declared type      | Path matches                                | Bad value gives |
|--------------------|---------------------------------------------|-----------------|
| `Long` / `Integer` | `"42"`                                      | 400 |
| `UUID`             | `"550e8400-e29b-41d4-a716-446655440000"`    | 400 |
| `Boolean`          | `"true"` / `"false"`                        | 400 |
| `LocalDate`        | `"2025-12-31"` (ISO-8601 by default)        | 400 |
| Your `enum`        | exact case-sensitive name match             | 400 |
| `String`           | anything                                    | (never fails) |

Picking the right type is free input validation. `@PathVariable Long id` rejects `id=abc` before your method body runs.

### Optional path variables

A `@PathVariable` is required by default. To make it optional, either declare two separate mappings:

```java
@GetMapping({"/books", "/books/{id}"})
public Object listOrOne(@PathVariable(required = false) Long id) {
    return id == null ? store.values() : store.get(id);
}
```

…or accept it as `Optional<Long>`:

```java
@GetMapping({"/books", "/books/{id}"})
public Object listOrOne(@PathVariable Optional<Long> id) {
    return id.map(store::get).orElseGet(store::values);
}
```

In practice, optional path variables are rare. Two separate handler methods are usually cleaner.

### Regex constraints on the path

Sometimes you want to restrict what counts as a match — e.g. an `id` must be digits, a `slug` must be lowercase letters. The syntax is `{name:regex}`:

```java
@GetMapping("/books/{id:\\d+}")        // only matches /books/42, not /books/abc
public Book one(@PathVariable Long id) { ... }

@GetMapping("/users/{username:[a-z0-9_]+}")
public User user(@PathVariable String username) { ... }
```

Two effects:

1. If the path does not match the regex, Spring returns `404 Not Found` (no handler matched), not `400`. From the routing layer's view, the URL is for a different endpoint that does not exist.
2. You can have a sibling mapping with a different pattern, and Spring picks the right one:
   ```java
   @GetMapping("/books/{id:\\d+}")        Book byNumericId(@PathVariable Long id) { ... }
   @GetMapping("/books/{slug:[a-z-]+}")   Book bySlug(@PathVariable String slug) { ... }
   ```
   The two compete for `/books/...` and the regex decides which one wins.

Use this sparingly. For most APIs, a typed `@PathVariable Long id` plus a 404 from the service layer (when the id isn't in the store) is enough. Regex is for the cases where the *shape* of the value is meaningful.

---

## Common pitfalls

- **Forgetting `@PathVariable`.** Without the annotation, Spring tries to bind from query parameters or the request body and fails confusingly. Always annotate.
- **Using `String` where the type is known.** `@PathVariable String id` then `Long.parseLong(id)` inside is what new joiners do. Let Spring convert; the 400 it gives on bad input is part of the API contract.
- **Different name in placeholder vs parameter.** `@GetMapping("/books/{id}") void m(@PathVariable Long bookId)` will fail at startup. Either match the names or pass the explicit `@PathVariable("id")`.
- **Putting query-shaped data in the path.** `/books/byAuthor/Bloch?sort=year` looks REST-y but `byAuthor` is a verb. Use a query parameter: `/books?author=Bloch&sort=year`. Topic 05 covers query parameters.
- **Slashes inside a path variable.** A path variable cannot contain `/` by default. If your value has slashes (e.g. a file path), encode them or restructure the URL.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/04-path-variables/path-variables-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```
   curl -i http://localhost:8080/api/books/1
   curl -i http://localhost:8080/api/books/abc        # 400 — Long conversion fails
   curl -i http://localhost:8080/api/books/1/reviews/7
   curl -i http://localhost:8080/api/users/alice
   curl -i http://localhost:8080/api/users/Alice123   # 404 — regex requires lowercase + digits + underscore
   curl -i http://localhost:8080/api/events/by-date/2025-12-31
   curl -i http://localhost:8080/api/events/by-date/not-a-date  # 400
   ```

2. Add a new endpoint `GET /api/books/{id:\\d{1,4}}` that requires 1–4 digit IDs. Confirm a 5-digit id falls through to 404.

3. Replace `@PathVariable Long id` with `@PathVariable String id`. Notice the 400 is gone and your method body now has to do `Long.parseLong(...)` itself — and decide what to do on failure. Argue for or against this change.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`PathVariablesController.java`** — every variant covered above, side by side.
3. **`PathVariablesControllerTest.java`** — MockMvc assertions for each route.

## Self-check

1. A request comes in as `GET /api/books/abc`. The handler declares `@PathVariable Long id`. What status code does the client get, and why?
2. Why does `/users/Alice` return 404 when the regex is `[a-z0-9_]+`, but `/books/abc` returns 400 when the type is `Long`?
3. You have `GET /repos/{owner}/{repo}/issues/{issueId}`. The repo name happens to contain a slash. What goes wrong, and what's the fix?
