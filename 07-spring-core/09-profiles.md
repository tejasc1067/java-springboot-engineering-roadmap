# Profiles: Environment-Specific Beans

Most production systems need different beans in different environments: an in-memory store in tests, Postgres in prod; a console email sink on a laptop, SMTP in prod; debug-friendly logging on staging, sparse logging on prod. **Profiles** let you declare those variations as Spring beans and pick the right set at startup by name.

By the end of this topic you can write `@Profile`-annotated beans, activate a profile from the command line, and reason about what happens when no profile is active.

---

## The problem this solves

So far the demos used `ConsoleEmailNotifier` because real SMTP is awkward in tutorials. In production you'd use a real notifier — say an `SmtpEmailNotifier`. Without profiles you'd either:

- Comment one bean in and the other out before deploying (please don't),
- Read a property in `main` and decide which to register (works; verbose),
- Wrap both in a factory bean (works; clever; opaque).

`@Profile` does the same job declaratively: each implementation says which profile it belongs to, you activate the profile at startup, and Spring registers only the matching beans.

## How it works

Mark each variant with `@Profile`:

```java
@Component
@Profile("dev")
public class ConsoleEmailNotifier implements EmailNotifier { ... }

@Component
@Profile("prod")
public class SmtpEmailNotifier implements EmailNotifier { ... }
```

Activate a profile at startup. Three common ways:

1. **JVM system property:**
   `java -Dspring.profiles.active=dev -jar app.jar`
2. **Environment variable:**
   `SPRING_PROFILES_ACTIVE=dev java -jar app.jar`
3. **Programmatically** (handy for tests):
   ```java
   var ctx = new AnnotationConfigApplicationContext();
   ctx.getEnvironment().setActiveProfiles("dev");
   ctx.register(AppConfig.class);
   ctx.refresh();
   ```

When `dev` is active, only `ConsoleEmailNotifier` gets registered; `SmtpEmailNotifier` is skipped entirely. When `prod` is active, the reverse. A bean with no `@Profile` is **always** registered — the profile only affects beans that opt into the mechanism.

```java
@Component
public class Mailer {
    private final EmailNotifier notifier;
    public Mailer(EmailNotifier notifier) { this.notifier = notifier; }
    public void send(String to, String msg) { notifier.send(to, msg); }
}
```

`Mailer` doesn't know or care which `EmailNotifier` is wired in. Spring picks whichever variant the active profile registered, and `Mailer` gets it through its constructor.

## What happens when no profile is active

If both implementations are `@Profile`-restricted and no profile is active, **neither** is registered. `Mailer`'s constructor parameter has no matching bean and the context fails to start.

That failure mode is sometimes what you want — "you forgot to set a profile, that's a config bug, don't start" — and sometimes inconvenient. Two ways to deal with it:

- **A default variant with no `@Profile`.** Always registered; the named variants override the default when their profile is active... actually they don't *override*, they *add*, so you'd hit the multiple-beans problem from topic 05. To use this pattern, mark the default with `@Profile("default")` — the `default` profile is implicitly active only when *no* other profile is set.
- **The `!` syntax.** `@Profile("!prod")` reads as "active when prod is *not* active." Useful for "this bean should run everywhere except production."

```java
@Component
@Profile("default")
public class InMemoryEmailNotifier implements EmailNotifier { ... }   // when no profile is set
```

## Multiple active profiles

You can activate more than one profile at a time. The names are comma-separated:

```
-Dspring.profiles.active=dev,verbose-logging
```

Both `@Profile("dev")` and `@Profile("verbose-logging")` beans are now registered. Useful for layering: a base environment plus an opt-in flag.

`@Profile` expressions support a small algebra:

```java
@Profile("dev")              // dev active
@Profile("!prod")            // prod NOT active
@Profile({"dev", "test"})    // dev OR test active
@Profile("dev & verbose")    // both dev AND verbose active (note the spaces)
```

You almost always want the simple "one profile name" form. The expressions exist; reach for them only when the simple version makes you copy a class.

## A separate place for profile-specific properties

In Spring Boot, `application-dev.properties` and `application-prod.properties` are loaded automatically on top of `application.properties` when the matching profile is active. In plain Spring Core (this module) you don't get that auto-loading; you either declare `@PropertySource` files explicitly or wait for module 08.

The mechanism in spirit is the same: profile-specific *values* alongside profile-specific *beans*. Keep that mental link.

## `@Profile` on `@Bean` methods, `@Configuration` classes, etc.

`@Profile` works in three places:

```java
@Profile("dev")
@Configuration
public class DevConfig { ... }              // whole class registered only in dev

public class AppConfig {
    @Bean
    @Profile("dev")
    public EmailNotifier consoleEmailNotifier() { ... }   // bean method registered only in dev
}

@Component
@Profile("dev")
public class ConsoleEmailNotifier { ... }   // scanned class registered only in dev
```

The first form — `@Profile` on a `@Configuration` — is useful when an entire group of beans belongs to one profile. The second is fine for one-off variants. The third is what you'll use most.

## Common pitfalls

- **Two variants of the same type, no profile active, both `@Profile`-restricted.** Neither registers, and a consumer crashes with `NoSuchBeanDefinitionException`. Either set a profile, or add a `@Profile("default")` fallback variant.
- **A profile name typo.** `@Profile("prod")` vs `-Dspring.profiles.active=produciton`. The bean isn't registered; the consumer fails. Names are case-sensitive and free-form -- a typo isn't a compile error.
- **Profile-specific logic inside a `@Profile("dev")` bean that calls a non-profile-specific bean.** Fine. The reverse -- non-profile bean calling into a profile-restricted bean -- breaks if the profile isn't active.
- **Profiles used to model feature flags.** A profile is a coarse-grained "this is which environment we're in," not a per-feature toggle. For per-feature toggles, use a real flag system or a properties-based check. Profiles get unwieldy past three or four.
- **Forgetting `spring.profiles.active` in production deployments.** No active profile means default-only, which usually isn't what you want for production. Set it explicitly in your deployment config.

---

## Try this yourself

1. Open `profiles-demo`. Run `mvn -q compile exec:java "-Dspring.profiles.active=dev"`. The Mailer wires the `ConsoleEmailNotifier`. Switch to `prod` and see the SMTP variant get wired.
2. Run with **no** profile: `mvn -q compile exec:java`. The context fails to start. Read the message -- it names `EmailNotifier` as the unsatisfied dependency.
3. Add a third notifier `LoggingEmailNotifier` with `@Profile("default")`. Run with no profile -- now the default kicks in and the Mailer wires the logging variant. Run with `-Dspring.profiles.active=dev` -- the default is *not* active and the console one wins.

## Self-check

1. A bean with no `@Profile` annotation is registered when? A bean with `@Profile("dev")` is registered when? A bean with `@Profile("default")` is registered when?
2. Why is "use profiles for feature flags" usually a bad idea? Give one symptom you'd notice on a real codebase that did this.
3. Two `EmailNotifier` beans, both `@Profile("prod")`. You activate the `prod` profile. What happens at the consumer's injection site, and what's the fix?

---

## Code examples

In reading order:

- `profiles-demo/src/main/java/com/example/EmailNotifier.java` -- the interface.
- `.../ConsoleEmailNotifier.java` -- `@Component @Profile("dev")`.
- `.../SmtpEmailNotifier.java` -- `@Component @Profile("prod")`. (Doesn't really send SMTP -- it just prints to make the profile choice visible.)
- `.../Mailer.java` -- consumer; injects whichever variant the active profile registered.
- `.../AppConfig.java`, `.../Main.java`.
- `src/test/java/com/example/ProfileTest.java` -- two tests activate profiles programmatically; a third asserts the no-profile failure.

Run with:

```bash
mvn -q test
mvn -q compile exec:java "-Dspring.profiles.active=dev"
mvn -q compile exec:java "-Dspring.profiles.active=prod"
```
