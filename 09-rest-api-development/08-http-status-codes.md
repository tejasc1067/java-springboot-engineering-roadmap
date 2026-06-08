# HTTP status codes — choosing the right one

The status code is part of the API contract. Pick the wrong one and consumers will write code to compensate; pick the right one and they barely notice. This topic is a decision tree for the eleven codes a Java backend actually returns, plus the three that confuse people the most.

---

## The problem this solves

Most newcomers reach for two codes: 200 for "it worked" and 500 for "it didn't." Real APIs use about a dozen. Knowing which one applies in which situation is what separates "I learned REST yesterday" from "I have shipped this."

The codes fall into three groups:

- **2xx** — the server understood the request and acted on it.
- **4xx** — the request itself is the problem (client's fault).
- **5xx** — the server failed while trying to act (server's fault).

3xx (redirects) and 1xx (informational) almost never appear in a JSON REST API and are not covered here.

---

## The 2xx codes

| Code | Name | Use when |
|------|------|----------|
| 200 | OK | A read succeeded; an update succeeded and you are returning the updated resource. |
| 201 | Created | A POST created a new resource. Always include a `Location` header pointing at it. |
| 202 | Accepted | The request was received but is being processed asynchronously (background job, queue). Body usually includes a job id or a status URL. |
| 204 | No Content | The action succeeded and there is nothing to return. Most commonly after DELETE; also acceptable for PUT/PATCH when you choose not to return the updated body. |

The choice between **200 and 204** on a successful update or delete is taste. Returning the resource (200 + body) saves the client one round-trip if they need it next. Returning 204 keeps the response slim. Pick one and apply it consistently across your API.

The choice between **200 and 201** is not taste. Use 201 only when a new resource came into existence. A PUT that updates an existing resource is 200; a PUT that creates a previously-missing resource (idempotent upsert) is 201.

---

## The 4xx codes — client errors

| Code | Name | Use when |
|------|------|----------|
| 400 | Bad Request | The request itself is malformed — bad JSON, missing required field, wrong type. |
| 401 | Unauthorized | No credentials were provided, or they are invalid. (Despite the name, it's about authentication, not authorization.) |
| 403 | Forbidden | Credentials are valid, but the user is not allowed to do this. |
| 404 | Not Found | The resource does not exist. |
| 405 | Method Not Allowed | The URL exists but does not accept this verb (e.g. PUT on a GET-only endpoint). Spring returns this automatically. |
| 409 | Conflict | The action conflicts with current server state — duplicate-key, optimistic-lock failure, "you can't delete a non-empty folder." |
| 422 | Unprocessable Entity | The JSON parsed and the field types are right, but the values don't satisfy business rules. |

### 400 vs 422 — the most-debated pair

This is the one that splits API design opinion in half.

**Both are valid.** 400 means "the request is malformed in a way I can't process." 422 means "I parsed your input, but the business rules say no."

- `POST /api/books` with `{"title": 42}` and the DTO expects a `String` -> **400**. Jackson cannot bind.
- `POST /api/books` with `{"title": ""}` and `@NotBlank` rejects the empty string -> **422** if you want to distinguish "your input is well-formed but semantically wrong" from "your input is broken." Or **400** if you treat validation failures as malformed input.

The Spring Boot default is **400** for `@Valid` failures. Many teams override to **422**. Pick one — the only wrong answer is "both, randomly."

### 401 vs 403 — the badly named pair

- **401 Unauthorized** = "Who are you? Tell me first." (Missing/invalid token.)
- **403 Forbidden** = "I know who you are. You are not allowed."

The names are historically swapped — 401 should have been "Unauthenticated." Live with it; everyone else does.

### 404 — and a small subtlety

A 404 is the right answer for a missing resource. It is also acceptable for "the resource exists but you don't have permission to know" — some APIs return 404 instead of 403 to avoid leaking existence (security through obscurity, mostly for endpoints that list things a user shouldn't see).

The wrong use of 404: returning it from a controller when the *URL doesn't have a handler*. Spring handles that automatically. Use 404 from inside your method only when "the user asked for a resource by id and that id is not in the store."

---

## The 5xx codes — server errors

| Code | Name | Use when |
|------|------|----------|
| 500 | Internal Server Error | An unexpected exception was thrown. Don't return this on purpose — it's the default for anything that escapes your try/catch. |
| 502 | Bad Gateway | An upstream service you depend on returned an unexpected response. |
| 503 | Service Unavailable | You are intentionally rejecting requests (overloaded, maintenance mode, circuit breaker open). |
| 504 | Gateway Timeout | An upstream service didn't respond in time. |

Most code in your service should never return a 5xx on purpose. They are signals to operators (and clients) that something is wrong on your side. The exception is **503** during planned downtime or rate-limiting — that one is sometimes deliberate.

---

## The decision tree

```
Did the client send valid input?
  no  -> is the structure broken (bad JSON, missing field, wrong type)?  -> 400
       -> is the structure fine but values fail business rules?            -> 400 or 422 (pick one)
       -> are they missing credentials?                                   -> 401
       -> are they authenticated but not allowed?                          -> 403
  yes -> does the resource exist?
       -> no, asking for it by id                                          -> 404
       -> yes, was the action a read?                                      -> 200
       -> yes, was it a create?
            -> new resource came into existence                           -> 201 + Location
            -> resource already existed (upsert)                          -> 200 (or 204)
       -> yes, was it an update?                                          -> 200 (if returning body) or 204
       -> yes, was it a delete?                                            -> 204 (or 404 if missing)
       -> yes, but it conflicts with current state?                       -> 409
       -> yes, it's being processed asynchronously?                       -> 202
  internal failure thrown                                                 -> 500
  upstream failure                                                        -> 502 / 504
  intentionally refusing (maintenance, rate limit)                       -> 503
```

---

## Setting the status in Spring

Three ways:

```java
// 1. Default. Plain return is 200. void/null is 200 with no body.
@GetMapping("/{id}")
public Book one(@PathVariable Long id) { return store.get(id); }

// 2. @ResponseStatus on a method or on an exception type.
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Book create(@RequestBody Book b) { ... }

// 3. ResponseEntity for full control (status, headers, body).
@PostMapping
public ResponseEntity<Book> create(@RequestBody Book b) {
    return ResponseEntity.status(HttpStatus.CREATED).location(uri).body(b);
}
```

Pick the simplest one that does the job. The default is fine for read endpoints; `ResponseEntity` is for create/update/delete that needs headers; `@ResponseStatus` is for the rare case where you always want one specific status and no headers.

For exceptions, the cleanest pattern is to annotate the exception type:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BookNotFoundException extends RuntimeException {
    public BookNotFoundException(Long id) { super("book " + id + " not found"); }
}
```

Throwing this from your service produces a 404 automatically. Module 11 covers the full pattern with `@ControllerAdvice`.

---

## Common pitfalls

- **`200 OK` with `{"success": false, "error": "..."}`.** The protocol has status codes for a reason. Use them.
- **`500` returned because of bad input.** A `NullPointerException` from a missing query parameter is a 400, not a 500 — but Spring will report 500 if it escapes uncaught. Validate at the boundary (topic 10, module 11).
- **`404` when you mean `403`.** Hides the existence of a forbidden resource, sometimes intentional, often accidental. Decide which one you mean and stick to it.
- **`201` without a `Location` header.** Technically valid, conventionally broken. Always include `Location` on 201.
- **`401` for a missing record.** That is 404. 401 is for missing/invalid auth credentials.
- **Mixing 400 and 422 for validation.** Pick one. The default in Spring Boot is 400 from `@Valid` failures; many teams overide to 422. Document your choice.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/08-http-status-codes/status-codes-demo`. It deliberately produces each code from a different endpoint so you can see them in `curl`.

1. Run `mvn -q spring-boot:run`, then walk the catalog:
   ```
   curl -i http://localhost:8080/api/status/ok
   curl -i http://localhost:8080/api/status/created
   curl -i http://localhost:8080/api/status/accepted
   curl -i http://localhost:8080/api/status/no-content
   curl -i http://localhost:8080/api/status/bad-request
   curl -i http://localhost:8080/api/status/unauthorized
   curl -i http://localhost:8080/api/status/forbidden
   curl -i http://localhost:8080/api/status/not-found
   curl -i http://localhost:8080/api/status/conflict
   curl -i http://localhost:8080/api/status/unprocessable
   curl -i http://localhost:8080/api/status/server-error
   curl -i http://localhost:8080/api/status/unavailable
   curl -i -X PUT http://localhost:8080/api/status/ok           # 405 - method not allowed
   ```

2. Read the status line on each. Notice how 204 has *no body at all*, while 201, 409, and 422 carry JSON.

3. Take one endpoint that returns 500 and trace why — `RuntimeException` is the lazy default for unmapped exceptions; module 11 will replace it.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`StatusCodesController.java`** — one endpoint per status code. Each shows the simplest correct way to produce that code in Spring.
3. **`StatusCodesControllerTest.java`** — MockMvc asserts each status code.

## Self-check

1. A client POSTs valid JSON to create a book. The title field is empty (`""`). Your validation rule says title must be non-empty. Which status code? Justify between 400 and 422.
2. The endpoint `GET /api/users/42` exists. The user 42 also exists. The caller is authenticated but not allowed to see that user. Which status?
3. After a successful DELETE of book 42, you return 204. The same client calls DELETE again on /api/books/42. Two reasonable answers — give both and pick a side.
