# `ResponseEntity` and response shaping

So far every controller method has returned an object and let Spring use 200 OK with the JSON body. That works until you need to return 201 Created, set a `Location` header, return 204 No Content, send an empty 404, or attach a cache-control header. The tool for all of that is `ResponseEntity<T>`.

---

## The problem this solves

Spring lets you return three different shapes from a controller method:

| Return value | Status | Body | Headers |
|--------------|--------|------|---------|
| `Book` (a plain object)       | 200 OK | the JSON of the object         | content-type only |
| `null` or `void`              | 200 OK | empty                          | content-type only |
| `ResponseEntity<Book>`        | whatever you set | whatever you set | whatever you set |

The first form is what we have been using since topic 02. The third form is the **escape hatch** — when you need control over the response, wrap your return value in `ResponseEntity`.

You don't have to wrap every method. The right rule is: **use `ResponseEntity` when the status or headers vary by outcome.** Otherwise return the object directly.

---

## How it works

### The constructors that matter

```java
// 200 OK with a body
ResponseEntity.ok(book);

// 201 Created with a Location header pointing at the new resource
URI location = URI.create("/api/books/" + created.id());
ResponseEntity.created(location).body(created);

// 204 No Content (e.g. after DELETE)
ResponseEntity.noContent().build();

// 404 Not Found (no body)
ResponseEntity.notFound().build();

// 200 OK with a custom header
ResponseEntity.ok()
        .header("X-Rate-Limit-Remaining", "42")
        .body(book);

// arbitrary status + body
ResponseEntity.status(HttpStatus.CONFLICT).body(error);
```

### The classic POST + Location pattern

This is what every well-built REST API does after a successful create:

```java
@PostMapping
public ResponseEntity<Book> create(@RequestBody Book incoming) {
    Book created = service.save(incoming);
    URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
}
```

The response:

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/books/42
Content-Type: application/json

{ "id": 42, "title": "Effective Java", ... }
```

Clients use the `Location` header to know where to fetch the newly created thing. The pattern is so common that `ResponseEntity.created(uri).body(...)` is a single line; the only fiddly bit is building the URI. `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")` builds it relative to the request that came in, which is exactly what you want.

### The 404 pattern

```java
@GetMapping("/{id}")
public ResponseEntity<Book> one(@PathVariable Long id) {
    Book found = store.get(id);
    if (found == null) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(found);
}
```

`ResponseEntity.notFound().build()` is the no-body 404. There is no body type to provide; the generic on `ResponseEntity<Book>` works because Java's type system tolerates a `ResponseEntity<Void>`-compatible build.

A cleaner shape using `Optional`:

```java
@GetMapping("/{id}")
public ResponseEntity<Book> one(@PathVariable Long id) {
    return Optional.ofNullable(store.get(id))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
}
```

In module 11 you will replace these explicit 404 returns with throwing a `ResourceNotFoundException` and handling it in `@ControllerAdvice`. For now, the explicit return is the simplest shape.

### The 204 pattern

After DELETE, the convention is **204 No Content**:

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    if (!store.containsKey(id)) {
        return ResponseEntity.notFound().build();
    }
    store.remove(id);
    return ResponseEntity.noContent().build();
}
```

204 means "the action succeeded and there is no body to return." The client should not try to parse JSON. Topic 08 covers when to use 204 vs 200 in more depth.

---

## When to use `ResponseEntity` and when not to

**Use `ResponseEntity` when:**

- The status code depends on the outcome (200 vs 404, 200 vs 201, 200 vs 409).
- You need to set a header (`Location`, `Cache-Control`, `X-...`).
- The body is sometimes absent (`build()` instead of `body(...)`).

**Don't use `ResponseEntity` when:**

- The method always returns 200 with a body. `return book;` is shorter and clearer than `return ResponseEntity.ok(book);` — and Spring does exactly the same thing.

Mixing styles in one controller is fine. List endpoints often stay plain; the create/update/delete endpoints reach for `ResponseEntity`.

---

## What about `@ResponseStatus`?

You may see the older pattern:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Book create(@RequestBody Book incoming) {
    return service.save(incoming);
}
```

This sets the status code at the annotation level. It works, but it does **not** let you set the `Location` header — and a `201 Created` without a `Location` is incomplete. Use `@ResponseStatus` for simple "always returns this status" cases (e.g. a 202 Accepted endpoint), and `ResponseEntity` whenever headers matter.

---

## Common pitfalls

- **`ResponseEntity.ok(null)` vs `ResponseEntity.ok().build()`.** The first sends a body that Jackson serializes as the JSON literal `null`. The second sends no body at all. Different on the wire.
- **Setting `Content-Type` manually.** Spring sets `application/json` by default for `@RestController`. Override only when you intentionally produce another format.
- **Forgetting the `Location` header on 201.** Technically valid HTTP, but it breaks the convention every client expects. Always include it on a successful create.
- **Hardcoding the URI in the `Location` header.** `URI.create("/api/books/42")` works in dev and breaks behind a reverse proxy where the path is prefixed. Use `ServletUriComponentsBuilder.fromCurrentRequest()` so the URL matches whatever the client sees.
- **Returning a 404 with a body and `Content-Type: application/json`.** Fine if you mean to; but a stray `ResponseEntity.notFound().body(...)` does compile and confuses clients who expect 404 to be empty. Module 11 standardises error bodies.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/07-responseentity-and-response-shaping/responseentity-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```
   curl -i -X POST http://localhost:8080/api/books \
        -H "Content-Type: application/json" \
        -d '{"title":"EJ","author":"Bloch"}'                # 201 + Location header
   curl -i http://localhost:8080/api/books/1                  # 200 + JSON
   curl -i http://localhost:8080/api/books/9999               # 404, no body
   curl -i -X DELETE http://localhost:8080/api/books/1        # 204, no body
   curl -i -X DELETE http://localhost:8080/api/books/9999     # 404, no body
   ```

2. Read the `Location` header off the 201 and `curl` it. You should get the resource back with 200.

3. Replace `ResponseEntity.notFound().build()` with `ResponseEntity.ok(null)`. Re-run; note the status drops from 404 to 200 and the body becomes `null`. Argue why this is worse for the client.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — the record.
3. **`BookController.java`** — POST returning 201 + Location, GET returning 200 or 404, DELETE returning 204 or 404. List endpoint stays plain (always 200).
4. **`BookControllerTest.java`** — MockMvc assertions for each status and the Location header.

## Self-check

1. After a successful POST, what status code should you return, and what header should accompany it?
2. When would you reach for `ResponseEntity` instead of returning the object directly? Give two scenarios.
3. What is the difference between `ResponseEntity.ok(null)` and `ResponseEntity.ok().build()` on the wire?
