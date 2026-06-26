# 03 — @ExceptionHandler in a controller

A controller-local `@ExceptionHandler` maps an exception to a status AND a body — but only for that controller.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# handled in BookController: 404 with a ProblemDetail body
curl -i http://localhost:8080/api/books/999

# same exception in ReportController, no handler there: 500
curl -i http://localhost:8080/api/reports/999
```

The two endpoints throw the *same* `BookNotFoundException` and return different statuses. That scope limit is why topic 04 lifts the handler into a `@RestControllerAdvice`.

## Run the tests

```bash
mvn -q test
```
