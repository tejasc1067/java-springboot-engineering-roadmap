# Method-level validation in services — `@Validated` beans, and input vs business rules

Topics 08–11 validated *user input* at the controller boundary: `@Valid` on a body, `@Validated` on path/query params. This topic does two things. First, it pushes bean validation one layer deeper — onto a `@Validated` *service* bean, so a method's parameters are checked on every call, not just the ones that came over HTTP. Second, and more important, it draws the line bean validation can't cross: the difference between *input validation* (shape and range, expressed as annotations) and *business-rule enforcement* (domain logic that needs live data, expressed as a thrown domain exception).

---

## The problem this solves

Bean validation answers "is this argument the right *shape*?" — non-null, positive, in range. It cannot answer "is this request *possible right now*?" Consider borrowing library copies:

```java
public int borrow(Long bookId, int copies) {
    // bean validation can check copies > 0...
    // ...but "are there `copies` available?" depends on current stock, which no annotation can see.
}
```

`@Positive int copies` rejects `copies = 0`. It can never reject `copies = 5` when only `3` are in stock — the validator has no access to the stock map. That check is a *business rule*, and it belongs in a thrown domain exception, not an annotation. The demo's `LibraryService.borrow` shows both living side by side.

There's also a defensive angle. A controller validates user input, but a service can be called from other places — a scheduled job, a message consumer, another service, a test. Putting `@Min`/`@Positive` on the service method itself guarantees the contract holds *no matter who calls it*.

---

## How it works

Annotate the service class with Spring's `@Validated` (the `org.springframework.validation.annotation` one — **not** `jakarta.validation.Valid`), and put constraints directly on method parameters:

```java
@Service
@Validated
public class LibraryService {

    private final Map<Long, Integer> stock = new ConcurrentHashMap<>(Map.of(1L, 3, 2L, 0));

    public int borrow(@Min(1) Long bookId, @Positive int copies) {
        int available = stock.getOrDefault(bookId, 0);
        if (copies > available) {                                   // BUSINESS RULE
            throw new InsufficientCopiesException(bookId, copies, available);
        }
        stock.put(bookId, available - copies);
        return available - copies;
    }
}
```

`@Validated` on the class is the trigger. Spring Boot autoconfigures a `MethodValidationPostProcessor`, which wraps every `@Validated` bean in a proxy. On each public-method call the proxy validates the arguments against their annotations *before* the method body runs. A violation throws `jakarta.validation.ConstraintViolationException` — the same type topic 09 saw from controller params, but originating here at the service boundary.

Two distinct failures can now come out of one method:

- `service.borrow(1L, 0)` → `@Positive` rejects `0` → `ConstraintViolationException` (input/shape).
- `service.borrow(2L, 1)` when book 2 has 0 copies → the `if` throws `InsufficientCopiesException` (business rule).

`MethodValidationTest` exercises both: a MockMvc POST that triggers the business rule and asserts **409**, and a *direct* call `service.borrow(1L, 0)` asserting the `ConstraintViolationException` with AssertJ's `assertThatThrownBy`.

---

## Where a service-layer violation maps — and why it's usually a 500

This is the judgment call the topic exists to make explicit. In topic 09, a `ConstraintViolationException` from a controller's `@PathVariable @Min(1) Long id` is *genuine user input* — the client typed `/api/books/0` — so it's a **400**. Here the same exception type means something different.

By the time a request reaches the service, the controller's `@Valid`/`@Validated` has already accepted it. The demo's `LibraryController` validates `BorrowRequest` with `@Valid`, so `copies:0` is rejected as a 400 *before the service is ever called*. So if the service's own `@Positive` still fires, the bad argument did **not** come from the user — it came from a *caller passing bad args*: a buggy job, a mis-wired service, a test. That's a programming error, not a client error. Programming errors are 500s.

That's why the demo's advice maps a service-origin `ConstraintViolationException` to **500** with a generic body, logging the real detail:

```java
@ExceptionHandler(ConstraintViolationException.class)
public ProblemDetail handleServiceConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
    log.error("Service-layer constraint violation (a caller passed invalid args): {}", ex.getMessage());
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
            "An unexpected error occurred.", request);
}
```

Be honest that this is a *decision*, not a law. If you have a service that is *also* called directly by a controller with no DTO validation in front of it, a violation there might genuinely be user input and a 400 is right. The rule is: map the violation to the status that matches *who realistically caused it* in your architecture. With a validating controller in front, that's a programming error → 500, and you keep the parameter detail out of the body (it describes internal method args, which the client can't fix and shouldn't see — the topic 06 leak rule).

## Input validation vs business-rule enforcement — the core distinction

| | Input validation | Business-rule enforcement |
|---|---|---|
| **Question** | Is the argument the right shape/range? | Can the domain satisfy this right now? |
| **Expressed as** | annotation (`@Min`, `@Positive`, `@Valid`) | a thrown domain exception |
| **Needs runtime data?** | No — self-contained | Yes — current stock, account balance, state |
| **In the demo** | `@Min(1) Long bookId`, `@Positive int copies` | `if (copies > available) throw new InsufficientCopiesException(...)` |
| **Maps to** | 400 (user input) / 500 (caller bug) | 409 Conflict |

The trap is trying to force a business rule into an annotation. There is no `@EnoughStock` — it would need the stock map injected into a validator, and even then the value it reads at validation time can be stale by the time the method runs. Stock checks, balance checks, state-transition checks, uniqueness against the database — all of these are domain logic. They live in the method body and throw a domain exception (topic 07's pattern), which the advice maps to a meaningful status (here 409).

## Programmatic validation, for the rare off-the-request-flow case

Occasionally you need to validate an object where there's no annotation-driven trigger — validating a row parsed from a CSV import, or an object assembled in a batch job. Inject `jakarta.validation.Validator` and call it yourself:

```java
@Autowired Validator validator;

Set<ConstraintViolation<BorrowRequest>> violations = validator.validate(request);
if (!violations.isEmpty()) {
    throw new ConstraintViolationException(violations);
}
```

This is the same engine `@Valid` uses, invoked by hand. Reach for it only when no declarative hook fits; in normal request handling, `@Valid` on the controller and `@Validated` on the service cover everything.

---

## Common pitfalls

- **Wrong `@Validated`.** The class-level trigger is Spring's `org.springframework.validation.annotation.Validated`. Importing `jakarta.validation.Valid` and putting it on the class does nothing — the proxy is never created and the parameter annotations are silently inert.
- **Forgetting the class-level annotation entirely.** `@Min`/`@Positive` on a service method parameter do *nothing* without `@Validated` on the class. The method runs with whatever it's given. Always test with a deliberately bad direct call.
- **Self-invocation skips validation.** Method validation works through the Spring proxy, just like `@Transactional`. If a method inside the same bean calls another of its own `@Validated` methods via `this.borrow(...)`, the call bypasses the proxy and isn't validated. Call across beans, or restructure.
- **Mapping the service violation to 400 reflexively.** Same exception type as topic 09, different meaning. From a validating controller's service, a violation is a caller bug → 500. Copy-pasting topic 09's 400 handler hides programming errors as client errors.
- **Leaking parameter names in the 500 body.** A `ConstraintViolationException`'s property path is `borrow.copies` — internal method/param names. Don't echo it to the client (topic 06). Log it; return a generic body.
- **Trying to express a business rule as a custom constraint.** If the rule needs the database or current state, it's not bean validation. Stale reads and missing context make it wrong; throw a domain exception from the method instead.
- **Constraints on a non-Spring-managed object.** Method validation only happens on Spring beans behind the proxy. `new LibraryService().borrow(...)` validates nothing — you got a raw instance, not the proxy.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/12-method-level-validation-in-services/method-validation-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```bash
   # valid -> 200, remaining returned
   curl -i -X POST http://localhost:8080/api/library/borrow \
        -H "Content-Type: application/json" -d '{"bookId":1,"copies":2}'

   # business rule (book 2 has 0 copies) -> 409
   curl -i -X POST http://localhost:8080/api/library/borrow \
        -H "Content-Type: application/json" -d '{"bookId":2,"copies":1}'

   # bad user input (copies not positive) -> 400, fieldErrors (controller @Valid, not the service)
   curl -i -X POST http://localhost:8080/api/library/borrow \
        -H "Content-Type: application/json" -d '{"bookId":1,"copies":0}'
   ```
2. Run `mvn -q test`. Note `callingServiceDirectlyWithBadArgsThrowsConstraintViolation` calls the service *directly* with `copies = 0` — the only way to make the service's own guard fire, since the controller rejects it first.
3. **Break it and watch a test fail.** Remove `@Validated` from `LibraryService`. Re-run `mvn -q test`. `callingServiceDirectlyWithBadArgsThrowsConstraintViolation` now fails — `borrow(1L, 0)` runs the body instead of throwing, because without `@Validated` the proxy never validates. Restore it.
4. Move the `@Positive` check into the controller's `BorrowRequest` only (remove it from the service signature) and confirm via curl that the user-facing 400 is unchanged — proving the service guard is a *defensive* second layer, not the user-facing one.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`InsufficientCopiesException.java`** — the business-rule domain exception (→ 409).
3. **`BorrowRequest.java`** — the user-facing input DTO, validated by the controller.
4. **`LibraryService.java`** — `@Validated` bean; `@Min`/`@Positive` parameter guards *and* the stock business-rule check side by side.
5. **`LibraryController.java`** — thin; `@Valid`s the body, delegates to the service.
6. **`GlobalExceptionHandler.java`** — body validation → 400; service-origin `ConstraintViolationException` → 500; `InsufficientCopiesException` → 409.
7. **`MethodValidationTest.java`** — MockMvc 409 for the business rule, a direct service call asserting `ConstraintViolationException` via `assertThatThrownBy`.

## Self-check

1. Why can `@Positive int copies` reject `0` but never reject "more copies than are in stock"? What expresses the second check instead?
2. A `ConstraintViolationException` reaches your advice from a controller `@PathVariable` versus from a `@Validated` service method (behind a validating controller). Why might the same exception type map to 400 in one case and 500 in the other?
3. You put `@Min(1)` on a service method parameter but bad values sail through unchecked. Name two distinct reasons this happens.
