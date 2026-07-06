# 12 — The 401/403 error contract (security failures as ProblemDetail)

Security `401`/`403` are raised in the **filter chain**, before the `DispatcherServlet`, so a `@RestControllerAdvice` never sees them. This demo wires a custom `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) so those come back in the **same RFC 7807 `ProblemDetail` shape** as a `404` from the advice.

Users: `alice`/`password` (USER), `admin`/`password` (ADMIN). HTTP Basic.

## Run the tests

```bash
mvn -q test
```

`ErrorContractTest` (4 cases): a `401` (from the entry point), a `403` (from the access-denied handler), and a `404` (from the advice) each asserted to be a `ProblemDetail` with `title`/`status`/`instance`/`timestamp` and `application/problem+json`; plus authorized requests succeed.

## See all three errors share one shape

```bash
mvn -q spring-boot:run
```

```bash
# 401 — unauthenticated (from AuthenticationEntryPoint)
curl -s http://localhost:8080/api/books | jq
# { "type":"about:blank", "title":"Unauthorized", "status":401,
#   "detail":"Authentication is required to access this resource.",
#   "instance":"/api/books", "timestamp":"..." }

# 403 — authenticated as alice (USER) hitting an admin route (from AccessDeniedHandler)
curl -s -u alice:password http://localhost:8080/api/admin/stats | jq
# { ..., "title":"Forbidden", "status":403, "instance":"/api/admin/stats", ... }

# 404 — missing book (from @RestControllerAdvice)
curl -s -u alice:password http://localhost:8080/api/books/999 | jq
# { ..., "title":"Book not found", "status":404, "instance":"/api/books/999", ... }

# check the media type is the same on all three
curl -s -i http://localhost:8080/api/books | grep -i content-type   # application/problem+json;charset=UTF-8

# sanity: authorized calls work
curl -s -u alice:password http://localhost:8080/api/books | jq
curl -s -u admin:password http://localhost:8080/api/admin/stats | jq
```

Same media type, same fields — the `401`/`403` from the filter chain are indistinguishable in shape from the `404` from the advice.

## Prove the gap this closes

Comment out the `.exceptionHandling(...)` block in `SecurityConfig` and re-run: the `401`/`403` revert to the framework default (bare/empty body) while the `404` keeps its `ProblemDetail`. That divergence — a client getting two different error formats — is exactly what the two custom handlers fix. Restore the block.

## Notes

- `ProblemDetailWriter` uses the **injected** Spring `ObjectMapper` (which has Boot's `ProblemDetail` Jackson mixin + JavaTime module) and forces UTF-8. `new ObjectMapper()` would serialize the wrong shape.
- The `detail` messages are intentionally generic — a `401`/`403` should not reveal whether an account exists or which role is required.
- Compare to topic 08, whose handlers returned bare status codes and noted "topic 12 replaces these with a ProblemDetail body." This is that replacement.
