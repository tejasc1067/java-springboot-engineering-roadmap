# 03 — HTTP verbs in Spring

Full CRUD on a `Book` resource using all five Spring verb annotations: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`.

## Run it

```bash
mvn -q spring-boot:run
```

Walk a full lifecycle:

```bash
curl -i -X POST http://localhost:8080/api/books \
     -H "Content-Type: application/json" \
     -d '{"title":"Effective Java","author":"Bloch"}'

curl -i http://localhost:8080/api/books
curl -i http://localhost:8080/api/books/1

# PUT: full replace (omit author and watch it disappear)
curl -i -X PUT http://localhost:8080/api/books/1 \
     -H "Content-Type: application/json" \
     -d '{"title":"Effective Java 3rd","author":"Bloch"}'

# PATCH: partial update (only title changes)
curl -i -X PATCH http://localhost:8080/api/books/1 \
     -H "Content-Type: application/json" \
     -d '{"title":"EJ3"}'

curl -i -X DELETE http://localhost:8080/api/books/1
```

## Run the tests

```bash
mvn -q test
```

`BookControllerTest` walks the same lifecycle with MockMvc and asserts behaviour at each step.
