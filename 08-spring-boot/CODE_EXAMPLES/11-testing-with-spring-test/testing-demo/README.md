# testing-demo

Two tests, two approaches:

- `HelloFullContextTest` — full `@SpringBootTest` + real Tomcat + `TestRestTemplate`. Real HTTP round-trip.
- `HelloControllerSliceTest` — `@WebMvcTest` + `@MockitoBean HelloService` + `MockMvc`. Controller-only, faster.

## Run

```bash
mvn -q test
```

## What to notice

- The slice test runs noticeably faster than the full-context one because it skips persistence, scheduling, and most autoconfig.
- `@MockitoBean` (Boot 3.4+) replaces the real `HelloService` bean in the slice test's context. The controller is wired with the mock automatically.
- `MockMvc` doesn't open a port — the request goes through the `DispatcherServlet` in-memory. Faster, and the assertion helpers (`status()`, `content()`, `jsonPath()`) are nicer than parsing a real response.
- Both tests live in the same package as `App` so `@SpringBootConfiguration` discovery works.
