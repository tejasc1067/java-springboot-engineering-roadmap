# actuator-demo

`spring-boot-starter-actuator` + a config block exposing `/actuator/health` and `/actuator/info`. A test asserts the health endpoint reports UP over real HTTP.

## Run

```bash
mvn -q test                  # 1 test passes
mvn -q spring-boot:run       # browse:
                             #   http://localhost:8080/actuator
                             #   http://localhost:8080/actuator/health
```

## What to notice

- Only `health` and `info` are exposed in `application.yml`. The default would expose only `health` and the discovery endpoint. Everything else stays internal until you opt in.
- `show-details: always` is on here so the response includes per-component statuses. In production prefer `when_authorized` paired with a security setup so unauthenticated callers see only `{"status":"UP"}`.
- The `ping` and `diskSpace` indicators come from Actuator's auto-configuration. Adding `spring-boot-starter-data-jpa` (or any other starter that registers a `HealthIndicator`) would add more.
