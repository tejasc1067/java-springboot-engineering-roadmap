# profiles-demo

Three property files (`application.yml`, `application-dev.yml`, `application-prod.yml`), one bean, different greeting per active profile.

## Run

```bash
mvn -q test                                                                       # 3 tests pass
mvn -q spring-boot:run                                                            # base greeting (no profile)
mvn -q spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev
mvn -q spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=prod
```

## What to notice

- The base `application.yml` defines `app.greeting`. The two profile-specific files override only that key.
- No `@PropertySource`, no `@Profile` annotations — just file naming.
- `@ActiveProfiles` in tests behaves the same way `--spring.profiles.active` does at runtime.
- The startup log line `Active profiles: [dev]` is your confirmation Boot saw the activation.
