# Common Mistakes — Spring Boot

Real bugs in Boot apps that compile and look reasonable. Each entry shows the bad pattern, the symptom you'd actually see, and the fix.

---

## Bootstrap and `@SpringBootApplication`

### 1. Bean lives outside the `@ComponentScan` reach

```java
// src/main/java/com/example/boot/App.java
package com.example.boot;

@SpringBootApplication
public class App { ... }
```

```java
// src/main/java/com/other/UserService.java
package com.other;

@Service
public class UserService { ... }
```

Booting the app: `NoSuchBeanDefinitionException: No qualifying bean of type 'com.other.UserService'`.

`@SpringBootApplication` includes `@ComponentScan`, which by default starts at the **package of the annotated class** (`com.example.boot`) and walks downward. `com.other` is neither under that package nor inside it.

**Fix:** move the bean to a sub-package of `App`'s package (`com.example.boot.user.UserService`), or expand the scan explicitly:

```java
@SpringBootApplication(scanBasePackages = { "com.example.boot", "com.other" })
public class App { ... }
```

Better: put `App` at the root of your company package so every other class is naturally below it.

---

### 2. Forgetting `<relativePath/>` on the parent

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.14</version>
</parent>
```

`mvn package` fails with `Could not find artifact ... in local`. Maven defaults `<relativePath>` to `../pom.xml`, so it looks for the parent in the directory above your project. The Boot parent lives in the Maven repository, not on your filesystem.

**Fix:** add the empty `<relativePath/>` element so Maven resolves the parent from the repository:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.14</version>
    <relativePath/>
</parent>
```

---

## Auto-configuration

### 3. Two `@Bean ObjectMapper` methods, no `@ConditionalOnMissingBean`

```java
@Configuration
public class MyJsonConfig {
    @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }
}

@Configuration
public class OtherJsonConfig {
    @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }
}
```

`NoUniqueBeanDefinitionException: No qualifying bean of type 'ObjectMapper' available: expected single matching bean but found 2`. Boot's auto-configured `ObjectMapper` *did* step aside (your bean exists, so `@ConditionalOnMissingBean` on the autoconfig held). But you defined the same type twice.

**Fix:** define `ObjectMapper` once. If you genuinely want two and want to inject by name, give them distinct method names and use `@Qualifier` at injection sites — but this is rare.

---

### 4. `@AutoConfiguration` in application code

```java
@AutoConfiguration                  // wrong: this is for library code
public class JacksonConfig { ... }
```

It compiles but doesn't run during your app's startup — `@AutoConfiguration` only fires when registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, which is a library convention.

**Fix:** use `@Configuration` in your own application code. `@AutoConfiguration` belongs in libraries that publish defaults for consumers.

---

## Properties and YAML

### 5. YAML indented with tabs

```yaml
spring:
	application:                     <-- tab here
		name: myapp                  <-- and here
```

Startup fails: `found character '\t' that cannot start any token`.

YAML requires spaces. The error message is confusing for beginners.

**Fix:** configure your editor to write spaces in `.yml` files. In IntelliJ: Settings -> Editor -> Code Style -> YAML -> Use tab character: off.

---

### 6. `@Value` without a default on a missing property

```java
@Value("${app.api-key}")
private String apiKey;
```

If `application.yml` doesn't define `app.api-key`, startup fails:

```text
java.lang.IllegalArgumentException: Could not resolve placeholder 'app.api-key' in value "${app.api-key}"
```

This *is* the correct behavior — silent fallback to `null` would surface as a `NullPointerException` somewhere downstream. But beginners often think "the placeholder didn't resolve" is a bug rather than a guard.

**Fix:** either define the property, or provide a default:

```java
@Value("${app.api-key:}")        // default to empty string
@Value("${app.api-key:#{null}}") // explicit null default
```

Prefer making the property required.

---

### 7. Boolean quoted as a string

```yaml
app:
  feature:
    enabled: "true"            <-- this is the string "true", not the boolean
```

```java
@ConfigurationProperties("app.feature")
public record FeatureProps(boolean enabled) { }
```

Binding throws: `Failed to convert String "true" to type boolean`. Or, worse, silently treats it as a custom converter input.

**Fix:** drop the quotes:

```yaml
app:
  feature:
    enabled: true
```

---

## `@ConfigurationProperties` and validation

### 8. `@Validated` without `spring-boot-starter-validation` on the classpath

```xml
<!-- pom.xml: only the core starter -->
<dependency>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
```

```java
@ConfigurationProperties("app.retry")
@Validated
public record RetryProperties(@Min(1) int maxAttempts, ...) { }
```

You set `max-attempts: 0` in YAML. App starts anyway, `maxAttempts` is `0`, and the retry logic runs zero times. No error.

The validation annotations are jakarta.validation; without an implementation (Hibernate Validator), `@Validated` is a no-op.

**Fix:** add the starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

### 9. `@Validated` on the outer record but not `@Valid` on the nested field

```java
@ConfigurationProperties("app")
@Validated
public record AppProperties(
    @NotBlank String name,
    Notification notification) {

    public record Notification(@NotBlank String email) { }
}
```

Set `name: ""` and binding fails (good). Set `notification.email: ""` and... binding succeeds. The `@NotBlank` on `email` was never checked.

**Fix:** add `@Valid` on the nested field so cascade validation fires:

```java
public record AppProperties(
    @NotBlank String name,
    @Valid Notification notification) { ... }
```

---

## Profiles

### 10. `spring.profiles.active` set inside a profile-specific file

```yaml
# application-prod.yml
spring:
  profiles:
    active: prod                   <-- no-op, file already loaded
```

By the time `application-prod.yml` is being parsed, `prod` is already active — that's why this file was loaded. Setting the property here does nothing useful and in older Boot versions logged a confusing warning.

**Fix:** set `spring.profiles.active` only in `application.yml`, on the command line (`--spring.profiles.active=prod`), or in the environment (`SPRING_PROFILES_ACTIVE=prod`).

---

### 11. Override-only config with no default

```yaml
# application.yml — empty for this key
```

```yaml
# application-prod.yml
app:
  api-host: https://api.example.com
```

Works in prod. In dev (no profile active), `@Value("${app.api-host}")` fails at startup because the placeholder doesn't resolve. Tests that don't set a profile also fail.

**Fix:** put the default in `application.yml`:

```yaml
# application.yml
app:
  api-host: https://default.example.com
```

Profile files supply *deltas*, not the only definitions.

---

## Packaging

### 12. Running the `.original` artifact

```bash
java -jar target/myapp-1.0.0.jar.original
# Error: Could not find or load main class JarLauncher
```

The `.original` is the plain Maven jar — no nested dependencies, no Boot launcher. Only the un-suffixed jar (`myapp-1.0.0.jar`) is executable.

**Fix:** run the un-suffixed jar. Delete `.original` files only if you're sure you don't publish your jar as a Maven dependency for other projects.

---

## Web

### 13. `@Controller` returning a string treated as a view name

```java
@Controller
public class HelloController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";    // expected as response body, but...
    }
}
```

`GET /hello` returns a 404 looking for a view called `Hello, World!`. With `@Controller` (not `@RestController`), the return value goes through view resolution.

**Fix:** use `@RestController` (which is `@Controller` + `@ResponseBody`), or annotate the method with `@ResponseBody`.

---

### 14. `@SpringBootTest` default `webEnvironment` + `TestRestTemplate`

```java
@SpringBootTest                                // default = MOCK -- no server starts
class HelloTest {
    @Autowired TestRestTemplate rest;          // injection fails
}
```

`No qualifying bean of type 'TestRestTemplate' available.` The auto-config that registers `TestRestTemplate` requires a real server to be running, which `MOCK` doesn't start.

**Fix:** specify `webEnvironment`:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloTest {
    @Autowired TestRestTemplate rest;
}
```

Or use `MockMvc` against the `DispatcherServlet` without starting a server.

---

## Logging

### 15. String concatenation in a `log.debug` call

```java
log.debug("user " + user.fullName() + " from " + user.country() + " did " + action);
```

The string concatenation runs *every time the method is called*, even when DEBUG is off. `user.fullName()` and `user.country()` are also invoked unnecessarily.

**Fix:** parameterized form:

```java
log.debug("user {} from {} did {}", user.fullName(), user.country(), action);
```

SLF4J evaluates the arguments only when the level is enabled. For very expensive arguments, guard explicitly:

```java
if (log.isDebugEnabled()) {
    log.debug("...", expensiveCall());
}
```

---

### 16. `logback.xml` when you needed `<springProfile>`

```xml
<!-- logback.xml -->
<springProfile name="prod">
    <root level="WARN"/>
</springProfile>
```

Boot logs warnings about an unknown `<springProfile>` tag and your profile-aware config doesn't apply. `logback.xml` is loaded before Spring is initialized, so Boot's profile-aware extensions aren't yet available.

**Fix:** rename the file to `logback-spring.xml`. Boot loads it after Spring initializes, and `<springProfile>` and `<springProperty>` tags work.

---

## Actuator

### 17. Exposing `*` in production

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

`/actuator/env` reveals every environment property, including database passwords and API keys that arrived as `${API_KEY}` placeholders. `/actuator/heapdump` lets anyone download a full heap dump — every in-memory object, including auth tokens.

**Fix:** be explicit. The minimum useful set in production is usually:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
```

Move Actuator to a separate port (`management.server.port`) and firewall it.

---

### 18. Health indicator throwing instead of returning DOWN

```java
@Component
public class FeatureFlagHealth implements HealthIndicator {
    @Override
    public Health health() {
        client.ping();              // throws IOException -- becomes a 500
        return Health.up().build();
    }
}
```

`/actuator/health` now returns HTTP 500 instead of `{"status":"DOWN"}`. Your load balancer can't even read the response to make a removal decision.

**Fix:** catch and return DOWN:

```java
@Override
public Health health() {
    try {
        client.ping();
        return Health.up().build();
    } catch (Exception e) {
        return Health.down().withException(e).build();
    }
}
```

---

## Testing

### 19. Stale context cached across tests with mutated state

```java
@SpringBootTest class TestA {
    @Autowired SomeBean bean;
    @Test void a() { bean.setFlag(true); /* ... */ }    // mutation
}

@SpringBootTest class TestB {
    @Autowired SomeBean bean;
    @Test void b() { assertThat(bean.flag()).isFalse(); }   // fails -- saw TestA's mutation
}
```

`@SpringBootTest` caches contexts by configuration. Both classes share one context. `TestA` mutated state; `TestB` saw the mutation.

**Fix:** keep beans stateless or use `@DirtiesContext` on the offending class (slow — use sparingly):

```java
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class TestA { ... }
```

Better: redesign so beans don't carry per-test state.

---

### 20. Log level leak across test contexts

```java
@SpringBootTest @TestPropertySource(properties = "logging.level.com.example=DEBUG")
class DebugLevelTest { ... }      // raises level

@SpringBootTest
class DefaultLevelTest {
    @Test void infoOnly() { /* fails when run after DebugLevelTest */ }
}
```

The Logback `LoggerContext` is JVM-global. A test that raises a level affects every subsequent test in the same JVM.

**Fix:** pin the level explicitly in every test that asserts log output:

```java
@TestPropertySource(properties = "logging.level.com.example=INFO")
class DefaultLevelTest { ... }
```

---

### 21. `@MockBean` instead of `@MockitoBean`

```java
@WebMvcTest(HelloController.class)
class HelloTest {
    @MockBean HelloService service;        // deprecated since Boot 3.4
}
```

The IDE underlines `@MockBean` with a deprecation warning. The test still works. New code should use the replacement.

**Fix:** rename the import:

```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(HelloController.class)
class HelloTest {
    @MockitoBean HelloService service;
}
```

---

## Dependencies and the BOM

### 22. Pinning a transitive when the parent already manages it

```xml
<!-- the parent already pins Jackson at 2.18.x -->

<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.18.3</version>
</dependency>
```

Your override wins for `jackson-databind`, but every other Jackson artifact (`jackson-core`, `jackson-annotations`, `jackson-datatype-jsr310`) still takes the parent's version. You've split the Jackson family — exactly the scenario starters exist to prevent. `NoSuchMethodError` ensues at runtime.

**Fix:** override the version property, which the parent uses to set every Jackson artifact together:

```xml
<properties>
    <jackson.version>2.18.3</jackson.version>
</properties>
```

Or, if there's no version property for the dependency you want to bump, import the BOM and override there.

---

### 23. `<scope>test</scope>` on the wrong starter

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
    <scope>test</scope>                  <-- wrong starter at test scope
</dependency>
```

Compilation succeeds (your tests can see Spring). Runtime fails: `ClassNotFoundException: org.springframework.context.ApplicationContext`. The core starter is missing at the `compile`/`runtime` scope.

**Fix:** keep the core starter at compile scope (the default) and only put `spring-boot-starter-test` at test scope:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```
