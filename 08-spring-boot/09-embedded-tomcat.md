# Embedded Tomcat — Running as a Service

Every Spring Boot demo so far has booted a context and exited. This one keeps the JVM alive serving HTTP requests. Adding **one** dependency — `spring-boot-starter-web` — pulls in an embedded Tomcat, auto-configures a `DispatcherServlet`, and arranges for `SpringApplication.run` to bind a port and park on a non-daemon thread. By the end of this topic you can serve `GET /hello`, change the port, and explain who's listening on it.

This is the last "introduce a starter" topic in module 08. The HTTP depth — `@RequestBody`, `@PathVariable`, validation, error handling, MockMvc — lives in module 09.

---

## The problem this solves

Plain Spring needs you to wire the web stack by hand. Module 07 ended with `AnnotationConfigApplicationContext` — no servlet container in sight. To serve HTTP from a plain Spring app you'd:

1. Add `spring-webmvc`, `tomcat-embed-core`, `tomcat-embed-jasper`, `jakarta.servlet-api`.
2. Build a `Tomcat` instance, configure a connector, set a port.
3. Register `DispatcherServlet` against a path mapping.
4. Wire `AnnotationConfigWebApplicationContext` into the servlet so Spring can find your controllers.
5. Call `tomcat.start()`, then `tomcat.getServer().await()` to keep the JVM alive.

Forty lines of boilerplate identical in every project. Spring Boot replaces it with `spring-boot-starter-web` and zero `@Configuration` of yours.

---

## How it works: the starter and the auto-config

Add this dependency to a Boot project:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

That pulls in (from topic 03's list):

- `spring-webmvc` and `spring-web` — the MVC framework, `@RestController`, `@RequestMapping`.
- `tomcat-embed-core`, `tomcat-embed-el`, `tomcat-embed-websocket` — embedded Tomcat as a library.
- `jackson-databind` — so JSON responses just work.
- `jakarta.validation-api` — `@Valid` compiles (impl comes from `spring-boot-starter-validation`).

Auto-configuration kicks in via three classes from `spring-boot-autoconfigure`:

| Auto-config class                            | What it builds                                       |
|----------------------------------------------|------------------------------------------------------|
| `ServletWebServerFactoryAutoConfiguration`   | A `TomcatServletWebServerFactory` (or Jetty/Undertow)|
| `DispatcherServletAutoConfiguration`         | A `DispatcherServlet`, registered at `/`             |
| `WebMvcAutoConfiguration`                    | `RequestMappingHandlerMapping`, JSON converters, etc.|

Each has `@ConditionalOnClass(Tomcat.class)` (or equivalent), so they activate exactly when `spring-boot-starter-web` is on the classpath and not before. Topic 04's machinery does the work.

`SpringApplication.run` also notices the servlet container on the classpath and switches the context type to `AnnotationConfigServletWebServerApplicationContext`. That context, on `refresh()`, calls into the auto-configured `TomcatServletWebServerFactory` to start the server.

End result: you write `@RestController` and Boot makes it reachable over HTTP.

---

## The smallest useful controller

```java
@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }
}
```

Two annotations:

- **`@RestController`** is `@Controller` + `@ResponseBody`. The class is a Spring bean (so component scan picks it up). Every method returns the value directly as the response body, instead of going through view resolution.
- **`@GetMapping("/hello")`** maps HTTP `GET /hello` to this method. There's a `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, and a generic `@RequestMapping`.

Start the app, hit `http://localhost:8080/hello`, get back `Hello, World!`. That is the entirety of the contract.

---

## Configuring the server

`server.*` properties control the embedded Tomcat. The two you'll set most often:

```yaml
server:
  port: 8080                  # default; set to 0 for "pick any free port"
  servlet:
    context-path: /api        # prefix every URL with /api -> /api/hello
```

A handful of others worth knowing:

- `server.tomcat.threads.max` — max worker threads (default 200).
- `server.tomcat.connection-timeout` — how long Tomcat waits for a slow client before closing the connection.
- `server.compression.enabled: true` — gzip responses above a threshold.
- `server.error.include-stacktrace: never` — production safety; do not leak stack traces in error responses.

Module 09 covers error responses, content negotiation, and the rest of the HTTP-shaped config in detail.

---

## What proves the embedded server is running

Three signals when you start the app:

```text
2026-06-01T10:15:11.823+05:30  INFO 12345 --- [embedded-tomcat-demo] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 8080 (http)
2026-06-01T10:15:11.835+05:30  INFO 12345 --- [embedded-tomcat-demo] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8080 (http) with context path '/'
2026-06-01T10:15:11.841+05:30  INFO 12345 --- [embedded-tomcat-demo] [           main] com.example.App                          : Started App in 1.123 seconds (process running for 1.412)
```

`Tomcat started on port 8080` is your proof the bind succeeded. The JVM now stays alive because a Tomcat acceptor thread is running (non-daemon).

In a browser or `curl`:

```bash
curl http://localhost:8080/hello
# Hello, World!
```

---

## Testing the controller

`@SpringBootTest(webEnvironment = RANDOM_PORT)` boots the full context *with the embedded server* on a free port, and Boot auto-configures a `TestRestTemplate` you can inject:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloEndToEndTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void helloEndpointReturnsTheGreeting() {
        ResponseEntity<String> response = rest.getForEntity("/hello", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Hello, World!");
    }
}
```

`RANDOM_PORT` is what you want by default — tests don't conflict with each other or with a dev server already on 8080. `TestRestTemplate` auto-configures itself with the right base URL.

There's also `MockMvc` — a *fake* HTTP layer that avoids starting Tomcat at all and tests controllers against the `DispatcherServlet` directly. It's faster and the standard for testing controller logic. Topic 11 introduces it and module 09 uses it extensively. For now, `TestRestTemplate` against a real server is the most direct proof "yes, HTTP works."

---

## Switching to Jetty or Undertow

If your team prefers Jetty, two pom changes:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```

Boot's `JettyServletWebServerFactoryAutoConfiguration` activates instead of Tomcat's. No code changes — auto-configuration is type-based, not server-name-based.

Tomcat is the default for a reason: it's the most thoroughly tested servlet container under Spring and is what every Spring Boot example uses. Don't switch unless you have a concrete reason.

---

## Common pitfalls

- **Port already in use.** "Web server failed to start. Port 8080 was already in use." Either another instance is running, or another service holds the port. Kill it (`netstat -ano | findstr :8080` on Windows, `lsof -i :8080` on macOS/Linux) or set `server.port=0` for a random one.
- **Forgetting `@RestController` and writing `@Controller` only.** With just `@Controller`, the return value is treated as a view name, not a response body. You'll get a 404 looking for `hello.html`. Either add `@ResponseBody` to each method or use `@RestController` on the class.
- **Path collisions.** Two `@GetMapping("/hello")` on different controllers boots fine but fails at first request with `IllegalStateException: Ambiguous mapping`. Boot validates mappings lazily; the test for "the mapping is unique" is to actually call the endpoint.
- **Expecting the embedded server in a test that uses `@SpringBootTest` with default `webEnvironment = MOCK`.** Default is `MOCK` — no real server starts; `TestRestTemplate` won't have anything to call. Use `webEnvironment = RANDOM_PORT` (or `DEFINED_PORT`) when you want a real HTTP round-trip.
- **Hardcoding `http://localhost:8080`.** Use `TestRestTemplate` (knows the port automatically) or read `@LocalServerPort int port`. Hard-coded ports break the moment two tests run in parallel.

---

## Try this yourself

1. Run `mvn -q spring-boot:run` against `embedded-tomcat-demo`. Open `http://localhost:8080/hello` in a browser. Watch the response.
2. Stop the app, set `server.port: 9090` in `application.yml`, restart, hit `http://localhost:9090/hello`.
3. Add a `server.servlet.context-path: /api` to `application.yml`. Restart. The endpoint is now `http://localhost:8080/api/hello` — `http://localhost:8080/hello` returns 404.
4. Run `mvn -q test`. Read the test output — observe the `Tomcat started on port <random>` line and that the assertions succeed despite the port being different every time.

---

## Self-check

1. Adding `spring-boot-starter-web` to a Boot project does two observable things at startup that the bare `spring-boot-starter` does not. What are they?
2. `@RestController` is composed of two annotations. Name both, and explain what would change if you used just `@Controller` instead.
3. Your test uses `@SpringBootTest` with default `webEnvironment` and then tries to inject `TestRestTemplate`. The injection fails. Why? What's the smallest change to make it work?

---

## Code examples

In reading order:

- `embedded-tomcat-demo/pom.xml` — adds `spring-boot-starter-web` to the standard two starters.
- `.../HelloController.java` — one `@GetMapping("/hello")` returning a `String`.
- `.../App.java` — `@SpringBootApplication`; no changes from earlier topics.
- `src/main/resources/application.yml` — `server.port` set to 8080 explicitly for the demo.
- `src/test/java/com/example/HelloEndToEndTest.java` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, asserts the response.

Run with:

```bash
mvn -q test                  # boots Tomcat on a random port, calls /hello, asserts response
mvn -q spring-boot:run       # boots Tomcat on 8080; curl http://localhost:8080/hello
```
