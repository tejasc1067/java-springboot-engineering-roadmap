# Why Spring Exists, and the IoC Container

In module 06 you saw that classes are easier to test when they accept their dependencies instead of building them. The bill for that style is paid in `main`: somebody has to construct every object and hand each one its collaborators. For one class, that's a line. For fifty classes, it's an unreadable wiring file nobody wants to touch. Spring's **Inversion-of-Control container** does that wiring for you, plus a handful of related powers (lifecycle hooks, scopes, configuration, AOP) that follow naturally from "the framework owns the objects."

This topic is the *why*. Topic 02 shows the first working Spring app.

---

## The problem this solves

Here is a small but realistic graph of services for placing an order. Each class is fine — narrow, testable, depends on what it needs. The trouble is not the classes; it's getting them stood up.

```java
public class Main {
    public static void main(String[] args) {
        InventoryService inventory   = new InventoryService();
        PriceCalculator priceCalc    = new PriceCalculator(0.10);          // tax rate
        EmailNotifier notifier       = new ConsoleEmailNotifier();
        OrderRepository orderRepo    = new InMemoryOrderRepository();
        OrderService orders          = new OrderService(inventory, priceCalc, notifier, orderRepo);

        Order placed = orders.placeOrder("ada@example.com", List.of("BOOK-A", "PEN-B"));
        System.out.println("Placed order " + placed.id());
    }
}
```

Five classes, five lines. Manageable. But notice what happens next:

1. **You add a sixth service.** Say `AuditLogger`, used by `OrderService`. You add a constructor parameter to `OrderService`, then a line in `main`, then update *every test* that builds an `OrderService` by hand (probably ten of them).
2. **You need a second environment.** Production uses `SmtpEmailNotifier`; tests use a fake. You either copy-paste `main` into a `TestMain`, or you put a flag in `main` (`if (env.equals("test"))`), or you write your own factory class. Whichever you pick, the wiring is now *duplicated* — and the duplicates will drift.
3. **A dependency needs configuration.** The tax rate comes from `application.properties`. Now `main` has to read the file, parse the value, validate it, and pass it to `PriceCalculator`. Multiply by every configurable thing.
4. **Lifecycle.** `InMemoryOrderRepository` is fine, but the real one (later) opens a database connection at startup and must close it at shutdown. Where do you put that? In `main`, with a `try/finally`? Now `main` also owns lifetimes.
5. **Cross-cutting concerns.** You want every service call timed and logged. You either copy a `long start = System.nanoTime();` block into every method, or you write a wrapper class for each service — `LoggingOrderService` that delegates to a real `OrderService` — and now `main` builds both and hands the wrapper out.

None of these is hard in isolation. All of them together turn `main` into the most-changed file in the codebase, the one place wiring, environment switching, configuration loading, lifecycle management, and cross-cutting wrappers all collide. That collision is what the IoC container exists to remove.

## What an IoC container actually is

Three nouns, then we'll define the thing.

- **A bean** is an object whose creation and lifetime is managed by the container instead of by your code. In Spring, "bean" is just the word for "an object the container owns." It doesn't imply any special interface, base class, or naming convention — `OrderService` is a bean if Spring made it; the same class is "just an object" if you `new`'d it.
- **A configuration** is your description of which beans should exist. You can write that description as a Java class with `@Bean` methods (topic 02), or by sprinkling `@Component` on classes and letting Spring find them (topic 03), or by an XML file (legacy — we won't teach this).
- **The container** is the object that reads the configuration, builds every bean, hands each one its dependencies, keeps them around until shutdown, then disposes of them. In Spring its concrete name is `ApplicationContext`.

Put them together:

> The **IoC container** is a registry of objects that knows how to construct them, what to pass to their constructors, and when to dispose of them.

That's it. The "I" and "C" in IoC stand for "Inversion of Control" — we'll unpack that name at the end of this topic, because the name confuses more than it explains. The thing itself is unmagic: a registry-plus-builder, automated.

## A first peek at what changes

You haven't written any Spring yet (topic 02 is the first one). But here is the same wiring, declared instead of executed:

```java
@Configuration
public class AppConfig {
    @Bean InventoryService inventoryService() { return new InventoryService(); }
    @Bean PriceCalculator priceCalculator()   { return new PriceCalculator(0.10); }
    @Bean EmailNotifier emailNotifier()       { return new ConsoleEmailNotifier(); }
    @Bean OrderRepository orderRepository()   { return new InMemoryOrderRepository(); }

    @Bean OrderService orderService(InventoryService inv, PriceCalculator pc,
                                    EmailNotifier en, OrderRepository or) {
        return new OrderService(inv, pc, en, or);
    }
}
```

Two things to notice without yet understanding the syntax:

- Each `@Bean` method *describes* an object you want. It does not run when you call `new AppConfig()` — Spring runs them, in the right order, when you start the container.
- `orderService(...)` takes parameters. Spring sees those parameters and supplies the matching beans automatically. *You did not call `orderService(...)` yourself.* You wrote down what you want; Spring did the call.

That second point is the heart of the framework. By topic 03 even the `@Bean` methods mostly go away — you just put `@Component` on classes and the container finds them.

## Why not just a hand-rolled wiring helper?

A reasonable instinct on first hearing this is "I could write a class that does the wiring for me — `AppWiring.buildOrderService()`." You could, and for a tiny project that's fine. The reason a real framework exists is that the container also does several things you'd otherwise build yourself:

| What the container does | What you'd otherwise hand-roll |
|--|--|
| Constructs beans in the right order, even when the graph is deep | A topological sort or a fragile chain of `new` calls |
| Manages **scope**: usually one shared instance, sometimes a new one per request (module 09) | Static fields and `synchronized` blocks, or per-thread factories |
| Runs **lifecycle hooks** on startup and shutdown (`@PostConstruct`, `@PreDestroy`) | A `try/finally` in `main` for every resource, easily forgotten |
| Reads external **configuration** and injects values (`@Value`, `@PropertySource`) | Hand-parsed `.properties` files threaded through constructors |
| Switches bean variants per **environment** (`@Profile`) | `if (env.equals("prod")) ...` checks scattered through `main` |
| Adds **cross-cutting** behavior (logging, timing, transactions) without changing the called code (AOP) | A `LoggingOrderService` wrapper class for every service |
| Decouples publishers from subscribers (application events) | Direct calls between classes that shouldn't know about each other |

Every one of those is a topic in this module. Once you have a container, all of them become a few annotations instead of pages of boilerplate.

## What "Inversion of Control" means

The name is older than Spring and applies to many frameworks. The idea, in plain English:

- In a normal program, *your* code calls the libraries — `new`, `.add(...)`, `.save(...)`. You control the flow.
- In a framework, *the framework* calls your code. You declare classes; it decides when to create them, when to call them, when to dispose of them. Control has been *inverted* — given over to the framework.

"Dependency Injection" (DI) is one specific form of IoC: instead of a class fetching its dependencies, the container *injects* them by calling the constructor with the right arguments. When you read "IoC container" and "DI container" in Spring docs, they mean the same thing in this context. We'll use both interchangeably.

Don't fight the name. The thing is simple even when the name isn't.

## Common pitfalls (mental-model edition)

These are *not* code bugs — they're misconceptions to head off before topic 02.

- **"A bean is a special class."** No. A bean is just any object Spring built. A `String`, a `BigDecimal`, your own `OrderService` — anything Spring's container creates and tracks is a bean. The class itself is unchanged.
- **"`@Autowired` is magic."** It is not. It is "look up by type in the registry and pass it in." Once you've seen the container, `@Autowired` is the boring inverse of `@Bean`.
- **"Spring is heavy because of the container."** The container itself is small and fast. What people mean by "Spring is heavy" is usually Spring *Boot* — the auto-configuration that makes a hundred decisions for you. Module 08. The container in this module is the small part.
- **"I shouldn't make every class a bean."** Mostly right. Beans are for *services* — things that do work and have collaborators. Value objects (`Order`, `Money`, `LineItem`) should stay plain — you build them with `new` whenever you need one, and pass them around. The line in practice: if it has behavior and dependencies, it's probably a bean; if it just holds data, it isn't.

---

## Try this yourself

1. Open the `wiring-by-hand-demo` project and read `Main.java`. Add a sixth dependency to `OrderService` (say, `AuditLogger` that prints `"AUDIT: <message>"`). Count exactly how many lines you had to change to make the app compile and run again: in the class, in `Main`, in the test. That count is the cost.
2. Imagine the same exercise on a real codebase with 200 services. Write down one specific thing about that 200-service program that would be unpleasant — and check at the end of the module whether Spring fixes it.

## Self-check

1. In your own words, what is a *bean*? Is `new ArrayList<>()` a bean? Why or why not?
2. The IoC container does five things on top of wiring (scope, lifecycle, config, profiles, AOP, events). Pick two and describe the manual workaround a non-Spring app would use for them.
3. Why is "Inversion of Control" a more accurate label than just "Dependency Injection"? When are the two phrases interchangeable in casual conversation?

---

## Code examples

In reading order:

- `wiring-by-hand-demo/src/main/java/com/example/InventoryService.java` — narrow, stateless service.
- `.../PriceCalculator.java` — narrow, takes a tax rate via constructor.
- `.../EmailNotifier.java` and `.../ConsoleEmailNotifier.java` — interface and one implementation.
- `.../OrderRepository.java` and `.../InMemoryOrderRepository.java` — same pattern.
- `.../Order.java` — plain value object (NOT a bean).
- `.../OrderService.java` — depends on all four services above.
- `.../Main.java` — the hand-wiring; counts the lines so you don't have to.
- `src/test/java/com/example/OrderServiceTest.java` — a unit test that has to *re-do* the wiring. The duplication is the point.

Run with:

```bash
mvn -q compile exec:java         # runs Main
mvn -q test                      # runs the unit test
```

There is no Spring in this project on purpose. Topic 02 is the first Spring-flavored demo.
