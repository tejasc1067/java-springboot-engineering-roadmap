# Common mistakes — REST APIs in Spring Boot

Real bugs, with code that demonstrates them and the fix. Skim these before you start a new controller; revisit when something behaves unexpectedly.

---

## 1. `@Controller` instead of `@RestController` for JSON endpoints

```java
@Controller                                  // wrong for JSON
@RequestMapping("/api/books")
public class BookController {
    @GetMapping
    public List<Book> all() { return ... ; }  // returned value treated as a view name
}
```

**What goes wrong:** Spring's view machinery tries to render `List<Book>` as a view, fails to resolve a view name, and either returns the Whitelabel error page or a 500.

**Fix:** Use `@RestController` (which is `@Controller + @ResponseBody`) for any controller serving JSON.

---

## 2. Mass-assignment by accepting the domain object as the request body

```java
@PostMapping
public User create(@RequestBody User user) {     // attacker sets passwordHash, createdAt
    return userService.save(user);
}
```

**What goes wrong:** A malicious client posts `{"username":"x","passwordHash":"evil","createdAt":"1970-01-01T00:00:00Z"}` and overwrites server-managed fields.

**Fix:** Split request and response DTOs. The request DTO declares only client-supplied fields; the response DTO hides sensitive ones. See topic 09.

---

## 3. `200 OK` with `{"success": false}` for errors

```java
@GetMapping("/{id}")
public Map<String, Object> one(@PathVariable Long id) {
    Book found = store.get(id);
    if (found == null) {
        return Map.of("success", false, "error", "not found");   // wrong: status is 200
    }
    return Map.of("success", true, "data", found);
}
```

**What goes wrong:** HTTP has status codes for a reason. Clients (and load balancers, monitoring tools, browsers) cannot tell from the status that this is an error. Retry logic gets it wrong; error rates show 0%.

**Fix:** Use the protocol. `404 Not Found` here. See topic 08.

---

## 4. Forgetting the `Location` header on a 201

```java
@PostMapping
public ResponseEntity<Book> create(@RequestBody Book b) {
    Book saved = service.save(b);
    return ResponseEntity.status(201).body(saved);     // missing Location
}
```

**What goes wrong:** Technically valid HTTP, conventionally broken. Clients expect a `Location` header on `201 Created` pointing at the new resource so they can fetch it later. They write code to read it; you broke their code.

**Fix:** `ResponseEntity.created(uri).body(saved)`, with `uri` built via `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri()`. See topic 07.

---

## 5. Hardcoded URI in `Location`

```java
URI location = URI.create("/api/books/" + saved.id());   // breaks behind a proxy
return ResponseEntity.created(location).body(saved);
```

**What goes wrong:** Works in dev, breaks in prod. Behind a reverse proxy that adds a prefix (`/service-foo/api/books/...`), the hardcoded path is wrong — clients get a 404 when they follow it.

**Fix:** `ServletUriComponentsBuilder.fromCurrentRequest()` builds the URI relative to the URL the client actually used.

---

## 6. Verbs in URLs

```text
POST /api/createUser          POST /api/getUserById       POST /api/deleteUser
```

**What goes wrong:** Loses caching, retry safety, and consumer expectations. Indistinguishable from RPC, but called REST.

**Fix:** Nouns in URLs, verbs in HTTP methods. `POST /api/users` to create, `GET /api/users/{id}` to read, `DELETE /api/users/{id}` to delete. See topic 01.

---

## 7. GET with a request body

```java
@GetMapping("/search")
public List<Book> search(@RequestBody SearchFilter filter) { ... }
```

**What goes wrong:** Technically allowed by HTTP/1.1, broken in practice. Proxies strip the body. CDNs cache GETs by URL only. `fetch()` in browsers refuses to attach a body to GET. Some servers ignore it.

**Fix:** Either encode the filter in query parameters (`?author=Bloch&year=2017`) or use `POST /api/searches` if the filter is too complex for the URL. See topic 05.

---

## 8. Forgetting the `Content-Type` header on POST

```bash
curl -X POST http://localhost:8080/api/books -d '{"title":"X"}'    # no Content-Type
```

**What goes wrong:** Spring returns `415 Unsupported Media Type`. The controller method is never called.

**Fix:** Always set `-H "Content-Type: application/json"` on JSON-body requests.

---

## 9. `@NotNull` on a String when you mean `@NotBlank`

```java
public record CreateUserRequest(@NotNull String username, ...) {}
```

**What goes wrong:** `{"username": ""}` passes validation because empty string is not null. The blank value gets through.

**Fix:** `@NotBlank` — not null, not empty, not whitespace-only. The right choice for required strings.

---

## 10. Forgetting `@Valid` on the parameter

```java
@PostMapping
public User create(@RequestBody CreateUserRequest req) { ... }   // missing @Valid
```

**What goes wrong:** The DTO has `@NotBlank` / `@Email` / etc. annotations, but Spring never runs them. Invalid input flows through to the method body. Easy to miss — the code compiles and seems to work.

**Fix:** `@Valid @RequestBody CreateUserRequest req`. See topic 10.

---

## 11. `@Valid` without `spring-boot-starter-validation`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<!-- spring-boot-starter-validation missing -->
```

**What goes wrong:** `jakarta.validation` annotations are on the classpath via transitive deps, the code compiles, but Hibernate Validator (the implementation that actually runs the rules) is not. Validations silently never fire.

**Fix:** Add `spring-boot-starter-validation` explicitly.

---

## 12. Returning a 200 with empty body when the resource is missing

```java
@GetMapping("/{id}")
public Book one(@PathVariable Long id) {
    return store.get(id);          // returns null when missing
}
```

**What goes wrong:** Spring serializes `null` as the empty body and returns 200. Client sees a successful response with no data and gets confused. Logs show no error. Real bug, hard to spot.

**Fix:** `ResponseEntity.notFound().build()` (topic 07) or throw a `ResponseStatusException(HttpStatus.NOT_FOUND, ...)` (topic 08) or, in module 11, a domain exception with `@ResponseStatus`.

---

## 13. Sorting / filtering on un-whitelisted fields

```java
String field = request.getParameter("sort");
Comparator<Book> c = Comparator.comparing(b -> /* reflection on field */);
```

**What goes wrong:** A malicious client can sort by `passwordHash`, exposing the lexicographic order (which leaks information). Or sort by an unindexed column once you have a real database — every query becomes a full scan. Both are real bugs.

**Fix:** Whitelist sortable fields explicitly. See the demo for topic 12.

---

## 14. No max page size

```java
@GetMapping
public List<Book> all(@RequestParam(defaultValue = "10") int size) {
    return store.values().stream().limit(size).toList();
}
```

**What goes wrong:** `?size=1000000` returns a million records — OOMs your service or starves it of CPU. The bug is exploitable; topic 12 documents it as the most common DoS vector for paginated APIs.

**Fix:** Clamp `size` to a hard maximum (e.g. 100). Document the maximum in your API docs.

---

## 15. Mixing 400 and 422 for validation in the same API

```java
@PostMapping
public ResponseEntity<User> create(@Valid @RequestBody CreateUserRequest req) { ... }
// Spring returns 400 by default

@PostMapping("/orders")
public ResponseEntity<Order> orders(@Valid @RequestBody CreateOrderRequest req) {
    if (req.items().isEmpty()) {
        return ResponseEntity.unprocessableEntity().build();   // 422
    }
    ...
}
```

**What goes wrong:** Clients can't tell which one to expect. Error handling on the client side becomes if/else for both codes. Inconsistent across endpoints, painful to document.

**Fix:** Pick one for the whole API and apply it consistently. Spring's default for `@Valid` is 400 — easiest to stick with that.

---

## 16. Inconsistent pluralization

```text
GET /api/users           list users
GET /api/order/{id}      one order  <- singular, vs plural elsewhere
GET /api/products        list products
```

**What goes wrong:** Client SDKs hand-roll the URL for each resource. Inconsistent plurals = bugs in every client.

**Fix:** Pick plurals (`/api/users`, `/api/orders`, `/api/products`) and apply across every endpoint.

---

## 17. Returning the entity directly when it has sensitive fields

```java
@GetMapping("/{id}")
public User one(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();   // contains passwordHash, ssn, ...
}
```

**What goes wrong:** Every response leaks fields the client shouldn't see. Often discovered the moment it lands in production logs or a frontend captures it.

**Fix:** Map to a `UserResponse` DTO that excludes sensitive fields. `@JsonIgnore` works as a quick patch but is not as durable as a separate DTO. See topic 09.

---

## 18. `@RequestParam List<String>` and comma-splitting

```java
@GetMapping
public List<Book> list(@RequestParam(required = false) List<String> sort) { ... }
```

Client sends `?sort=year,asc`. Spring splits the value on commas and binds `sort = ["year", "asc"]`, not `["year,asc"]` as a Spring Data-style parser would expect.

**What goes wrong:** Your parsing logic for `"field,direction"` never sees the comma — it gets two single-field entries instead.

**Fix:** Read the raw values via `HttpServletRequest.getParameterValues("sort")`, or rely on Spring Data's `SortHandlerMethodArgumentResolver` (module 10).

---

## 19. Skipping version numbers

```text
/api/v1/books            shipped 2024
/api/v3/books            shipped 2025 — no /api/v2 ever existed
```

**What goes wrong:** Clients trying to upgrade incrementally find no `/api/v2/`. Confusion.

**Fix:** Bump by one. Even if "v2 was internal experiments," call the next public version v2.

---

## 20. Trailing slash inconsistency

Some clients call `/api/books/`, others call `/api/books`. Since Spring Boot 3.0, the two are different paths by default.

**What goes wrong:** Half of clients get 404.

**Fix:** Pick one — usually no trailing slash — and document it. If you must accept both, configure `PathPatternRouteFunction` to ignore trailing slashes, or expose both paths on the same handler.

---

## 21. Logging the response body of every request

```java
log.info("Response body: {}", body);   // includes passwordHash, PII, secrets
```

**What goes wrong:** Log aggregation systems index everything. Passwords, PII, tokens land in your log database. Compliance failure, breach risk.

**Fix:** Don't log full response bodies. If you must log for debugging, redact sensitive fields. Better: log only the status and request id.

---

## 22. Forgetting `@SpringBootTest` or `@WebMvcTest` on a controller test

```java
class BookControllerTest {                       // no test annotation
    @Autowired MockMvc mockMvc;                  // null
}
```

**What goes wrong:** `MockMvc` is null at runtime; the test fails with `NullPointerException` before any assertion runs. Spring did not boot.

**Fix:** Annotate the class — `@SpringBootTest` for full context, `@WebMvcTest(BookController.class)` for slice. See topic 11.
