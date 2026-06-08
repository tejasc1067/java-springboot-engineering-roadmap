# 04 — Path variables

`@PathVariable` covers every shape you will hit: single, multiple, type-converted, regex-constrained, date-bound.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -i http://localhost:8080/api/books/1
curl -i http://localhost:8080/api/books/abc           # 400 - Long conversion fails
curl -i http://localhost:8080/api/books/1/reviews/7
curl -i http://localhost:8080/api/users/alice
curl -i http://localhost:8080/api/users/Alice         # 404 - fails the regex
curl -i http://localhost:8080/api/events/by-date/2025-12-31
curl -i http://localhost:8080/api/events/by-date/oops # 400
```

## Run the tests

```bash
mvn -q test
```
