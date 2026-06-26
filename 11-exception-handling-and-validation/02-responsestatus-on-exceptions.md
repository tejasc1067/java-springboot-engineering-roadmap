# `@ResponseStatus` on exceptions — the 30-second mapping

The simplest way to stop an exception from becoming a 500 is to put one annotation on the exception class. `@ResponseStatus(HttpStatus.NOT_FOUND)` on your `BookNotFoundException` tells Spring "if this escapes a controller, the status is 404." No handler method, no advice. This topic is that mechanism — and an honest look at the two things it *can't* do, which are why the next topics exist.

---

## The problem this solves

From topic 01: an unmapped exception becomes a 500. A service that does `repository.findById(id).orElseThrow()` throws `NoSuchElementException`, and the client gets `500 Internal Server Error` for asking about a book that isn't there. That's a lie — the server is fine; the request was for a missing resource. The right answer is 404.

You could catch it in every controller method and call `ResponseEntity.notFound()`. That's noise repeated in every method. `@ResponseStatus` moves the decision to the exception type, once.

---

## How it works

`@ResponseStatus` is a marker Spring reads when an exception escapes a handler. Annotate the exception class with the status it represents:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BookNotFoundException extends RuntimeException {
    public BookNotFoundException(Long id) {
        super("book " + id + " not found");
    }
}
```

Now throw it from anywhere a controller can reach:

```java
@GetMapping("/{id}")
public Book one(@PathVariable Long id) {
    Book book = store.get(id);
    if (book == null) {
        throw new BookNotFoundException(id);   // -> 404, automatically
    }
    return book;
}
```

Look at the demo. `BookController` keeps an in-memory `Map<Long, Book>`. `GET /api/books/1` returns the book with 200. `GET /api/books/999` throws `BookNotFoundException`, and because of the annotation the client gets:

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": null, "instance": "/api/books/999" }
```

Compare `GET /api/books/explode`, which throws a plain `IllegalStateException` (no `@ResponseStatus`). That one still falls through to a **500**. The contrast is the lesson: the annotation is what changes the status, nothing else.

The test `BookControllerTest` asserts all three: 200 for a present book, 404 for a missing one, 500 for the unmapped exception.

---

## `code` and `reason` — and the `reason` trap

`@ResponseStatus` has two attributes:

```java
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "ISBN already exists")
public class DuplicateIsbnException extends RuntimeException { }
```

`code` (or its alias `value`) is the status. `reason` looks convenient — but it has a side effect most people don't expect: **when `reason` is set, Spring handles the exception with `HttpServletResponse.sendError(...)`**, which routes through the servlet container's error path and produces the whitelabel page / default error body. Your exception's own message is discarded, and you can't attach a JSON body. For an API, leave `reason` off and let the message and body be controlled elsewhere (topics 03–05).

---

## When to use it / when not to

**Use `@ResponseStatus` when:**
- The exception always maps to exactly one status, regardless of context. `BookNotFoundException` is *always* 404. That's a perfect fit.
- You want the mapping to live with the exception, so anyone reading the exception class sees its HTTP meaning.

**Reach for something else (topics 03–04) when:**
- **You need a response body.** `@ResponseStatus` controls *only* the status line. It cannot add `{ "field": "...", "message": "..." }`. The body is still the bare default `ProblemDetail`.
- **The status depends on context.** The same exception type sometimes means 404 and sometimes 410, depending on data. A static annotation can't express that.
- **The exception isn't yours to annotate.** You can't put `@ResponseStatus` on `EntityNotFoundException` from Jakarta Persistence or `DataIntegrityViolationException` from Spring — you don't own those classes. You need an `@ExceptionHandler` (topic 03) or advice (topic 04) to map them.

That last point is the usual reason teams outgrow `@ResponseStatus`: half the exceptions you want to map come from libraries.

---

## Common pitfalls

- **Expecting a custom body.** `@ResponseStatus` sets the status and nothing in the body. If you wanted `detail` or `fieldErrors`, the annotation alone won't give it — that's topic 03 onward.
- **Setting `reason`.** It switches Spring to `sendError`, throwing away your exception message and any body control. Omit it for APIs.
- **Annotating a library exception.** You can't — you don't own the class. Map it in advice instead.
- **Putting it on a checked exception you then catch.** `@ResponseStatus` only fires when the exception *escapes the controller* unhandled. If you catch it, the annotation never runs.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/02-responsestatus-on-exceptions/responsestatus-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```bash
   curl -i http://localhost:8080/api/books/1      # 200, the book
   curl -i http://localhost:8080/api/books/999    # 404, BookNotFoundException
   curl -i http://localhost:8080/api/books/explode # 500, unmapped IllegalStateException
   ```
2. Add `reason = "no such book"` to `@ResponseStatus` on `BookNotFoundException`, restart, and re-run the 404 call. Notice the body changes (servlet error path) and the `Content-Type` is no longer `application/problem+json`. Remove it again.
3. Try to add a JSON body listing the missing id. You can't, with this mechanism alone. That frustration is exactly what topic 03 solves.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — the domain record reused across this module.
3. **`BookNotFoundException.java`** — `@ResponseStatus(NOT_FOUND)`, the whole mechanism.
4. **`BookController.java`** — in-memory store; throws the mapped exception and an unmapped one for contrast.
5. **`BookControllerTest.java`** — asserts 200, 404, and the 500 fall-through.

## Self-check

1. You annotate `BookNotFoundException` with `@ResponseStatus(NOT_FOUND)` and throw it. What status does the client get? What's in the response *body*?
2. Why can't you use `@ResponseStatus` to map `EntityNotFoundException` (from a library) to a 404?
3. What surprising thing happens to the response when you set the `reason` attribute, and why does that make it a poor fit for a JSON API?
</content>
