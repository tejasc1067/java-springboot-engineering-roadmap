# actuator-demo

`spring-boot-starter-actuator` + a config block exposing `/actuator/health`, `/actuator/info` and `/actuator/prometheus`. One test asserts the health endpoint reports UP over real HTTP; another increments a custom Micrometer counter and finds it in the Prometheus scrape.

## Run

```bash
mvn -q test                  # 2 tests pass
mvn -q spring-boot:run       # browse:
                             #   http://localhost:8080/actuator
                             #   http://localhost:8080/actuator/health
                             #   http://localhost:8080/hello  (then)
                             #   http://localhost:8080/actuator/prometheus
```

## What to notice

- `greetings.sent` (Micrometer name) is exported as `greetings_sent_total` (Prometheus name).
- `PrometheusEndpointTest` needs `@AutoConfigureObservability`: Boot disables metrics export in tests, so without it the endpoint is a 404.
- Only `health`, `info` and `prometheus` are exposed in `application.yml`. The default would expose only `health` and the discovery endpoint. Everything else stays internal until you opt in.
- `show-details: always` is on here so the response includes per-component statuses. In production prefer `when_authorized` paired with a security setup so unauthenticated callers see only `{"status":"UP"}`.
- The `ping` and `diskSpace` indicators come from Actuator's auto-configuration. Adding `spring-boot-starter-data-jpa` (or any other starter that registers a `HealthIndicator`) would add more.
