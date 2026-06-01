# configuration-properties-demo

Type-safe binding of a nested config block to a Java record, with `@Validated` rejecting bad values at startup.

## Run

```bash
mvn -q test                  # 5 tests pass
mvn -q spring-boot:run       # prints the bound retry policy
```

## What to notice

- `RetryProperties` is a `record`, not a class. Boot 3.x uses constructor binding for records automatically.
- `spring-boot-starter-validation` is what makes `@Min`, `@Max`, `@NotBlank` actually fire. Remove that dependency and the constraints become silent no-ops.
- `@ConfigurationPropertiesScan` on `App` lets Boot find `RetryProperties` without an explicit `@EnableConfigurationProperties` annotation.
- `InvalidRetryPropertiesTest` uses `ApplicationContextRunner` to assert that bad config causes startup to fail. This is the canonical pattern for testing validation rules.
- To see the same failure in `spring-boot:run`, change `max-attempts: 3` to `max-attempts: 0` in `application.yml`.
