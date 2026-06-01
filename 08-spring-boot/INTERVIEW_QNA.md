# Interview Q&A — Spring Boot

Each question is the kind you'd actually be asked in a backend interview. Answers go beyond the one-line definition — the goal is to demonstrate that you understand *why* Boot is shaped the way it is, not just to recite its annotation names.

---

## 1. What does `@SpringBootApplication` do, and what is it composed of?

`@SpringBootApplication` is a meta-annotation that bundles three:

- **`@SpringBootConfiguration`** — a flavor of `@Configuration`. The annotated class becomes a source of bean definitions (`@Bean` methods inside it are honored).
- **`@ComponentScan`** — scans for `@Component`, `@Service`, `@Repository`, `@RestController`, and `@Configuration` classes starting from the *package of the annotated class* and walking down. That's why putting `App` at the root of your package tree is a convention: everything else is naturally below.
- **`@EnableAutoConfiguration`** — the only annotation unique to Boot. It triggers Spring Boot's auto-configuration machinery: scanning `META-INF/spring/.../AutoConfiguration.imports` from every jar on the classpath, evaluating each listed class's `@Conditional*` rules, and registering the beans whose conditions all hold.

Two of the three (`@Configuration`, `@ComponentScan`) are plain Spring annotations from module 07. Only `@EnableAutoConfiguration` is new in module 08. That's why "Spring Boot is a layer on top of Spring" is literally true at the annotation level: `@SpringBootApplication` is mostly Spring annotations plus one Boot annotation that does the magic.

A common follow-up: "what if you don't want auto-configuration?" Then use `@SpringBootConfiguration` + `@ComponentScan` directly (no `@EnableAutoConfiguration`), or set `@SpringBootApplication(exclude = { SomeAutoConfiguration.class })` to disable individual ones.

---

## 2. Walk me through how auto-configuration decides which beans to create.

Auto-configuration is **conditional bean registration**. Boot ships ~150 `@AutoConfiguration` classes inside `spring-boot-autoconfigure.jar` (and starters bring their own). Each is a normal `@Configuration` class with two extra ingredients:

1. **Class-level `@AutoConfiguration`** (or, in older versions, `@Configuration` + presence in `spring.factories`) registers the class with Boot's machinery.
2. **`@Conditional*` annotations** on the class and on each `@Bean` method gate registration.

At startup, `@EnableAutoConfiguration` reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` from every jar (Boot's and any starter you've added). For each listed class, Boot evaluates its conditions:

- `@ConditionalOnClass(Tomcat.class)` — is the class on the classpath?
- `@ConditionalOnMissingBean(ObjectMapper.class)` — does the user already have a bean of this type?
- `@ConditionalOnProperty(name = "..", havingValue = "..")` — is the property set?
- `@ConditionalOnWebApplication` — is the context a web one?

If all conditions hold, Boot applies the `@Configuration` and registers its `@Bean` methods. If any condition fails, the whole class is skipped, and the user either has to provide their own bean or accept the absence.

The "non-intrusive" property comes from `@ConditionalOnMissingBean`: Boot's auto-configured beans almost always have it. The moment you define your own `ObjectMapper`, Boot's steps aside.

A practical follow-up: "how would you confirm which auto-configs fired?" Run with `--debug` (or set `debug=true`) and Boot prints a `CONDITIONS EVALUATION REPORT` with `Positive matches`, `Negative matches`, and the specific predicate that failed for each skipped autoconfig.

---

## 3. You want to replace Boot's default `ObjectMapper`. Two ways. Which would you reach for first?

The two mechanisms:

**1. Define your own bean.** `JacksonAutoConfiguration` declares `@Bean @ConditionalOnMissingBean ObjectMapper jacksonObjectMapper(...)`. The moment your context has any `ObjectMapper` bean, the condition fails and Boot's bean is not registered.

```java
@Configuration
public class MyJacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
```

**2. Customize Boot's via `Jackson2ObjectMapperBuilderCustomizer`.** Don't replace it; let Boot's builder do the work and inject tweaks.

```java
@Component
public class MyObjectMapperCustomizer implements Jackson2ObjectMapperBuilderCustomizer {
    @Override
    public void customize(Jackson2ObjectMapperBuilder builder) {
        builder.serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
```

Reach for **#2 first**. Boot's default `ObjectMapper` is already configured with sensible Spring/JSR-310 integration; the customizer lets you add or override without losing Boot's defaults. Mechanism #1 throws all of that away and forces you to remember everything Boot would have done.

#1 is right when you genuinely want a *different* mapper — e.g. a Kotlin app that wants `KotlinModule().enable(NullToEmptyCollection)` as the default, or a project that wants `jackson-dataformat-cbor` instead of JSON.

This shape — "customize the auto-configured thing" vs "replace it" — applies to many auto-configs: `WebMvcConfigurer` for MVC, `HibernatePropertiesCustomizer` for JPA, `RestTemplateBuilder` for HTTP clients.

---

## 4. Walk me through Spring Boot's property source precedence.

There are roughly 15 sources stacked from highest priority to lowest. The high-priority end (the ones you care about day to day):

1. **`@TestPropertySource` / `@SpringBootTest(properties = ...)`** — test-only.
2. **Command-line arguments** — `--app.name=foo`.
3. **`SPRING_APPLICATION_JSON`** environment variable — a JSON blob in one variable.
4. **OS environment variables** — `APP_NAME=foo`.
5. **JVM system properties** — `-Dapp.name=foo` on the command line.

Then the file-based and in-jar sources:

6. **External profile-specific files** — `application-prod.yml` next to the jar.
7. **External `application.yml`** — next to the jar.
8. **Profile-specific files inside the jar** — `application-prod.yml` on the classpath.
9. **`application.yml` inside the jar** — your in-source defaults.
10. **`@PropertySource` on `@Configuration` classes**.
11. **`SpringApplication.setDefaultProperties(...)`** — lowest.

The thumb rule: **more "external" or "specific" beats more "internal" or "generic."** That's why you can ship a jar with sensible defaults inside (`application.yml`) and have an operations team override per-environment values on the command line, without rebuilding.

A common pitfall this ordering explains: putting `app.api-host` only in `application-prod.yml` (no default in `application.yml`) means startup fails in any environment that doesn't activate the prod profile — sources 6–9 have no value, sources 1–5 don't either, the placeholder doesn't resolve.

---

## 5. `@Value` versus `@ConfigurationProperties` — when is each correct?

`@Value("${some.prop}")` is for **one or two single values**. It's a `String`/`int`/`Duration` injection point with optional default syntax (`${some.prop:default}`). No grouping, no type-safe nested structure, no validation.

`@ConfigurationProperties` is for **structured config blocks**, especially when:

- You have three or more related values that belong together conceptually (a retry policy, a CORS config, a third-party integration).
- The config is nested (`app.retry.notification.email`).
- You want startup-time validation via jakarta.validation (`@Min`, `@NotBlank`, `@Pattern`).
- You want to inject the whole block as one bean rather than three constructor parameters.

A concrete example: a notification config with `email`, `phone`, `enabled`, `templateId`, `retryCount`. `@Value` means five constructor parameters in every class that uses any of them and five property names duplicated across the codebase. `@ConfigurationProperties` means one `NotificationProperties` bean, one place to refactor, automatic IDE support.

The hybrid case: a `@Service` reads one config value via `@Value` and a whole config block via an injected `@ConfigurationProperties` bean. Both annotations can coexist; pick the right tool per case.

A bonus mention: don't use `@ConfigurationProperties` on a mutable POJO with setters when a record works. Records communicate "this is set at startup and never changes" — which is what config should be.

---

## 6. What's actually inside a Spring Boot fat jar, and why is it laid out that way?

Unzipping a Boot fat jar shows three top-level directories:

```text
META-INF/                        # MANIFEST.MF
BOOT-INF/
  ├── classes/                   # your compiled code + resources
  ├── lib/                       # every dependency as a nested jar
  └── layers.idx                 # ordering hint for Docker layering
org/springframework/boot/loader/ # Boot's launcher code
```

The `MANIFEST.MF` points `Main-Class` at `org.springframework.boot.loader.launch.JarLauncher`, not your `App`. When the JVM runs `java -jar myapp.jar`, Boot's launcher executes first. It then:

1. Reads `BOOT-INF/lib/` and constructs a classloader that can see every nested jar.
2. Reads `Start-Class` from the manifest (set automatically to your `@SpringBootApplication` class) and invokes its `main`.

**Why this layout instead of `maven-shade-plugin`?** Shade unzips every dependency and merges them into a flat namespace. That's fine when no two jars contain a file with the same path — but it falls apart on resources like `META-INF/services/...`, `META-INF/spring.handlers`, `META-INF/spring.schemas`. Multiple jars contribute files there with the same name; Shade either overwrites or concatenates, and either choice breaks something. Boot's nested-jar layout keeps each dependency intact, so its resources stay together and the launcher's classloader knows which jar each class came from.

**Why `BOOT-INF/classes/` instead of jar root?** Boot's launcher needs to distinguish "your code" from "dependency code" so it can build the classloader properly. Putting your classes in their own directory makes the layout self-describing.

The `layers.idx` file is the Docker hook: it lists which files in the jar change rarely (Spring jars) versus often (your `BOOT-INF/classes/`). A Dockerfile can then unpack the jar with `java -Djarmode=tools -jar app.jar extract --layers` and `COPY` each layer separately, so a code-only rebuild doesn't bust 50MB of dependency layers in your registry.

---

## 7. `@SpringBootTest` versus `@WebMvcTest`. When would you use each?

Both annotations boot a Spring context for a test, but the contents differ.

**`@SpringBootTest`** boots the *full* application context: every `@Component`, `@Service`, `@Repository`, every auto-configuration that fires, your `@SpringBootApplication` class as the configuration source. By default, no embedded web server is started (`webEnvironment = MOCK`) — to do real HTTP, you set `webEnvironment = RANDOM_PORT`.

Use it when:
- The test's purpose is "does the whole app wire up?" — i.e., a smoke test.
- You're testing across layers (controller calls service calls repository).
- You're testing the full end-to-end HTTP path (with `RANDOM_PORT` + `TestRestTemplate`).
- You're testing a piece that depends on a specific auto-configuration firing.

**`@WebMvcTest(SomeController.class)`** boots a deliberately *small* context: the `DispatcherServlet`, the targeted controller(s), `MockMvc`, Jackson, and the web-layer infrastructure. `@Service` and `@Repository` beans are not scanned. Persistence auto-config is off. Security, if present, is included.

Use it when:
- The test's purpose is "does this controller return the right status, headers, JSON shape?"
- You can supply collaborators as `@MockitoBean` rather than real beans.
- You care about speed — a `@WebMvcTest` is roughly 5-10x faster than `@SpringBootTest`.

The decision rule: **use the smallest annotation that covers what the test verifies.** Defaulting to `@SpringBootTest` for everything makes a 500-test suite slow and brittle (one auto-config change breaks everything).

Related slices to know: `@DataJpaTest` (Hibernate + an in-memory DB + repositories), `@JdbcTest` (`DataSource` + `JdbcTemplate`), `@RestClientTest` (HTTP client + `MockRestServiceServer`), `@JsonTest` (Jackson alone).

---

## 8. `MockMvc` versus `TestRestTemplate`. What's the difference?

Both let you exercise a Spring MVC controller from a test, but they take different paths.

**`TestRestTemplate`** is a real HTTP client. The test requires a running embedded server (`@SpringBootTest(webEnvironment = RANDOM_PORT)`); the template sends actual HTTP requests over a socket. Pros: closer to production behavior — content negotiation, filters, encoding all run as in prod. Cons: slower, requires a free port, less expressive assertions.

**`MockMvc`** simulates the HTTP layer without opening a port. It feeds requests directly into the `DispatcherServlet` and inspects the result. Pros: fast, deterministic, no network involved; assertion helpers (`status().isOk()`, `jsonPath("$.field")`, `header().exists("Location")`) are nicer. Cons: doesn't exercise the real HTTP serializers/deserializers in the same way (e.g. some filter behavior is skipped), and request matching is slightly more permissive than real Tomcat.

**Decision rule:** use `MockMvc` for **controller-layer tests** (what the controller does with a request, what it returns) and `TestRestTemplate` for **end-to-end smoke tests** (does the whole stack handle a request). Real codebases have many of the first and few of the second.

---

## 9. The Spring Boot version policy: how do you handle "I need a newer Jackson than Boot ships"?

The wrong way: pin `<dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.18.3</version></dependency>` directly. Your override wins for `jackson-databind`, but every other Jackson artifact (`jackson-core`, `jackson-annotations`, `jackson-datatype-jsr310`) still takes the version the parent pinned. You've **split the Jackson family**, which surfaces at runtime as `NoSuchMethodError` from inconsistent version pairs.

The right way: override the version *property* the parent uses.

```xml
<properties>
    <jackson.version>2.18.3</jackson.version>
</properties>
```

The parent's `<dependencyManagement>` references this property for every Jackson artifact; overriding the property bumps the whole family at once. Read the Spring Boot release notes when you upgrade to confirm which property name to override — most are documented.

If the dependency has no version property in the parent (rarer), import the BOM and override there:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson</groupId>
            <artifactId>jackson-bom</artifactId>
            <version>2.18.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

And the rule of thumb: **override reluctantly**. The whole point of the BOM is that the Boot team tested the set together. Every override is a version you now own — when you bump Boot, you have to recheck whether the override is still needed.

---

## 10. The Boot startup log shows `No active profile set, falling back to 1 default profile: "default"`. What does that mean, and how would you change it?

Boot has a concept of an **active profile** — the answer to "which configuration variant are we running with?" If no profile is active, Boot uses one literally named `default`. That's what the log message says.

Practical consequences:

- `application-default.yml`, if you have one, *is* loaded.
- `application-dev.yml`, `application-prod.yml`, etc. are *not* — those only load when their name is active.
- `@Component @Profile("dev") public class X { }` — that bean is *not* registered.

A common beginner pitfall: write `application-dev.yml` with dev overrides, never activate the `dev` profile, run the app, observe that none of the dev overrides apply. The fix is to activate the profile:

- In `application.yml`: `spring.profiles.active: dev`
- On the command line: `--spring.profiles.active=dev`
- In the environment: `SPRING_PROFILES_ACTIVE=dev`

Or accept the "default" profile and rename your file to `application-default.yml`.

A related concept worth knowing: **profile groups**. If `prod` is conceptually `prod` + `monitoring` + `tls`, define a group:

```yaml
spring:
  profiles:
    group:
      production: [prod, monitoring, tls]
```

Then activating `production` activates the three underlying profiles together.

---

## 11. Production Actuator: which endpoints would you expose, which would you lock down, and why?

The minimum useful set for production is usually:

- **`/actuator/health`** — exposed, no auth. Load balancers need this. Use `show-details: when_authorized` so unauthenticated callers see only `{"status":"UP"}`.
- **`/actuator/info`** — exposed, no auth. Build version, git SHA, deploy time. Useful for "what's running on this pod?" Add `build-info` to the Spring Boot Maven plugin to populate it.
- **`/actuator/metrics`** and **`/actuator/prometheus`** — exposed to a monitoring system on an internal network. Reveals nothing secret on its own; useful for alerting.

Lock down — *never* expose to the public internet, even with auth, unless you've audited what's in them:

- **`/actuator/env`** — every environment property, including unresolved `${SECRET_KEY}` placeholders. Leaks every secret.
- **`/actuator/heapdump`** — a binary heap dump. Reveals every in-memory object: auth tokens, decrypted credentials, customer PII.
- **`/actuator/threaddump`** — current stack traces. Useful for ops, but leaks internal class names and method invocations.
- **`/actuator/configprops`** — every `@ConfigurationProperties` bean's current values. Same secret-leak risk as `/env`.
- **`/actuator/loggers`** with POST — anyone who can hit the POST endpoint can change log levels at runtime. DoS risk if not authenticated.
- **`/actuator/shutdown`** — turning it on lets anyone POST to gracefully shut down the JVM. Off by default; keep it off.

The defense-in-depth pattern: **put management on a separate port** (`management.server.port: 9090`), firewall it so only operations subnets reach it, and configure Spring Security on top of that for endpoints that mutate state. The application's user-facing port (8080) should serve only `/health` (or a health proxy you write).

Don't `include: "*"` in production — that's the configuration that ships secrets to the public Internet. Be explicit about the list.

---

## 12. `SpringApplication.run` returns a `ConfigurableApplicationContext`. What is it, and why do most tutorials ignore the return value?

`SpringApplication.run(App.class, args)` returns a fully-initialized `ConfigurableApplicationContext` — the same `ApplicationContext` from module 07, just wrapped in a richer interface that lets you refresh, close, register beans dynamically, and access lifecycle hooks. The `Configurable` prefix means "you can change me," which a bare `ApplicationContext` interface implies you cannot.

Why most tutorials drop the return value with a void-style call:

```java
public static void main(String[] args) {
    SpringApplication.run(App.class, args);    // return value discarded
}
```

For a web app, you don't need the return value: the embedded server holds the JVM alive on a non-daemon thread, lifecycle hooks fire, and Boot registers a JVM shutdown hook that closes the context cleanly. There's nothing else `main` should do.

For a non-web app (like the `hello-demo` in topic 02), grabbing the return value is useful:

```java
public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(App.class, args);
    HelloService service = context.getBean(HelloService.class);
    service.doWork();
    // when main exits, Boot's shutdown hook closes the context
}
```

You can also wrap it in `try-with-resources` for explicit close:

```java
try (ConfigurableApplicationContext context = SpringApplication.run(App.class, args)) {
    // ...
}
```

Boot's shutdown hook handles close in either case; the explicit try-with-resources just makes the lifecycle visible.

An advanced use: `SpringApplicationBuilder` lets you build a hierarchy of contexts:

```java
new SpringApplicationBuilder(ParentConfig.class)
    .child(ChildConfig.class)
    .web(WebApplicationType.NONE)
    .run(args);
```

Rare in application code; common in libraries that integrate two SpringApplications (Spring Cloud, some testing setups).

---

## 13. How does Boot's auto-configuration handle "I have a `DataSource` on my classpath but no `spring.datasource.url`"?

This is a useful question because it shows the layered conditionals at work.

Adding `spring-boot-starter-data-jpa` puts:
- The JPA API and Hibernate on the classpath.
- A `DataSource` *class* (`HikariDataSource`) on the classpath.

But Boot needs more than the class — it needs a URL, username, and (sometimes) credentials to construct a real `DataSource` bean. So `DataSourceAutoConfiguration` is gated on multiple conditions:

- `@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })` — the classes are present.
- `@ConditionalOnMissingBean({ DataSource.class, XADataSource.class })` — no user-defined one.

Then inside the autoconfig, two `@Configuration` inner classes branch:

- One for "embedded" mode — fires only when `EmbeddedDatabaseType.embeddedDatabaseConnection` finds an in-process DB driver (H2, HSQLDB, Derby). Auto-creates a `DataSource` pointing at the embedded database.
- One for "pooled" mode — fires when `spring.datasource.url` is set. Auto-creates a `HikariDataSource` configured against that URL.

With JPA on the classpath, no URL set, and no embedded driver, *neither* branch fires. You get the famous error:

```text
APPLICATION FAILED TO START

Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.

Reason: Failed to determine a suitable driver class
```

The error report names the autoconfig and tells you exactly what it was looking for. Three reasonable fixes depending on intent:

1. **You wanted JPA.** Set `spring.datasource.url`, `username`, `password` in `application.yml`. Then add the matching JDBC driver dependency.
2. **You wanted to develop without a real DB.** Add an embedded driver (`com.h2database:h2:runtime`). The embedded branch fires; you get an in-memory database.
3. **You don't want JPA at all.** Remove `spring-boot-starter-data-jpa`, or exclude the autoconfig: `@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })`.

The general lesson: when Boot's autoconfig "fails to do something obvious," read the failure message *and* the auto-configuration report (`--debug`). The report tells you which condition failed; the failure message tells you what to set.

---

## 14. Spring Boot's `RANDOM_PORT` test mode picks a free port. How does the test know which port to use?

The interesting part isn't the port allocation (the JVM is good at finding a free port); it's how the test discovers which port Boot picked.

When `@SpringBootTest(webEnvironment = RANDOM_PORT)` boots the context, Boot starts the embedded server on a free port and registers the chosen port number into the `Environment` as the property `local.server.port`. Two test-friendly hooks then make it available:

- **`@LocalServerPort int port`** on a test field — Spring's `@Value("${local.server.port}")` short-hand.
- **`TestRestTemplate`** — auto-configured by `@SpringBootTest` to use `http://localhost:${local.server.port}` as its base URL. Any relative URL you pass (`rest.getForEntity("/hello", ...)`) resolves against it.

So the test never sees the actual number unless it asks for it:

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class MyTest {
    @LocalServerPort int port;             // optional
    @Autowired TestRestTemplate rest;       // uses the port automatically

    @Test
    void test() {
        rest.getForEntity("/hello", String.class);     // hits the right port
    }
}
```

A related question: "what if my test needs to construct a URL outside `TestRestTemplate`?" Inject `@LocalServerPort` and build the URL by hand, or use the same `local.server.port` property in any other config.

The reason `RANDOM_PORT` exists at all: parallel test execution. If two test classes both used port 8080, the second would fail with "port already in use." Random ports make every test class self-contained.

---

## 15. Why does Boot ship Logback by default, and how would you switch to Log4j2?

Boot picks one logging implementation and defaults you into it so you don't have to. Logback is the choice because:

- It's actively maintained, fast, and has good Spring integration (the `<springProfile>` tag in `logback-spring.xml` is implemented by Boot's listener).
- It was authored by the same team that wrote SLF4J, so the API surface and the implementation are well-aligned.
- It supports JSON output via add-on encoders (`logstash-logback-encoder`), which most observability stacks expect.

To switch to Log4j2 (a real reason: better async performance under high request rates, or your company's stack standardizes on it):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-log4j2</artifactId>
</dependency>
```

Exclude `spring-boot-starter-logging` (which brings in Logback) from every starter, then add `spring-boot-starter-log4j2`. Boot's `LoggingApplicationListener` notices Log4j2 on the classpath and configures it instead of Logback. Logback's `logback-spring.xml` is replaced by `log4j2-spring.xml` (same `-spring.xml` suffix so profile-aware tags work).

The rest of your code doesn't change. SLF4J is the API; the implementation is swappable.

A related decision: don't have both implementations on the classpath. SLF4J would bind to whichever is found first, which is non-deterministic across machines and JVMs. Pick one and exclude the other completely.
