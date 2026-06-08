# 06 — Request body and Jackson

`@RequestBody` plus Jackson: JSON in, Java records out.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -i -X POST http://localhost:8080/api/books \
     -H "Content-Type: application/json" \
     -d '{"title":"Effective Java","author":"Bloch","published_year":2017}'

# Missing fields -> null / 0 (no validation yet; topic 10 adds @Valid)
curl -i -X POST http://localhost:8080/api/books \
     -H "Content-Type: application/json" \
     -d '{"title":"Clean Code"}'

# Unknown field silently ignored by default
curl -i -X POST http://localhost:8080/api/books \
     -H "Content-Type: application/json" \
     -d '{"title":"X","secret":"evil"}'

# Wrong content-type -> 415
curl -i -X POST http://localhost:8080/api/books \
     -H "Content-Type: text/plain" \
     -d 'not json'
```

## Run the tests

```bash
mvn -q test
```
