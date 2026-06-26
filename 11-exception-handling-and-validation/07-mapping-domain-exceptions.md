# Mapping domain exceptions — the service throws, the advice translates

This is where module 10's entities meet the advice. A service that does `repository.findById(id).orElseThrow(...)` should throw a *domain* exception — `BookNotFoundException` — not an HTTP status, not a `ResponseEntity`. The advice (topic 04) turns that domain exception into a 404. This separation — services speak the domain, the advice speaks HTTP — is the pattern that keeps a codebase clean as it grows. This topic also handles the exceptions you *don't* own: the ones JPA and Spring throw at you.

---

## The problem this solves

Two anti-patterns this replaces:

```java
// Anti-pattern 1: HTTP leaks into the service.
public Book getById(Long id) {
    return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));  // service knows about HTTP
}

// Anti-pattern 2: the controller does the deciding.
@GetMapping("/{id}")
public ResponseEntity<Book> one(@PathVariable Long id) {
    Optional<Book> book = service.find(id);
    if (book.isEmpty()) return ResponseEntity.notFound().build();  // every controller repeats this
    return ResponseEntity.ok(book.get());
}
```

Both scatter HTTP decisions. The clean version: the service throws a domain exception that *means something in the domain* ("this book isn't here"), and one advice decides what HTTP that maps to.

---

## How it works — the layering

```
Controller   ── calls ──▶   Service   ── throws ──▶   BookNotFoundException
    │                                                        │
    │  (never catches it)                                    │
    ▼                                                        ▼
GlobalExceptionHandler  ◀──────── Spring routes the exception here ──────── maps to 404 ProblemDetail
```

The service:

```java
@Service
public class BookService {

    public Book getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));   // domain language, no HTTP
    }
}
```

The controller is thin and never thinks about errors:

```java
@GetMapping("/{id}")
public Book one(@PathVariable Long id) {
    return service.getById(id);   // if it's missing, the exception flies past here to the advice
}
```

The advice maps it, using the same contract helper from topic 06. The demo wires this against a real `@Entity Book` and a `JpaRepository`, so `GET /api/books/999` returns a 404 `ProblemDetail` and `GET /api/books/1` returns the book.

## Exception translation — turning a library exception into a domain one

Building on module 03a's "translate exceptions across layers": when a lower layer throws something framework-specific, the service catches it and rethrows a domain exception. The classic case is a unique-constraint violation. The `Book` entity marks `isbn` unique:

```java
@Column(unique = true, nullable = false)
private String isbn;
```

Saving a duplicate makes Hibernate's `INSERT` fail, surfacing as Spring's `DataIntegrityViolationException` — a generic, framework-level exception. The service translates it into something the domain (and the advice) understands:

```java
public Book create(Book book) {
    try {
        return repository.saveAndFlush(book);   // saveAndFlush forces the INSERT now, so the catch can see it
    } catch (DataIntegrityViolationException ex) {
        throw new DuplicateIsbnException(book.getIsbn());   // domain exception -> advice maps to 409
    }
}
```

`saveAndFlush` (not plain `save`) matters here: `save` may defer the `INSERT` until the transaction commits, *after* your `try` block has returned — so the `DataIntegrityViolationException` would escape uncaught. Flushing inside the `try` is what lets you catch and translate it. (This is the same flush-timing subtlety module 10 topic 14 raised.)

## The exceptions you don't own

You can't put `@ResponseStatus` on a library class. So library exceptions are mapped in the advice instead. Two you'll meet constantly:

- **`jakarta.persistence.EntityNotFoundException`** — thrown when you call `getReferenceById(id)` and then touch a proxy whose row doesn't exist. Map to **404**.
- **`org.springframework.dao.DataIntegrityViolationException`** — a constraint violation that reached the advice without being translated. Map to **409** as a safety net (though translating it in the service, as above, gives a better message).

The demo's advice has handlers for both the domain exceptions and these library ones:

```java
@ExceptionHandler(BookNotFoundException.class)      // 404, my exception
@ExceptionHandler(DuplicateIsbnException.class)     // 409, my exception
@ExceptionHandler(EntityNotFoundException.class)    // 404, JPA's exception — I can't annotate it
```

---

## Building a domain-exception catalog

As an app grows, a small, well-named set of domain exceptions emerges. A common shape:

| Domain exception | Maps to | Thrown when |
|------------------|---------|-------------|
| `BookNotFoundException` | 404 | A lookup by id finds nothing. |
| `DuplicateIsbnException` | 409 | A uniqueness rule is violated. |
| `BookNotBorrowableException` | 409 | A business rule forbids the action (already lent out). |
| `InvalidStateTransitionException` | 409 | The entity can't move to the requested state. |

Some teams give them a common base class (`DomainException`) so the advice can have one handler for the family plus specific ones where the status differs — exactly the hierarchy module 03a topic 02 sketched, now with HTTP meaning attached in the advice.

---

## Common pitfalls

- **Throwing `ResponseStatusException` from the service.** It works, but it drags `HttpStatus` into your domain layer. Fine for a tiny app; in anything larger, prefer a named domain exception so the service stays HTTP-agnostic and the mapping stays in one place.
- **Using `save` instead of `saveAndFlush` when you need to catch the constraint violation.** With `save`, the `INSERT` may fire at commit — after your `try` block — so the exception escapes your translation. Flush inside the `try`.
- **Catching `DataIntegrityViolationException` and assuming it's always a duplicate key.** It also covers not-null violations, foreign-key violations, and check-constraint failures. If you translate it to `DuplicateIsbnException` blindly, a different constraint produces a misleading 409. Inspect the cause, or constrain what can reach that `catch`.
- **Letting `EntityNotFoundException` reach the client unmapped.** Without a handler it's a 500. Add the handler (or avoid `getReferenceById` when you mean `findById`).
- **A controller that catches the domain exception itself.** Then the advice never runs and you're back to per-controller handling. Let domain exceptions propagate.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/07-mapping-domain-exceptions/domain-exceptions-demo` (uses JPA + H2, like module 10).

1. Run `mvn -q spring-boot:run`, then:
   ```bash
   curl -i http://localhost:8080/api/books/1      # 200, seeded book
   curl -i http://localhost:8080/api/books/999    # 404, BookNotFoundException -> advice
   curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" \
        -d '{"title":"Dup","author":"X","isbn":"9780201616224"}'   # 409, translated DataIntegrityViolation
   ```
2. In `BookService.create`, change `saveAndFlush` to `save`. Re-run the duplicate POST. The 409 turns into a 500 — the `INSERT` fired after your `try` returned, so the catch never saw it. Revert and watch it become 409 again.
3. Add a `BookNotBorrowableException` (→ 409) and a service method that throws it, and a handler in the advice. Confirm the new domain exception maps without touching the controller.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — JPA `@Entity` with a unique `isbn` column.
3. **`BookRepository.java`** — `JpaRepository<Book, Long>`.
4. **`BookRequest.java`** — input DTO for POST, so the entity isn't the request body.
5. **`BookNotFoundException.java`** / **`DuplicateIsbnException.java`** — the domain exceptions.
6. **`BookService.java`** — throws `BookNotFoundException`; translates `DataIntegrityViolationException` into `DuplicateIsbnException`.
7. **`BookController.java`** — thin; never catches anything.
8. **`GlobalExceptionHandler.java`** — maps the domain exceptions *and* JPA's `EntityNotFoundException`.
9. **`DomainExceptionsTest.java`** — asserts 200 / 404 / 409 end to end.

## Self-check

1. Why should a service throw `BookNotFoundException` rather than `ResponseStatusException(NOT_FOUND)`?
2. Why does catching a unique-constraint violation require `saveAndFlush` rather than `save`?
3. You can't put `@ResponseStatus` on `jakarta.persistence.EntityNotFoundException`. How do you make it return a 404?
</content>
