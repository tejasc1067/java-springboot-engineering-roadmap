# custom-constraints-demo

Writing your own Bean Validation constraints when the built-in ones run out: a custom **field**
constraint (`@ValidIsbn`) and a class-level **cross-field** constraint (`@ValidDateRange`).
`POST /api/books` takes a `CreateBookRequest(title, isbn, loanPeriod)`; both custom constraints
report through the same `fieldErrors` array the rest of module 11 uses.

## Run it

```bash
mvn -q test                 # the three assertions below, end to end
mvn -q spring-boot:run      # boots on :8080
```

## What each request does

```bash
# valid ISBN-13 + valid date range -> 201, echoes the request
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
     -d '{"title":"Effective Java","isbn":"9780132350884","loanPeriod":{"startDate":"2026-01-01","endDate":"2026-01-15"}}'

# bad ISBN checksum (last digit 5, not 4) -> 400, fieldErrors has field "isbn"
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
     -d '{"title":"Effective Java","isbn":"9780132350885","loanPeriod":{"startDate":"2026-01-01","endDate":"2026-01-15"}}'

# endDate before startDate -> 400, fieldErrors has field "loanPeriod.endDate"
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
     -d '{"title":"Effective Java","isbn":"9780132350884","loanPeriod":{"startDate":"2026-01-15","endDate":"2026-01-01"}}'
```

`9780132350884` is a real ISBN-13: its weighted digit sum (weights 1,3,1,3,…) is 100, a multiple of
10. Changing the check digit to 5 breaks that, so `IsbnValidator` rejects it.

## Files (reading order)

1. `App.java` — bootstrap.
2. `ValidIsbn.java` — the custom field constraint annotation.
3. `IsbnValidator.java` — `ConstraintValidator` implementing the ISBN-13 checksum; null treated as valid.
4. `ValidDateRange.java` — the class-level (cross-field) constraint annotation.
5. `LoanPeriod.java` — the record `@ValidDateRange` is attached to.
6. `DateRangeValidator.java` — compares the two fields; re-attaches the violation to `endDate`.
7. `CreateBookRequest.java` — combines `@NotBlank`, `@ValidIsbn`, and a `@Valid` nested `LoanPeriod`.
8. `BookController.java` — `POST /api/books`, `@Valid @RequestBody`.
9. `GlobalExceptionHandler.java` — the module's `fieldErrors` override for body validation.
10. `CustomConstraintsTest.java` — asserts 201 / 400-isbn / 400-endDate.
