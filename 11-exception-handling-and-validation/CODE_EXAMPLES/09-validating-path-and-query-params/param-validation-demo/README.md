# param-validation-demo

Validates `@PathVariable` and `@RequestParam` with class-level `@Validated`, and unifies the
resulting `ConstraintViolationException` with the request-body `MethodArgumentNotValidException`
into one `fieldErrors` error contract.

`BookController` is annotated `@Validated` and exposes:

- `GET /api/books/{id}` — `@PathVariable @Min(1) Long id`
- `GET /api/books?size=` — `@RequestParam @Min(1) @Max(100) int size`
- `POST /api/books` — `@Valid @RequestBody CreateBookRequest` (`@NotBlank title`)

A bad param throws `ConstraintViolationException`; a bad body throws `MethodArgumentNotValidException`.
`GlobalExceptionHandler` handles both and emits the same shape.

## Run it

```bash
mvn -q test                 # asserts all three failures return the same fieldErrors shape
mvn -q spring-boot:run      # boots on :8080
```

```bash
# valid
curl -i http://localhost:8080/api/books/1                 # 200
curl -i "http://localhost:8080/api/books?size=20"         # 200
curl -i -X POST http://localhost:8080/api/books \
     -H "Content-Type: application/json" -d '{"title":"Clean Code"}'   # 201

# invalid — all three return 400 with a fieldErrors array
curl -i http://localhost:8080/api/books/0                 # 400, ConstraintViolationException (path)
curl -i "http://localhost:8080/api/books?size=999"        # 400, ConstraintViolationException (param)
curl -i -X POST http://localhost:8080/api/books \
     -H "Content-Type: application/json" -d '{"title":""}' # 400, MethodArgumentNotValidException (body)
```

All three invalid responses share:

```json
{
  "title": "Validation failed",
  "status": 400,
  "fieldErrors": [ { "field": "...", "message": "..." } ]
}
```
