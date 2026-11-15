# Logging — Logback Defaults and How to Tune Them

Since topic 02 every Boot app has been logging — that's where the Spring banner and the `INFO ... Started App in X seconds` line come from. None of that needed configuration. This topic explains the logging stack Boot installs by default, how to change log levels per package, how to write to a file, and how to override the format.

By the end of this topic you can set `logging.level.com.yourpackage=DEBUG`, see your `log.debug(...)` lines appear, and switch to a file output in a profile-specific way.

---

## The problem this solves

Plain Spring leaves logging up to you. The minimum setup:

1. Add SLF4J API.
2. Pick an implementation (Logback, Log4j2, JUL — pick exactly one).
3. Write `logback.xml` with a `ConsoleAppender`, a pattern layout, root logger level, and per-package overrides.
4. Bridge other logging APIs (commons-logging, Log4j 1.x) so transitive deps don't end up writing to a different log.

Each project does the same setup. Spring Boot bundles it as `spring-boot-starter-logging`, included transitively in *every* starter. You get:

- **SLF4J API** for code to call.
- **Logback Classic** as the implementation.
- **Sensible defaults** — INFO root level, color-coded console output, application name in the log prefix.
- **Bridges** so Log4j and JUL calls also end up in Logback.

You start writing `log.info(...)` and lines appear. To change behavior, you set properties — no XML required for the everyday case.

---

## Getting a logger

The same SLF4J pattern you'd use anywhere:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingService {

    private static final Logger log = LoggerFactory.getLogger(LoggingService.class);

    public void doWork() {
        log.trace("very detailed trace info");
        log.debug("useful for debugging");
        log.info("the normal operational line");
        log.warn("something is off but the app continues");
        log.error("something failed", exception);
    }
}
```

Five levels, lowest to highest: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`. The logger only emits lines at or above its configured level. Default root level is `INFO`, so `trace` and `debug` are silent until you bump the level.

Parameterized messages avoid string concatenation cost when the level is disabled:

```java
log.debug("user {} attempted login from {}", username, ip);   // good
log.debug("user " + username + " attempted login from " + ip); // bad - concatenation runs even when DEBUG is off
```

---

## Setting log levels

In `application.yml`:

```yaml
logging:
  level:
    root: INFO                            # default for everything
    com.example: DEBUG                    # your package, verbose
    com.example.security: WARN            # but security stays quieter
    org.springframework.web: DEBUG        # see Spring MVC's internal log
```

The matching is by package prefix. `com.example.something.deeper` inherits `com.example`'s level unless overridden specifically.

Three command-line shortcuts:

```bash
java -jar app.jar --debug         # enables DEBUG on Boot's core packages + autoconfig report
java -jar app.jar --trace         # enables TRACE on Boot's core packages
java -jar app.jar --logging.level.com.example=DEBUG    # any package, any level
```

`--debug` is the one you reach for first when something doesn't work — it also prints the auto-configuration report from topic 04.

---

## What the default log line looks like

```text
2026-06-01T10:23:11.812+05:30  INFO 12345 --- [myapp] [           main] com.example.LoggingService              : the normal operational line
```

Six fields:

| Field                       | Source                                              |
|-----------------------------|-----------------------------------------------------|
| `2026-06-01T10:23:11.812..` | timestamp, ISO-8601 with local offset               |
| `INFO`                      | log level                                           |
| `12345`                     | process ID                                          |
| `---`                       | a separator (Boot's convention)                     |
| `[myapp]`                   | application name from `spring.application.name`     |
| `[           main]`         | thread name (right-padded to 15 chars)              |
| `com.example.LoggingService`| logger name (the class) — truncated for column fit  |
| `:`                         | separator                                           |
| `the normal operational line`| your message                                       |

In a real terminal the level is color-coded. The format is set by `logging.pattern.console` (and `.file` for files); the defaults are intentional and read well at scale — don't customize them unless you have a specific reason.

---

## Writing to a file

Console-only is fine for development. Production usually wants a file too:

```yaml
logging:
  file:
    name: logs/myapp.log         # explicit file path
  pattern:
    file: "%d %-5level [%thread] %logger - %msg%n"
```

Or just a directory; Boot picks the filename:

```yaml
logging:
  file:
    path: /var/log/myapp         # writes /var/log/myapp/spring.log
```

Both write to the file *and* keep writing to the console. To suppress console, you go XML — see "Custom Logback configuration" below.

Boot ships sensible rotation defaults: 10MB per file, 7 days retention. Override with `logging.logback.rollingpolicy.*` properties.

---

## Profile-specific log levels

Combine topic 07 with logging properties:

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.example: INFO
```

```yaml
# application-dev.yml
logging:
  level:
    com.example: DEBUG          # noisier locally
    org.springframework.web: DEBUG
```

```yaml
# application-prod.yml
logging:
  level:
    root: WARN                  # quieter in production
    com.example: INFO
```

Activate the profile and the levels apply. This is how most teams handle "DEBUG in dev, less in prod" without two `logback.xml` files.

---

## Custom Logback configuration

When property-based config isn't enough — multiple appenders, JSON output, custom filters — drop a `logback-spring.xml` in `src/main/resources/`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

    <springProfile name="!prod">
        <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

The filename is `logback-spring.xml` (not `logback.xml`) so Boot's profile-aware `<springProfile>` tag works. JSON-encoded logs require the `logstash-logback-encoder` dependency; pick it up later when you wire structured logging into a real log aggregator.

This is the escape hatch. 95% of projects don't need it; the property-based config covers them.

## Switching to Log4j2

Your code only ever talks to SLF4J (`LoggerFactory.getLogger(...)`), so swapping Logback for Log4j2 is a pom change, not a code change. Teams do it for Log4j2's async loggers (higher throughput under heavy logging) or because a company standard requires it.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>   <!-- or -web, whichever you use -->
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>   <!-- removes Logback -->
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-log4j2</artifactId>
</dependency>
```

`logging.level.*` properties keep working. Custom config moves from `logback-spring.xml` to `log4j2-spring.xml`.

**Forget the exclusion and nothing fails.** SLF4J finds two providers, prints this at startup, and carries on with Logback:

```text
SLF4J(W): Class path contains multiple SLF4J providers.
SLF4J(W): Found provider [ch.qos.logback.classic.spi.LogbackServiceProvider@...]
SLF4J(W): Found provider [org.apache.logging.slf4j.SLF4JServiceProvider@...]
SLF4J(I): Actual provider is of type [ch.qos.logback.classic.spi.LogbackServiceProvider@...]
```

Your `log4j2-spring.xml` is then silently ignored. Read those warnings, or assert the binding in a test as `log4j2-demo` does.

**Log4j 1.x is different.** It reached end of life in 2015 and has unfixed security vulnerabilities. If a dependency still pulls in `log4j:log4j`, exclude it and add the `log4j-1.2-api` bridge. (Log4Shell, CVE-2021-44228, was in Log4j2 `2.0-beta9` through `2.14.1`, fully fixed in `2.17.1`. Boot's managed versions are long past it — another reason not to pin logging versions yourself.)

---

## Common pitfalls

- **Using `System.out.println` instead of a logger.** No level, no formatting, no destination control. Always use the logger — even quick debugging that "you'll remove later."
- **String concatenation inside a debug call.** `log.debug("hello " + expensiveCall())` runs `expensiveCall()` even when DEBUG is off. Use `log.debug("hello {}", expensiveCall())` so it's evaluated lazily. (Better: guard with `if (log.isDebugEnabled())` when the argument is very expensive.)
- **Creating a logger per instance.** `Logger log = LoggerFactory.getLogger(...)` should be `static final`. One per class. Putting it in a non-static field creates a new logger per object — costs memory and the loggers are interchangeable anyway.
- **Configuring two implementations.** Adding `spring-boot-starter-log4j2` without excluding `spring-boot-starter-logging` leaves both on the classpath. SLF4J prints a "multiple SLF4J providers" warning and keeps Logback, so your Log4j2 config does nothing. Exclude one.
- **`logback.xml` instead of `logback-spring.xml`.** Both files work, but `logback.xml` is loaded before Spring is initialized — `<springProfile>` and `<springProperty>` tags don't work. Use `logback-spring.xml` when you need profile-aware logging config.
- **Logging sensitive data.** Stack traces are fine; request bodies, passwords, and tokens are not. Set log levels for security-sensitive packages explicitly to suppress accidental detail.

---

## Try this yourself

1. Run `mvn -q test` against `logging-demo`. Two tests pass: one asserts INFO appears at default level, one asserts DEBUG appears when the level is overridden.
2. Run `mvn -q spring-boot:run`. Read the printed line from `LoggingService`. Only INFO and above show.
3. Run `mvn -q spring-boot:run -Dspring-boot.run.arguments=--logging.level.com.example=DEBUG`. Watch the DEBUG line appear.
4. Add `logging.file.name: logs/demo.log` to `application.yml` and run again. The console still logs *and* a file appears at `logs/demo.log`.
5. In `log4j2-demo/pom.xml`, delete the `<exclusions>` block and run `mvn test`. Read the `SLF4J(W)` lines and see `slf4jIsNowBackedByLog4j2` fail: Logback won.

---

## Self-check

1. Why is `log.debug("user {} signed in", user)` better than `log.debug("user " + user + " signed in")` even when DEBUG is enabled — and what additional cost the second form pays when DEBUG is disabled?
2. You want DEBUG in dev and WARN in prod, both for `com.acme.payments`. What's the cleanest way to do this without writing any XML?
3. You add `spring-boot-starter-log4j2` and a `log4j2-spring.xml`, but your Log4j2 pattern never shows up. Nothing failed at startup. What did you forget, and which startup line would have told you?
4. You have a `logback.xml` in `src/main/resources/` and your `<springProfile>` tags don't apply. What's the one-word fix to the filename, and why?

---

## Code examples

In reading order:

- `logging-demo/pom.xml` — same as topic 02's pom; Logback is already there via `spring-boot-starter`.
- `.../LoggingService.java` — a `@Component` whose `doWork()` logs at INFO, DEBUG, and TRACE so you can see which appear at each level.
- `.../App.java` — `@SpringBootApplication`; the `main` calls `doWork()` so you see the lines on a normal run.
- `src/main/resources/application.yml` — sets the application name; leaves levels at default.
- `src/test/java/com/example/DefaultLevelTest.java` — `OutputCaptureExtension` asserts INFO appears, DEBUG does not (the default level).
- `.../DebugLevelTest.java` — `@TestPropertySource` raises `com.example` to DEBUG; asserts both appear.
- `log4j2-demo/pom.xml` — the same app with Logback excluded and `spring-boot-starter-log4j2` added. `LoggingService` is unchanged.
- `log4j2-demo/.../Log4j2SwitchTest.java` — asserts SLF4J is now bound to Log4j2 and the same INFO/DEBUG behaviour holds.

Run with:

```bash
mvn -q test                                                                       # both tests pass
mvn -q spring-boot:run                                                            # default levels
mvn -q spring-boot:run -Dspring-boot.run.arguments=--logging.level.com.example=DEBUG
```
