# 02 — @ResponseStatus on exceptions

One annotation on the exception class maps it to a status. `BookNotFoundException` -> 404. An unmapped exception still -> 500.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -i http://localhost:8080/api/books/1       # 200, the book
curl -i http://localhost:8080/api/books/999     # 404, BookNotFoundException (@ResponseStatus)
curl -i http://localhost:8080/api/books/explode  # 500, unmapped IllegalStateException
```

Note the 404 body is the bare default ProblemDetail — `@ResponseStatus` controls the status line only, not the body. Adding a useful body is topic 03 onward.

## Run the tests

```bash
mvn -q test
```
