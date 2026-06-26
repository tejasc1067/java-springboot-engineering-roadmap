# 05 — ProblemDetail and RFC 7807

`ProblemDetail` is Spring's RFC 7807 error body. Extending `ResponseEntityExceptionHandler` makes Spring's own exceptions use the same shape.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# domain error -> problem+json (type, title, status, detail, timestamp)
curl -i http://localhost:8080/api/books/999

# malformed JSON -> Spring's built-in exception, ALSO problem+json
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" -d '{ broken'
```

Both responses carry `Content-Type: application/problem+json`. Comment out `extends ResponseEntityExceptionHandler` and the malformed-JSON body changes shape — that's the point.

## Run the tests

```bash
mvn -q test
```
