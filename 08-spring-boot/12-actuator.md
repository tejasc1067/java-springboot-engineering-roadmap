# Actuator — Health, Info, and Metrics

A deployed Spring Boot service needs a small set of endpoints that operations teams scrape: "is this instance healthy?", "what version is running?", "what's the current request rate?". Writing those by hand is exactly the kind of boilerplate Spring Boot exists to remove. Add `spring-boot-starter-actuator` and you get them for free, with sane defaults and configurable exposure.

By the end of this topic you can expose `/actuator/health` and `/actuator/info`, decide what else to expose, and explain why some endpoints must stay locked down in production.

---

## The problem this solves

A load balancer needs to know "should I send traffic to this pod?" before it ships requests. A monitoring system needs to know "what's the current heap size and request latency?" continuously. Both want **machine-readable, well-known URLs** that work the same way on every service in your fleet.

Writing those endpoints by hand means writing the same `/health` controller in every service — checking the database, checking dependencies, returning a status code. Multiply by 50 services and the boilerplate adds up. Actuator is Spring Boot's batteries-included answer: drop in a starter, set a property or two, and `/actuator/health` returns the right thing.

---

## Adding Actuator

One dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

That brings in `spring-boot-actuator` and `spring-boot-actuator-autoconfigure`. Auto-configuration adds endpoints to the existing `DispatcherServlet`, registers health indicators based on what's on the classpath (a `DataSource` -> a database health indicator; Kafka -> a Kafka one), and wires the `/actuator` path prefix.

Hit `http://localhost:8080/actuator` and you'll see a JSON listing of available endpoints — by default just `/actuator/health` and `/actuator` itself.

---

## The default endpoints

Boot ships about two dozen endpoints. Two are **exposed over HTTP by default**: `health` and the discovery endpoint at `/actuator`. The rest are *enabled* (the data is collected) but not exposed.

| Endpoint               | What it shows                                                 | Default web exposure |
|------------------------|---------------------------------------------------------------|----------------------|
| `/actuator/health`     | Aggregate health of the app and its dependencies              | Yes                  |
| `/actuator/info`       | Build/git info — empty unless you configure contributors      | No                   |
| `/actuator/metrics`    | Per-meter snapshots: `jvm.memory.used`, `http.server.requests`| No                   |
| `/actuator/env`        | All environment properties (resolves placeholders)            | No                   |
| `/actuator/configprops`| Every `@ConfigurationProperties` bean and its current values  | No                   |
| `/actuator/beans`      | Full list of beans in the context                             | No                   |
| `/actuator/mappings`   | Every `@RequestMapping` Spring registered                     | No                   |
| `/actuator/loggers`    | Current log levels per logger; supports POST to change at runtime | No               |
| `/actuator/threaddump` | A live thread dump                                            | No                   |
| `/actuator/heapdump`   | A binary heap dump (download)                                 | No                   |

To expose more, use the `management.endpoints.web.exposure` properties:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, mappings
        # or include: "*"  -- everything (rarely a good idea in production)
        exclude: heapdump      # never expose this on a public network
```

The default of "everything off except health" is correct for production. Open up `info` and `metrics` for monitoring; consider `loggers` for runtime log-level tweaks; keep `heapdump`, `env`, and `threaddump` strictly internal.

---

## Health checks

`/actuator/health` returns a JSON document like:

```json
{ "status": "UP" }
```

The `status` is the aggregate of every registered `HealthIndicator`. Boot auto-registers indicators for things it finds on the classpath:

- `DiskSpaceHealthIndicator` — disk has enough free space.
- `DataSourceHealthIndicator` — registered when a `DataSource` bean exists. Runs `SELECT 1` (or the equivalent for that database).
- `RedisHealthIndicator`, `MongoHealthIndicator`, `KafkaHealthIndicator` — added by the corresponding starter.
- A `ping` indicator that always returns UP.

To see per-indicator detail, expose more in the response:

```yaml
management:
  endpoint:
    health:
      show-details: when_authorized      # never | when_authorized | always
      show-components: when_authorized
```

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL", "validationQuery": "SELECT 1" } },
    "diskSpace": { "status": "UP", "details": { "total": 500000000000, "free": 250000000000 } },
    "ping": { "status": "UP" }
  }
}
```

`show-details: always` is fine in development; in production prefer `when_authorized` (with a security setup that authorizes operators only) so you don't leak infrastructure details.

### Custom health indicators

Write a `@Component` implementing `HealthIndicator`:

```java
@Component
public class FeatureFlagHealth implements HealthIndicator {

    @Override
    public Health health() {
        boolean reachable = pingTheFeatureFlagService();
        if (reachable) return Health.up().build();
        return Health.down().withDetail("reason", "feature flag service unreachable").build();
    }
}
```

Boot picks it up by bean type, includes it in the aggregate. The bean's simple name (minus the `HealthIndicator` suffix) becomes the component name in the JSON — here `"featureFlag"`.

### Health groups for the load balancer

Most teams want two health URLs:

- `/actuator/health/liveness` — "is the JVM alive?" (used by orchestrators to restart crashed containers).
- `/actuator/health/readiness` — "is the app ready for traffic?" (used by load balancers to add/remove from the pool).

Boot 2.3+ auto-configures both as health groups in Kubernetes-style deployments. Override which indicators belong to each group:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: db, redis        # don't take traffic until DB and Redis are up
        liveness:
          include: ping             # liveness is just "JVM responding"
```

---

## `/actuator/info`

Empty by default. Add a contributor to populate it. The most common: build info, generated by the Maven plugin:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>build-info</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

That writes `target/classes/META-INF/build-info.properties` at build time. `/actuator/info` then returns:

```json
{
  "build": {
    "artifact": "myapp",
    "name": "myapp",
    "time": "2026-06-01T10:23:11.812Z",
    "version": "1.0.0",
    "group": "com.example"
  }
}
```

Useful for "what version is running on this pod?" without grepping container labels.

You can also write a custom `InfoContributor` to expose anything — the active feature flags, the build SHA, the deploy timestamp.

---

## `/actuator/metrics`

Returns a list of available metric names plus a way to drill down:

```text
GET /actuator/metrics
{ "names": ["jvm.memory.used", "http.server.requests", "jvm.gc.pause", ...] }

GET /actuator/metrics/http.server.requests
{
  "name": "http.server.requests",
  "measurements": [ { "statistic": "COUNT", "value": 1234.0 }, ... ],
  "availableTags": [ { "tag": "uri", "values": ["/hello", "/health"] }, ... ]
}
```

In real deployments you wouldn't read these by hand — a Prometheus, Datadog, or New Relic agent scrapes them. Boot ships `micrometer-core`; add `micrometer-registry-prometheus` to also expose `/actuator/prometheus` in the Prometheus text format. That hook-up is where most observability stacks plug in.

---

## Security — what to expose and what not to

Three rules of thumb:

1. **Never expose `heapdump`, `threaddump`, or `env` on a network anyone but operators can reach.** They leak everything: passwords in environment variables, internal IPs, JWT secrets, query strings. If you must expose them, lock them behind authentication.
2. **`info` and `metrics` are usually fine to expose** to monitoring systems on a separate network. They don't reveal secrets — but check what custom `InfoContributor` beans you've added.
3. **Put Actuator on a different port** for defense in depth:
    ```yaml
    management:
      server:
        port: 9090       # actuator on a separate port from the app on 8080
    ```
   Now `/actuator/*` is reachable on `:9090` only — easy to firewall.

---

## Common pitfalls

- **Exposing `*` in production.** `management.endpoints.web.exposure.include: "*"` is fine in dev. In production it leaks `/env` and `/heapdump`. Be explicit about the list.
- **Forgetting to enable build info and wondering why `/info` is empty.** Add the `build-info` goal to the Spring Boot Maven plugin and rebuild.
- **A health indicator throwing instead of returning DOWN.** Indicators are called frequently; an unhandled exception bubbles up and breaks `/health` entirely. Catch in the indicator and return `Health.down().withException(e)` so the aggregate stays consistent.
- **Custom indicators that are too slow.** `/health` is hit every few seconds by the load balancer. An indicator that pings five external services and waits for all of them turns "is this instance up?" into a one-second-latency endpoint. Either cache the result for a few seconds or use a separate health *group* for slow checks.
- **Using `health.show-details: always` in production.** The default is `never`. `when_authorized` is the production-safe middle. `always` reveals the internal component statuses to anyone who can hit the URL.

---

## Try this yourself

1. Run `mvn -q spring-boot:run` against `actuator-demo`. Hit `http://localhost:8080/actuator` in a browser to see what's exposed.
2. Hit `http://localhost:8080/actuator/health`. Get `{"status":"UP"}`.
3. Edit `application.yml` to set `show-details: always`. Restart. Re-hit `/actuator/health` — now you see the components.
4. Add `metrics` to the exposure include list. Restart. Hit `/actuator/metrics`. Pick a meter and drill down: `/actuator/metrics/jvm.memory.used`.

---

## Self-check

1. Out of the box, which Actuator endpoints are exposed over HTTP, and what does it take to expose the rest?
2. You add `spring-boot-starter-data-jpa` and a `DataSource` to your app. `/actuator/health` now reports DOWN even though the app starts. Where is the additional indicator coming from, and how would you investigate the failure?
3. Why is it dangerous to expose `/actuator/env` on a public-facing port even if you've authenticated the request?

---

## Code examples

In reading order:

- `actuator-demo/pom.xml` — adds `spring-boot-starter-actuator` to the standard web + test starters.
- `.../App.java` — `@SpringBootApplication`.
- `src/main/resources/application.yml` — exposes `health` and `info`; sets `show-details: always` so the test can see the components.
- `src/test/java/com/example/HealthEndpointTest.java` — `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`; calls `/actuator/health` and asserts `UP`.

Run with:

```bash
mvn -q test                  # asserts /actuator/health returns 200 + status UP
mvn -q spring-boot:run       # browse http://localhost:8080/actuator
```
