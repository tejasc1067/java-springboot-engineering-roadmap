# HTTP verbs in Spring

Spring has five method-level mapping annotations that bind a controller method to one HTTP verb. The five together let you implement a full CRUD resource. Knowing which verb means what is half of REST; this topic is the other half — how to express each one in Spring.

---

## The problem this solves

In bare Spring MVC you would write:

```java
@RequestMapping(value = "/books", method = RequestMethod.GET)
public List<Book> all() { ... }

@RequestMapping(value = "/books", method = RequestMethod.POST)
public Book create(@RequestBody Book book) { ... }
```

That works, but it is noisy. Since Spring 4.3 (2016) there have been five focused shortcuts:

| Shortcut | Equivalent to | HTTP verb |
|----------|---------------|-----------|
| `@GetMapping`    | `@RequestMapping(method = RequestMethod.GET)`    | GET    |
| `@PostMapping`   | `@RequestMapping(method = RequestMethod.POST)`   | POST   |
| `@PutMapping`    | `@RequestMapping(method = RequestMethod.PUT)`    | PUT    |
| `@DeleteMapping` | `@RequestMapping(method = RequestMethod.DELETE)` | DELETE |
| `@PatchMapping`  | `@RequestMapping(method = RequestMethod.PATCH)`  | PATCH  |

They are syntactic sugar. Behind the scenes the framework treats them identically to the long form. Always reach for the focused shortcut.

---

## How it works — a full CRUD controller

This is the shape every resource controller will follow in modules 09–10:

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final Map<Long, Book> store = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @GetMapping                      // GET /api/books        list
    public Collection<Book> all() {
        return store.values();
    }

    @GetMapping("/{id}")             // GET /api/books/{id}   read one
    public Book one(@PathVariable Long id) {
        return store.get(id);
    }

    @PostMapping                     // POST /api/books       create
    public Book create(@RequestBody Book incoming) {
        Long id = nextId.getAndIncrement();
        Book created = new Book(id, incoming.title(), incoming.author());
        store.put(id, created);
        return created;
    }

    @PutMapping("/{id}")             // PUT /api/books/{id}   full replace
    public Book replace(@PathVariable Long id, @RequestBody Book incoming) {
        Book replacement = new Book(id, incoming.title(), incoming.author());
        store.put(id, replacement);
        return replacement;
    }

    @PatchMapping("/{id}")           // PATCH /api/books/{id} partial update
    public Book patch(@PathVariable Long id, @RequestBody Map<String, Object> changes) {
        Book current = store.get(id);
        String title  = (String) changes.getOrDefault("title", current.title());
        String author = (String) changes.getOrDefault("author", current.author());
        Book updated = new Book(id, title, author);
        store.put(id, updated);
        return updated;
    }

    @DeleteMapping("/{id}")          // DELETE /api/books/{id}
    public void delete(@PathVariable Long id) {
        store.remove(id);
    }
}
```

The status codes here are simplified — every method returns `200 OK` by default. Real APIs return `201 Created` after POST, `204 No Content` after DELETE, and so on. Topic 07 (`ResponseEntity`) and topic 08 (status codes) cover that. Validation, error handling, and "what if `id` doesn't exist" all get unpacked later. This topic is just about the **verb mapping itself.**

---

## PUT vs PATCH — the one decision people get wrong

This is the single most-asked interview question on REST.

**PUT replaces the whole resource.** The body is the new, complete state. If you omit a field, that field becomes null (or its default).

```http
PUT /api/books/42
Content-Type: application/json

{ "title": "Effective Java", "author": "Bloch" }
```

After this request, book 42 has exactly those two fields. If the original had a `publishedYear`, it is gone now.

**PATCH partially updates the resource.** The body says what changed; the rest stays.

```http
PATCH /api/books/42
Content-Type: application/json

{ "title": "Effective Java, 3rd Edition" }
```

After this request, book 42's title changed; everything else is untouched.

The mental model: **PUT is "here is the new version of the resource," PATCH is "here are the fields I want to change."**

### Idempotency revisited

- PUT is idempotent: sending the same PUT five times leaves the resource in the same state as sending it once. Safe to retry.
- PATCH is **not required to be** idempotent (though it can be). `{"title": "x"}` happens to be idempotent. `{"appendTag": "new"}` is not. Treat PATCH as non-idempotent unless your specific operation provably is.
- POST is not idempotent: each call creates a new resource. (This is why a network-retry after a POST can create duplicate records — design for it.)
- GET, DELETE are idempotent. GET is also safe (no side effects).

### When you can use just PUT

Some APIs skip PATCH entirely and use PUT for both replacement and "I'm sending the full thing back, updated." That works for small resources. The trouble starts with large ones: clients must send fields they don't care about, and you lose the ability to express "set this field to null" cleanly. PATCH wins for anything non-trivial.

---

## Where it pays off

The CRUD shape above is the foundation of every Spring Boot REST API. Once you have it:

- Topic 04 refines `{id}` extraction (`@PathVariable`).
- Topic 05 adds filters (`@RequestParam`).
- Topic 06 explains how the JSON body becomes a `Book` (`@RequestBody` + Jackson).
- Topic 07 wraps the responses in `ResponseEntity` so you can control status and headers.
- Topic 10 adds `@Valid` to the request body so bad input is rejected with a 400.
- Topic 11 tests the whole thing with MockMvc.

This topic's demo is the simplest possible version. Each later topic upgrades one piece.

---

## Common pitfalls

- **Using POST for everything.** Tutorial code from 2010 often does this. It works, but you lose retry safety, caching, and the conventions other engineers expect. Use the verb that fits.
- **Wrapping GET in POST to send a body.** Tempting when you have a complex filter. Use query parameters (topic 05); or, if the filter genuinely cannot fit in a URL, accept that `POST /searches` is the right shape for that endpoint and document it.
- **Confusing PUT and PATCH.** If your "update" endpoint requires the client to send every field, that's PUT. If it accepts a subset, that's PATCH. Picking the wrong one will surprise consumers.
- **Forgetting that DELETE is idempotent.** Calling DELETE on an already-deleted resource is fine — return 204 (or 404, depending on your taste). Topic 08 has the decision tree.
- **GETs with side effects.** Never. Crawlers, prefetchers, and proxies all assume GET is safe. A GET that mutates state will eventually fire when you don't expect.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/03-http-verbs-in-spring/http-verbs-demo`.

1. Run `mvn -q spring-boot:run`, then walk a full CRUD round-trip:
   ```
   curl -i -X POST http://localhost:8080/api/books \
        -H "Content-Type: application/json" \
        -d '{"title":"Effective Java","author":"Bloch"}'
   curl -i http://localhost:8080/api/books
   curl -i http://localhost:8080/api/books/1
   curl -i -X PUT http://localhost:8080/api/books/1 \
        -H "Content-Type: application/json" \
        -d '{"title":"Effective Java 3rd","author":"Bloch"}'
   curl -i -X PATCH http://localhost:8080/api/books/1 \
        -H "Content-Type: application/json" \
        -d '{"title":"EJ3"}'
   curl -i -X DELETE http://localhost:8080/api/books/1
   ```

2. Watch what PATCH does when you send only `{"title": ...}` — the `author` stays. Then watch PUT erase `author` if you omit it from the body.

3. Send a `DELETE /api/books/9999` (a missing id). Note the status is still 200 with this naive implementation. We will fix that in topic 08 (status codes).

## Code examples (reading order)

1. **`App.java`** — `@SpringBootApplication` bootstrap.
2. **`Book.java`** — the record we move over the wire.
3. **`BookController.java`** — the full CRUD using all five verbs.
4. **`BookControllerTest.java`** — MockMvc walks through each verb.

## Self-check

1. Which two of GET, POST, PUT, PATCH, DELETE are idempotent? Which is also safe?
2. You have an `Order` resource with 20 fields. The client wants to update one field (`status`). PUT or PATCH, and why?
3. A client times out on `POST /api/orders`. They retry the same request. What can go wrong, and what would make the retry safe?
