# 08 — Spring Boot

How Spring Boot turns plain Spring's verbose, hand-assembled configuration into one annotation and a `main` method — and what is actually happening under that one annotation.

Module 07 left you with `AnnotationConfigApplicationContext`, `@Configuration` classes, and a pile of explicit bean definitions. That works, but it gets old fast: every new dependency needs hand-written wiring, every new project starts from a blank slate, and there is no embedded server in sight. Spring Boot is the layer that solves all three — **auto-configuration** picks sensible beans for you, **starters** bundle compatible dependency versions, and an **embedded Tomcat** means a Spring service is just a `java -jar` away.

This module teaches the mechanics of Boot itself: the annotation, property files, profiles, packaging the fat jar, running the embedded server, logging, the testing infrastructure (`@SpringBootTest`), and Actuator. REST depth, persistence, and security live in later modules.

## Who this is for

- Anyone who finished module 07 and wants to see what `@SpringBootApplication` collapses fifteen lines of plain-Spring boilerplate into.
- Developers who have followed Spring Boot tutorials but cannot say what auto-configuration is actually doing.
- Career switchers from Rails, Django, or Nest — Boot is the same idea (convention over configuration) and this module names the parts in Java.

## What you'll be able to do at the end

- [ ] Explain what `@SpringBootApplication` is composed of and bootstrap an app with `SpringApplication.run`.
- [ ] Read a list of `spring-boot-starter-*` dependencies and predict what they bring in.
- [ ] Read the `--debug` auto-configuration report and tell which conditional fired or skipped.
- [ ] Override an auto-configured bean correctly, without breaking the rest of the autoconfig chain.
- [ ] Load configuration from `application.yml`, explain property-source precedence, and override values from the command line.
- [ ] Bind structured config into a `@ConfigurationProperties` record and validate it with `@Validated`.
- [ ] Switch beans by `@Profile` using `application-{profile}.yml` and activate a profile three different ways.
- [ ] Build an executable fat jar with `spring-boot-maven-plugin` and run it with `java -jar`.
- [ ] Expose a `GET /hello` endpoint on a custom port and show that the embedded Tomcat is what served the response.
- [ ] Configure log levels per-package via `logging.level.*` and route logs to a file in a profile-specific way.
- [ ] Write a `@SpringBootTest` that boots the whole context, and a slice test (`@WebMvcTest`) that boots only the web layer.
- [ ] Enable Actuator, expose `/health` and `/info` over HTTP, and explain why `/env` and `/heapdump` should stay locked down in production.

If you can do all twelve, the module worked.

## Prerequisites

- Module 07 done. You should be comfortable reading a `@Configuration` class, picking between constructor and setter injection, resolving a `NoUniqueBeanDefinitionException`, and reading a `@Value("${prop}")` placeholder.
- Module 06 done. You should be able to read a `@Test`, an `assertThat`, and a Mockito `when(...).thenReturn(...)` without looking them up.
- A JDK installed (Java 17 or newer; examples target Java 21 — Spring Boot 3.x's baseline is Java 17).

## How this module is organized

Topic 01 names the pain points of plain Spring you just lived with in module 07 and previews what Boot does about each.

Topics 02–04 are the Boot **annotation + auto-configuration** story: `@SpringBootApplication`, starters, and the conditional-bean machinery that decides what gets created. By the end of topic 04 you can read an autoconfig report and know what is happening.

Topics 05–07 are **configuration**: `application.properties` / `application.yml`, type-safe binding with `@ConfigurationProperties`, and profiles in Boot (which differ from plain `@Profile` in module 07 in one important way — file-per-profile).

Topic 08 packages your Spring Boot app as an executable fat jar — the deliverable a deployment team actually receives.

Topic 09 starts the embedded server and serves one `GET /hello`. The full REST treatment is module 09.

Topic 10 covers logging defaults and how to tune them.

Topic 11 introduces `spring-test`: `@SpringBootTest`, slice tests, and `@MockitoBean`. This is what your earlier topics' tests were quietly using — now it gets unpacked.

Topic 12 turns on Actuator so the operations team has health and metrics endpoints to scrape.

```text
01-what-spring-boot-adds                    <- pain points of plain Spring; the Boot pitch
02-first-spring-boot-app                    <- @SpringBootApplication, SpringApplication.run
03-starters                                 <- spring-boot-starter, -web, -test; the parent BOM
04-auto-configuration                       <- @ConditionalOnXxx; --debug report; overrides
05-application-properties-and-yaml          <- property sources, precedence, CLI overrides
06-configuration-properties                 <- @ConfigurationProperties, @Validated
07-profiles                                 <- application-{profile}.yml; spring.profiles.active
08-packaging-the-fat-jar                    <- spring-boot-maven-plugin; java -jar; spring-boot:run
09-embedded-tomcat                          <- port/context-path; GET /hello (REST depth = module 09)
10-logging                                  <- Logback defaults; logging.level.*; profile-specific
11-testing-with-spring-test                 <- @SpringBootTest; slice tests; @MockitoBean
12-actuator                                 <- /health, /info, /metrics; exposure config
```

```text
COMMON_MISTAKES.md       <- real Spring Boot bugs with code and the fix
INTERVIEW_QNA.md         <- interview-grade questions on auto-config, properties, testing, Actuator
```

## The toolkit (what we add to the pom)

Spring Boot's **starters** are the headline change versus module 07: a single starter dependency pulls in a curated, version-aligned bundle. After topic 03 you will rarely write a version number again — `spring-boot-starter-parent` locks them for you.

| Library | Coordinates | What it does | First used in |
|---------|-------------|--------------|---------------|
| Spring Boot Starter Parent | `org.springframework.boot:spring-boot-starter-parent:3.5.x` | parent POM; locks versions for everything below | topic 02 |
| Spring Boot Starter | `org.springframework.boot:spring-boot-starter` | core: spring-context, logging, YAML, autoconfigure | topic 02 |
| Spring Boot Starter Web | `org.springframework.boot:spring-boot-starter-web` | embedded Tomcat + Spring MVC | topic 09 |
| Spring Boot Starter Test | `org.springframework.boot:spring-boot-starter-test` | JUnit 5, AssertJ, Mockito, Spring test, MockMvc | every topic |
| Spring Boot Starter Validation | `org.springframework.boot:spring-boot-starter-validation` | jakarta.validation + Hibernate Validator | topic 06 |
| Spring Boot Starter Actuator | `org.springframework.boot:spring-boot-starter-actuator` | health/info/metrics endpoints | topic 12 |
| Spring Boot Maven Plugin | `org.springframework.boot:spring-boot-maven-plugin` | builds the executable fat jar; provides `spring-boot:run` | topic 08 |

Inside `spring-boot-starter-test` you get JUnit Jupiter, AssertJ, Mockito, Hamcrest, JSONassert, JsonPath, and Spring's own test support (`@SpringBootTest`, `MockMvc`, `TestRestTemplate`). No need to pin any of those individually — `spring-boot-starter-parent` handles versions for the entire family.

What's **not** here: `spring-boot-starter-data-jpa` (module 10), `spring-boot-starter-security` (module 12). REST-specific concerns (`@RequestBody`, `@RestControllerAdvice`, `MockMvc` against real JSON bodies, error responses) all wait for module 09.

## How to run the examples

Each topic is a real Maven project under `CODE_EXAMPLES/NN-topic-name/topic-demo/`. To see a demo:

```bash
cd 08-spring-boot/CODE_EXAMPLES/02-first-spring-boot-app/hello-demo
mvn -q spring-boot:run            # starts the app; prints the Spring banner and startup log
mvn -q test                       # runs the @SpringBootTest assertions
```

Or open the topic's project in IntelliJ and click the green ▶ next to the `App` class's `main` method.

For topic 09 onward (anything web-related), watch the console for `Tomcat started on port 8080` — that line is your proof the embedded server is running. Then visit `http://localhost:8080/hello` in a browser.

## Checkpoint

You're done with this module when you can:

1. Start a fresh Spring Boot project from `spring-boot-starter-parent`, write a `@SpringBootApplication` class, add `spring-boot-starter-web`, and serve `GET /hello` — without copying a tutorial.
2. Be handed an `application.yml` and a `--debug` autoconfig report and tell which beans came from autoconfiguration, which came from your code, and which conditional refused to fire.
3. Bind a nested config block (e.g. `app.retry.max-attempts: 3` and `app.retry.backoff: 500ms`) into a `@ConfigurationProperties` record with validation, then break the validation deliberately to see the failure message at startup.
4. Write a `@SpringBootTest` that loads the full context, then rewrite the same test as `@WebMvcTest` and explain why it starts ten times faster.

If those four feel comfortable, move to module 09, where REST APIs get the depth they deserve — served by the embedded Tomcat you just learned to start.

## A note on what's *not* here

- **No REST depth.** `@RestController` appears in exactly one topic (09) and serves exactly one `GET /hello` plain-string response. `@RequestBody`, JSON binding, `@PathVariable`, request-body validation, `MockMvc` with real JSON assertions, error handling — all module 09.
- **No persistence.** No `spring-boot-starter-data-jpa`, no `@Entity`, no `JpaRepository`. Module 10.
- **No security.** No `spring-boot-starter-security`, no auth filters, no method-level security. Module 12.
- **No `@Async` or scheduling.** `@EnableAsync`, `@Async`, `@Scheduled` are Spring features that work in Boot but each needs careful explanation; they show up later, alongside event-driven systems (module 17).
- **No Spring Cloud or microservices wiring.** Module 13.
- **No deployment, container image, or CI.** `mvn package` produces the jar; what you do with that jar lives in module 14 (Docker / DevOps) and beyond.
