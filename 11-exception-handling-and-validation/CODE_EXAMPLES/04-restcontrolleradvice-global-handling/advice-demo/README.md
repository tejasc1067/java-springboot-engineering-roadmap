# 04 — @RestControllerAdvice global handling

One `GlobalExceptionHandler` handles exceptions from *every* controller. The two controllers from topic 03 now share one 404 mapping.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -i http://localhost:8080/api/books/999     # 404
curl -i http://localhost:8080/api/reports/999   # 404 — same handler, different controller

# duplicate ISBN (9780201616224 is seeded) -> 409
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
     -d '{"title":"Dup","author":"X","isbn":"9780201616224"}'

# new ISBN -> 201
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
     -d '{"title":"Clean Code","author":"Martin","isbn":"9780132350884"}'
```

The `Exception` fallback in `GlobalExceptionHandler` catches anything unmapped, logs the real cause, and returns a generic 500 — the stack trace goes to the log, never the response.

## Run the tests

```bash
mvn -q test
```
