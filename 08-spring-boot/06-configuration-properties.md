# `@ConfigurationProperties` — Type-Safe Binding

Topic 05 used `@Value("${app.message}")` to read one property at a time. That works for two or three values, but the moment your config grows nested — retry policies, connection pools, CORS allow-lists, a section per integration — `@Value` becomes a long list of constructor parameters and no compile-time check that you typed the property name right. `@ConfigurationProperties` binds a whole block of properties to a strongly-typed Java object, with validation, IDE auto-complete, and a single name to refactor.

By the end of this topic you can declare a `@ConfigurationProperties` record, bind it to nested YAML, and have invalid values rejected at application startup rather than at runtime.

---

## The problem this solves

A real config block — a retry policy with a nested notification target:

```yaml
app:
  retry:
    max-attempts: 3
    backoff: 500ms
    notification:
      email: ops@example.com
      enabled: true
```

With `@Value` you'd inject those four values one at a time:

```java
@Component
public class RetryService {
    public RetryService(
            @Value("${app.retry.max-attempts}") int maxAttempts,
            @Value("${app.retry.backoff}") Duration backoff,
            @Value("${app.retry.notification.email}") String email,
            @Value("${app.retry.notification.enabled}") boolean enabled) { ... }
}
```

Four parameters now; eight if the config grows another sub-block. Five typo opportunities (`max_attempts` instead of `max-attempts` is silent), no validation that `maxAttempts > 0`, and the property name is duplicated in every place you inject it. `@ConfigurationProperties` collapses all four into one object you can pass around.

---

## How it works: the record + the annotation

The grown-up version, in three pieces:

```java
@ConfigurationProperties(prefix = "app.retry")
@Validated
public record RetryProperties(
        @Min(1) @Max(10) int maxAttempts,
        @NotNull Duration backoff,
        @NotNull @Valid Notification notification) {

    public record Notification(
            @NotBlank String email,
            boolean enabled) { }
}
```

Three annotations to understand:

- **`@ConfigurationProperties(prefix = "app.retry")`** binds every property under `app.retry.*` into this record. `max-attempts` -> `maxAttempts` is automatic (relaxed binding from topic 05). `notification.email` recurses into the nested `Notification` record.
- **`@Validated`** turns on jakarta.validation. Without it, the `@Min`/`@Max`/`@NotBlank` annotations are ignored.
- **`@Valid`** on the nested record field is what makes `@Validated` cascade into `Notification`. Without it, `email` and `enabled` are bound but never validated.

For records, Boot uses constructor binding automatically — no `@ConstructorBinding` annotation needed in Spring Boot 3.x. Each parameter name (after relaxed binding) maps to a property key.

To register the record as a bean, two options:

```java
// Option A: scan annotation on the App class (most common)
@SpringBootApplication
@ConfigurationPropertiesScan
public class App { ... }

// Option B: explicit, when you don't want a scan
@SpringBootApplication
@EnableConfigurationProperties(RetryProperties.class)
public class App { ... }
```

Once registered, `RetryProperties` is a regular bean — inject it like any other:

```java
@Component
public class RetryService {

    private final RetryProperties config;

    public RetryService(RetryProperties config) {
        this.config = config;
    }

    public void retry(Runnable task) {
        for (int i = 0; i < config.maxAttempts(); i++) {
            // ...
        }
    }
}
```

One constructor parameter; one source of truth for the property names.

---

## `Duration`, `DataSize`, and other typed bindings

Boot binds many JDK and Spring types intelligently:

```yaml
app:
  retry:
    backoff: 500ms        # binds to Duration
  cache:
    max-size: 10MB        # binds to DataSize
  startup:
    deadline: PT5S        # ISO-8601 duration also works
```

| Java type        | YAML examples Boot accepts                              |
|------------------|---------------------------------------------------------|
| `Duration`       | `500ms`, `5s`, `2m`, `1h`, `1d`, `PT5S` (ISO-8601)      |
| `DataSize`       | `10B`, `4KB`, `2MB`, `1GB`                              |
| `Period`         | `1d`, `2w`, `3m`, `1y`, `P1Y2M`                         |
| `LocalDate`      | `2026-06-01`                                            |
| `URI`/`URL`      | `https://example.com`                                   |
| Enums            | matched case-insensitively against `name()`             |
| Lists            | YAML `- item1\n- item2` or `[item1, item2]`             |
| Maps             | YAML `key: value` blocks                                |

This is "type-safe binding" doing real work — your record gets a real `Duration`, not a `String` you have to parse yourself.

---

## Validating at startup

The validation annotations are jakarta.validation (`jakarta.validation.constraints.*`) — `@Min`, `@Max`, `@NotNull`, `@NotBlank`, `@Pattern`, `@Email`, `@Size`. To make them fire on a `@ConfigurationProperties`:

1. Add `spring-boot-starter-validation` to your `pom.xml`. That brings in Hibernate Validator (the jakarta.validation reference implementation).
2. Add `@Validated` on the record (or class).
3. Add `@Valid` on every nested record field that itself has constraint annotations on its fields.

When the user supplies a bad value:

```yaml
app:
  retry:
    max-attempts: 0     # violates @Min(1)
    backoff: 500ms
    notification:
      email: ""         # violates @NotBlank
      enabled: true
```

Boot logs a clear startup failure and refuses to start:

```text
Failed to bind properties under 'app.retry' to com.example.RetryProperties:

    Property: app.retry.maxAttempts
    Value: "0"
    Origin: class path resource [application.yml] - 4:18
    Reason: must be greater than or equal to 1

    Property: app.retry.notification.email
    Value: ""
    Origin: class path resource [application.yml] - 8:14
    Reason: must not be blank

Action:

Update your application's configuration
```

Failing fast at startup is the entire point. A misconfigured retry policy that allows zero attempts is a production incident waiting to happen — much better to refuse to boot than to run with a silently-wrong value.

---

## `@Value` vs `@ConfigurationProperties` — when to use which

| Need                                              | Reach for                  |
|---------------------------------------------------|----------------------------|
| One or two simple values                          | `@Value`                   |
| Three or more related values                      | `@ConfigurationProperties` |
| Nested structure                                  | `@ConfigurationProperties` |
| Validation at startup                             | `@ConfigurationProperties` |
| The same config block injected into multiple beans| `@ConfigurationProperties` |
| Dynamic key lookup (`getProperty(name)`)          | inject `Environment`       |

`@ConfigurationProperties` is almost always the right answer once a config block has structure. `@Value` is fine for the occasional single value, but reach for the typed record by default.

---

## Common pitfalls

- **Forgetting `spring-boot-starter-validation` on the classpath.** `@Validated` becomes a no-op silently — no validator, no error, the bad value is bound as-is. If `@Min(1)` doesn't seem to fire, check the pom.
- **Putting `@Validated` without `@Valid` on nested fields.** Top-level fields are validated; nested record fields are bound but never checked. Add `@Valid` next to the nested type.
- **Using `@Component` *and* `@EnableConfigurationProperties(MyProps.class)`.** Either works; both creates two beans of the same type and Spring throws `NoUniqueBeanDefinitionException`. Pick one mechanism — for records, `@ConfigurationPropertiesScan` on `App` is cleanest.
- **Mismatched property names.** `app.retry.maxAttempts: 3` in YAML matches `int maxAttempts`. `app.retry.max_attempts: 3` also matches (relaxed binding handles underscores). But `app.retry.maxattempts: 3` (no separator) does *not* match — Boot can't tell where the word break is. Stick to kebab-case in YAML: `max-attempts: 3`.
- **Defaulting a primitive to its zero value to "make it optional."** `int maxAttempts` with no property in YAML binds to `0` silently. If the field is genuinely optional, use a wrapper type (`Integer`, `@DefaultValue("3") int`), or require it with `@NotNull` on a wrapper. Don't rely on zero meaning "missing."
- **Mutating a bound record.** Records are immutable, which is the right default — your config object should not change after startup. If you reach for a class to allow mutation, ask why. Live-reloading config is a different feature (Spring Cloud Config) and not what `@ConfigurationProperties` is for.

---

## Try this yourself

1. Run `mvn -q test` against `configuration-properties-demo`. Both tests pass — one boots the context with valid config, one (using `ApplicationContextRunner`) asserts the context fails with bad config.
2. Edit `src/main/resources/application.yml` to `max-attempts: 0`. Run `mvn -q spring-boot:run`. Read the startup failure — Boot's report shows the exact property, file, line, and constraint that was violated.
3. Add a new nested record `Throttle(@Min(1) int qps, Duration window)` inside `RetryProperties` and a matching block in YAML. Run the test — observe that you didn't have to touch any code that *uses* the properties, only the schema.

---

## Self-check

1. You have a config block with five related values and one nested sub-block. What's the cost of using five `@Value` constructor parameters instead of one `@ConfigurationProperties` record?
2. You add `@Validated` and `@Min(1)` to a `@ConfigurationProperties`. The bad value still passes. Name two likely reasons.
3. Boot binds `app.retry.max-attempts: 500ms` and your `maxAttempts` field is an `int`. What happens, when, and how do you get a useful error message?

---

## Code examples

In reading order:

- `configuration-properties-demo/pom.xml` — adds `spring-boot-starter-validation` to the usual two starters.
- `.../RetryProperties.java` — record with `@ConfigurationProperties`, `@Validated`, nested record, full constraint annotations.
- `.../RetryService.java` — a `@Component` that depends on `RetryProperties` (proof you can inject the record).
- `.../App.java` — `@SpringBootApplication` + `@ConfigurationPropertiesScan`.
- `src/main/resources/application.yml` — valid retry config.
- `src/test/java/com/example/ValidRetryPropertiesTest.java` — `@SpringBootTest` asserts the values bound from YAML.
- `.../InvalidRetryPropertiesTest.java` — `ApplicationContextRunner` provides bad values and asserts the context fails to start.

Run with:

```bash
mvn -q test
mvn -q spring-boot:run
```
