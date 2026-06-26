# Default Spring error handling — what you get for free, and why it's not enough

Before you write a single `@ExceptionHandler`, Spring Boot already has an answer for "what happens when an exception escapes a controller." Knowing that default is the starting point: it's what your API does *today* if you've written nothing, and the rest of this module is a series of deliberate replacements for it. This topic has no demo of its own — you'll see the real responses in topic 02 onward. Here we name the machinery and the goal.

---

## The problem this solves

You write a controller method. It throws — maybe a `NullPointerException`, maybe an `IllegalArgumentException`, maybe a `findById(...).orElseThrow()` that found nothing. You wrote no `try`/`catch`. The exception unwinds the stack and leaves your code entirely.

In a plain Java program (module 03a), an uncaught exception prints a stack trace and kills the thread. In a Spring Boot web app, it doesn't kill anything — the request thread is owned by Tomcat, and Spring catches the exception *for* you, somewhere above your controller. The question this whole module answers is: **what does Spring do with it, and how do you take control of that?**

---

## How it works — the default machinery

Three pieces handle an unhandled exception in Spring Boot, in order:

1. **`DispatcherServlet`** catches anything thrown from a controller and hands it to its chain of `HandlerExceptionResolver`s.
2. The built-in resolvers map a handful of Spring's own exceptions to status codes — `HttpMessageNotReadableException` (broken JSON) → 400, `NoResourceFoundException` (no handler for the URL) → 404, `HttpRequestMethodNotSupportedException` → 405, and so on. Your `@ExceptionHandler` and `@ControllerAdvice` methods (topics 03–04) plug in here too.
3. Anything *not* resolved falls through to the **`BasicErrorController`**, mapped to `/error`. It builds a default error response and returns it.

What that default response *looks like* depends on who's asking:

**A browser** (sends `Accept: text/html`) gets the **whitelabel error page** — a bland white HTML page:

```
Whitelabel Error Page
This application has no explicit mapping for /error, so you are seeing this as a fallback.
Wed Jun 26 12:00:00 UTC 2026
There was an unexpected error (type=Internal Server Error, status=500).
```

**An API client** (sends `Accept: application/json`, like every `curl -H "Accept: application/json"` or fetch call) gets JSON. In Spring Boot 3.x this is a minimal `ProblemDetail` (topic 05 covers the format in full):

```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": null,
  "instance": "/api/books/999"
}
```

Two things are true about that default JSON, and both are problems:

- **For an unmapped exception, the status is 500.** A `findById(...).orElseThrow()` that found nothing throws `NoSuchElementException` — an unchecked exception with no status mapping — so the client sees `500 Internal Server Error`. But the client did nothing wrong: they asked for a book that doesn't exist. That should be a **404**. The default has no idea; *you* know the semantics, and the rest of this module is how you tell Spring.
- **The body carries no useful detail.** `detail` is `null`. For a validation failure it says `"Invalid request content."` with no per-field information (module 09 topic 10 showed this exactly). The client can't tell *what* was wrong.

## What's hidden by default (and why)

By default Spring does **not** include the exception message, stack trace, or binding errors in the response body. You can turn them on:

```yaml
server:
  error:
    include-message: always         # default: never
    include-stacktrace: always      # default: never
    include-binding-errors: always  # default: never
```

It is tempting to flip all three to `always` in development. **Never ship that to production.** A stack trace in an error body is a gift to an attacker: it reveals your framework versions, internal class names, package structure, SQL fragments, and file paths — the reconnaissance step of an attack. The default (`never`) is the safe default. Topic 06 makes the rule explicit: the body tells the client what *they* can fix, never what your server *is*.

So the default leaves you with a dilemma. Leave it alone and clients get useless 500s with no detail. Flip the include-* flags and you leak internals. The way out is not those flags — it's writing handlers that produce a *deliberate* body: the right status, the detail the client needs, and nothing else.

---

## The goal of this module — a consistent error contract

Every well-built API answers errors with a **predictable, documented shape**. A client should be able to write one error-handling code path because every error — a 404, a 409, a validation 400 — comes back looking the same:

```json
{
  "type": "https://api.example.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "2 fields are invalid",
  "instance": "/api/books",
  "timestamp": "2026-06-26T12:00:00Z",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" },
    { "field": "isbn",  "message": "must be a valid ISBN-13" }
  ]
}
```

That is what you'll be able to produce by the end of the module. It is RFC 7807 `ProblemDetail` (topic 05) plus a couple of custom properties (topic 06), populated by a single `@RestControllerAdvice` (topic 04) that handles every exception your app throws — including the validation errors (topics 08+) that module 09 left stuck in the log.

The mechanisms, simplest first:

| Topic | Mechanism | Scope |
|------|-----------|-------|
| 02 | `@ResponseStatus` on the exception class | Static status, no body control. The 30-second fix. |
| 03 | `@ExceptionHandler` method | One controller. Full control of status and body. |
| 04 | `@RestControllerAdvice` | The whole app. Where real projects put it. |

---

## Common pitfalls

- **Assuming the default 500 is "fine for now."** It silently tells every client that *they* broke your server when they merely asked for a missing record. It also trains clients to retry (5xx implies "try again"), hammering you with requests that will never succeed. Map not-found to 404 early.
- **Turning on `include-stacktrace: always` to debug, then forgetting to turn it off.** This is a real, common production data-leak. If you need stack traces, read them from your logs, not the response body.
- **Thinking the whitelabel page is what your API returns.** It's only for `Accept: text/html`. Your JSON clients get the `ProblemDetail`. Test with the `Accept` header your real clients send.

---

## Self-check

1. A controller does `repository.findById(id).orElseThrow()` and the id doesn't exist. With zero exception handling written, what status code does an API client get, and why is it the wrong one?
2. Why is `server.error.include-stacktrace: always` dangerous in production? Name one concrete thing an attacker learns from a leaked stack trace.
3. What is the difference between the whitelabel error page and the default JSON error body — when does a client get each one?
</content>
