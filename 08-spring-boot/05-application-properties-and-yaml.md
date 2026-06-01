# `application.properties` and `application.yml`

In module 07 you read external config with `@PropertySource("classpath:application.properties")` and `@Value("${prop}")`. Spring Boot keeps `@Value` exactly the same and removes the `@PropertySource` boilerplate — it loads `application.properties` and `application.yml` from a handful of conventional locations automatically. It also lets command-line arguments, environment variables, and profile-specific files override file values without any code change.

By the end of this topic you can read a property from `application.yml`, override it from the command line, and explain — in order — which source wins when more than one tries to define the same key.

---

## The problem this solves

Plain Spring needs you to wire the property loading manually:

```java
@Configuration
@PropertySource("classpath:application.properties")
public class AppConfig { ... }
```

That works for one file. The moment you want a profile-specific override, a YAML file, or a value from the command line, you're writing a custom `PropertySourcesPlaceholderConfigurer` and possibly your own `EnvironmentPostProcessor`. Boot replaces all of that with a convention: drop an `application.yml` (or `.properties`) on the classpath, and Boot finds and loads it. Override it by setting an environment variable, a JVM `-D` flag, or a `--prop=value` command-line argument — Boot finds those too.

The mechanism behind "Boot finds the file" is the same `Environment` from module 07. Boot just registers more `PropertySource` instances into it automatically, and orders them so that more-specific sources win.

---

## Where Boot looks for the file

`SpringApplication.run` scans these four locations, in this order, for both `.properties` and `.yml`:

```text
1. file:./config/    (a ./config/ directory next to the working dir)
2. file:./           (the working directory itself)
3. classpath:/config/
4. classpath:/        (the most common location — src/main/resources/)
```

Files in earlier locations override files in later ones. The everyday case is location 4: `src/main/resources/application.yml`. Locations 1–3 are useful in production for per-deployment overrides without rebuilding the jar — drop a `./config/application.yml` next to the jar at deploy time and its values take precedence.

The base filename is `application` by default. You can change it with `spring.config.name` (e.g. `--spring.config.name=myapp`) or load additional files with `spring.config.import`.

---

## Properties vs YAML

`application.properties` is flat key-value:

```properties
spring.application.name=hello-demo
app.message=Default from properties
server.port=8080
```

`application.yml` is hierarchical:

```yaml
spring:
  application:
    name: hello-demo
app:
  message: Default from yml
server:
  port: 8080
```

Both are equally valid. Boot loads whichever exists; if both exist, `.properties` wins (it's higher in Boot's internal ordering). YAML is the modern default — easier to read for nested config, supports lists naturally:

```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - https://example.com
```

The same in `.properties` needs index syntax (`app.cors.allowed-origins[0]=...`), which gets ugly fast.

---

## Property source precedence (the priority list)

Boot stacks property sources from highest to lowest priority. When two sources define the same key, the higher one wins. The simplified list — drop levels you'll never use — looks like this:

```text
1. Devtools global settings (~/.spring-boot-devtools.properties) -- highest
2. @TestPropertySource / @SpringBootTest(properties = ...)         (tests only)
3. Command line arguments                                          --foo=bar
4. SPRING_APPLICATION_JSON                                          (an env var holding JSON)
5. ServletConfig / ServletContext init params                       (web apps)
6. JNDI attributes from java:comp/env                               (legacy)
7. Java System properties                                           -Dfoo=bar on JVM start
8. OS environment variables                                         FOO=bar
9. RandomValuePropertySource                                        ${random.uuid}
10. Profile-specific application-{profile}.yml outside the jar      (topic 07)
11. Profile-specific application-{profile}.yml inside the jar
12. application.yml outside the jar
13. application.yml inside the jar
14. @PropertySource on @Configuration classes
15. Default properties (SpringApplication.setDefaultProperties)     -- lowest
```

The thumb rule: **the more "external" or "specific" a source, the higher it ranks.** CLI args beat env vars beat config files beat the in-jar defaults. Profile-specific files beat the base file. That ordering is what lets `application.yml` ship sensible defaults inside the jar, while operators override per-environment from outside without recompiling.

---

## Environment variable mapping — the "relaxed binding" rule

`SPRING_APPLICATION_NAME=myapp` maps to the property `spring.application.name`. Boot translates between the two conventions automatically:

- Dots in property names become underscores: `spring.application.name` -> `SPRING_APPLICATION_NAME`.
- Kebab-case becomes upper-case underscores: `app.allowed-origins` -> `APP_ALLOWED_ORIGINS`.
- Index syntax becomes underscores: `app.servers[0].host` -> `APP_SERVERS_0_HOST`.

This is called **relaxed binding**. The same value is reachable as `app.message`, `APP_MESSAGE`, `app_message`, `app-message` — Boot canonicalizes them. The point is so that property files can use the conventional dotted form while environment variables (which on most shells can't contain dots) can still set them.

---

## Reading a property — three styles

### `@Value` — single property, simple cases

Same as module 07 topic 08:

```java
@Component
public class MessagePrinter {

    private final String message;

    public MessagePrinter(@Value("${app.message:Hello, World!}") String message) {
        this.message = message;
    }

    public String getMessage() { return message; }
}
```

`@Value("${app.message}")` reads the property, throws if it's missing. `@Value("${app.message:Hello, World!}")` reads it with a default fallback. Use this when you need one or two values.

### `Environment` — programmatic lookup

```java
@Component
public class Settings {

    private final Environment environment;

    public Settings(Environment environment) {
        this.environment = environment;
    }

    public String message() {
        return environment.getProperty("app.message", "fallback");
    }
}
```

Use this when you need to look up keys dynamically (the key isn't known at compile time) or want to call `getActiveProfiles()` from the same object.

### `@ConfigurationProperties` — type-safe binding for nested config

The grown-up version, covered in topic 06. Skip ahead if your config block has more than three keys.

---

## Overriding a property — five ways, in priority order

Given an `application.yml` that says `app.message: Default from yml`, here is how to override it. Highest priority first.

### 1. Command-line argument

```bash
java -jar myapp.jar --app.message="Override from CLI"
```

Or with the Maven plugin:

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments=--app.message="Override from CLI"
```

### 2. Environment variable

```bash
APP_MESSAGE="Override from env" java -jar myapp.jar
```

Note the uppercase underscore form — relaxed binding handles the conversion.

### 3. JVM system property

```bash
java -Dapp.message="Override from sysprop" -jar myapp.jar
```

### 4. Profile-specific file

`application-prod.yml`:

```yaml
app:
  message: Override from prod profile
```

Then activate the profile: `--spring.profiles.active=prod`. Topic 07 details.

### 5. External `application.yml` next to the jar

Put a file at `./config/application.yml` next to your jar:

```yaml
app:
  message: Override from external file
```

This file (location 1 in the search order above) overrides the same key in the in-jar file (location 4) without recompiling.

When debugging "why does this property have *that* value," walk the list in order and check each layer.

---

## Random values

Useful in tests and in places where you need a unique-per-startup token:

```yaml
app:
  instance-id: ${random.uuid}
  startup-marker: ${random.int(1000,9999)}
```

The `RandomValuePropertySource` (level 9 above) supplies these. Each evaluation is fresh — at the moment the property is read into a bean.

---

## Common pitfalls

- **Indenting YAML with tabs.** YAML requires spaces. Tabs produce a confusing parser error at startup ("found character '\t' that cannot start any token"). Configure your editor to write spaces in `.yml` files.
- **Quoting a Boolean.** `app.feature.enabled: "true"` makes the value the *string* `"true"`, not the boolean. `@Value("${app.feature.enabled}") boolean enabled` still works (string parsing), but `@ConfigurationProperties` on a `Boolean` field receives the string and fails. Drop the quotes for booleans, integers, and numbers.
- **Assuming env var case matters.** It does on Linux. Relaxed binding handles `APP_MESSAGE` -> `app.message`, but if your shell exports `App_Message`, neither matching works. Stick to UPPER_SNAKE for environment variables.
- **`@Value` on a missing property without a default.** Boot fails fast at startup with `IllegalArgumentException: Could not resolve placeholder 'app.message'`. Either provide a default (`@Value("${app.message:hello}")`) or make sure the property exists in your config. Failing fast is correct — silent fallback to `null` would surface as a `NullPointerException` later.
- **Mixing `application.properties` and `application.yml` and expecting predictable behavior.** Both load, but `.properties` wins on overlapping keys. Pick one format per project.
- **Confusing `--debug` with `--trace`.** `--debug` prints the auto-configuration report (topic 04). `--trace` prints SQL, request bodies, and every framework log line at TRACE — wildly verbose, useful for one specific debugging session.

---

## Try this yourself

1. Run `mvn -q spring-boot:run` against `properties-demo`. Read the printed message — it comes from `application.yml`.
2. Run `mvn -q spring-boot:run -Dspring-boot.run.arguments=--app.message="Override"` — the message changes without rebuilding.
3. Set `APP_MESSAGE=from-env` (Windows: `$env:APP_MESSAGE='from-env'`) and run again — the env-var value wins.
4. Set *both* a CLI arg and an env var simultaneously — confirm the CLI arg wins. That's source 3 beating source 8.

---

## Self-check

1. You start the app with `--app.message="A"`, your environment has `APP_MESSAGE=B`, and `application.yml` says `app.message: C`. Which value does the bean see, and why?
2. Why does Boot not require you to write `@PropertySource("classpath:application.yml")`?
3. You have a value `app.cors.allowed-origins` that is a list of two URLs. Write the YAML form, then sketch the equivalent `.properties` form. Which is easier to read at five entries?

---

## Code examples

In reading order:

- `properties-demo/pom.xml` — same parent + starters as topic 04.
- `.../MessagePrinter.java` — single `@Component` that captures `app.message` via `@Value` and exposes it.
- `.../App.java` — boots the context, prints the message.
- `src/main/resources/application.yml` — `app.message: "Default from yml"`.
- `src/test/java/com/example/DefaultMessageTest.java` — `@SpringBootTest` asserts the YAML value.
- `.../OverrideMessageTest.java` — `@SpringBootTest` with `@TestPropertySource` that overrides the message; asserts the override won.

Run with:

```bash
mvn -q test
mvn -q spring-boot:run
mvn -q spring-boot:run -Dspring-boot.run.arguments=--app.message="From CLI"
```
