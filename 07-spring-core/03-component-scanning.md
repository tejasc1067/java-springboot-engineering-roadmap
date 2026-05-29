# Component Scanning

Topic 02 registered every bean by writing a `@Bean` method for it. For five classes that's tolerable; for a real codebase it would mean a hundred-line `AppConfig` whose job is just to call constructors. **Component scanning** is the shortcut: put `@Component` (or one of its specializations) on the class itself, then `@ComponentScan` tells Spring to find them automatically.

By the end of this topic you can pick a sensible mix of `@Component`, `@Service`, `@Repository`, and `@Controller`, and know when to fall back to `@Bean`.

---

## The problem this solves

`AppConfig` from topic 02:

```java
@Bean public InventoryService inventoryService()   { return new InventoryService(); }
@Bean public PriceCalculator  priceCalculator()    { return new PriceCalculator(); }
@Bean public EmailNotifier    emailNotifier()      { return new ConsoleEmailNotifier(); }
@Bean public OrderRepository  orderRepository()    { return new InMemoryOrderRepository(); }
```

Every method does the same thing: `return new TheClass()`. The class name is in the return type *and* the method name *and* the constructor call. There is no decision being made here — the only reason these methods exist is to register the bean. We can remove them by letting Spring scan the classpath and find every class marked as "yes, I'm a bean."

## How it works

Two ingredients.

**On the classes you want as beans**, add a stereotype annotation. The four built-in options:

```java
@Component                       // generic; "this is a bean"
@Service                         // business logic / use cases
@Repository                      // data access (and gets some extra exception translation later, see topic 06 in module 10)
@Controller                      // web request handlers (used by Spring MVC in module 09)
```

**On your configuration class**, add `@ComponentScan`:

```java
@Configuration
@ComponentScan(basePackages = "com.example")
public class AppConfig { }
```

That's it. The bodies of all those `@Bean` methods are gone. The container, when it starts, will:

1. Walk the classpath under `com.example` and its subpackages.
2. Find every class annotated with `@Component` or any annotation that itself has `@Component` on it (`@Service`, `@Repository`, `@Controller`, and `@Configuration` all do).
3. Register one bean per class. The bean's name defaults to the class name with the first letter lowercased (`InventoryService` -> `inventoryService`).
4. Instantiate each one, satisfying constructor parameters from the other beans it found — the same way it would for `@Bean` methods.

Same outcome, half the typing.

## The four stereotype annotations

All four register a bean. They differ in *intent*, not behavior — `@Service` and `@Component` produce indistinguishable beans for almost all purposes. Use them for what they communicate:

| Annotation | Use it on | Why this label |
|--|--|--|
| `@Component` | anything bean-worthy that doesn't fit the others | the generic fallback |
| `@Service` | a class that holds business logic — what your application actually *does* | reads "this is a service" to anyone scanning the codebase |
| `@Repository` | a class that hides persistence behind a domain-shaped interface | also activates Spring's translation of vendor-specific DB exceptions into a common hierarchy (relevant in module 10) |
| `@Controller` | a class that handles HTTP requests | activated by Spring MVC's scanner in module 09 |

A grey-area example: is `EmailNotifier` a service or a component? It does no business logic; it's an outbound adapter. Use `@Component` and move on. The cost of picking the "wrong" stereotype is essentially zero — *not* using one at all is the only mistake that matters.

## Constructor injection happens for free

With `@Bean` methods you had to write out the constructor call by hand. With `@Component`-scanned classes, Spring inspects the class's constructors and uses them automatically.

The rule: **if a class has exactly one constructor, Spring uses it without you having to ask**. No `@Autowired` annotation needed.

```java
@Service
public class OrderService {

    private final InventoryService inventory;
    private final PriceCalculator priceCalculator;
    private final EmailNotifier notifier;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventory,
                        PriceCalculator priceCalculator,
                        EmailNotifier notifier,
                        OrderRepository orderRepository) {
        this.inventory = inventory;
        this.priceCalculator = priceCalculator;
        this.notifier = notifier;
        this.orderRepository = orderRepository;
    }

    // ...
}
```

That's the entire wiring. Spring will:

- See `OrderService` is `@Service` -> register a bean.
- See it has one constructor -> use that to build it.
- See the constructor's parameter types -> look up beans matching each, found because they're all `@Component`-family annotated.
- Pass them in. Done.

You did not write `@Autowired` anywhere. You don't need to. (You'd only need `@Autowired` to disambiguate when a class has *more than one* constructor and you have to tell Spring which one to use.)

## When `@Bean` is still the right choice

`@Component` works only on classes *you can edit*. You can't put `@Repository` on `javax.sql.DataSource` — that's a JDK interface. The same problem applies to anything from a third-party library: `ObjectMapper` from Jackson, `RestTemplate` from Spring itself, an SDK class.

For those, `@Bean` is the answer:

```java
@Configuration
@ComponentScan(basePackages = "com.example")
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

Mixing the two styles is **the** normal pattern in real codebases:

- `@Component`/`@Service`/`@Repository` for your own classes (most of them).
- `@Bean` methods for library types and for any bean whose construction needs logic beyond `new X()`.

The two styles coexist in one container. Spring doesn't care which mechanism registered a bean once it's there.

## How scan paths work

`@ComponentScan(basePackages = "com.example")` is the explicit form. There's also a shorthand:

```java
@Configuration
@ComponentScan          // no argument
public class AppConfig { }
```

With no `basePackages`, Spring scans the package that `AppConfig` itself is in, plus every subpackage. In practice this is what most codebases use — you put `AppConfig` (or in Spring Boot, your `@SpringBootApplication` class) at the top of your package tree, and the scan covers everything under it.

You can also include or exclude by annotation or regex:

```java
@ComponentScan(
    basePackages = "com.example",
    excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = LegacyModule.class)
)
```

Useful occasionally; not the default.

## Common pitfalls

- **Annotated the class but forgot `@ComponentScan`.** The class sits there with `@Service` and nobody scans it. Spring will fail with `NoSuchBeanDefinitionException` when something tries to inject it. Either add the scan, or put `AppConfig` at the right level so the default scan covers the class.
- **Annotated an interface.** `@Service` on `EmailNotifier` (the interface) does nothing. Interfaces can't be instantiated. Annotate the implementation (`ConsoleEmailNotifier`).
- **Put `@Component` on a class with a primitive-typed constructor parameter.** `public PriceCalculator(double taxRate)` — Spring can't supply a primitive `double` from the container; there's no `Double` bean. Either give the class a no-arg default, use `@Value("${tax.rate}")` on the parameter (topic 08), or register it via `@Bean` instead.
- **Two `@Component`-scanned classes implement the same interface.** Spring registers both, then panics when a single dependency asks for "an `EmailNotifier`." Topic 05 covers the fix (`@Qualifier`, `@Primary`).
- **Annotated a value object.** `Order` is a `record` with no behavior. Don't `@Component` it. Value objects are constructed by your code with `new`, passed around, and forgotten. They aren't beans.

## Components that *don't* get scanned

These are not scannable, no matter how hard you wish:

- **Anonymous classes and lambdas.** No name, no class file -> not visible to the scanner.
- **Classes in a package outside your `basePackages`.** Including library classes.
- **Inner non-static classes.** They depend on an enclosing instance; the scanner gives up.

---

## Try this yourself

1. Open `scan-demo`. Comment out `@Service` on `OrderService`. Re-run the tests. Read the `NoSuchBeanDefinitionException` carefully -- it names exactly what's missing.
2. Restore the annotation. Now comment out the entire `@ComponentScan` line in `AppConfig`. Re-run. The error is different: `OrderService` is now defined nowhere, *and* nothing has been scanned. Notice which error appears first.
3. Add a new bean: a `LoggingNotifier` that also implements `EmailNotifier`. Annotate it with `@Component`. Re-run the tests. The container will now have *two* `EmailNotifier` beans and `OrderService` won't know which to inject. Read the error. Don't fix it -- topic 05 is about fixing it.

## Self-check

1. What's the difference, in observable behavior, between `@Service` and `@Component`? Why use one over the other if behavior is the same?
2. Why does `@Bean` still exist if `@Component` is shorter? Give one concrete situation where `@Component` cannot be used.
3. Why is no `@Autowired` annotation required on `OrderService`'s constructor here, when older Spring tutorials show it everywhere?

---

## Code examples

In reading order:

- `scan-demo/src/main/java/com/example/{InventoryService,PriceCalculator,ConsoleEmailNotifier,InMemoryOrderRepository,OrderService}.java` -- each now carries a `@Service`/`@Component`/`@Repository` annotation. Constructors are unchanged.
- `.../AppConfig.java` -- now just `@Configuration` + `@ComponentScan`. The `@Bean` methods are gone.
- `.../Main.java` -- identical to topic 02; the container looks the same from the outside.
- `src/test/java/com/example/ScanTest.java` -- asserts that the scanned beans are present, of the right type, and properly wired.

Run with:

```bash
mvn -q compile exec:java     # boots the scanned container, places one order
mvn -q test                  # verifies the scan picked up everything
```
