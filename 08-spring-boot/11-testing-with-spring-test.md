# Testing with `spring-test` — `@SpringBootTest`, Slice Tests, `@MockitoBean`

Every test class since topic 02 has used `@SpringBootTest`. This is the topic where it stops being magic. You'll see what `@SpringBootTest` actually boots, why a *slice test* (like `@WebMvcTest`) is the right choice when you don't need the full context, and how `@MockitoBean` replaces a real bean with a mock at the seam between layers.

By the end of this topic you can write three flavors of test — full context, web slice, plain unit — and pick the right one for the job.

REST-specific test depth (`MockMvc` assertions against JSON, validation error responses, `@RestControllerAdvice` testing) lives in module 09. This topic introduces `MockMvc` only as far as one slice test goes.

---

## The problem this solves

A real codebase has tests at multiple levels:

- **Unit tests** of a single class with collaborators mocked.
- **Slice tests** of one layer (web, persistence, or a single client) without booting the rest.
- **Full integration tests** that boot the whole context and call the app over HTTP.

Plain JUnit handles unit tests fine — module 06 covered them in depth. Slice and integration tests need Spring's context-loading machinery. Boot's testing autoconfiguration gives you three annotations (`@SpringBootTest`, `@WebMvcTest`, and friends) that boot exactly the right amount of context for the test you're writing — full app, just the web layer, just the data layer.

The slice tests are the productivity win. A full `@SpringBootTest` takes 1–3 seconds to boot a real app's context. A `@WebMvcTest` is closer to 200ms because it skips persistence, security, and most autoconfig. Multiply by 200 tests in CI.

---

## `@SpringBootTest` — the full context

What you've been using:

```java
@SpringBootTest
class FullContextTest {

    @Autowired
    HelloService service;

    @Test
    void greetReturnsTheConfiguredMessage() {
        assertThat(service.greet()).isEqualTo("Hello, World!");
    }
}
```

Behind that annotation:

- Boot searches for an `@SpringBootConfiguration` class — usually your `App` class with `@SpringBootApplication` (which is itself a `@SpringBootConfiguration`). That's the "primary source." If your test class lives in `com.example.foo` and `App` is in `com.example`, Boot's search walks up the package tree and finds it.
- It boots a full `ApplicationContext` exactly as `SpringApplication.run` would, *except* by default the embedded web server is *not* started (`webEnvironment = MOCK`). For HTTP round-trips you need `webEnvironment = RANDOM_PORT` (topic 09) or `MOCK_MVC` (this topic, below).
- The context is cached across test classes that load the same configuration. If `FullContextTest` and `AnotherFullTest` both use bare `@SpringBootTest`, they share one context — boots once, reused.

A few flavors:

```java
@SpringBootTest                                         // full context, no server
@SpringBootTest(webEnvironment = RANDOM_PORT)           // full context + real server
@SpringBootTest(properties = "app.feature=true")        // override one property
@SpringBootTest(classes = { CoreConfig.class })         // load *only* these configs
```

Use full-context tests for: "does the whole app wire up?", "does the end-to-end HTTP path work?", "does the database integration work?" — i.e., tests that span layers.

---

## Slice tests — `@WebMvcTest` and friends

A *slice test* annotation boots a deliberately small context with just enough auto-configuration for one layer:

| Annotation              | What's in the context                                     |
|-------------------------|-----------------------------------------------------------|
| `@WebMvcTest`           | `DispatcherServlet`, your controllers, `MockMvc`, Jackson |
| `@DataJpaTest`          | Hibernate, an in-memory database, `EntityManager`, repositories |
| `@JdbcTest`             | `DataSource` + `JdbcTemplate`, no JPA                     |
| `@RestClientTest`       | A configured `RestTemplate` + `MockRestServiceServer`     |
| `@JsonTest`             | Jackson + the configured `ObjectMapper`                   |

`@WebMvcTest` is the one you'll see most. A controller test that wants nothing to do with the service implementations:

```java
@WebMvcTest(HelloController.class)
class HelloControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    HelloService helloService;            // replaces any real HelloService bean

    @Test
    void getHelloDelegatesToHelloService() throws Exception {
        when(helloService.greet()).thenReturn("Mocked greeting");

        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Mocked greeting"));
    }
}
```

Three things to notice:

- **`@WebMvcTest(HelloController.class)`** boots only the web layer and only one controller. No `@Service`, no `@Repository`, no `DataSource` — those auto-configurations are switched off.
- **`@MockitoBean HelloService`** replaces the real bean with a Mockito mock. The controller is wired with the mock automatically; no manual injection needed.
- **`MockMvc`** is a fake servlet stack that exercises the `DispatcherServlet` directly — no port binding, no socket. Faster than `TestRestTemplate` and gives you better assertion helpers for JSON, headers, and status codes.

That test runs in roughly 200ms. The same test under `@SpringBootTest(webEnvironment = RANDOM_PORT)` would take 1–3 seconds and require the real `HelloService` to be present and working.

---

## `MockMvc` — the basics

`MockMvc` simulates an HTTP request without going over the network. The shape is `perform → andExpect`:

```java
mockMvc.perform(get("/hello"))
       .andExpect(status().isOk())
       .andExpect(content().string("Hello, World!"));

mockMvc.perform(get("/users/42"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.id").value(42))
       .andExpect(jsonPath("$.name").value("Ada"));

mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"BOOK-A\"}"))
       .andExpect(status().isCreated())
       .andExpect(header().exists("Location"));
```

Three families of assertions:

- **`status()`** — `isOk()`, `isCreated()`, `isBadRequest()`, etc.
- **`content()`** and **`jsonPath()`** — assert on the response body.
- **`header()`** — assert response headers.

This is the surface — module 09 uses MockMvc for every controller it introduces and goes deeper into request bodies, headers, and `@RestControllerAdvice` testing. For now: enough to write the slice test above.

---

## `@MockitoBean` vs `@Mock` from module 06

Module 06's `@Mock` (used outside a Spring context) creates an isolated Mockito mock — you wire it into the class under test yourself.

`@MockitoBean` is a Spring-aware annotation: it creates a Mockito mock *and* registers it in the application context as a bean of that type, replacing the real bean if one exists. Beans that depend on it via constructor injection (or `@Autowired`) get the mock automatically.

```java
// module 06 style — pure unit test
@ExtendWith(MockitoExtension.class)
class HelloServiceUnitTest {
    @Mock Notifier notifier;
    @InjectMocks HelloService service;
    // ...
}

// module 08 style — slice test
@WebMvcTest(HelloController.class)
class HelloControllerSliceTest {
    @MockitoBean HelloService helloService;
    // controller is wired with the mock automatically
}
```

Different tools for different tests. `@Mock` for class-level unit tests; `@MockitoBean` when Spring needs to wire the mock into a context.

(Older Boot used `@MockBean`. It's deprecated since Boot 3.4. Use `@MockitoBean` going forward.)

---

## Picking a test type — decision flow

| You want to test...                                      | Reach for                                |
|----------------------------------------------------------|------------------------------------------|
| One class's logic with collaborators stubbed             | Plain `@ExtendWith(MockitoExtension.class)` |
| A controller's request mapping + response shaping        | `@WebMvcTest` + `@MockitoBean` services  |
| A repository's queries against a real (in-memory) DB     | `@DataJpaTest`                           |
| That all layers wire together                            | `@SpringBootTest` (no web)               |
| That HTTP requests actually round-trip end-to-end        | `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `TestRestTemplate` |

Default to the smallest annotation that still covers what you want to verify. The big annotations are fine sparingly; relying on them for every test is what makes test suites slow.

---

## Common pitfalls

- **`@WebMvcTest` and the test fails with `NoSuchBeanDefinitionException: HelloService`.** Slice tests don't include `@Service` beans. Add `@MockitoBean HelloService` to provide one. If your controller depends on three services, mock all three.
- **`@SpringBootTest` plus `webEnvironment = MOCK` plus `TestRestTemplate`.** Default `MOCK` means no real server starts. `TestRestTemplate` won't be auto-configured. Either switch to `RANDOM_PORT` or use `MockMvc` instead.
- **Asserting JSON with `content().string("...")`.** Brittle — any whitespace or field-order change breaks the test. Use `jsonPath("$.field")` for individual values or `content().json("{\"a\":1}", true)` for structural comparison.
- **Forgetting that the context is cached.** Two test classes with `@SpringBootTest` share one context. If test A's mutations bleed into test B, you'll see weird failures depending on test order. Either keep tests stateless or annotate the offending class with `@DirtiesContext` to force a fresh context (slow — use sparingly).
- **Using `@MockBean` in new code.** Deprecated since Boot 3.4 in favor of `@MockitoBean`. IDE warnings; same behavior; just rename the import.

---

## Try this yourself

1. Run `mvn -q test` against `testing-demo`. Two test classes pass: one full-context, one slice with mocks.
2. Open `HelloControllerSliceTest` and look at the import for `@MockitoBean` — note it lives in `org.springframework.test.context.bean.override.mockito`. That package didn't exist before Boot 3.4.
3. Add a new `@Service` bean to the app (say, an unused `Greeter`). Run both tests. The slice test still passes (it doesn't boot the bean) — proof slice tests really do skip what they don't need.
4. Time both tests: `mvn test` with surefire's `--show-version` or by reading the `Time elapsed` lines. The slice test is dramatically faster.

---

## Self-check

1. You have a controller that depends on three `@Service` beans. You want to test the controller alone. What annotation do you use, and what does the test class need to provide for each service?
2. `@SpringBootTest` and `@WebMvcTest` both boot a context. What's in the slice test's context that's *not* in the full one, and vice versa?
3. You see `@MockBean` in a colleague's code. What's wrong with it (post-Boot-3.4) and what's the rename?

---

## Code examples

In reading order:

- `testing-demo/pom.xml` — same starters as topic 09 (`spring-boot-starter-web` + `-test`).
- `.../HelloService.java` — a `@Service` with one method `greet()`.
- `.../HelloController.java` — `@RestController` delegates `GET /hello` to `HelloService`.
- `.../App.java` — `@SpringBootApplication`.
- `src/main/resources/application.yml` — sets the application name.
- `src/test/java/com/example/HelloFullContextTest.java` — `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`; calls the real service via real HTTP.
- `.../HelloControllerSliceTest.java` — `@WebMvcTest(HelloController.class)` + `@MockitoBean HelloService` + `MockMvc`; the controller is wired with a mock and tested in isolation.

Run with:

```bash
mvn -q test
```
