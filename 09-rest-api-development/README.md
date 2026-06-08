# 09 — REST API Development

How to actually build the HTTP surface of a Spring Boot service — controllers, request and response bodies, status codes, JSON, validation, MockMvc testing, pagination, OpenAPI, and versioning.

Module 08 ended with a single `GET /hello` returning a plain string just to prove the embedded Tomcat could serve a request. That is not a real API. A real API has resources, request bodies, validation, well-chosen HTTP status codes, response shapes that don't leak your database, tests that assert on JSON, and a contract you can hand to a frontend team. This module is where all of that gets unpacked.

## Who this is for

- Anyone who finished module 08 and is ready to turn the one-endpoint demo into an actual JSON HTTP API.
- Developers who have written `@RestController` from copied tutorials but cannot say what `@RestController` is composed of, or when to return 201 vs 204 vs 200.
- Anyone who has used REST APIs (as a frontend, mobile, or QA engineer) and wants to know what is happening on the other side.

## What you'll be able to do at the end

- [ ] Explain REST in terms a frontend engineer would accept — resources, statelessness, verbs, idempotency — and recognise URL designs that are *not* REST.
- [ ] Write a `@RestController` from scratch with class-level `@RequestMapping("/api/books")` and method-level `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`.
- [ ] Use `@PathVariable` for resource identifiers, `@RequestParam` for filters and pagination, `@RequestBody` for JSON payloads, and choose between plain returns and `ResponseEntity`.
- [ ] Pick the right HTTP status code for every common outcome — 200, 201 (with `Location`), 204, 400, 404, 409, 422 — and defend the choice.
- [ ] Separate request DTOs, response DTOs, and the in-memory domain model so a wire-shape change does not break your storage and vice versa.
- [ ] Add `@Valid` and Bean Validation annotations on a request DTO and get a predictable 400 on bad input (the deep treatment is module 11).
- [ ] Test a controller two ways: full-context with `@SpringBootTest` + `MockMvc`, and slice with `@WebMvcTest` — and explain why the slice starts ten times faster.
- [ ] Implement hand-rolled `?page=0&size=10&sort=name,asc` filtering, paging, and sorting against an in-memory list — no Spring Data magic.
- [ ] Wire `springdoc-openapi-starter-webmvc-ui` into the project and visit `/swagger-ui.html` to see your API contract auto-generated from the controllers.
- [ ] Pick a versioning strategy (URI / header / media-type) for a real service and articulate the tradeoffs.

If you can do all nine, the module worked.

## Prerequisites

- Module 08 done. You should be comfortable starting a Spring Boot app with `@SpringBootApplication`, reading `application.yml`, and running `mvn spring-boot:run` and `mvn test`.
- Module 06 done. You need to read `@Test`, `assertThat`, and Mockito `when(...).thenReturn(...)` without thinking.
- A passing familiarity with HTTP — you have seen `GET /users/42` before, you know what a 404 means, you have used the browser dev-tools Network tab at least once. If not, read MDN's HTTP overview before topic 01.
- A JDK installed (Java 17 or newer; examples target Java 21 — Spring Boot 3.x's baseline is Java 17).

## How this module is organized

Topic 01 is the only markdown-only topic. It names what REST is, what it isn't, and why the design choices in the rest of the module are the way they are.

Topics 02–03 are the Spring side of REST: the `@RestController` annotation, class-level mapping, and the five HTTP-verb annotations.

Topics 04–06 are the **inputs**: path variables, query parameters, and request bodies (and how Jackson turns JSON into Java).

Topic 07 is the **output side**: plain returns, `ResponseEntity`, headers, and the 201-with-`Location` pattern that real APIs use after `POST`.

Topic 08 is a decision tree for HTTP status codes — when 200, when 201, when 204, when 400, when 404, when 409, when 422.

Topic 09 introduces the **DTO discipline** that keeps your wire shape independent of your storage. (Storage is just in-memory `Map` here — real persistence is module 10.)

Topic 10 previews `@Valid` and jakarta.validation so the API can reject bad input with a 400. Full validation depth, custom validators, and `@ControllerAdvice` are module 11.

Topic 11 is testing — `MockMvc`, `jsonPath`, `@WebMvcTest`. This is what module 08 only glanced at.

Topic 12 covers pagination, filtering, and sorting as URL conventions you implement by hand against an in-memory list. When Spring Data arrives in module 10, you will see how it automates exactly this pattern.

Topic 13 wires up OpenAPI / Swagger UI with `springdoc-openapi`. Every real Java backend has this; it is the contract you hand to consumers.

Topic 14 is API versioning — URI versus header versus media-type — and which one to pick for a new service.

```text
01-what-rest-is                            <- REST principles, resources, verbs, idempotency
02-restcontroller-and-requestmapping       <- @RestController vs @Controller + @ResponseBody
03-http-verbs-in-spring                    <- @GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping
04-path-variables                          <- @PathVariable with type conversion and regex
05-query-parameters                        <- @RequestParam required/optional/default/collections
06-request-body-and-jackson                <- @RequestBody, JSON binding, records as DTOs
07-responseentity-and-response-shaping     <- status, headers, Location on 201
08-http-status-codes                       <- decision tree across 200/201/204/4xx/5xx
09-dto-vs-entity                           <- read/write DTO split; not returning your domain object
10-validation-preview                      <- @Valid + Bean Validation; default 400 shape
11-testing-rest-apis-with-mockmvc          <- jsonPath, @WebMvcTest, posting JSON
12-pagination-filtering-sorting            <- hand-rolled ?page=0&size=10&sort=name,asc
13-openapi-and-swagger                     <- springdoc-openapi; /v3/api-docs; /swagger-ui.html
14-api-versioning                          <- URI vs header vs media-type
```

```text
COMMON_MISTAKES.md       <- real REST API bugs with code and the fix
INTERVIEW_QNA.md         <- interview-grade questions on controllers, status codes, DTOs, testing
```

## The toolkit (what we add to the pom)

Module 09 stays on the same Spring Boot version as module 08 so projects move between modules without surprises. The starters are still the headline: a single coordinate pulls in a curated, version-aligned bundle.

| Library | Coordinates | What it does | First used in |
|---------|-------------|--------------|---------------|
| Spring Boot Starter Parent | `org.springframework.boot:spring-boot-starter-parent:3.5.14` | parent POM; locks versions | every topic |
| Spring Boot Starter Web | `org.springframework.boot:spring-boot-starter-web` | embedded Tomcat + Spring MVC + Jackson | every topic |
| Spring Boot Starter Test | `org.springframework.boot:spring-boot-starter-test` | JUnit 5, AssertJ, Mockito, Spring test, MockMvc | every topic |
| Spring Boot Starter Validation | `org.springframework.boot:spring-boot-starter-validation` | jakarta.validation + Hibernate Validator | topic 10 onward |
| springdoc-openapi-starter-webmvc-ui | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17` | OpenAPI spec + Swagger UI | topic 13 |

What's **not** here: `spring-boot-starter-data-jpa` (module 10), `spring-boot-starter-security` (module 12), `spring-boot-starter-actuator` (already covered in module 08 topic 12). No WebFlux, no HATEOAS.

## How to run the examples

Each topic is a real Maven project under `CODE_EXAMPLES/NN-topic-name/topic-demo/`. To see a demo:

```bash
cd 09-rest-api-development/CODE_EXAMPLES/02-restcontroller-and-requestmapping/restcontroller-demo
mvn -q spring-boot:run            # starts the app; visit endpoints with curl or a browser
mvn -q test                       # runs the MockMvc assertions
```

Or open the topic's project in IntelliJ and click the green play next to the `App` class.

For every web topic, watch the console for `Tomcat started on port 8080`. Then send requests with `curl`:

```bash
curl -i http://localhost:8080/api/books
curl -i -X POST http://localhost:8080/api/books -H "Content-Type: application/json" -d '{"title":"Effective Java","author":"Bloch"}'
```

The `-i` flag prints the response status line and headers — useful for the `Location` header on 201 (topic 07) and the validation 400 shape (topic 10).

## Checkpoint

You're done with this module when you can:

1. Build a `BookController` from scratch that handles `GET /api/books`, `GET /api/books/{id}`, `POST /api/books`, `PUT /api/books/{id}`, `PATCH /api/books/{id}`, and `DELETE /api/books/{id}` against an in-memory store — picking the correct status code and response body shape for each.
2. Be given a payload that violates a `@NotBlank` and predict what the 400 body looks like (default Spring Boot 3.x shape).
3. Write a `@WebMvcTest` that posts a JSON body, asserts `status().isCreated()`, asserts a `Location` header, then `jsonPath("$.id").exists()` on the response body.
4. Add `springdoc-openapi-starter-webmvc-ui` to a fresh Boot project, hit `/swagger-ui.html`, and explain how the docs got there without you writing them.
5. Compare URI versioning (`/api/v1/books`) and media-type versioning (`Accept: application/vnd.example.v1+json`) and recommend one for a new internal service.

If those five feel comfortable, move to module 10, where the in-memory `Map<Long, Book>` you have been using gets replaced by a real database with Spring Data JPA.

## A note on what's *not* here

- **No persistence.** No `@Entity`, no `JpaRepository`, no SQL. All examples use `Map<Long, Book>` or `List<Book>` in memory. Module 10.
- **No security.** No `spring-boot-starter-security`, no auth filters, no JWT. CORS gets a one-paragraph mention; full treatment is module 12.
- **No deep exception handling.** Topic 10 shows the *default* 400 body from a `@Valid` failure. `@ControllerAdvice`, `@ExceptionHandler`, custom error responses, and `MethodArgumentNotValidException` plumbing are module 11.
- **No WebFlux / reactive.** Everything here is servlet-based Spring MVC, which is what most Spring Boot services in production still use. Reactive is a separate path.
- **No HATEOAS, no JSON:API, no GraphQL.** These are alternative API styles. The module teaches plain JSON REST because that is the dominant shape in the Java ecosystem.
- **No Spring Cloud, no gateway, no service discovery.** Module 13 (microservices).
