# 13 — Common pitfalls (runnable before/after)

Three of the catalog's pitfalls made executable: forgetting `@Valid` (1), swallowing an exception in the handler (3), and the body-vs-param validation split that a too-narrow advice would mishandle (5/6). Each is a before/after pair on one `GlobalExceptionHandler`.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# PITFALL 1 — no @Valid: blank input is ACCEPTED (201)
curl -i -X POST http://localhost:8080/api/users/without-valid \
     -H "Content-Type: application/json" -d '{"username":"","email":""}'

# FIX — @Valid: same input rejected (400) with a fieldErrors array
curl -i -X POST http://localhost:8080/api/users/with-valid \
     -H "Content-Type: application/json" -d '{"username":"","email":""}'

# PITFALL 6 — param validation: DIFFERENT exception, SAME envelope (400 + fieldErrors)
curl -i http://localhost:8080/api/users/0

# PITFALL 3 — swallowed: the charge threw, but the client is told "ok" (200), nothing logged
curl -i http://localhost:8080/api/payments/swallow

# FIX — propagate: real 500, generic body, cause logged (never leaked)
curl -i http://localhost:8080/api/payments/propagate
```

## Run the tests

```bash
mvn -q test
```

`missingValid_acceptsBadInput_returns201` is the pitfall written as a passing test — a 201 for garbage input. `withValid_rejectsBadInput_returns400WithFieldErrors` is the fix. `paramValidation_returnsSameShapeAsBody` proves the advice unifies both validation exceptions. `swallowingHandler_hidesFailure_returns200Ok` vs `propagatingHandler_surfacesFailure_returns500Generic` is the swallow before/after.
