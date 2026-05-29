# The Application Context and Beans

Topic 01 ended with a five-line wiring block in `main`. This topic is the first place that block goes away. You'll write a *configuration class* — Java code that *describes* the bean graph — and ask Spring to build it. The configuration looks similar to the hand-wired version, but Spring will read the method signatures, figure out the order, satisfy constructor parameters from other beans, and hand you the finished `OrderService` ready to use.

By the end of this topic you can boot a Spring container, retrieve a bean, and explain (in your own words) what `@Configuration` and `@Bean` actually do.

---

## The problem this solves

Hand-wired `main` from topic 01:

```java
InventoryService inventory     = new InventoryService();
PriceCalculator priceCalculator = new PriceCalculator(0.10);
EmailNotifier notifier          = new ConsoleEmailNotifier();
OrderRepository orderRepository = new InMemoryOrderRepository();
OrderService orderService       = new OrderService(
        inventory, priceCalculator, notifier, orderRepository);
```

Two complaints from topic 01:

- It is duplicated between `main` and every test setup.
- The order matters — `OrderService` last, because it needs the other four already built.

We will replace those five lines with a declarative description and let Spring handle both.

## How it works: a configuration class

Add **one** dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <version>6.2.18</version>
</dependency>
```

Then write `AppConfig.java`:

```java
@Configuration
public class AppConfig {

    @Bean
    public InventoryService inventoryService() {
        return new InventoryService();
    }

    @Bean
    public PriceCalculator priceCalculator() {
        return new PriceCalculator(0.10);
    }

    @Bean
    public EmailNotifier emailNotifier() {
        return new ConsoleEmailNotifier();
    }

    @Bean
    public OrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    @Bean
    public OrderService orderService(InventoryService inventory,
                                     PriceCalculator priceCalculator,
                                     EmailNotifier emailNotifier,
                                     OrderRepository orderRepository) {
        return new OrderService(inventory, priceCalculator, emailNotifier, orderRepository);
    }
}
```

Two annotations to understand:

- **`@Configuration`** marks the class as a source of bean definitions. When Spring sees a class tagged this way, it will scan every method on it and treat each `@Bean`-annotated method as a recipe.
- **`@Bean`** marks a method as a recipe for one bean. The method's return type is the bean's *type*. The method's name (`orderService`) is, by default, the bean's *name*.

Important: **you do not call these methods.** Spring does. Calling `new AppConfig().orderService(...)` directly would do nothing useful — your call wouldn't be tracked by any container.

Look at `orderService(...)`. It takes four parameters. Those are not arguments you pass; they're a *contract*: "to build an `OrderService` bean, please give me four other beans matching these types." Spring reads the parameter list, finds beans of those types in its registry, and invokes the method with them.

## Starting the container

The container is an object: `AnnotationConfigApplicationContext`. You construct it with your `@Configuration` class. From that point on, it knows every bean.

```java
public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService = context.getBean(OrderService.class);

            Order placed = orderService.placeOrder("ada@example.com", List.of("BOOK-A", "PEN-B"));
            System.out.println("Placed " + placed.id());
        }
    }
}
```

Three things just happened:

1. **`new AnnotationConfigApplicationContext(AppConfig.class)`** booted the container. It scanned `AppConfig`, found five `@Bean` methods, worked out the dependency order (singletons with no dependencies first, then `OrderService` which depends on them), and invoked the methods. By the time the constructor returned, all five beans existed.
2. **`context.getBean(OrderService.class)`** retrieved the already-built `OrderService`. It did *not* build a new one — the bean already existed; this looked it up by type.
3. **The `try-with-resources`** ensured the container was closed. `ApplicationContext` (specifically `ConfigurableApplicationContext`, which `AnnotationConfigApplicationContext` implements) is `AutoCloseable`. Closing it runs any `@PreDestroy` hooks on the beans (topic 07).

This is the entire payoff of `@Configuration` so far: you declared, the container executed. The wiring is now in one Java class instead of repeated in `main` and tests.

## What "the container did the wiring" actually looks like

Trace it in your head:

| Step | Spring does | Why |
|--|--|--|
| 1 | Reads `AppConfig`, sees five `@Bean` methods | because the class is `@Configuration` |
| 2 | Builds a dependency graph: `OrderService` needs the other four | by reading method parameter types |
| 3 | Calls `inventoryService()`, `priceCalculator()`, `emailNotifier()`, `orderRepository()` first | leaves with no dependencies |
| 4 | Calls `orderService(...)` last, passing the four already-built beans | dependencies are satisfied |
| 5 | Stores each returned object in its registry, keyed by type and name | so `getBean` can find them later |

Nothing magical. You wrote a recipe; Spring followed it in the order the parameters implied.

## Bean names: implicit and explicit

By default, a bean's name is the `@Bean` method's name. So `orderService()` registers a bean named `"orderService"` of type `OrderService`. You can look it up by either:

```java
context.getBean(OrderService.class);          // by type
context.getBean("orderService");              // by name (returns Object — usually cast)
context.getBean("orderService", OrderService.class);  // by name AND type (preferred)
```

You can override the name with `@Bean(name = "ordersFacade")` or by giving the method that name. We won't usually — the default is fine.

## Singleton by default

Calling `getBean(OrderService.class)` ten times returns the **same** instance ten times. That's because beans are *singleton-scoped* by default — one instance per container, shared by everyone who asks. Topic 06 covers when to deviate from this (prototype scope, web scopes), but the default is what you want most of the time.

A quick test, which the demo project includes:

```java
OrderService a = context.getBean(OrderService.class);
OrderService b = context.getBean(OrderService.class);
assertThat(a).isSameAs(b);                    // true — same object identity
```

This matters because singleton beans must be **thread-safe**. The container can hand the same instance to many threads simultaneously. Stateless services like `OrderService` are fine; if a bean holds mutable state, you'd need to guard it (or change scope).

## When `@Bean` methods call each other

You'll sometimes see one `@Bean` method call another to wire up a graph:

```java
@Bean OrderService orderService() {
    return new OrderService(inventoryService(), priceCalculator(),
                             emailNotifier(), orderRepository());
}
```

This *looks* like it would build a new `InventoryService` (and three others) every call. It does not. `@Configuration` classes are subclassed at runtime by Spring (a CGLIB proxy), and the proxy intercepts intra-class `@Bean` calls so they go through the container's registry — you get the cached singleton, not a fresh object.

The cleaner style, used in this module's demo and in most modern code, is to take the dependencies as method parameters (`orderService(InventoryService inv, ...)`) instead. Same result; no reliance on proxy interception; cleaner to test in isolation if you ever wanted to.

## `@Configuration` vs no annotation

If you ever drop `@Configuration` and use just `@Bean` methods inside a plain class (registered with `register(...)` on the context), Spring will not proxy the class. Intra-class `@Bean` calls will then build new objects, not look them up — and you'll get duplicates. Use `@Configuration` on config classes. It is the safe default.

## Common pitfalls

- **Forgetting `try-with-resources` on the context.** The container is `AutoCloseable`. If you don't close it, `@PreDestroy` hooks never fire, background threads may keep running, and short-lived programs leak. In a long-running web app Spring Boot closes it for you on shutdown; in a `main`, you close it yourself.
- **Calling `@Bean` methods directly from your code.** `context.getBean(...)` looks up an existing bean; `new AppConfig().orderService(...)` builds a brand-new orphan that the container doesn't know about. The orphan has no lifecycle, no scope, no AOP — none of the container's services apply.
- **Expecting `getBean(SomeInterface.class)` to fail because the bean is declared as the implementation.** It won't — `@Bean InventoryService inventoryService()` registers a bean *of type* `InventoryService` (the declared return type). Spring resolves by *type* including supertypes. If you return `InMemoryOrderRepository` from a method declared as `OrderRepository orderRepository()`, lookups by either type usually work, but the declared type is the public one.
- **Two `@Bean` methods with the same return type and no name disambiguation.** Topic 05 covers what to do; the short version is "Spring will throw `NoUniqueBeanDefinitionException` at the dependency site that tries to inject by type."

---

## Try this yourself

1. In the demo project, after retrieving `OrderService`, also retrieve `EmailNotifier` from the context and `assertThat(notifier).isInstanceOf(ConsoleEmailNotifier.class)`. Notice that the bean was *declared* as `EmailNotifier` but the container is happy to return it as either the interface or the implementation type.
2. Comment out `@Configuration` on `AppConfig`. Re-run. Read the error. Re-enable it. Read your understanding of what `@Configuration` actually adds beyond just having `@Bean` methods.
3. Add a sixth `@Bean` — an `AuditLogger` that wraps a `Logger` — and inject it into `OrderService` (you'll modify `OrderService`'s constructor too). Notice that `main` did not change. The wiring change happened in `AppConfig`. That separation is the point.

## Self-check

1. What does `@Configuration` mark a class as? What does `@Bean` mark a method as? Who calls those methods at runtime — you or Spring?
2. The `orderService(...)` `@Bean` method takes four parameters. Where do those values come from? What would happen if one of the four had no matching bean?
3. Why is `try-with-resources` on the `ApplicationContext` not just polite, but actually important?

---

## Code examples

In reading order:

- `context-demo/src/main/java/com/example/{InventoryService,PriceCalculator,EmailNotifier,ConsoleEmailNotifier,OrderRepository,InMemoryOrderRepository,Order,OrderService}.java` — same domain as topic 01, no changes.
- `.../AppConfig.java` — the new file. Five `@Bean` methods.
- `.../Main.java` — boots the context, retrieves `OrderService`, places one order, closes the context.
- `src/test/java/com/example/AppConfigTest.java` — asserts that the container produced the expected beans, that `OrderService` is a singleton, and that the same instance is returned across calls.

Run with:

```bash
mvn -q compile exec:java     # runs Main
mvn -q test                  # runs the wiring assertions
```
