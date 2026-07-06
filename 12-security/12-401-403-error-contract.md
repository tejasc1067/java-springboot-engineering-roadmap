# The 401/403 error contract — security failures as ProblemDetail

Module 11 gave every error one predictable shape: an RFC 7807 `ProblemDetail` with `title`, `status`, `detail`, `instance`, and a `timestamp`, produced by a `@RestControllerAdvice`. But if you `curl` an unauthenticated request at the apps from topics 08–11, the `401` and `403` come back as *something else* — a bare status, an empty body, or Spring's whitelabel error page. They never match your contract. This topic explains why (security failures happen before your advice can see them) and fixes it: a custom `AuthenticationEntryPoint` for `401` and `AccessDeniedHandler` for `403` that emit the same `ProblemDetail` shape.

---

## The problem this solves

A client that talks to your API wants *one* error format. It parses `problem.title` and `problem.status` for a validation `400`, a not-found `404`, a conflict `409`. Then it hits a `401` or `403` and gets a completely different body — or no body — and its error handling breaks. Worse, the default may be an HTML error page, so a JSON client chokes on `<html>`. A consistent API means security errors look exactly like every other error.

The reason they don't, by default, is *where* they happen.

---

## How it works

### Why `@RestControllerAdvice` never sees a security failure

Your `@ExceptionHandler` methods run inside the `DispatcherServlet`, which sits at the *end* of the request pipeline. Spring Security is a chain of **servlet filters** that runs *before* the `DispatcherServlet` is ever invoked:

```
request ─▶ [ Security filter chain ] ─▶ DispatcherServlet ─▶ your controller
                     │                          │
      ExceptionTranslationFilter        @RestControllerAdvice
      catches AuthenticationException   catches exceptions thrown
      & AccessDeniedException HERE       by controllers, HERE
```

When an unauthenticated caller hits a protected route, or an authenticated caller lacks the role, the exception is raised **in the filter chain** and caught by `ExceptionTranslationFilter` — which never lets it reach the servlet, so your advice can't handle it. `ExceptionTranslationFilter` instead delegates to two collaborators:

- **`AuthenticationEntryPoint`** — for an `AuthenticationException` (not authenticated → **401**). "Commence authentication."
- **`AccessDeniedHandler`** — for an `AccessDeniedException` (authenticated, insufficient authority → **403**).

Whatever those two write *is* the response. So to put `401`/`403` into your contract, you replace those two — not touch the advice.

### The entry point and the handler, writing ProblemDetail

Because these run at the servlet-filter level, there's no message converter turning a return value into JSON — you write the response yourself. Both delegate to one small writer that stamps the exact envelope module 11 used:

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
problem.setTitle(title);
problem.setInstance(URI.create(request.getRequestURI()));
problem.setProperty("timestamp", Instant.now());

response.setStatus(status.value());
response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
response.setCharacterEncoding(StandardCharsets.UTF_8.name());
objectMapper.writeValue(response.getWriter(), problem);   // objectMapper is the Spring-configured bean
```

Two details make or break this:

- **Use the injected Spring `ObjectMapper`, not `new ObjectMapper()`.** Boot configures its mapper with a `ProblemDetail` Jackson mixin (so `type`/`title`/`status`/`detail`/`instance` and custom properties serialize *flat*) and the JavaTime module (so the `Instant` becomes an ISO string). A hand-built mapper produces the wrong JSON and a raw `Instant` array.
- **Set UTF-8.** `response.getWriter()` defaults to ISO-8859-1; the advice's output is UTF-8. Force UTF-8 so the two truly match.

Wire them in one place:

```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint(problemDetailAuthenticationEntryPoint)   // 401
    .accessDeniedHandler(problemDetailAccessDeniedHandler));           // 403
```

### The payoff: three errors, one shape

All three of these now come back identically shaped — the `401`/`403` from the filter chain, the `404` from the advice:

```json
// 401  GET /api/books        (no credentials)           — from AuthenticationEntryPoint
{ "type":"about:blank", "title":"Unauthorized", "status":401,
  "detail":"Authentication is required to access this resource.",
  "instance":"/api/books", "timestamp":"2026-07-06T15:05:57.812Z" }

// 403  GET /api/admin/stats  (authenticated as alice/USER) — from AccessDeniedHandler
{ "type":"about:blank", "title":"Forbidden", "status":403,
  "detail":"You do not have permission to access this resource.",
  "instance":"/api/admin/stats", "timestamp":"..." }

// 404  GET /api/books/999    (missing book)             — from @RestControllerAdvice
{ "type":"about:blank", "title":"Book not found", "status":404,
  "detail":"No book with id 999", "instance":"/api/books/999", "timestamp":"..." }
```

Same media type (`application/problem+json`), same fields, three different origins. A client writes one error parser.

---

## When to use it / when not

- **Do this for any JSON API** that already has a `ProblemDetail` contract. Inconsistent security errors are a real integration tax on every client.
- **The `detail` for `401`/`403` should stay generic.** "Authentication is required," not "user alice not found" or "missing ROLE_ADMIN." Verbose security errors leak whether an account exists and what roles gate a resource. Keep the envelope; keep the message vague.
- **You don't always need both custom handlers.** A pure bearer-token API might accept the framework's spec-compliant `401` with a `WWW-Authenticate` header (topic 09) and only customize the body if a client demands it. Customize when consistency matters more than the default's protocol niceties.
- **This is orthogonal to the auth mechanism.** Basic, form login, JWT, OAuth2 — all route unauthenticated/forbidden through these same two hooks. The demo uses HTTP Basic only because it's the least code to produce a `401` and a `403`.

---

## Common pitfalls

- **Expecting `@ExceptionHandler` to catch `AccessDeniedException`.** It won't for filter-chain denials — they're gone before the servlet. (A method-security `@PreAuthorize` denial *inside* a controller is also ultimately routed to the `AccessDeniedHandler` by `ExceptionTranslationFilter`, not your advice.) Fix it on the security chain, not in the advice.
- **`new ObjectMapper()` in the handler.** Misses the `ProblemDetail` mixin and JavaTime module → nested `properties`, a raw `Instant`, wrong shape. Inject the Boot-configured `ObjectMapper`.
- **Leaving the charset at the servlet default.** `getWriter()` is ISO-8859-1; your advice is UTF-8. Non-ASCII `detail` text (or just header-sniffing clients) will disagree. Set UTF-8 explicitly.
- **Using `response.sendError(...)` instead of `setStatus` + write.** `sendError` triggers the container's `/error` re-dispatch, which (as topic 08 showed empirically) can re-enter the chain as anonymous and turn a `403` into a `401`. Write the body directly.
- **Confusing the two codes in the handlers.** The entry point is `401` (who are you?), the handler is `403` (you may not). Swapping them tells a forbidden user to re-authenticate — which never helps.
- **Forgetting `title`/`instance` drift.** If the advice sets `timestamp` but your writer doesn't (or names it differently), the "same shape" promise quietly breaks. Share one helper, as the demo does.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/12-401-403-error-contract/error-contract-demo`. Users: `alice`/`password` (USER), `admin`/`password` (ADMIN).

1. **Run the tests** (`mvn test`) — a `401`, a `403`, and a `404` are each asserted to be a `ProblemDetail` with `title`/`status`/`instance`/`timestamp` and the `application/problem+json` media type.
2. **See all three on the wire.** Boot and `curl` `/api/books` (no creds → 401), `/api/admin/stats` as `alice` (→ 403), and `/api/books/999` as `alice` (→ 404). Diff the JSON — same shape.
3. **Watch the gap the topic fixes.** Comment out the `.exceptionHandling(...)` block and re-run: the `401`/`403` revert to the framework default (bare/empty), while the `404` from the advice keeps its `ProblemDetail`. That divergence is the problem this topic exists to close. Put it back.
4. **Break the mapper.** Change `ProblemDetailWriter` to `new ObjectMapper()` and hit a `401`: the JSON shape breaks (timestamp/properties render wrong). Restore the injected bean.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`ProblemDetailWriter.java`** — the shared envelope: builds the `ProblemDetail` and writes it with the Spring `ObjectMapper` in UTF-8. The heart of the topic.
3. **`ProblemDetailAuthenticationEntryPoint.java`** — the `401` hook.
4. **`ProblemDetailAccessDeniedHandler.java`** — the `403` hook.
5. **`SecurityConfig.java`** — `.exceptionHandling(...)` wiring both hooks; Basic auth and the roled users.
6. **`GlobalExceptionHandler.java`** — the module-11 advice, unchanged, producing the `404` whose shape the security errors now match.
7. **`BookController.java`** — public/authenticated/admin routes plus the `404`-throwing lookup.
8. **`ErrorContractTest.java`** — asserts `401`, `403`, and `404` share the shape and media type.

## Self-check

1. Why can't a `@RestControllerAdvice` `@ExceptionHandler` handle a `401` from an unauthenticated request? Where in the pipeline is that error raised and caught?
2. Which component turns an *unauthenticated* request into a response, and which turns a *forbidden* one? Which HTTP status does each own?
3. You copy `new ObjectMapper()` into your entry point and the `ProblemDetail` JSON comes out malformed. Why — and what should you use instead?
4. Why should the `detail` message on a `401`/`403` be generic rather than specific?
5. Your custom `AccessDeniedHandler` calls `response.sendError(403)` and the client sometimes sees `401`. What's happening, and what should it call instead?
