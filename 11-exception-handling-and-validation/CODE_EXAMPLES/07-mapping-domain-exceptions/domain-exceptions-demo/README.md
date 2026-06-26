# 07 — Mapping domain exceptions

The service throws domain exceptions (`BookNotFoundException`, `DuplicateIsbnException`); the advice maps them to 404 / 409. JPA's own `EntityNotFoundException` is mapped too, since you can't annotate a library class. Uses H2, like module 10.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -i http://localhost:8080/api/books/1      # 200, seeded book
curl -i http://localhost:8080/api/books/999    # 404
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
     -d '{"title":"Dup","author":"X","isbn":"9780201616224"}'   # 409, duplicate (translated)
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
     -d '{"title":"Clean Code","author":"Martin","isbn":"9780132350884"}'   # 201
```

## Run the tests

```bash
mvn -q test
```

Try changing `saveAndFlush` to `save` in `BookService` and re-running the duplicate POST — the 409 becomes a 500, because the INSERT fires after the `try` block returns.
