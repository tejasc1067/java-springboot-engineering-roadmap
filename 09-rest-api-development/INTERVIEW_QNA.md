# Interview questions — REST APIs in Spring Boot

Each entry is a real question from a real interview, answered with depth. If you can read the question out loud and reconstruct the answer in your own words, you're ready.

---

## 1. What does REST actually mean, beyond "JSON over HTTP"?

REST is a set of architectural constraints, defined by Roy Fielding in 2000. The constraints that matter on day one:

- **Resources, not actions.** URLs identify *things*, not operations. `GET /users/42`, not `POST /getUserById`.
- **HTTP verbs do the work.** GET reads, POST creates, PUT replaces, PATCH partially updates, DELETE removes. The verb is part of the request, not the URL.
- **Stateless server.** Each request carries everything needed to handle it. No server-side session keyed by a client cookie. Statelessness lets you scale horizontally — any instance can handle any request.
- **Meaningful status codes.** The HTTP status is part of the contract: 200, 201, 204, 400, 401, 403, 404, 409, 422, 500 each mean specific things.
- **(Fielding-strict) hyperlinks between resources** — HATEOAS. Almost no Java backend in production does this. Not required.

REST predates JSON. The protocol is content-type-agnostic; JSON is just the dominant payload format today.

---

## 2. What is the difference between `idempotent` and `safe` HTTP verbs?

**Safe:** the verb does not change server state. GET is safe. POST, PUT, PATCH, DELETE are not.

**Idempotent:** doing the request twice has the same effect as doing it once.

| Verb | Safe | Idempotent |
|------|------|-----------|
| GET    | yes | yes |
| POST   | no  | no  |
| PUT    | no  | yes |
| PATCH  | no  | not required |
| DELETE | no  | yes |

Why it matters: **idempotent operations are safe to retry** on network failure. After a timeout, the client doesn't know if the request succeeded — for an idempotent verb, retrying is the right call; for a non-idempotent one (POST), retrying might create a duplicate resource. APIs that need retry-safe POST add idempotency keys (a unique client-supplied id the server uses to deduplicate).

---

## 3. PUT vs PATCH — when do you use which?

**PUT replaces the whole resource.** The body is the new complete state. Fields omitted from the body become null (or default). Idempotent.

**PATCH partially updates the resource.** The body is just the changed fields. Idempotency depends on the operation (`{title: "x"}` is idempotent; `{appendTag: "x"}` is not).

Practical rule:
- Body has *all* the fields the resource owns -> PUT.
- Body has *some* of the fields the resource owns -> PATCH.

Some APIs avoid PATCH entirely and use PUT for both ("send the whole thing"). That works for small resources but forces clients to read-then-write — they can't update one field without knowing the others. PATCH wins for non-trivial resources.

---

## 4. After a successful `POST /api/books`, what status do you return, and what header?

**Status:** `201 Created`.

**Header:** `Location: /api/books/{id}` pointing at the newly created resource.

**Body:** the created resource (most common) or empty.

```java
URI location = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(created.id())
        .toUri();
return ResponseEntity.created(location).body(created);
```

A 201 without a `Location` header is technically valid HTTP but breaks every reasonable client expectation. Always include it.

---

## 5. 400 vs 422 — when do you return which?

**400 Bad Request:** the request itself is malformed. Bad JSON, missing required field, wrong type, type-mismatch.

**422 Unprocessable Entity:** the JSON parsed and the field types are right, but the values fail business rules — empty title, negative price, end date before start date.

Spring's default for `@Valid` failures is 400. Many teams (and many style guides) prefer 422 for validation failures, reserving 400 for "broken request structure." Both are defensible. The wrong answer is "we use both, inconsistently."

If asked in an interview: pick one, justify it, mention the other exists.

---

## 6. Why split request DTOs from the domain object?

Two failure modes that DTO discipline prevents:

**Mass assignment (input side).** If `User` is your `@RequestBody`, an attacker sends `{"username":"x","passwordHash":"evil","createdAt":"1970-01-01T00:00:00Z"}` and overwrites server-managed fields. The DTO solves it by simply not having those fields:

```java
record CreateUserRequest(String username, String password) {}
```

**Leak (output side).** If you return `User` directly, the response includes every internal field — password hashes, soft-delete flags, ownership pointers. Easy way for sensitive data to land in logs and frontend code.

```java
record UserResponse(Long id, String username, Instant createdAt) {}
```

`@JsonIgnore` on the domain object papers over the leak but doesn't fix the mass-assignment problem and couples your DB schema to your API contract. Three shapes per resource is the durable answer.

---

## 7. What is `@RestController` composed of, and why does that matter?

`@RestController` is shorthand for `@Controller + @ResponseBody`. `@Controller` registers the class as a Spring MVC controller; `@ResponseBody` tells Spring to write the method's return value directly to the response body (via `HttpMessageConverter` — usually Jackson for JSON) instead of treating it as a view name.

It matters because:

- You can recognize the alternative — plain `@Controller` with `@ResponseBody` on each method — as the equivalent long form.
- You can recognize plain `@Controller` *without* `@ResponseBody` as the view-rendering form, used for server-rendered HTML.
- A `@Controller` method that returns `List<Book>` without `@ResponseBody` tries to render a view, fails, and produces a 500 or whitelabel page. This is the most common "why doesn't my API work" beginner bug.

---

## 8. How does Spring turn a JSON request body into a Java object?

The chain:

1. The request arrives with `Content-Type: application/json`.
2. Spring's `RequestMappingHandlerAdapter` sees `@RequestBody` on the parameter.
3. It looks up an `HttpMessageConverter` that handles `application/json` — by default `MappingJackson2HttpMessageConverter`.
4. Jackson reads the body, finds the target type (your DTO), and binds.
5. For records, Jackson uses the canonical constructor. For classes, it uses a no-arg constructor + setters (or `@JsonCreator` to pick a constructor).
6. The bound object is passed to your method.

Side effects of the chain:

- Unknown fields are **ignored** by default. Toggle with `spring.jackson.deserialization.fail-on-unknown-properties: true`.
- Missing fields become `null` (or primitive default). Validation (`@Valid` + `@NotNull`) catches this.
- Wrong types cause `HttpMessageNotReadableException` -> 400.

---

## 9. What is the difference between `@PathVariable` and `@RequestParam`?

`@PathVariable` binds a segment of the URL path. `@RequestParam` binds a query-string key/value.

| | `@PathVariable` | `@RequestParam` |
|---|---|---|
| Lives in | `/api/books/{id}` | `?author=Bloch` |
| Identifies | The resource itself | A filter, paging, or option |
| Required by default | Yes | Yes |
| Repeatable | No | Yes (`?tag=a&tag=b`) |

Rule of thumb: if removing the value changes *which* resource is addressed, it's a path variable. If it changes *which subset of a collection* you get back, it's a query parameter.

---

## 10. What happens when Spring can't convert a `@PathVariable Long id` from "abc"?

Spring throws `MethodArgumentTypeMismatchException`. The default handler returns `400 Bad Request`. The controller method is never called.

This is one of the small wins of typed path variables: you get input validation for free. If the parameter were `@PathVariable String id` and you did `Long.parseLong(id)` inside, the failure would be a `NumberFormatException` -> 500 instead of the cleaner 400.

---

## 11. What's a `ResponseEntity`, and when should I use one?

`ResponseEntity<T>` is Spring's wrapper for "controlled response": you set the status, headers, and body explicitly. Reach for it when any of those vary by outcome:

- Status varies (200 vs 404, 200 vs 201).
- Headers matter (`Location` on 201, `Cache-Control`, `Retry-After`).
- Sometimes there is no body (`ResponseEntity.noContent().build()`).

For methods that always return 200 with a body, plain `return book;` is shorter and behaves identically. `ResponseEntity` is the escape hatch, not the default.

---

## 12. What does `@Valid` actually do?

`@Valid` triggers Bean Validation on the annotated parameter. Spring inspects the parameter's class for `jakarta.validation` constraint annotations (`@NotBlank`, `@Email`, `@Min`, ...), runs the validator (Hibernate Validator by default), and if any constraint fails, throws `MethodArgumentNotValidException`. The default handler returns 400.

Three gotchas:

1. Without `@Valid`, the constraint annotations are *not* run. Easy to miss.
2. For nested DTOs, you need `@Valid` on the nested field as well.
3. For `@PathVariable` and `@RequestParam` validation, use class-level `@Validated` (a Spring annotation, not `@Valid`) — failures throw `ConstraintViolationException`, a different exception.

---

## 13. Why is `Hibernate Validator` not in `spring-boot-starter-web`?

Historical reason: Hibernate Validator was once an optional dependency, and `spring-boot-starter-web` aimed to stay slim. The validation API (`jakarta.validation-api`) is on the classpath via transitive dependencies, so your annotations *compile*, but no implementation is wired to run them.

Result: a project with `spring-boot-starter-web` only and `@Valid` annotations everywhere silently doesn't validate.

Fix: explicitly add `spring-boot-starter-validation`. It is a separate dependency on purpose so the framework doesn't pull in Hibernate Validator for services that don't need it.

---

## 14. When would you use `@WebMvcTest` instead of `@SpringBootTest`?

`@WebMvcTest` loads **only** the web layer: the specified controller, MVC infrastructure, message converters, validators. No `@Service`, `@Repository`, `@Component` outside the web layer. Dependencies must be mocked via `@MockitoBean`.

Use it when:
- You want to test the controller in isolation, with its collaborators stubbed.
- You want fast tests — `@WebMvcTest` finishes in ~500 ms; `@SpringBootTest` takes several seconds because it boots the whole context.
- You explicitly want to assert "this controller does the right thing *if* its service does X."

Use `@SpringBootTest` when you want an integration test exercising real beans together. Both have their place; in a large codebase, the slice tests vastly outnumber the full-context ones.

---

## 15. How is `MockMvc` different from `TestRestTemplate`?

`MockMvc` performs the request *inside the JVM*. No socket, no network, no real Tomcat. It runs the full Spring MVC pipeline — binding, validation, routing, message conversion — and gives you the response object that would have been written to the wire. Faster, deterministic, no port conflicts.

`TestRestTemplate` makes a real HTTP request to a real embedded server (started by `@SpringBootTest(webEnvironment = RANDOM_PORT)`). It tests the actual servlet container, real filter chain, real socket I/O.

For controller behaviour: `MockMvc`. For integration tests of the whole stack including Tomcat and security filters: `TestRestTemplate`. Most projects use `MockMvc` for 95% of tests and `TestRestTemplate` for a small number of end-to-end checks.

---

## 16. What's wrong with offset pagination on large tables, and what's the alternative?

Two problems:

1. **Cost grows with offset.** A SQL `LIMIT 20 OFFSET 1000000` makes the database scan a million rows and discard them. At 100 rows/page you can browse 1000 pages cheaply; by page 50 000 the query is unusable.

2. **Drift under concurrent writes.** If a row is inserted between fetches of page 1 and page 2, the new row appears on both pages — or skips one — depending on ordering.

**Cursor pagination** addresses both. Instead of a numeric offset, the client sends a token (opaque to them) derived from the last item of the previous page. The server decodes it into a `WHERE id > X` clause. Cost is O(limit), not O(offset). And the cursor pins the position — no drift.

Cursor paging is harder to implement and breaks "jump to page 47." Use offset for small datasets and admin UIs; switch to cursor when you outgrow it.

---

## 17. Three places to put a version, and which one's the default?

| Strategy | Example | When |
|----------|---------|------|
| URI | `GET /api/v1/books` | **Default.** Most teams, most services. Easy to debug, easy to test, easy to route. |
| Header | `GET /api/books` + `X-API-Version: 1` | Rare. Worst-of-both-worlds: invisible, custom header. |
| Media type | `GET /api/books` + `Accept: application/vnd.example.v1+json` | REST-pure. Used by GitHub, Stripe-era SDKs. Awkward to test by hand. |

For a new service, URI versioning until you have a reason to do otherwise. Start at `v1` on day one — retroactively adding the prefix is a flag day.

---

## 18. What does `springdoc-openapi` give you, and what would a production deployment change?

Adding `springdoc-openapi-starter-webmvc-ui` exposes two endpoints automatically:

- `/v3/api-docs` — the OpenAPI 3 JSON spec, generated from your `@RestController`s, `@RequestBody` shapes, return types, and any springdoc annotations (`@Operation`, `@Schema`, `@ApiResponses`, `@Tag`).
- `/swagger-ui.html` — interactive UI that consumes the JSON.

In production, two changes:

- **Disable the UI** (`springdoc.swagger-ui.enabled: false`). An open Swagger UI lists every endpoint to anyone who can reach the service — useful intelligence for an attacker.
- **Keep the spec** (`springdoc.api-docs.enabled: true`) so tooling — code generators, contract tests — can still consume it.

Or put both behind the same authentication you use for admin endpoints.

---

## 19. How do you express "this endpoint creates a thing async and you should poll for completion"?

Return **`202 Accepted`** with a body that includes:

- a `jobId` (or status URL) the client uses to poll progress,
- optionally the estimated time-to-completion.

The semantics: "I received your request, it's running in the background, here's how to check on it."

A separate endpoint — `GET /api/jobs/{id}` — returns the job state (pending, running, done, failed). Once done, the response usually includes a URL to the final created resource. This is the standard pattern for long-running operations: file processing, data imports, integration-with-slow-third-parties.

---

## 20. A request lands at a controller that requires authentication. The user is not logged in. Which status?

**401 Unauthorized.** Despite the name, it means *unauthenticated* — no credentials supplied or the credentials are invalid.

If credentials were supplied and are valid but the user isn't permitted to access this resource: **403 Forbidden.**

This is the most-confused status code pair. Remember:

- 401 = "Who are you? I don't know yet."
- 403 = "I know who you are. You can't have this."

Module 12 covers the security side of how Spring decides which one to return.
