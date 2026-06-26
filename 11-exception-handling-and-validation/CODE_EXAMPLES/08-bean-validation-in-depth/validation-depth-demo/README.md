# 08 — Bean validation in depth

Module 09's preview rejected bad input with a bare 400 — the failed-field details only reached the
log. This demo surfaces them: a `GlobalExceptionHandler` overrides `handleMethodArgumentNotValid` to
add a `fieldErrors` array, and the DTO shows nested (`@Valid Publisher`) and collection
(`List<@NotBlank String>`) validation reporting `publisher.name` and `tags[0]` paths.

## Run it

```bash
mvn -q spring-boot:run
```

```bash
# all valid -> 201, echoes the request back
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" -d '{
  "title": "The Pragmatic Programmer",
  "author": "Hunt & Thomas",
  "isbn": "9780201616224",
  "publisher": { "name": "Addison-Wesley" },
  "tags": ["classic", "engineering"]
}'

# blank title + 3-char isbn -> 400 with fieldErrors listing BOTH fields
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" -d '{
  "title": "", "author": "Martin", "isbn": "123",
  "publisher": { "name": "Addison-Wesley" }, "tags": ["classic"]
}'

# blank nested publisher name -> 400, field path "publisher.name"
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" -d '{
  "title": "Clean Code", "author": "Martin", "isbn": "9780132350884",
  "publisher": { "name": "" }, "tags": ["classic"]
}'

# blank tag element -> 400, field path "tags[1]"
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" -d '{
  "title": "Clean Code", "author": "Martin", "isbn": "9780132350884",
  "publisher": { "name": "Prentice Hall" }, "tags": ["classic", ""]
}'
```

## Run the tests

```bash
mvn -q test
```
