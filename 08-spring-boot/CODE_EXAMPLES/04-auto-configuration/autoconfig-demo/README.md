# autoconfig-demo

Demonstrates Spring Boot's `@ConditionalOnProperty` and `@ConditionalOnMissingBean` — the same mechanism every built-in auto-configuration uses.

## Run

```bash
mvn -q test                                                                # both tests pass
mvn -q spring-boot:run                                                     # prints plain greeting
mvn -q spring-boot:run -Dspring-boot.run.arguments=--greeter.style=fancy   # prints fancy greeting
```

## What to notice

- `GreeterConfig` declares two `Greeter` beans. Only one ever registers because of the conditionals.
- Without `greeter.style=fancy`, the fancy bean's `@ConditionalOnProperty` fails. Then `@ConditionalOnMissingBean` on the plain bean holds (no `Greeter` exists yet) and the plain bean registers.
- With `greeter.style=fancy`, the fancy bean registers first. By the time `@ConditionalOnMissingBean` is evaluated for the plain bean, a `Greeter` already exists, so the plain bean is skipped.
- Run with `--debug` to see Boot's own auto-config report alongside ours.
