# Query parameters — `@RequestParam`

Query parameters are the `?key=value&other=value` part of the URL. They handle filtering, paging, sorting, search — everything that isn't the resource identifier. This topic covers the four `@RequestParam` shapes you will use day to day: required, optional with default, collection, and "all params at once."

---

## The problem this solves

Path variables identify *which* resource. Query parameters say *how to slice it*. The convention is:

| Use the path for       | Use a query parameter for                              |
|------------------------|--------------------------------------------------------|
| Resource id (`/books/{id}`) | Filtering (`?author=Bloch`)                       |
| Sub-resource path (`/books/{id}/reviews`) | Paging (`?page=0&size=10`)         |
| Action shape           | Sorting (`?sort=title,asc`)                            |
|                        | Search (`?q=java`)                                     |
|                        | Optional toggles (`?includeArchived=true`)             |

The rule of thumb: if removing the value changes *which resource* is addressed, it goes in the path. If it changes *which subset of a collection* you get back, it goes in the query.

---

## How it works

### Required parameter

```java
@GetMapping("/search")
public List<Book> search(@RequestParam String author) {
    return store.values().stream()
            .filter(b -> b.author().equals(author))
            .toList();
}
```

`/search` without `?author=...` returns **400 Bad Request** — `@RequestParam` is required by default. Like `@PathVariable`, Spring converts the string to whatever type you declare (`Long`, `LocalDate`, `enum`, etc.) and 400s on conversion failure.

### Optional with default

```java
@GetMapping("/books")
public List<Book> list(@RequestParam(defaultValue = "0")  int page,
                       @RequestParam(defaultValue = "10") int size) {
    return store.values().stream().skip((long) page * size).limit(size).toList();
}
```

`defaultValue` makes the parameter optional *and* gives it a fallback. This is the pattern for paging, sorting, and other "knobs with sensible defaults." Topic 12 builds a full paging implementation around this.

### Optional without a default

```java
@GetMapping("/books")
public List<Book> list(@RequestParam(required = false) String author) {
    if (author == null) return List.copyOf(store.values());
    return store.values().stream().filter(b -> b.author().equals(author)).toList();
}
```

…or with `Optional`:

```java
@GetMapping("/books")
public List<Book> list(@RequestParam Optional<String> author) {
    return author
        .map(a -> store.values().stream().filter(b -> b.author().equals(a)).toList())
        .orElseGet(() -> List.copyOf(store.values()));
}
```

`Optional<T>` and `(required = false)` are equivalent. Pick one style across your codebase.

### Multi-valued parameter

A client can repeat a parameter: `?tag=java&tag=spring&tag=backend`. Bind it as a `List`:

```java
@GetMapping("/books")
public List<Book> list(@RequestParam(required = false) List<String> tag) {
    if (tag == null || tag.isEmpty()) return List.copyOf(store.values());
    return store.values().stream()
            .filter(b -> b.tags().containsAll(tag))
            .toList();
}
```

Comma-separated also works: `?tag=java,spring,backend` binds to the same `List<String>`.

### All parameters at once

Sometimes (admin endpoints, debug tools, generic search) you want every query parameter:

```java
@GetMapping("/debug")
public Map<String, String> debug(@RequestParam Map<String, String> all) {
    return all;
}
```

Use this rarely. Naming each parameter explicitly is better — it documents the API and lets Spring validate inputs.

---

## Type conversion — same as `@PathVariable`

The list from topic 04 applies. `@RequestParam Long bookId`, `@RequestParam LocalDate from`, `@RequestParam Status status` (your enum) — Spring handles the conversion and returns 400 on failure. The error from a missing required parameter is `MissingServletRequestParameterException`; from a bad conversion it's `MethodArgumentTypeMismatchException`. Both surface as 400 by default. Module 11 unifies their error response shape.

---

## When `@RequestParam` vs `@PathVariable` — the decision

Three rules of thumb, in order:

1. **Path = which resource. Query = which subset of a collection.** `/books/{id}` is the book itself; `?author=Bloch` filters the collection.
2. **Path values are required and part of the URL identity. Query values are optional and modify the request.** If your endpoint cannot exist without it, it's a path variable. If the endpoint still makes sense without it, it's a query parameter.
3. **A repeating value is a query parameter.** Paths are single-valued; queries can repeat (`?tag=a&tag=b`).

Wrong:
```text
GET /api/books/byAuthor/Bloch         # "byAuthor" is a verb in the URL
GET /api/books/page/2/size/10         # paging in the path
```

Right:
```text
GET /api/books?author=Bloch
GET /api/books?page=2&size=10
```

---

## Common pitfalls

- **Required parameter missing.** `@RequestParam String author` then a request without it -> 400. If "no author" is a valid call, declare a default or make it optional.
- **`defaultValue` + `Optional<T>` together.** `defaultValue` makes the param technically optional; combining it with `Optional<String>` is contradictory. Use one.
- **Long names that don't match.** `@RequestParam Long bookId` reads `?bookId=...`. The client sending `?book_id=...` will get 400. Either rename the parameter to `book_id` (ugly) or alias with `@RequestParam("book_id") Long bookId`.
- **Sensitive data in query parameters.** URLs are logged everywhere — load balancers, proxies, browser history. Don't put passwords, tokens, or PII in query strings. Use the request body (POST) or headers.
- **Trying to send a complex object as a query parameter.** Once your "filter" needs nested structure, switch to a POST with a JSON body. Or, if it must stay GET-able, accept a flat structure and parse server-side.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/05-query-parameters/query-parameters-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```
   curl -i "http://localhost:8080/api/books?author=Bloch"
   curl -i "http://localhost:8080/api/books"
   curl -i "http://localhost:8080/api/books?page=1&size=2"
   curl -i "http://localhost:8080/api/books?tag=java&tag=spring"
   curl -i "http://localhost:8080/api/books?tag=java,spring"
   curl -i "http://localhost:8080/api/debug?foo=1&bar=2&baz=3"
   curl -i "http://localhost:8080/api/search"        # 400 - author required
   ```

2. Send `?page=abc` and watch the 400 come back. The conversion to `int` fails.

3. Add a fourth parameter `@RequestParam(defaultValue = "title") String sortBy`. Notice you can give defaults for as many parameters as you like.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`QueryParametersController.java`** — all five shapes (required, defaulted, optional, list, all-params).
3. **`QueryParametersControllerTest.java`** — MockMvc assertions for each.

## Self-check

1. When does a value go in the path and when in the query?
2. What is the difference between `@RequestParam(required = false) String x` and `@RequestParam Optional<String> x`?
3. A client sends `?page=abc`. The controller declares `@RequestParam int page`. What does the response look like, and what changed for the response if you switch the parameter type to `String`?
