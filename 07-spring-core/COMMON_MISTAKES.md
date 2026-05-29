# Common Mistakes — Spring Core

Real bugs people hit with the Spring container, the symptom you'll see, and the fix.

---

## 1. Field injection makes the bean impossible to unit-test without Spring

**The mistake.**

```java
@Service
public class OrderService {
    @Autowired private InventoryService inventory;
    @Autowired private PriceCalculator pricer;
    // ...
}
```

**The symptom.** You sit down to write a plain JUnit test:

```java
OrderService service = new OrderService();
service.place(...);                          // NullPointerException -- inventory is null
```

You cannot construct the object with its dependencies satisfied. `inventory` is `private`, no constructor accepts it, no setter exists. The only way to populate it is reflection (`ReflectionTestUtils.setField(...)`) or booting a Spring context — both turn a unit test into something more elaborate.

**The fix.** Constructor injection:

```java
@Service
public class OrderService {
    private final InventoryService inventory;
    private final PriceCalculator pricer;

    public OrderService(InventoryService inventory, PriceCalculator pricer) {
        this.inventory = inventory;
        this.pricer = pricer;
    }
}
```

Now: `new OrderService(new InventoryService(), new PriceCalculator())` works in a unit test, and the dependencies are `final`, and Spring still wires them automatically in production.

---

## 2. Forgetting `@ComponentScan` (or annotating the wrong package)

**The mistake.**

```java
// AppConfig is in package com.example.config
@Configuration
public class AppConfig { }      // no @ComponentScan, no @Bean methods

// OrderService is in package com.example.service with @Service
```

**The symptom.** Context starts; `getBean(OrderService.class)` throws `NoSuchBeanDefinitionException`. The class exists, the annotation exists; Spring just never looked at it.

**The fix.** Either explicit:

```java
@Configuration
@ComponentScan(basePackages = "com.example")
public class AppConfig { }
```

Or move `AppConfig` to a parent package and use bare `@ComponentScan` — Spring scans the config class's own package and subpackages by default.

Alternative cause: `@ComponentScan` is on the class, but the `basePackages` value points at a package that doesn't contain the missing bean. Double-check the literal package name.

---

## 3. Two beans of the same type, no `@Primary` and no `@Qualifier`

**The mistake.**

```java
@Component public class ConsoleEmailNotifier implements EmailNotifier { ... }
@Component public class SmtpEmailNotifier    implements EmailNotifier { ... }

@Service
public class Mailer {
    public Mailer(EmailNotifier notifier) { ... }   // which one?
}
```

**The symptom.** Context fails to start with:

```
NoUniqueBeanDefinitionException: No qualifying bean of type 'EmailNotifier' available:
expected single matching bean but found 2: consoleEmailNotifier, smtpEmailNotifier
```

**The fix.** Pick the right intent:

```java
// "this one is the default":
@Component @Primary public class ConsoleEmailNotifier implements EmailNotifier { ... }

// "this specific consumer wants the SMTP one":
public Mailer(@Qualifier("smtpEmailNotifier") EmailNotifier notifier) { ... }

// "I want all of them":
public Broadcast(List<EmailNotifier> notifiers) { ... }
```

The exception message names the candidate bean names — `@Qualifier` matches against those names.

---

## 4. Prototype dependency in a singleton, no `ObjectProvider`

**The mistake.**

```java
@Component @Scope("prototype")
public class WorkTicket { ... }

@Component
public class Coordinator {
    public Coordinator(WorkTicket ticket) { this.ticket = ticket; }
    public void doWork() { use(this.ticket); }   // SAME ticket every time
}
```

**The symptom.** The prototype scope appears to be ignored — every call to `doWork()` uses the same `WorkTicket` instance. A unit test that expected unique tickets sees duplicates.

**The fix.** Inject a factory for the prototype instead of the prototype itself:

```java
@Component
public class Coordinator {
    private final ObjectProvider<WorkTicket> tickets;

    public Coordinator(ObjectProvider<WorkTicket> tickets) { this.tickets = tickets; }
    public void doWork() {
        WorkTicket fresh = tickets.getObject();   // new instance per call
        use(fresh);
    }
}
```

`ObjectProvider.getObject()` re-queries the container, which honors the prototype scope correctly.

---

## 5. Calling `new SomeBean()` from your code

**The mistake.**

```java
@Service
public class ReportService {
    public void run() {
        EmailNotifier notifier = new ConsoleEmailNotifier();   // wrong
        notifier.send("ada@example.com", "report");
    }
}
```

**The symptom.** Hard to spot — the code compiles and "works." But:

- `notifier` is not the Spring-managed bean. Its lifecycle hooks (`@PostConstruct`, `@PreDestroy`) never run.
- AOP advice (logging, transactions, security) does not apply.
- Two notifiers exist now: one Spring made, one your code made, and they may disagree about state.

**The fix.** Inject the notifier; let the container build it once:

```java
@Service
public class ReportService {
    private final EmailNotifier notifier;
    public ReportService(EmailNotifier notifier) { this.notifier = notifier; }
}
```

The general rule: anything with collaborators and a lifecycle should be a constructor-injected bean. `new` is for value objects.

---

## 6. Self-invocation defeats AOP advice

**The mistake.**

```java
@Service
public class OrderService {

    @Audited
    public Order place(String customer) { return logAndCreate(customer); }

    @Audited
    public Order logAndCreate(String customer) { ... }
}
```

You have an aspect that triggers on `@Audited`. `place(...)` is advised; `logAndCreate(...)` is *not*, when `place` calls it. Audit logs are missing for the inner method.

**The symptom.** The advice only fires for "external" calls; internal `this.x()` calls slip through.

**The fix.** The call from `place()` to `logAndCreate()` is `this.logAndCreate()`, which bypasses the Spring proxy. Options:

1. **Restructure**: move `logAndCreate` to a different bean. The cross-bean call goes through the proxy.
2. **Look up the proxy explicitly**: ugly, possible via `AopContext.currentProxy()` after enabling `@EnableAspectJAutoProxy(exposeProxy = true)`.

Restructuring is almost always the right move. The other path is a workaround, not a solution.

---

## 7. `@PostConstruct` doing a slow network call

**The mistake.**

```java
@Component
public class ConfigClient {

    @PostConstruct
    public void loadRemoteConfig() {
        // 15-second HTTPS round-trip to a config service
        remoteConfig = httpClient.fetch(...);
    }
}
```

**The symptom.** Startup time balloons. The application appears to hang for 15 seconds before logging "started." A failed network call crashes the whole app at boot.

**The fix.** Treat `@PostConstruct` as small, synchronous setup. For remote calls, lazy-load on first use, or schedule a background refresh:

```java
@Component
public class ConfigClient {
    private volatile RemoteConfig cached;

    public RemoteConfig get() {
        RemoteConfig snap = cached;
        if (snap == null) {
            snap = httpClient.fetch(...);
            cached = snap;
        }
        return snap;
    }
}
```

Or, with Spring Boot, an `@Async @Scheduled` refresh — coming in module 08.

---

## 8. `@Value` without a default on a property nobody set

**The mistake.**

```java
@Component
public class PricingPolicy {
    public PricingPolicy(@Value("${pricing.tax.rate}") BigDecimal taxRate) { ... }
}
```

The property isn't in `application.properties`, no `-D` flag, no environment variable.

**The symptom.** Context refuses to start:

```
IllegalArgumentException: Could not resolve placeholder 'pricing.tax.rate' in value
"${pricing.tax.rate}"
```

**The fix.** Two choices, depending on whether the missing value is a config bug or a normal optional:

```java
@Value("${pricing.tax.rate:0.10}") BigDecimal taxRate    // 0.10 if unset
```

vs. leave it required and make sure your deployment always sets it. The wrong move is silently catching the failure — then you'd run with `null` or random defaults.

---

## 9. `@Profile` typo

**The mistake.**

```java
@Component @Profile("prod")
public class SmtpEmailNotifier { ... }
```

Production startup uses `-Dspring.profiles.active=produciton` (the typo is on the command line, not in the code).

**The symptom.** Mailer's constructor fails with `NoSuchBeanDefinitionException` for `EmailNotifier`. Logs may also show "active profile: produciton" — but `produciton` matches no `@Profile` annotation, so neither notifier registers.

**The fix.** Profile names are unchecked strings. Check the spelling in *both* places: the annotation and the activation. Tip: define profile names as constants in one class and reference the constant from `@Profile(...)` *and* your activation logic, so a typo only happens once.

---

## 10. Two `@Configuration` classes registering the same bean

**The mistake.**

```java
@Configuration
public class AppConfig {
    @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }
}

@Configuration
public class JsonConfig {
    @Bean public ObjectMapper objectMapper() { return new ObjectMapper().registerModule(...); }
}
```

**The symptom.** Depends on registration order: the second registration may *override* the first (Spring sometimes allows this, sometimes complains based on `setAllowBeanDefinitionOverriding`). A consumer of `ObjectMapper` gets a configuration you didn't expect.

**The fix.** One bean per `@Bean`-name, per type, in your configuration set. If you genuinely need two `ObjectMapper`s for different purposes, give them distinct names: `@Bean("jacksonForApi")`, `@Bean("jacksonForLogs")`, and disambiguate consumers with `@Qualifier`.

In Spring Boot, bean override is *disabled* by default — a duplicate fails fast. Take that as the model for plain Spring Core configs too.

---

## 11. Mutable field on a singleton

**The mistake.**

```java
@Service
public class RequestContextHolder {
    private String currentUser;                       // shared!
    public void setUser(String user) { this.currentUser = user; }
    public String getUser() { return currentUser; }
}
```

**The symptom.** Two concurrent requests interleave. Request A sets `currentUser = "ada"`, request B sets `currentUser = "bob"`. Request A's downstream code reads `bob`. A user sees data belonging to another user.

**The fix.** Singletons must be stateless. Either:

- Pass the per-call value as a method parameter — the simplest and best fix.
- Use `request`-scoped beans (web only, module 09).
- Use `ThreadLocal` if the design truly demands a thread-local — but be careful with thread pools.

Storing per-request data on a singleton is one of the most common production incidents in Spring apps. Default to "no instance fields except final dependencies."

---

## 12. Spring `@Transactional` (preview) suffers the same self-invocation problem

This belongs more to module 10 but the cause is identical to mistake #6. If you write:

```java
@Service
public class OrderService {
    public Order place(...) { return doWork(...); }       // not transactional

    @Transactional
    public Order doWork(...) { ... }                       // transactional only when called externally
}
```

calling `place()` does NOT open a transaction around `doWork()` — `this.doWork()` is a direct call that bypasses the transactional proxy. Spring's transactional advice has the same proxy-based limitations as any other AOP advice.

Same fix: put the transactional boundary on a method that's called from another bean, or split the methods across beans.
