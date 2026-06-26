# method-validation-demo

Method-level validation on a `@Validated` service bean, and the line between *input
validation* (bean validation) and *business-rule enforcement* (a thrown domain exception).

`LibraryService.borrow(@Min(1) Long bookId, @Positive int copies)` carries two kinds of check:
the annotation guards (shape — a defensive layer behind the controller) and an explicit stock
check that throws `InsufficientCopiesException` when the domain can't satisfy a well-formed request.

## Run it

```bash
cd 11-exception-handling-and-validation/CODE_EXAMPLES/12-method-level-validation-in-services/method-validation-demo
mvn -q test                 # MockMvc + a direct service-call assertion
mvn -q spring-boot:run      # boots on :8080
```

Seeded stock: book `1` has 3 copies, book `2` has 0.

## curl

```bash
# valid -> 200, remaining count returned
curl -i -X POST http://localhost:8080/api/library/borrow \
     -H "Content-Type: application/json" -d '{"bookId":1,"copies":2}'

# business rule: book 2 has 0 copies -> 409 Conflict (InsufficientCopiesException)
curl -i -X POST http://localhost:8080/api/library/borrow \
     -H "Content-Type: application/json" -d '{"bookId":2,"copies":1}'

# bad user input: copies must be positive -> 400 with fieldErrors (controller's @Valid)
curl -i -X POST http://localhost:8080/api/library/borrow \
     -H "Content-Type: application/json" -d '{"bookId":1,"copies":0}'
```

The fourth case — the service's own `@Positive` guard firing — can't be reached through the
controller (its `@Valid` already rejects `copies:0` as a 400). It's reached only by a *direct*
call, `service.borrow(1L, 0)`, which is what `MethodValidationTest` does. That throws
`ConstraintViolationException`, and the advice maps a service-origin violation to **500**, not
400, because reaching it means a caller passed bad args — a programming error, not user error.
