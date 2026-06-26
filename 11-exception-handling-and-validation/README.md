# 11 — Exception Handling and Validation

Module 03a taught you Java exceptions — `try`/`catch`, checked vs unchecked, how to write a custom exception. Module 09 taught you to *choose* the right HTTP status code, and gave you a **preview** of `@Valid` that rejected bad input with a bare 400. This module connects those two threads: when an exception is thrown deep in your service, how does it become a clean, consistent HTTP error response — and how do you reject bad input with a body the client can actually act on?

By the end you will have one `@RestControllerAdvice` that turns every exception your app throws — a missing entity, a duplicate key, a failed validation — into an RFC 7807 `ProblemDetail` with a stable shape, no leaked stack traces, and per-field error details. You will know how to validate request bodies, path variables, query parameters, and nested objects; how to write a custom constraint; how to validate differently for create vs update; and how to enforce business rules that bean validation can't express. The entities from module 10 meet `@RestController` again here — this time with `EntityNotFoundException` mapping to a 404 instead of escaping as a 500.

## Who this is for

- Anyone who finished modules 09 (REST APIs) and 10 (Spring Data JPA) and is ready to make their controllers fail *gracefully* instead of returning a whitelabel 500.
- Developers who have seen `@ControllerAdvice` in a codebase and copied it without knowing why it catches some exceptions and not others.
- Anyone whose API currently returns `200 OK` with `{"success": false}`, or a 500 with a full stack trace, and wants to know what production-grade error handling looks like.

## What you'll be able to do at the end

- [ ] Explain what Spring does *by default* when an exception escapes a controller (the `BasicErrorController`, the whitelabel page, the default `ProblemDetail`) and why that default is not enough for an API.
- [ ] Map an exception type to a status code three ways — `@ResponseStatus`, a controller-local `@ExceptionHandler`, and a global `@RestControllerAdvice` — and say when each is the right tool.
- [ ] Centralize all error handling in one `@RestControllerAdvice` and reason about handler precedence (most-specific exception wins).
- [ ] Return RFC 7807 `ProblemDetail` bodies (`application/problem+json`), add custom properties, and extend `ResponseEntityExceptionHandler` to reshape Spring's built-in errors.
- [ ] Design a stable error contract (timestamp, path, a `fieldErrors` array) and defend what you deliberately *do not* put in it — stack traces, SQL, internal class names.
- [ ] Throw domain exceptions (`BookNotFoundException` → 404, `DuplicateIsbnException` → 409) from the service layer and let the advice translate them, keeping HTTP concerns out of the service.
- [ ] Surface the per-field details from `MethodArgumentNotValidException` that module 09's preview left stuck in the server log.
- [ ] Validate `@PathVariable` and `@RequestParam` with class-level `@Validated`, and unify the resulting `ConstraintViolationException` with the request-body case in one error shape.
- [ ] Use validation groups so the same DTO validates differently on create (no id) versus update (id required).
- [ ] Write a custom constraint (`@ValidIsbn`) with a `ConstraintValidator`, and a class-level cross-field constraint (`endDate` after `startDate`).
- [ ] Trigger method-level validation on a `@Validated` service bean, and explain the line between *input validation* (bean validation) and *business-rule enforcement* (a thrown domain exception).
- [ ] List the exception-handling and validation pitfalls that bite teams — swallowing exceptions, catching `Exception` too broadly, advice that silently doesn't match, validation that silently doesn't run — and the fix for each.

If you can do all thirteen, the module worked.

## Prerequisites

- **Module 03a done.** You should already know the difference between a checked and an unchecked exception, what `try`/`catch`/`finally` does, and how to write a custom exception class. This module does *not* re-teach Java exceptions — it teaches what happens to them at the HTTP boundary.
- **Module 09 done.** You need `@RestController`, `@RequestMapping`, `@RequestBody`, `ResponseEntity`, the status-code decision tree (topic 08), and the `@Valid` preview (topic 10). This module is the promised follow-up to both.
- **Module 10 helpful.** Topics 06–08 use a `Book` entity and `JpaRepository` so the domain exceptions (`EntityNotFoundException` → 404, duplicate-key → 409) map onto something real. You can follow without it — the entities are simple — but it lands harder if you've done module 10.
- A JDK installed (Java 17 or newer; examples target Java 21, Spring Boot 3.x's baseline is Java 17).
- No database required for most topics. The demos use an in-memory `Map` store unless a topic specifically needs JPA, in which case it pulls in H2 like module 10.

## How this module is organized

Topic 01 is the only markdown-only topic. It shows what Spring does with an unhandled exception *out of the box* and names the goal of the whole module: a consistent error contract. Without seeing the ugly default, the rest of the module is just annotations.

Topics 02–04 are the **three mechanisms**, simplest first: `@ResponseStatus` on an exception (topic 02), a controller-local `@ExceptionHandler` (topic 03), and the global `@RestControllerAdvice` (topic 04) that every real app converges on.

Topic 05 is `ProblemDetail` — Spring 6's RFC 7807 error body — and how to extend `ResponseEntityExceptionHandler` so even Spring's built-in errors match your shape. Topic 06 steps back and designs the **error contract** itself, including the security angle: what must never appear in an error body.

Topic 07 is **domain exceptions** — the catalog of app-specific exceptions, thrown in the service, mapped in the advice. This is where module 10's entities reappear.

Topics 08–12 are **validation**, picking up where module 09 topic 10 stopped. Topic 08 surfaces the field errors the preview left in the log. Topic 09 handles path/query parameters and unifies `ConstraintViolationException` with the body case. Topic 10 is validation groups. Topic 11 is custom constraints and cross-field validation. Topic 12 is method-level validation on services and the input-vs-business-rule distinction.

Topic 13 is a catalog of the bugs people hit, with the cause and fix for each — the same shape as module 10's "common pitfalls" topic.

```text
01-default-spring-error-handling            <- whitelabel page, BasicErrorController, the goal; markdown-only
02-responsestatus-on-exceptions             <- @ResponseStatus, the simplest mapping and its limits
03-exceptionhandler-in-a-controller         <- @ExceptionHandler, controller-local scope
04-restcontrolleradvice-global-handling     <- @RestControllerAdvice, centralizing, handler precedence
05-problemdetail-and-rfc7807                <- ProblemDetail, application/problem+json, ResponseEntityExceptionHandler
06-designing-an-error-contract              <- stable shape, fieldErrors, what NOT to leak (security)
07-mapping-domain-exceptions                <- BookNotFoundException -> 404, DuplicateIsbnException -> 409
08-bean-validation-in-depth                 <- @Valid body, surfacing per-field errors, nested @Valid, collections
09-validating-path-and-query-params         <- @Validated, ConstraintViolationException, unify with body case
10-validation-groups                        <- @Validated(OnCreate.class), create vs update
11-custom-constraints-and-cross-field       <- @Constraint + ConstraintValidator, class-level validation
12-method-level-validation-in-services      <- @Validated beans, input validation vs business rules
13-common-pitfalls                          <- swallowing, broad catch, advice that doesn't match, silent no-validation
```

```text
COMMON_MISTAKES.md       <- real exception-handling and validation bugs with code and the fix
INTERVIEW_QNA.md         <- interview-grade questions on advice precedence, ProblemDetail, validation, rollback
```

## The toolkit (what we add to the pom)

Module 11 stays on the same Spring Boot version as modules 08–10 so projects move between modules without surprises. The new starter is `-validation`; everything else is already familiar from module 09.

| Library | Coordinates | What it does | First used in |
|---------|-------------|--------------|---------------|
| Spring Boot Starter Parent | `org.springframework.boot:spring-boot-starter-parent:3.5.14` | parent POM; locks versions | every topic |
| Spring Boot Starter Web | `org.springframework.boot:spring-boot-starter-web` | `@RestController`, MVC, embedded Tomcat, Jackson | every topic |
| Spring Boot Starter Validation | `org.springframework.boot:spring-boot-starter-validation` | Hibernate Validator (the Bean Validation reference impl) | topics 08+ |
| Spring Boot Starter Data JPA | `org.springframework.boot:spring-boot-starter-data-jpa` | Hibernate + Spring Data JPA | topic 07 (domain exceptions) |
| H2 Database | `com.h2database:h2:2.2.224` | in-memory database | topic 07 |
| Spring Boot Starter Test | `org.springframework.boot:spring-boot-starter-test` | JUnit 5, AssertJ, Mockito, MockMvc | every topic |

A note on `-validation`: as module 09 topic 10 warned, the `jakarta.validation` *annotations* are on the classpath even without this starter, but **Hibernate Validator isn't** — so the constraints silently don't run. Topics 02–07 (pure exception handling) don't need it; topics 08+ always pull it in.

## How to run the examples

Each topic with a demo is a real Maven project under `CODE_EXAMPLES/NN-topic-name/topic-demo/`. To run one:

```bash
cd 11-exception-handling-and-validation/CODE_EXAMPLES/04-restcontrolleradvice-global-handling/advice-demo
mvn -q test                       # runs the MockMvc assertions
mvn -q spring-boot:run            # boots the app on :8080 so you can curl it
```

Every demo's README lists the `curl` commands that produce each error, so you can *see* the response body and status line, not just read about them. Reading the actual JSON Spring returns — and comparing the default to your customized version — is half the point of this module.

## Checkpoint

You're done with this module when you can:

1. Be shown a controller that throws `EntityNotFoundException` and returns a 500 with a stack trace, and fix it so it returns a 404 with a clean `ProblemDetail` — using a `@RestControllerAdvice`, without touching the controller.
2. Explain why, when two `@ExceptionHandler` methods could both handle a thrown exception, Spring picks the one for the most specific type.
3. Take module 09's validation preview (which returned a bare 400) and add the `fieldErrors` array so the client learns *which* field failed and *why* — by handling `MethodArgumentNotValidException` in your advice.
4. Add `@Validated` to a controller and a `@Min(1)` to a `@PathVariable`, then explain why the failure is a `ConstraintViolationException` and not a `MethodArgumentNotValidException`, and handle both in one place.
5. Write a `CreateBookRequest` and an `UpdateBookRequest` validated from one DTO using groups, so `id` is required on update but forbidden on create.
6. Write a `@ValidIsbn` constraint with its `ConstraintValidator`, and a class-level constraint that rejects a date range where `endDate` is before `startDate`.
7. State the rule for what belongs in an error response body and what must never appear in it, and explain the attack a leaked stack trace enables.

If those seven feel comfortable, move to module 12 (Security), where the 401/403 cases from topic 08 of module 09 finally get real authentication and authorization behind them.

## A note on what's *not* here

- **No security.** 401 (unauthenticated) and 403 (forbidden) are *named* here as status codes, but the machinery that produces them — authentication filters, `@PreAuthorize`, `AccessDeniedException` handling — is module 12. This module assumes every request is already allowed; it only handles *malformed* and *not-found* and *conflicting* requests.
- **No reactive / WebFlux error handling.** `@RestControllerAdvice` works the same in WebFlux, but the roadmap is servlet-stack throughout. No `onErrorResume`, no `Mono` error operators.
- **No i18n of validation messages.** You can externalize messages to a `messages.properties` and have Hibernate Validator resolve them per-locale; topic 08 shows the `message` attribute but stops short of full internationalization, which is a presentation concern.
- **No retry / resilience.** Mapping an upstream `502`/`504` to a retry, circuit breakers, fallbacks — that's resilience engineering, touched on in module 19, not exception *handling* in the request/response sense.
- **No global filter / `HandlerExceptionResolver` internals.** Topic 04 names where `@ExceptionHandler` sits in Spring's `HandlerExceptionResolver` chain, but writing a custom resolver is rarely needed and out of scope. `@RestControllerAdvice` covers virtually every real case.
</content>
