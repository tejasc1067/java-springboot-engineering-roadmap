# DTO vs entity, and shaping responses

Returning your domain object directly works on day one. By day thirty the same object is leaking a password hash, missing a field the new mobile client needs, and tightly coupling your database schema to your API contract. This topic introduces the discipline that prevents all three: separate **request DTOs**, **response DTOs**, and the **domain model**.

---

## The problem this solves

Imagine a `User` class with internal fields:

```java
class User {
    Long id;
    String username;
    String passwordHash;       // never goes out
    Instant createdAt;         // server-managed, never comes in
    Instant lastLoginAt;       // also server-managed
}
```

If your controller does this:

```java
@PostMapping
public User create(@RequestBody User user) {           // wrong
    return service.save(user);                          // wrong
}
```

…you have two leaks. **Inbound:** a malicious client can send `{"id": 1, "passwordHash": "...", "createdAt": "1970-01-01T00:00:00Z"}` and try to overwrite server-managed fields. **Outbound:** every response now includes the password hash and the internal timestamps.

The fix is to split the wire shape from the domain shape:

```java
record CreateUserRequest(String username, String password) {}
record UserResponse(Long id, String username, Instant createdAt) {}
```

The controller now binds `CreateUserRequest` (no `id`, no `passwordHash`, no `createdAt`), produces a `User` internally, and returns a `UserResponse` (no `passwordHash`).

---

## How it works — three shapes per resource

For most resources you will have three distinct types:

| Type | Purpose | Owns | Excludes |
|------|---------|------|----------|
| `CreateXRequest` | The body of POST | User-supplied creation fields | Anything the server assigns: `id`, timestamps, server-side flags |
| `UpdateXRequest` | The body of PUT/PATCH | Mutable fields | `id` (in the path), `createdAt`, anything immutable after creation |
| `XResponse` | The body of GET / response of POST | Everything safe to expose | Internal fields: hashes, secrets, soft-delete flags, ownership pointers, internal ids |

And alongside them, your **domain object** (`User`) — the in-memory or persisted representation. Module 10 turns this into a JPA `@Entity`; here in module 09 it is just a plain class living in `service`.

The controller's job is to **translate** between the wire shapes and the domain object.

### A full example

```java
// Domain
public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private Instant createdAt;
    // constructors, getters/setters
}

// Wire — input
public record CreateUserRequest(String username, String password) {}
public record UpdateUserRequest(String username) {}

// Wire — output
public record UserResponse(Long id, String username, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getCreatedAt());
    }
}

// Controller
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest req) {
        User created = service.create(req.username(), req.password());
        URI loc = URI.create("/api/users/" + created.getId());
        return ResponseEntity.created(loc).body(UserResponse.from(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> one(@PathVariable Long id) {
        return service.findById(id)
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

Notice the controller never sees the password *hash*. The service hashes it inside `create(...)` before storing it. The response DTO doesn't even have the field. There is no `@JsonIgnore` needed because the field literally is not on the wire type.

---

## The static factory method pattern

`UserResponse.from(User)` is a small but useful convention. Three reasons:

1. **One place to change.** Add a new response field, update one factory method, every endpoint reflects the change.
2. **Reads naturally.** `UserResponse.from(user)` is a clearer name than `new UserResponse(user.getId(), user.getUsername(), user.getCreatedAt())` scattered everywhere.
3. **Composable for lists.** `users.stream().map(UserResponse::from).toList()` is a one-liner.

For the inbound direction, the service usually handles the mapping rather than the DTO — `service.create(req.username(), req.password())` is clearer than `service.create(req.toUser())`. Either works; pick a style and apply it.

For larger projects, libraries like **MapStruct** generate the mapping code at compile time so you don't write the field-by-field copy. Useful in services with 50+ fields per DTO. Overkill for a 3-field record.

---

## What `@JsonIgnore` is, and why DTOs are still better

You can avoid leaking the password hash without separate DTOs:

```java
class User {
    Long id;
    String username;
    @JsonIgnore String passwordHash;
    Instant createdAt;
}
```

…and return `User` directly. It works. So why bother with DTOs?

- **One class, two contracts.** The same `User` is used for input and output. To prevent a client from setting `createdAt` on input, you would have to also `@JsonIgnore` it, but then the *response* can't expose it either. The two directions need different shapes.
- **Coupling.** When the DB schema gains a column, your API contract changes silently. Consumers can't tell whether a new field is "I added it on purpose" or "the database has a new column."
- **Validation.** Topic 10 will put `@NotBlank` / `@Min` annotations on DTOs. Those validations apply to request shapes; putting them on the domain object also makes them apply on read, which is wrong.

`@JsonIgnore` is a quick fix. DTO splitting is the long-term answer.

---

## When you can skip DTOs

Honest answer: when the wire shape and the domain object happen to be identical, and you are confident they will stay identical, you can return the domain object directly. For small, internal-only APIs with no sensitive fields this is fine.

The trouble starts when the shapes diverge — which they always do, eventually. The cheap day-one decision to skip DTOs turns into the expensive day-thirty refactor to add them across 40 endpoints.

Default to DTOs. Drop them only when you have measured the cost and it isn't justified.

---

## Common pitfalls

- **Returning the entity (or domain object) directly when it has sensitive fields.** Leaks. Always use a response DTO when the domain object contains anything the client should not see.
- **Accepting the entity directly on POST/PUT.** Mass-assignment vulnerability: a client can set `id`, `createdAt`, or any other server-managed field. Always use a request DTO.
- **One mega-DTO for everything.** `UserDto` used for create, update, and response. Either it has fields the wrong direction needs (server-set fields on the input shape) or fields you have to leave null. Three shapes is correct.
- **DTOs that mirror the domain object 1:1.** If `User` and `UserResponse` have the exact same fields, you are paying the DTO cost without buying any safety. That's fine *if you anticipate the divergence*; otherwise just return the domain object and add DTOs when you actually need them.
- **Mapping logic inside the controller.** A controller with twenty lines of field copying is a smell. Push mapping into the DTO (`UserResponse.from(...)`) or a dedicated mapper.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/09-dto-vs-entity/dto-vs-entity-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```
   curl -i -X POST http://localhost:8080/api/users \
        -H "Content-Type: application/json" \
        -d '{"username":"alice","password":"hunter2"}'
   ```
   Read the response. Note that there is no `passwordHash` field, even though the domain object has one. Note that `createdAt` is present in the response (server-set) but you didn't send it in the request.

2. Try sending `passwordHash` or `createdAt` in the POST body:
   ```
   curl -i -X POST http://localhost:8080/api/users \
        -H "Content-Type: application/json" \
        -d '{"username":"bob","password":"x","passwordHash":"evil","createdAt":"1970-01-01T00:00:00Z"}'
   ```
   Notice the server silently ignores those fields — they are not on the `CreateUserRequest` DTO, so Jackson drops them. Mass-assignment foiled.

3. Get a user back: `curl -i http://localhost:8080/api/users/1`. Compare the response shape to the domain object. The hash is invisible.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`User.java`** — the domain object with internal fields.
3. **`CreateUserRequest.java`** — POST body shape.
4. **`UserResponse.java`** — GET response shape, with the `from(User)` factory.
5. **`UserService.java`** — owns the in-memory store and the password hashing.
6. **`UserController.java`** — translates between wire and domain.
7. **`UserControllerTest.java`** — MockMvc proves the password hash never leaves the server.

## Self-check

1. Why is it dangerous to use the same class for both the POST request body and the GET response body?
2. A new requirement: API responses should include `lastLoginAt`. Where do you add it, and what other places in the codebase have to change?
3. A junior engineer adds `@JsonIgnore` to a password field on a shared `User` class and calls it fixed. What problems remain?
