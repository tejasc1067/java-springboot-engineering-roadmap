# properties-demo

`@Value` reads a property; `application.yml` supplies the default; overrides come from CLI args, env vars, or `@TestPropertySource` (in tests).

## Run

```bash
mvn -q test                                                                # both tests pass
mvn -q spring-boot:run                                                     # prints "Default from yml"
mvn -q spring-boot:run -Dspring-boot.run.arguments=--app.message="X"       # prints "X"
```

## What to notice

- `application.yml` is loaded by Boot without any `@PropertySource` annotation.
- `DefaultMessageTest` reads the YAML value as-is.
- `OverrideMessageTest` uses `@TestPropertySource` to inject a higher-precedence source; the YAML value is shadowed.
- The same shadowing happens in production when you pass `--app.message=...` on the command line.
