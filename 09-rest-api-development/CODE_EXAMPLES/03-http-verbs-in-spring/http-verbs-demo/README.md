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

# Verbs you never mapped
curl -i -X OPTIONS http://localhost:8080/api/books/1   # 200, Allow: GET,HEAD,PUT,PATCH,DELETE,OPTIONS
curl -I http://localhost:8080/api/books                 # HEAD: GET's headers, no body
curl -i -X TRACE http://localhost:8080/api/books        # 405, blocked by Tomcat
```

## Run the tests

```bash
mvn -q test
```

`BookControllerTest` walks the same lifecycle with MockMvc and asserts behaviour at each step.

`OptionsHeadTraceTest` starts a real embedded Tomcat (MockMvc would skip Tomcat's TRACE block) and asserts the OPTIONS `Allow` header, HEAD with no body, `405` on an unmapped verb, and `405` on TRACE.
