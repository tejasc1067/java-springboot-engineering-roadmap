# 06 — Designing an error contract

Every error carries the same envelope (type/title/status/detail/instance/timestamp). The fallback logs the real cause but never leaks it to the client.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# full contract
curl -i http://localhost:8080/api/books/999

# generic 500 — the connection string / password is NOT in the body (check the server log: it IS there)
curl -i http://localhost:8080/api/books/leak
```

## Run the tests

```bash
mvn -q test
```

`internalErrorDoesNotLeakTheSecret` asserts the secret is absent from the response body — the security boundary written as a test.
