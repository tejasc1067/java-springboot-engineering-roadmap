# Multiple Beans of the Same Type: @Qualifier and @Primary

So far every injection has been unambiguous — one `EmailNotifier` bean, one `OrderRepository` bean. Real applications routinely have two of the same kind: a real and a fake, a production and a dev variant, a primary and a backup. Spring needs a tiebreaker. This topic introduces the two tiebreakers — `@Primary` and `@Qualifier` — and the one collection trick (`List<Type>`) that grabs all of them.

By the end you can read a `NoUniqueBeanDefinitionException` stack trace and pick the right fix in under a minute.

---

## The problem this solves

Add a second `EmailNotifier` implementation:

```java
@Component
public class LoggingNotifier implements EmailNotifier {
    public void send(String to, String message) {
        System.out.println("LOG to=" + to + " msg=" + message);
    }
}
```

The container now has *two* beans of type `EmailNotifier`: `consoleEmailNotifier` and `loggingNotifier`. The first bean that asks for a single `EmailNotifier` will crash the startup:

```
NoUniqueBeanDefinitionException: No qualifying bean of type 'com.example.EmailNotifier'
available: expected single matching bean but found 2: consoleEmailNotifier, loggingNotifier
```

The exception is helpful. It tells you:

- The type that couldn't be resolved (`EmailNotifier`).
- That there's more than one match.
- The bean *names* of the candidates (`consoleEmailNotifier`, `loggingNotifier`). Names matter — they're what `@Qualifier` is going to refer to.

You have three ways to fix this. Pick based on what you actually mean.

## Fix 1: `@Primary` — "this one is the default"

Mark one of the candidates as primary. Now when a single `EmailNotifier` is asked for and no other qualifier is given, Spring picks the primary one and moves on.

```java
@Component
@Primary
public class ConsoleEmailNotifier implements EmailNotifier { ... }
```

Use `@Primary` when **most** consumers want the same notifier and only the rare consumer needs the other. It expresses "this is the default; if you don't say otherwise, you get this one."

`@Primary` also works on `@Bean` methods:

```java
@Bean
@Primary
public EmailNotifier consoleEmailNotifier() { return new ConsoleEmailNotifier(); }
```

Only one bean per type can be primary. Two primaries of the same type would just shift the ambiguity, and Spring will say so loudly.

## Fix 2: `@Qualifier` — "I specifically want this one"

Mark the injection site with a name. Spring matches the qualifier against bean names.

```java
@Service
public class LoggingNotificationSender {
    private final EmailNotifier notifier;

    public LoggingNotificationSender(@Qualifier("loggingNotifier") EmailNotifier notifier) {
        this.notifier = notifier;
    }
}
```

`@Qualifier("loggingNotifier")` says "of the beans matching `EmailNotifier`, give me the one named `loggingNotifier`." The name is the default bean name for `LoggingNotifier` (class name with first letter lowercased). You can override the name at the source with `@Component("loggingChannel")` and qualify by that, but the default usually reads fine.

Use `@Qualifier` when the choice **belongs to the consumer**, not the producer — i.e., several beans want different notifiers and no one is "the default."

You can combine `@Primary` and `@Qualifier`: `@Primary` sets the default; `@Qualifier` is an explicit override for the consumers who want something else.

## Fix 3: `List<EmailNotifier>` — "I want all of them"

Sometimes the right answer is "give me every implementation and I'll deal with them all." Declare the injection as a `List` of the interface, and Spring populates it with every matching bean in the container:

```java
@Service
public class BroadcastSender {

    private final List<EmailNotifier> notifiers;

    public BroadcastSender(List<EmailNotifier> notifiers) {
        this.notifiers = notifiers;
    }

    public void announce(String to, String message) {
        for (EmailNotifier n : notifiers) {
            n.send(to, message);
        }
    }
}
```

This is the right shape when you have a plugin model — every implementation is meant to run, not one of them. Audit logging is a classic case: each notifier logs separately and all of them run. `Set<EmailNotifier>` works too; `Map<String, EmailNotifier>` gives you names-to-beans.

The list comes back in the order Spring registered the beans, which is usually file/scan order. Don't depend on that order for correctness — if order matters, sort by an explicit field (e.g., a numeric priority) inside the consumer.

## Which one when?

| You want... | Use |
|--|--|
| One default that almost everyone wants | `@Primary` on the default |
| One specific bean at a specific call site | `@Qualifier("name")` at the injection |
| Every implementation simultaneously | `List<Type>` or `Map<String, Type>` |
| A default *plus* an override for special consumers | `@Primary` + `@Qualifier` |

If you find yourself reaching for `@Qualifier` on every single injection of a type, that's a hint there is no "default" and the names are doing all the work — consider whether the two beans should actually be different types instead. Disambiguation is for variants of the same idea, not for two unrelated things that happened to be modeled with the same interface.

## What `@Qualifier` matches against

Three layers, in order of how Spring resolves:

1. **Bean name** (the default name from `@Component` or the explicit name on `@Component("x")` / `@Bean(name="x")`).
2. **Explicit `@Qualifier` on the producer**:
   ```java
   @Component
   @Qualifier("audit")
   public class AuditEmailNotifier implements EmailNotifier { ... }
   ```
   Then consumers write `@Qualifier("audit") EmailNotifier notifier`.
3. **Custom qualifier annotations** — you can define your own annotation meta-annotated with `@Qualifier`, e.g., `@Audit`. Powerful, occasionally useful, mostly overkill for a beginner module.

Most code uses layer 1 — the bean name. That's what we'll do here.

## Common pitfalls

- **`@Primary` on every implementation.** Spring picks one and ignores the others — but it's not deterministic which. Have exactly zero or exactly one primary per type.
- **`@Qualifier` with a typo.** `@Qualifier("loggingNotfier")` (missing an `i`) won't match any bean and you get `NoSuchBeanDefinitionException` instead of `NoUniqueBeanDefinitionException`. The error names what was looked for, which is the clue.
- **Injecting `EmailNotifier` when you meant `List<EmailNotifier>`.** Spring will refuse the single injection in the presence of two candidates. If your intent is "send through all," declare the parameter as a list.
- **Two `@Primary` beans of the same type.** Spring throws at startup with a different but related error. The fix is to delete one of the `@Primary` annotations.
- **`@Qualifier` on a setter, expecting it to apply to a constructor parameter.** Each injection site has its own qualifier. Constructor parameter qualifiers go on the constructor parameter; setter qualifiers go on the setter parameter (or the setter itself).

---

## Try this yourself

1. In `disambiguation-demo`, remove `@Primary` from `ConsoleEmailNotifier`. Run `mvn test`. Read the new error — `NotificationSender`'s constructor can no longer resolve a single `EmailNotifier`. Put `@Primary` back.
2. Add a `@Qualifier("consoleEmailNotifier")` to the constructor parameter in `NotificationSender`. Notice that even with `@Primary` removed, the explicit qualifier resolves the ambiguity. Both fixes are valid; this is the consumer-driven one.
3. Introduce a third `EmailNotifier` -- say `FileEmailNotifier`. Notice that `BroadcastSender`'s injected list now has three entries automatically, without you changing `BroadcastSender` at all. Spring's collection injection is the cleanest fit for a true plugin model.

## Self-check

1. What is the difference, in one sentence each, between `@Primary` and `@Qualifier`? When is each the right choice?
2. You inject `List<EmailNotifier>` and the list comes back in a different order than you expected. Where should you put the ordering logic?
3. The error says `NoUniqueBeanDefinitionException: expected single matching bean but found 2: a, b`. Which two pieces of information in that message tell you exactly what to do next?

---

## Code examples

In reading order:

- `disambiguation-demo/src/main/java/com/example/EmailNotifier.java` -- the interface.
- `.../ConsoleEmailNotifier.java` -- `@Component @Primary`.
- `.../LoggingNotifier.java` -- `@Component` (no primary).
- `.../NotificationSender.java` -- depends on `EmailNotifier`; gets the primary.
- `.../LoggingNotificationSender.java` -- depends on `EmailNotifier` with `@Qualifier("loggingNotifier")`.
- `.../BroadcastSender.java` -- depends on `List<EmailNotifier>`; gets both.
- `.../AppConfig.java` -- `@ComponentScan`.
- `.../Main.java` -- exercises all three senders.
- `src/test/java/com/example/DisambiguationTest.java` -- asserts each resolution path, and shows the unqualified-with-two-matches failure via an inline broken config.

Run with `mvn -q test` and `mvn -q compile exec:java`.
