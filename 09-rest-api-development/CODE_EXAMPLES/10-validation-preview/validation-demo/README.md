# 10 — Validation preview

`@Valid` plus jakarta.validation annotations on a DTO. Bad input -> 400.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# valid
curl -i -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"username":"alice","email":"alice@example.com","age":30}'

# blank username -> 400
curl -i -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"username":"","email":"alice@example.com","age":30}'

# bad email -> 400
curl -i -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"username":"alice","email":"oops","age":30}'

# under-age -> 400
curl -i -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"username":"alice","email":"alice@example.com","age":5}'
```

## Run the tests

```bash
mvn -q test
```
