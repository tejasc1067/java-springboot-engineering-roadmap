# Profiles in Spring Boot

Module 07 topic 09 introduced `@Profile` on beans and `@ActiveProfiles` on tests — Spring's mechanism for switching bean variants by environment. Spring Boot keeps both unchanged and adds one convention on top: **profile-specific YAML files**. Drop an `application-dev.yml` next to your `application.yml` and the values inside it override the base file when `dev` is active, with no `@PropertySource` annotation needed.

By the end of this topic you can write per-environment config in three files (`application.yml`, `application-dev.yml`, `application-prod.yml`), activate a profile three different ways, and explain how Boot merges them.

---

## The problem this solves

Plain Spring's `@Profile` on a bean is good for "switch the *implementation*" — `EmailNotifier` vs `ConsoleNotifier`. It is less good for "switch the *value*" — a database URL or an API host that differs per environment but doesn't justify two classes. Plain Spring forced you to write something like:

```java
@Bean @Profile("dev") String apiHost() { return "https://dev.example.com"; }
@Bean @Profile("prod") String apiHost() { return "https://api.example.com"; }
```

For each value. Boot's profile-specific YAML lets you keep one `@Value` (or one `@ConfigurationProperties` record) and supply different values in different files:

```yaml
# application-dev.yml
app:
  api-host: https://dev.example.com
```

```yaml
# application-prod.yml
app:
  api-host: https://api.example.com
```

Same bean, different value per active profile. No two beans, no `@Profile` annotation.

---

## How file-based profiles work

When `SpringApplication.run` boots, it does this in order:

1. Loads `application.yml` (and `.properties`) from the search locations (topic 05).
2. Determines the active profile(s) from `spring.profiles.active` — settable in the same file, in env, or on the CLI.
3. For each active profile, loads `application-{profile}.yml` from the same locations.
4. Merges them. The profile-specific files sit *above* the base file in the property source order, so any key that appears in both takes the profile's value.

A concrete example:

```yaml
# application.yml
app:
  api-host: https://default.example.com
  retry:
    max-attempts: 3
  feature:
    flag-a: false
```

```yaml
# application-prod.yml
app:
  api-host: https://api.example.com
  feature:
    flag-a: true
```

With `spring.profiles.active=prod`, the effective config is:

```yaml
app:
  api-host: https://api.example.com   # from prod
  retry:
    max-attempts: 3                    # from base (prod didn't override)
  feature:
    flag-a: true                       # from prod
```

The merge is per-key, not per-file. The base file supplies defaults for everything; each profile file supplies a delta.

---

## Activating a profile — three ways

In order of priority (highest first; later wins over earlier):

### 1. Command-line argument

```bash
java -jar myapp.jar --spring.profiles.active=prod
mvn -q spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev
```

Highest priority. Use this for ad-hoc per-run activation.

### 2. Environment variable

```bash
SPRING_PROFILES_ACTIVE=prod java -jar myapp.jar
```

The relaxed-binding rules from topic 05 apply: dotted property -> upper-snake env var.

### 3. In `application.yml`

```yaml
spring:
  profiles:
    active: dev
```

Lowest of the three. Useful as the default profile for local development; production overrides with CLI or env.

You can activate multiple profiles at once — comma-separated: `--spring.profiles.active=prod,monitoring`. Boot loads `application-prod.yml` *and* `application-monitoring.yml` in that order. Later profiles win on conflicts.

---

## `@Profile` on beans — unchanged from module 07

The annotation-driven mechanism still works in Boot, identically to module 07 topic 09:

```java
@Component
@Profile("dev")
public class FakeEmailNotifier implements EmailNotifier { ... }

@Component
@Profile("prod")
public class SmtpEmailNotifier implements EmailNotifier { ... }
```

Use file-based profiles for *values*; use `@Profile` on beans for *implementations*. Both mechanisms can coexist in the same project — the demo project for this topic uses just the file-based one, but the markdown shows both because real projects use both.

---

## `@ActiveProfiles` in tests

Same annotation, same semantics as module 07. A test that wants to verify the `prod` profile's config:

```java
@SpringBootTest
@ActiveProfiles("prod")
class ProdProfileTest {

    @Autowired Environment environment;

    @Test
    void prodProfileLoadsTheProductionApiHost() {
        assertThat(environment.getProperty("app.api-host"))
            .isEqualTo("https://api.example.com");
    }
}
```

Boot picks up `application-prod.yml` for this test the same way it would in production.

---

## The default profile

If no profile is active, Boot uses one called `default`. So `application-default.yml`, if it exists, is loaded when nothing else is. The startup log line you've been seeing since topic 02 says so:

```text
No active profile set, falling back to 1 default profile: "default"
```

A common pitfall: developers write `application-dev.yml` and assume it loads "in dev." If they never activate any profile, only `application.yml` and `application-default.yml` load. The fix is to set `spring.profiles.active: dev` in `application.yml` (or run with `--spring.profiles.active=dev`).

---

## Profile groups — naming a set of profiles

Sometimes a single conceptual environment is actually two or three profiles. Boot lets you name a group:

```yaml
spring:
  profiles:
    group:
      production:
        - prod
        - email-smtp
        - monitoring
      local-dev:
        - dev
        - email-fake
```

Activating `--spring.profiles.active=production` then activates `prod`, `email-smtp`, and `monitoring` together. Useful when "the prod environment" is conceptually a bundle of orthogonal toggles.

---

## Common pitfalls

- **Setting `spring.profiles.active` inside a profile-specific file.** `application-prod.yml` saying `spring.profiles.active: prod` is a no-op — by the time that file loads, the profile is already active. Worse, in older Boot versions this caused warnings; in 3.x it's silently ignored. To activate profiles dynamically, use `spring.profiles.include`.
- **Forgetting that profile-specific files only *override*, not *replace*.** Putting `app.api-host` only in `application-prod.yml` and nothing in `application.yml` works when prod is active and *fails* when prod isn't. If a value is required, put a default in the base file.
- **Naming profiles inconsistently.** `dev` vs `development` vs `local-dev` — pick one and stick to it. Boot will happily load `application-development.yml` if the active profile is `development`, but not if it's `dev`.
- **Using `@Profile("!prod")` to mean "not production."** It works, but it inverts the dependency: every non-prod beans needs the `!prod` annotation. Prefer the positive form (`@Profile("dev")`, `@Profile("test")`, etc.) — explicit beats clever.
- **Letting the default profile silently hide bad config.** If `application-prod.yml` has a typo (`spring.datasourrce.url`), nobody notices in dev — and prod fails at startup. Pair every new profile-specific key with a test that activates that profile.

---

## Try this yourself

1. Run `mvn -q spring-boot:run` against `profiles-demo`. Read the printed greeting — it comes from `application.yml` (no profile active).
2. Run `mvn -q spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev`. Greeting changes to the dev one.
3. Try `--spring.profiles.active=dev,prod` — observe which one wins. (Hint: comma-separated profiles are processed in order; later wins.)
4. Set `SPRING_PROFILES_ACTIVE=prod` in your shell and run `mvn -q spring-boot:run` without any CLI arg. Confirm the prod greeting wins.

---

## Self-check

1. You have three files: `application.yml` (defines `app.api-host`), `application-dev.yml` (overrides `app.api-host`), `application-prod.yml` (overrides `app.api-host`). With no active profile set, which value wins? With `--spring.profiles.active=dev`?
2. `@Profile("dev")` and `application-dev.yml` are two different things. Give one example where each is the right choice.
3. The startup log says `No active profile set, falling back to 1 default profile: "default"`. What file (if any) does Boot then try to load *in addition to* `application.yml`?

---

## Code examples

In reading order:

- `profiles-demo/pom.xml` — same starters as the other topics.
- `.../Greeter.java` — `@Component` that captures `app.greeting` via `@Value`.
- `.../App.java` — `@SpringBootApplication`; prints the greeting and the active profile.
- `src/main/resources/application.yml` — base config with a default greeting.
- `.../application-dev.yml` — dev overrides.
- `.../application-prod.yml` — prod overrides.
- `src/test/java/com/example/DefaultProfileTest.java` — no `@ActiveProfiles`; reads the base value.
- `.../DevProfileTest.java` — `@ActiveProfiles("dev")`; reads the dev value.
- `.../ProdProfileTest.java` — `@ActiveProfiles("prod")`; reads the prod value.

Run with:

```bash
mvn -q test                                                                       # all three pass
mvn -q spring-boot:run                                                            # prints the base greeting
mvn -q spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev   # prints the dev greeting
mvn -q spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=prod  # prints the prod greeting
```
