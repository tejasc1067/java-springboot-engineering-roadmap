# `@RestController` and `@RequestMapping`

Module 08 dropped a `@RestController` on you in topic 09 with one method, no explanation, just enough to prove the embedded server worked. This topic unpacks what that annotation actually means, what `@RequestMapping` does at the class level, and why the combination is the foundation of every REST controller you will ever write in Spring.

---

## The problem this solves

Spring MVC (the web framework under Spring Boot) was designed in 2007 to serve HTML pages from controllers that return a **view name** — a string like `"books/list"` that Spring resolves to a template file. The same controller could be told "no, this method returns the *body* of the response directly" by adding `@ResponseBody` to it.

That worked, but every method on a JSON API needed `@ResponseBody`. In 2014 Spring added `@RestController` — a single class-level annotation that does **both** `@Controller` *and* `@ResponseBody` once, for every method on the class. That is the entire mechanic.

```java
// the long form — equivalent
@Controller
public class BookController {
    @GetMapping("/books")
    @ResponseBody
    public List<Book> list() { ... }

    @GetMapping("/books/{id}")
    @ResponseBody
    public Book one(@PathVariable Long id) { ... }
}

// the modern form — same behaviour, less noise
@RestController
public class BookController {
    @GetMapping("/books")
    public List<Book> list() { ... }

    @GetMapping("/books/{id}")
    public Book one(@PathVariable Long id) { ... }
}
```

Always use `@RestController` for JSON APIs. The only time you would use plain `@Controller` (without `@ResponseBody`) is when you are serving HTML via a template engine like Thymeleaf — which this roadmap does not cover.

---

## How it works

`@RestController` is a **stereotype** annotation. Spring scans for classes annotated with `@RestController` (just like `@Service`, `@Repository`, `@Component` from module 07) and registers them as beans. It also tells Spring MVC: every method on this class returns the response body directly, not a view name.

Combine it with `@RequestMapping` at the class level to set a base path, and method-level mapping annotations (`@GetMapping`, `@PostMapping`, ...) for the verb-and-suffix combinations:

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping                    // GET /api/books
    public List<Book> all() { ... }

    @GetMapping("/{id}")           // GET /api/books/42
    public Book one(@PathVariable Long id) { ... }

    @PostMapping                   // POST /api/books
    public Book create(@RequestBody Book book) { ... }
}
```

`@RequestMapping("/api/books")` at the class is the base path. Method-level `@GetMapping("/{id}")` is appended. There is no method-level mapping on `all()` — Spring uses just the base path.

### What about the JSON?

When `BookController.one(...)` returns a `Book`, Spring MVC asks: "what does the client want?" The client's `Accept` header tells it (`application/json` by default for JSON APIs). Spring then picks a `HttpMessageConverter` — for JSON, this is `MappingJackson2HttpMessageConverter` — and serializes the `Book` to a JSON string. The string becomes the response body.

You added Jackson to your classpath the moment you added `spring-boot-starter-web` (Jackson is a transitive dependency of the starter). You did nothing else to get JSON serialization. Topic 06 unpacks Jackson in depth.

---

## Under the hood: servlets and the `DispatcherServlet`

You will see the word "servlet" in stack traces, test names (`MockMvc` "drives the `DispatcherServlet`"), and every Spring Security doc. Here is what it means.

A **servlet** is the Java standard (Jakarta Servlet API) for "code that handles an HTTP request." A servlet container — embedded Tomcat, in Spring Boot — owns the socket, parses the raw HTTP, and calls a servlet's `doGet` / `doPost` / ... with a request and response object. Before Spring MVC, you wrote servlets directly:

```java
public class RawBookServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long id;
        try {
            id = Long.parseLong(req.getPathInfo().substring(1));   // parse "/1" yourself
        } catch (RuntimeException e) {
            resp.sendError(400, "id must be a number");          // pick the status yourself
            return;
        }
        // ...look it up, 404 if missing...
        resp.setContentType("application/json");                   // set headers yourself
        resp.getWriter().write("{\"id\":1,\"title\":\"Effective Java\",\"author\":\"Bloch\"}"); // write JSON yourself
    }
}
```

That is `BookController.one(...)` with nothing done for you.

Spring MVC is **one servlet** — the `DispatcherServlet` — that Boot registers at `/`. Every request it receives goes through the same steps:

```text
HTTP request
   │
   ▼
Tomcat ── parses bytes into HttpServletRequest
   │
   ▼
Filter chain ── logging, CORS, Spring Security (module 12) — run before ANY servlet
   │
   ▼
DispatcherServlet
   ├─ HandlerMapping      which controller method matches this verb + path?   (no match → 404, wrong verb → 405)
   ├─ argument resolvers  @PathVariable, @RequestParam, @RequestBody → Java objects
   ├─ your controller method runs
   └─ HttpMessageConverter  return value → JSON (Jackson)
   │
   ▼
HTTP response
```

Everything this module teaches — path variables, JSON bodies, validation, `ResponseEntity`, exception handlers — is one of the `DispatcherServlet`'s steps. `@RestController` is how you plug a method into the middle of it.

The demo registers `RawBookServlet` at `/raw/books/*` beside the `DispatcherServlet`, plus a filter that stamps an `X-Passed-Filter` header. `ServletFlowTest` shows:

- `GET /raw/books/1` and `GET /api/books/1` return the same JSON — one hand-written, one generated.
- Both carry `X-Passed-Filter`: filters run before whichever servlet handles the request.
- `POST /raw/books/1` is `405` only because `HttpServlet`'s default `doPost` says so; anything else the raw servlet does, you coded by hand.

You will not write raw servlets in this roadmap. You will write **filters** (module 12) and read stack traces full of `DispatcherServlet`, `FilterChain`, and `HttpServlet.service` — now you know where each sits.

## Three forms — pick the right one

**1. `@RestController`** — what you want 99% of the time for JSON APIs.

```java
@RestController
@RequestMapping("/api/books")
public class BookController {
    @GetMapping
    public List<Book> all() { ... }
}
```

**2. `@Controller` + `@ResponseBody`** — equivalent, but verbose. Use only if you have a few JSON endpoints mixed into an HTML-serving controller.

```java
@Controller
@RequestMapping("/legacy/books")
public class LongFormBookController {
    @GetMapping
    @ResponseBody                  // required on every method
    public List<Book> all() { ... }
}
```

**3. `@Controller` alone** — the return value is treated as a **view name**. This is what you want for server-rendered HTML with Thymeleaf or JSP. Out of scope for this module — but you should recognize what happens if you forget `@ResponseBody`:

```java
@Controller
@RequestMapping("/broken")
public class BrokenController {
    @GetMapping("/books")
    public List<Book> all() {
        return List.of(...);       // Spring tries to find a view named ... the toString of a list. Fails.
    }
}
```

You will see a 500 or a "Whitelabel Error Page". The lesson: `@Controller` without `@ResponseBody` means "render a view, don't serialize this." If you want JSON, use `@RestController`.

---

## Class-level `@RequestMapping` — base path discipline

Always set the base path at the class. Three reasons:

1. **DRY** — you write `/api/books` once instead of on every method.
2. **Discoverable** — anyone opening the controller knows immediately which URL space it owns.
3. **Refactorable** — if your API moves from `/api/books` to `/api/v2/books`, you change one line.

```java
// good
@RestController
@RequestMapping("/api/books")
public class BookController {
    @GetMapping            public List<Book> all() { ... }
    @GetMapping("/{id}")   public Book one(@PathVariable Long id) { ... }
    @PostMapping           public Book create(@RequestBody Book book) { ... }
}

// avoid — base path repeated, hard to spot inconsistencies
@RestController
public class BookController {
    @GetMapping("/api/books")           public List<Book> all() { ... }
    @GetMapping("/api/books/{id}")      public Book one(@PathVariable Long id) { ... }
    @PostMapping("/api/book")           public Book create(@RequestBody Book book) { ... }  // typo: book vs books
}
```

The second form is how `/users` and `/order` end up in the same service (see topic 01's pitfalls).

---

## Common pitfalls

- **Forgetting `@ResponseBody` on a `@Controller`** — Spring treats the return value as a view name. Use `@RestController` for JSON.
- **Repeating the base path on every method.** Lift it to a class-level `@RequestMapping`.
- **Mixing trailing slashes.** `/api/books` and `/api/books/` are treated as different paths since Spring Boot 3.0 (the `trailing-slash-match` was removed for security reasons). Pick one — usually no trailing slash — and stick to it.
- **Expecting a filter to see controller exceptions as JSON.** A filter runs *outside* the `DispatcherServlet`, so an exception thrown in a filter never reaches `@ExceptionHandler` / `@RestControllerAdvice` (module 11). Handle it in the filter, or let Spring Security's own entry points do it (module 12).
- **One giant controller for the whole API.** One controller per top-level resource: `BookController`, `AuthorController`, `ReviewController`. Keep them small.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/02-restcontroller-and-requestmapping/restcontroller-demo`.

1. Run `mvn -q spring-boot:run`, then in a separate terminal:
   ```
   curl -i http://localhost:8080/api/books
   curl -i http://localhost:8080/api/books/1
   curl -i http://localhost:8080/legacy/books
   curl -i http://localhost:8080/broken/books
   curl -i http://localhost:8080/raw/books/1
   ```
   `/raw/books/1` returns the same JSON as `/api/books/1` — from a hand-written servlet. Both responses carry `X-Passed-Filter: true`.
   Compare the response of `/api/books` (200, JSON) and `/broken/books` (500 or whitelabel page).

2. Delete the `@ResponseBody` from one method in `LongFormBookController` and re-run. Confirm it now returns a Whitelabel 500 instead of JSON.

3. Change the class-level `@RequestMapping("/api/books")` to `@RequestMapping("/api/v2/books")`. Confirm every method moves with it.

## Code examples (reading order)

1. **`App.java`** — `@SpringBootApplication`; nothing controller-related, just the bootstrap.
2. **`BookController.java`** — `@RestController` + `@RequestMapping("/api/books")`. The idiomatic shape.
3. **`LongFormBookController.java`** — `@Controller` + `@ResponseBody` on every method. Equivalent, verbose.
4. **`BrokenController.java`** — `@Controller` without `@ResponseBody`. Demonstrates the failure mode.
5. **`BookControllerTest.java`** — MockMvc assertions that prove the three styles behave differently. Test 11 unpacks MockMvc; treat the asserts as readable English for now.
6. **`RawBookServlet.java`** — the same endpoint written as a plain servlet: parsing, status, headers and JSON by hand.
7. **`ServletConfig.java`** — registers the raw servlet at `/raw/books/*`, beside the `DispatcherServlet` at `/`.
8. **`RequestPathFilter.java`** — a filter that stamps `X-Passed-Filter` on every response.
9. **`ServletFlowTest.java`** — real embedded Tomcat: same JSON from both servlets, the filter runs before both, the raw servlet only does what it was coded to do.

## Self-check

1. What does `@RestController` collapse into, and what would you have to write instead if you used `@Controller`?
2. Why is class-level `@RequestMapping("/api/books")` better than putting the full path on every method?
3. Put these in the order a request meets them: your controller method, a servlet filter, Tomcat, `HttpMessageConverter`, `HandlerMapping`. Which of them would you need to write yourself in a raw servlet?
4. A `@Controller` method (no `@ResponseBody`) returns the string `"books/list"`. What does Spring try to do with that string, and what happens in a Spring Boot app with no template engine on the classpath?
