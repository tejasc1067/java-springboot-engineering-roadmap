# Testing REST APIs with `MockMvc`

You have been using `MockMvc` since topic 02 to assert "request in, response out." This topic unpacks the tool itself — what it is, what it skips, the two ways to set it up, and the assertions that matter day-to-day.

---

## The problem this solves

You need to test a Spring controller. Three options:

1. **Unit test the method directly** — call `controller.create(req)` like any Java method. Fast, but skips Spring's binding, validation, error handling, and routing. You will miss a lot of bugs.
2. **Start the real server** — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`. Slow (Tomcat starts up), but tests the actual HTTP stack.
3. **`MockMvc`** — runs the full Spring MVC machinery in memory, no real socket. You get binding, validation, error handling, routing, response shaping — but no network. **The right default for controller tests.**

`MockMvc` performs the request inside the JVM. The "response" is the Spring object that *would have been written* to the wire. You can assert on its status, headers, and body without ever opening a port.

---

## Two setup styles

### 1. Full context — `@SpringBootTest`

This is what we have been using since topic 02:

```java
@SpringBootTest
class BookControllerTest {

    @Autowired WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(context).build(); }
    // ...
}
```

`@SpringBootTest` starts the **entire** Spring context — every `@Component`, `@Service`, `@Repository`, plus your `@RestController`. The MockMvc routes through the full MVC pipeline.

**Use this when:** the controller depends on real beans you want to exercise (in-memory store, simple services), or you want one test class to cover several controllers.

**Cost:** the whole context boots once per test class. Fine for the size you have. In a large app, this is the slow option.

### 2. Slice — `@WebMvcTest`

```java
@WebMvcTest(BookController.class)
class BookControllerSliceTest {

    @Autowired MockMvc mockMvc;          // injected directly

    @MockitoBean BookService service;    // a Mockito mock replaces the real bean
    // ...
}
```

`@WebMvcTest` starts a **minimal** context: only `BookController` + the MVC infrastructure. Service beans, repositories, all `@Component`s outside the web layer are **not** loaded.

To handle the controller's dependencies, declare each one as a `@MockitoBean`. It is a Mockito mock that Spring injects into the controller. You then `when(service.find(...)).thenReturn(...)` to script the dependency, exactly like a regular unit test.

**Use this when:** the controller depends on services you want to stub. Tests run dramatically faster — Spring loads only what the web layer needs.

**Cost:** every dependency must be mocked. If a controller pulls in eight collaborators, you have eight `@MockitoBean`s.

### Which one when

| If… | Use |
|------|------|
| Controller depends on simple, fast beans you want to exercise | `@SpringBootTest` |
| Controller depends on slow/external collaborators (DB, HTTP clients, message queues) | `@WebMvcTest` + `@MockitoBean` |
| You want to test a single controller in isolation | `@WebMvcTest` |
| You want one test to cover multiple controllers wired together | `@SpringBootTest` |

In practice, larger codebases lean on `@WebMvcTest` for per-controller tests and use a small number of `@SpringBootTest` integration tests to verify wiring end-to-end. Module 10 introduces a third slice (`@DataJpaTest`) once persistence is in play.

---

## The assertion vocabulary

### Status

```java
.andExpect(status().isOk())                  // 200
.andExpect(status().isCreated())             // 201
.andExpect(status().isNoContent())           // 204
.andExpect(status().isBadRequest())          // 400
.andExpect(status().isNotFound())            // 404
.andExpect(status().isConflict())            // 409
.andExpect(status().is(418))                 // any code
```

### Headers

```java
.andExpect(header().exists("Location"))
.andExpect(header().string("Location", "/api/books/1"))
.andExpect(header().string("Location", Matchers.endsWith("/api/books/1")))
.andExpect(header().longValue("X-Rate-Limit-Remaining", 99))
```

### Body — `jsonPath`

`jsonPath` uses JsonPath syntax (`$.field`, `$[0]`, `$..price`):

```java
.andExpect(jsonPath("$.id").value(42))
.andExpect(jsonPath("$.title").value("Effective Java"))
.andExpect(jsonPath("$.author").doesNotExist())          // missing OR null
.andExpect(jsonPath("$.tags.length()").value(3))
.andExpect(jsonPath("$.tags[0]").value("java"))
.andExpect(jsonPath("$.price").value(Matchers.greaterThan(0.0)))
.andExpect(jsonPath("$[?(@.author == 'Bloch')].title").exists())
```

`jsonPath("$.x").doesNotExist()` is misleadingly named — it passes both when the field is absent and when its value is `null`. Topic 06 used this without comment; now you know.

### Raw body

```java
.andExpect(content().string(""))                         // empty
.andExpect(content().string(Matchers.containsString("EJ")))
.andExpect(content().contentType(MediaType.APPLICATION_JSON))
```

### Posting JSON bodies

```java
mockMvc.perform(post("/api/books")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"EJ\",\"author\":\"Bloch\"}"))
   .andExpect(status().isCreated());
```

`.content(...)` takes the JSON string. For complex objects, use Jackson:

```java
@Autowired ObjectMapper mapper;

mockMvc.perform(post("/api/books")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(new CreateBookRequest("EJ", "Bloch"))))
   .andExpect(status().isCreated());
```

---

## When to skip `MockMvc` and use a real server

`TestRestTemplate` (with `@SpringBootTest(webEnvironment = RANDOM_PORT)`) starts a real Tomcat on a random port. Use it when:

- You want to verify the full HTTP stack, including filters and the actual servlet container.
- You're integration-testing against an external dependency (e.g. a WireMock server).
- Your test asserts behaviour that depends on real socket I/O — connection limits, streaming, large uploads.

Module 08 topic 09 used `TestRestTemplate` for the embedded-Tomcat demo. For controller tests, `MockMvc` is faster and just as informative.

---

## What `MockMvc` does *not* test

- The actual servlet container (Tomcat). Wrong Tomcat config will not surface here.
- Filter chains added via `WebSecurityConfigurerAdapter` or `SecurityFilterChain` may or may not be active — depends on slice. `@WebMvcTest` does not apply security by default; `@SpringBootTest` does.
- Anything that runs after the response is written (real `HttpServletResponse.flushBuffer`, async dispatch internals).

For 95% of controller behaviour, `MockMvc` is the right tool. The other 5% lives in proper integration tests, typically a separate Maven profile.

---

## Common pitfalls

- **Forgetting `.contentType(...)`.** A POST with no content-type returns `415 Unsupported Media Type`, not the 4xx you expected from validation. Always set it.
- **`@WebMvcTest` without `@MockitoBean` for collaborators.** Spring fails to start the context with `NoSuchBeanDefinitionException`. Either add the mock or use `@SpringBootTest`.
- **State leaking between tests.** As topics 03 and 09 hit: the controller bean is a singleton and the in-memory store is shared. Either write each test to be self-contained (don't assume id=1), or use `@DirtiesContext` (slow).
- **`jsonPath(...).doesNotExist()` and the null-vs-absent confusion.** This matcher passes for both cases. If you need to distinguish them, assert on the raw body or use a `Matchers.nullValue()` check.
- **Trusting `mockMvc.perform(...).andReturn().getResponse().getContentAsString()` to be JSON.** It is whatever Spring serialised. For error responses, it might be `application/problem+json`. Check the content-type or parse with a tolerant reader.

---

## Try this yourself

The demo project is `CODE_EXAMPLES/11-testing-rest-apis-with-mockmvc/mockmvc-demo`. It contains one controller, one service, and **two** test classes — one full-context, one slice — exercising the same endpoints.

1. Run `mvn -q test`. Both classes pass. Note that the slice test (`BookControllerSliceTest`) finishes noticeably faster.

2. Read `BookControllerSliceTest`. Notice the `@MockitoBean BookService service` and the `when(service.findById(1L)).thenReturn(Optional.of(...))` setup. The real service is never loaded.

3. Add a new endpoint to `BookController` that calls `service.deleteById(...)`. Run the slice test — it passes because the controller still compiles and routes correctly. Then add a slice test for the new endpoint, mocking `service.deleteById(...)` and asserting `verify(service).deleteById(1L)`.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — the record.
3. **`BookService.java`** — a service with simple operations (the dependency we will mock in the slice test).
4. **`BookController.java`** — uses the service.
5. **`BookControllerFullContextTest.java`** — `@SpringBootTest` + real service.
6. **`BookControllerSliceTest.java`** — `@WebMvcTest` + `@MockitoBean BookService`.

## Self-check

1. What does `MockMvc` skip compared to a `TestRestTemplate` against a `RANDOM_PORT` server?
2. When should you reach for `@WebMvcTest` over `@SpringBootTest`?
3. A test asserts `jsonPath("$.email").doesNotExist()`. The response actually contains `"email": null`. Does the assertion pass or fail, and how would you write a stricter check that distinguishes "absent" from "present-but-null"?
