# Interview Q&A — Spring Core

Questions that come up in real Spring interviews, with answers that aim past the surface definition. The goal is to demonstrate that you understand *why* the framework works the way it does, not just what the annotations are called.

---

### 1. What is the Spring IoC container, in your own words?

The IoC container is a registry of objects (called *beans*) plus the code that builds them and hands them to whoever needs them. It solves a bookkeeping problem: in any non-trivial application, somebody has to construct every service, pass each one its dependencies, manage when each is created and destroyed, and load configuration from outside the code. In a small app you do that yourself in `main`. In a large app, doing it by hand turns into a thousand-line wiring file that everyone has to touch on every change.

Spring's container reads a *description* of which beans should exist — written in Java with `@Configuration`/`@Bean`, or implied by `@Component` plus `@ComponentScan` — and builds the graph automatically. It also adds powers that follow naturally from "the framework owns the objects": scope (singleton vs prototype), lifecycle hooks, property injection, profile-based variants, AOP, and event publishing. The container itself is small; the value comes from these capabilities composing on top of it.

The "Inversion of Control" name refers to the relationship flip: in a normal program your code calls libraries, but in a framework the framework calls your code (constructs your beans, calls their initialization methods, hands them to consumers). Dependency Injection is one specific form of IoC — your class accepts what it needs, the container injects it.

---

### 2. Constructor, setter, or field injection — which and why?

Constructor injection, almost always. Three concrete reasons:

- **Required dependencies become required by the type system.** You cannot construct the object without supplying every argument. The compiler catches missing dependencies. With setter or field injection, the object can exist in a partially-built state where some fields are still null.
- **The fields can be `final`.** `final` is only possible when the field is assigned in the constructor. Final fields cannot be reassigned by accident, and the compiler enforces that — a small invariant that pays off when reading the code six months later.
- **Plain Java tests can construct the bean.** `new OrderService(new InventoryService(), new PriceCalculator())` works in a unit test without booting Spring. With field injection, the only ways to populate a private field are reflection or starting the container — both turn unit tests into something heavier.

Setter injection is acceptable for genuinely optional dependencies, but most things sold as "optional" are required at runtime and the optionality just hides bugs. Field injection is concise and was idiomatic in older Spring tutorials, but it loses all three constructor-injection benefits in exchange for fewer lines — bad trade.

Two side-notes that interviewers like: (a) modern Spring (since 4.3) doesn't need `@Autowired` on a single constructor — the constructor is used automatically; (b) "I'd use field injection because constructor injection makes my constructor ugly" is a smell that the class has too many responsibilities; the ugly constructor is the *hint*, not the problem.

---

### 3. What is the default bean scope, and what happens when you inject a prototype-scoped bean into a singleton?

Default scope is **singleton** — one instance per container, shared by every consumer. The container instantiates the bean once at startup, caches it, and hands the same instance to anyone who asks via `getBean(...)`. Important consequence: singleton beans must be thread-safe, since the same instance can be invoked by many threads at once.

If you inject a `@Scope("prototype")` bean directly into a singleton's constructor, Spring resolves the dependency exactly *once* — when the singleton is being constructed. From that point on, the singleton holds one reference to one prototype instance, forever. The prototype scope on the dependency is silently ignored from inside the singleton; every call to the singleton's methods reuses the same prototype object. This is one of the most common surprises in Spring.

The fix is to inject an `ObjectProvider<T>` (or `Provider<T>`, or use `@Lookup`) instead of the prototype directly. `ObjectProvider.getObject()` re-queries the container on each call, which honors the prototype scope and produces a fresh instance per invocation. The principle generalizes: when a singleton needs a "new one each time," inject a *factory*, not the value.

Worth knowing that "singleton" in Spring means *per container*, not the Gang-of-Four "one per JVM" — two `ApplicationContext`s in the same JVM produce two separate sets of singletons.

---

### 4. When would you use `@Primary` vs `@Qualifier`?

Both resolve the same problem: more than one bean matches a single injection point. The difference is *who decides* which to pick.

- **`@Primary` is producer-side.** You annotate one of the candidate beans as the default. Every consumer that asks for the type with no further qualification gets that default. Use it when there is a clear "most consumers want this one" answer — e.g., `@Primary` on `RealEmailNotifier`, while a separate `LoggingEmailNotifier` is used only by a couple of audit-specific consumers.
- **`@Qualifier` is consumer-side.** The injection point names the specific bean it wants. Use it when individual consumers know which variant they need, and there is no single "default." `@Qualifier("loggingNotifier") EmailNotifier notifier` reads as "I specifically want the logging one."

You can combine them: a `@Primary` provides a fallback default, and a `@Qualifier` overrides for specific consumers. If you find yourself adding `@Qualifier` to *every* injection of a type, that's a smell — there's no real default; consider whether the variants should be different types instead.

A bonus pattern: inject `List<Type>` (or `Map<String, Type>`) to get *every* matching bean. Right when you want a plugin-style "run all of them" or "look one up by name at runtime."

---

### 5. Walk through a bean's lifecycle from "Spring is starting" to "Spring is shutting down."

Per bean, the container walks these steps:

1. **Instantiate** — call the constructor. With constructor injection, dependencies are supplied as arguments.
2. **Inject** — for field/setter injection, this step populates the rest.
3. **`BeanPostProcessor.postProcessBeforeInitialization`** — framework hooks; rarely yours.
4. **Initialize** — `@PostConstruct` methods run, then `InitializingBean.afterPropertiesSet()` if implemented, then any `@Bean(initMethod = ...)`.
5. **`BeanPostProcessor.postProcessAfterInitialization`** — AOP proxies are wrapped here.

After step 5 the bean is *ready* and the container hands it out. At shutdown the container walks the destruction phase in **reverse dependency order**: `@PreDestroy` methods, then `DisposableBean.destroy()`, then `@Bean(destroyMethod = ...)`. A bean's `@PreDestroy` runs while all of its dependencies are still alive — Spring guarantees you can still call them during cleanup.

The reason `@PostConstruct` exists at all, separate from the constructor: dependencies are guaranteed wired by step 4, and a *failure* in initialization gets reported cleanly as "bean X failed to initialize: caused by ..." rather than getting tangled into business logic that calls `new`. It's also a clean signal in the code — "constructor builds the object; `@PostConstruct` makes it ready."

Caveat: prototype-scoped beans go through steps 1-5 but the container does not track them after. Their `@PreDestroy` is never called by Spring.

---

### 6. `@Component` vs `@Bean` — when do you use each?

Both register a bean; the question is which registration mechanism fits.

`@Component` (and its specializations `@Service`, `@Repository`, `@Controller`) goes on a class *you own*. The class is discovered by `@ComponentScan`, and its constructor is invoked automatically. This is the right default for the classes you wrote — services, repositories, components — because it minimizes boilerplate. The constructor signature documents the dependencies; Spring matches by type.

`@Bean` goes on a method inside a `@Configuration` class. The method body explicitly constructs the bean. Use `@Bean` when:

- **The class isn't yours.** You can't put `@Component` on `javax.sql.DataSource`, `RestTemplate`, `ObjectMapper`, or any library class. `@Bean` is the only way to register it as a bean.
- **Construction requires logic.** `new ObjectMapper().registerModule(...).disable(...)` doesn't fit a constructor — you need code to build the bean correctly.
- **You want multiple instances of the same class with different configuration.** Two `DataSource` beans for two databases, each with different settings.

Mixing both styles in one app is normal: `@Component` for your domain, `@Bean` for library types and configured beans. The container doesn't care which mechanism registered a bean once it's there.

Tangential nice-to-know: the stereotype annotations differ semantically, not behaviorally — `@Service` and `@Component` produce indistinguishable beans, but `@Service` reads as "business logic" to a reviewer. `@Repository` does add one runtime behavior (Spring translates vendor-specific DB exceptions into a common hierarchy), but that's a module-10 concern.

---

### 7. How does `@Value` resolve `${some.property}`? What are the property sources and their precedence?

`@Value("${some.property}")` is a *property placeholder* that Spring resolves at the time the bean is constructed. The container looks up the key in its `Environment` — a layered collection of property sources — and converts the resulting string into the declared parameter type via the `ConversionService` (which knows about `int`, `long`, `BigDecimal`, `Duration`, `boolean`, comma-separated lists, etc.).

Default property sources, in **decreasing precedence**:

1. **JVM system properties** (`-Dkey=value` on the command line, or `System.setProperty(...)`).
2. **OS environment variables** (also matched in a normalized way: `MY_DATABASE_URL` matches the key `my.database.url`).
3. **`@PropertySource`-loaded files** (e.g., `classpath:application.properties`).

So a property set as a JVM `-D` flag *overrides* the same key in a file. This precedence is what lets ops adjust a value at deploy time without recompiling — set the override on the JVM, leave the file alone, the override wins.

A few important corollaries:

- `@Value("${foo:default}")` supplies a fallback when no source provides the key. Use it for anything that isn't strictly required, otherwise a missing property crashes the context.
- `@Value("#{...}")` is **Spring Expression Language** — a small expression evaluator, distinct from `${...}` which is a dumb lookup. SpEL is rarely needed in normal backend code; reach for it only when you actually need an expression.
- Spring Boot adds a richer set of sources (YAML, profile-specific files, command-line `--key=val`), but the precedence concept is the same.

---

### 8. What is a Spring profile? What happens if no profile is active?

A profile is a named "environment" — `dev`, `prod`, `test` — that controls which beans get registered. Annotate a bean with `@Profile("dev")` and it only registers when the `dev` profile is active. Activate a profile via `-Dspring.profiles.active=dev`, the `SPRING_PROFILES_ACTIVE` env var, or programmatically with `context.getEnvironment().setActiveProfiles(...)`.

The point is to express environment-specific variants — in-memory store vs real database, console notifier vs SMTP, debug logger vs production logger — without `if (env.equals("prod"))` scattered through your code.

What happens with **no profile active**: beans without `@Profile` register normally; beans with any `@Profile(...)` annotation are skipped. If every implementation of an interface is `@Profile`-restricted and none of the relevant profiles are active, the container starts but consumers that need that interface fail with `NoSuchBeanDefinitionException`. The fix is either to set a profile or to provide a non-profile-restricted default (often with `@Profile("default")`, which activates implicitly when no other profile is set).

Common pitfall: profile names are unchecked strings — `@Profile("prod")` and `-Dspring.profiles.active=produciton` (typo) silently fail to match. Define profile names as constants and reference the constant in both places, so a typo only happens once.

Don't model feature flags as profiles. Profiles are coarse-grained "which environment." Per-feature toggles want a proper flag system.

---

### 9. How does Spring AOP work, and what are its limitations?

Spring AOP works through **runtime proxies**. When `@EnableAspectJAutoProxy` is active, Spring scans the container for `@Aspect` beans, identifies which beans have methods matching any aspect's pointcut, and wraps each matching bean in a proxy. The proxy delegates to the real bean but intercepts every external call to apply the matching advice (`@Before`, `@Around`, `@After`, `@AfterReturning`, `@AfterThrowing`).

Pointcut expressions (`execution(* com.example..*Service.*(..))`) pick which method invocations to advise. `@Around` is the most powerful — you get a `ProceedingJoinPoint`, decide whether to call `proceed()`, can modify arguments or return values. The other advices are narrower and more readable for their specific case.

Two limitations come from "it's a proxy":

1. **Only methods reachable through the proxy are advised.** The proxy intercepts public method calls from outside the bean. `private` methods, `static` methods, and methods on objects you constructed yourself with `new` are not advised.
2. **Self-invocation is not advised.** If `place()` inside `OrderService` calls `this.logAndCreate()`, that call uses the real instance, not the proxy. The aspect targeting `logAndCreate` does not fire. The standard workaround is restructure — move the inner method to a different bean so the call goes through the proxy.

These limitations matter because Spring's `@Transactional` is itself implemented as AOP advice. The same proxy-based caveats apply: a `@Transactional` method called via `this.foo()` does *not* open a transaction.

For most backend concerns AOP is the right shape: timing, logging, security checks, audit, transactions. It becomes a foot-gun when used to hide important logic ("now you can't tell what this method does without reading three aspects"). Reserve it for genuinely cross-cutting concerns that should be uniform.

---

### 10. Application events vs direct method calls — when and why?

A direct method call is the right shape when the dependency is *intrinsic* — `OrderService` calls `repository.save(order)` because saving is part of placing an order. Direct calls are easy to read, easy to trace in a stack, and the dependency is explicit in the constructor.

Application events fit when you have *multiple consumers* of "something happened" and the producer shouldn't be responsible for knowing about all of them. `OrderService` publishes `OrderPlacedEvent`. An `AuditLogger`, `EmailNotifier`, `MetricsRecorder`, and `RecommendationsUpdater` each `@EventListener` the event. `OrderService` knows nothing about any of them — and adding a fifth listener doesn't require editing `OrderService`.

The cost of events is traceability. A stack trace from inside an event listener won't include the publisher's frame in the natural way, and "who handles this event?" becomes a search for `@EventListener(OrderPlacedEvent.class)` instead of "follow the method call." For a single downstream concern that will never grow, direct call wins.

Events should describe *what happened*, not *what should be done*. `OrderPlacedEvent` is right; `SendConfirmationEmailEvent` is a method call disguised as an event — the name reveals the publisher is dictating downstream behavior. If you find yourself naming events as commands, you're using events to do too much.

Sync vs async: events are synchronous by default, meaning every listener runs on the publisher's thread before `publishEvent` returns. That's right for "audit before reporting success." It's wrong when a listener does slow IO. The fix is `@Async` plus `@EnableAsync` — covered properly in module 08.

---

### 11. What is the difference between `BeanFactory` and `ApplicationContext`?

`BeanFactory` is the core container interface — defines `getBean(...)`, scope handling, bean instantiation. `ApplicationContext` extends `BeanFactory` and adds the features you actually use in real applications: `@PropertySource` and `Environment`-based property resolution, internationalization (`MessageSource`), the application event publishing/listening model, and integration with Spring's `Resource` abstraction.

In practice you always use `ApplicationContext` (`AnnotationConfigApplicationContext`, `ClassPathXmlApplicationContext`, etc.). `BeanFactory` was the original container interface from Spring 1.x and exists for backward compatibility and for the very rare case where you want a tiny, lazy-init-only registry. Modern Spring docs treat `ApplicationContext` as the default; `BeanFactory` shows up in the API as a parent type, not as something you instantiate.

Two trivia notes that interviewers occasionally ask: `BeanFactory` originally supported lazy instantiation by default (beans were built on first `getBean`), while `ApplicationContext` eagerly initializes singleton beans at startup. And: every `ApplicationContext` is-a `BeanFactory`, so methods declared on `BeanFactory` work either way.

---

### 12. How does Spring handle circular dependencies?

Spring detects and works around *some* circular dependencies — specifically, two singleton beans that depend on each other via setter or field injection. The container creates one bean, exposes a half-built reference, injects it into the other, then completes the first. This works because setters/fields can be assigned after construction.

It does **not** handle constructor-injected circular dependencies. Two beans whose constructors require each other create a chicken-and-egg problem with no resolution. The context fails to start with a clear error message naming the cycle.

What you should actually do when you hit a circular dependency: treat it as a design signal, not a problem to work around. A cycle usually means one of these:

- Two classes have overlapping responsibilities that should be merged.
- A third "coordinator" class should be extracted, and both original classes should depend on *it*.
- One direction of the dependency should become an event publication instead of a direct call (chapter 11).

Reaching for setter injection just to make a circular dependency compile is almost always the wrong choice — you've made the design problem invisible, not solved it. Refactor.

(For completeness: `@Lazy` is another workaround. Marking one of the cyclic dependencies as `@Lazy` makes Spring inject a lazy proxy that resolves on first use. Works; same caveat — addresses the symptom, not the design.)
