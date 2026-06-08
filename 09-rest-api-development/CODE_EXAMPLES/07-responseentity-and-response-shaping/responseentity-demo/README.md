# 07 — `ResponseEntity` and response shaping

POST returns 201 + Location. GET returns 200 or 404. DELETE returns 204 or 404. List stays plain.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
curl -i -X POST http://localhost:8080/api/books \
     -H "Content-Type: application/json" \
     -d '{"title":"EJ","author":"Bloch"}'                  # 201 + Location

curl -i http://localhost:8080/api/books                     # 200, list
curl -i http://localhost:8080/api/books/1                   # 200, one
curl -i http://localhost:8080/api/books/9999                # 404, no body
curl -i -X DELETE http://localhost:8080/api/books/1         # 204, no body
curl -i -X DELETE http://localhost:8080/api/books/9999      # 404, no body
```

## Run the tests

```bash
mvn -q test
```
