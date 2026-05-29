# Bean Scopes

Every bean so far has been a *singleton* — one instance per container, shared by every consumer. That is the right default for almost everything and is what Spring gives you unless you say otherwise. Sometimes it isn't right: a stateful work-unit that needs a fresh copy per call, a per-HTTP-request context, a per-user session object. **Scope** is how you tell the container how many instances to make and how long each lives.

By the end you can pick between `singleton` and `prototype`, you recognize the named web scopes, and you can spot — and fix — the trap of putting a prototype-scoped bean inside a singleton.

---

## The default: singleton

```java
@Service
public class OrderService { ... }     // implicitly @Scope("singleton")
```

One instance per container. Every consumer of `OrderService` gets the same object. Every test that asks the container for `OrderService` gets the same object. This is what you want for stateless services — repositories, calculators, gateways. The container instantiates it once at startup and hands it out forever.

Important corollary: **singleton beans must be thread-safe**. The same instance can be invoked by many threads at once. Stateless services are trivially safe. A singleton bean with a mutable instance field is not.

"Singleton" in Spring means *one per container*, **not** "one per JVM" (that's the Gang-of-Four singleton pattern, which is a different idea). Two `ApplicationContext`s in the same JVM produce two separate sets of singletons.

## When singleton is wrong: prototype

A *prototype* bean is built fresh every time someone asks for it. The container doesn't cache it.

```java
@Component
@Scope("prototype")
public class WorkTicket {
    private final String id = UUID.randomUUID().toString();
    public String id() { return id; }
}
```

`context.getBean(WorkTicket.class)` returns a brand-new object every call:

```java
WorkTicket a = ctx.getBean(WorkTicket.class);
WorkTicket b = ctx.getBean(WorkTicket.class);
assertThat(a).isNotSameAs(b);
assertThat(a.id()).isNotEqualTo(b.id());
```

The constant for the scope name is `ConfigurableBeanFactory.SCOPE_PROTOTYPE` (`"prototype"`); the literal string is fine.

When is prototype the right choice? When the bean carries per-use state that must not bleed across uses — a builder you mutate, a form-binding holder, a per-request context object. Hint: if the bean has fields that get *assigned* (not just read) during its lifetime, prototype might be right. If the bean has only final fields and pure methods, you almost certainly want singleton.

## The big surprise: a singleton that has a prototype dependency

Now the trap. Put a prototype `WorkTicket` inside a singleton `Coordinator` the obvious way:

```java
@Component
public class BrokenCoordinator {
    private final WorkTicket ticket;

    public BrokenCoordinator(WorkTicket ticket) {   // injected ONCE
        this.ticket = ticket;
    }

    public String startWork() {
        return "starting with ticket " + ticket.id();
    }
}
```

What you might expect: each call to `startWork()` builds a fresh `WorkTicket`, because `WorkTicket` is prototype-scoped.

What actually happens: `BrokenCoordinator` is a singleton. Spring builds it once. To build it, Spring resolves the constructor parameter once — that gives `BrokenCoordinator` exactly *one* `WorkTicket` for its entire life. Every call to `startWork()` reuses that same ticket. The prototype scope on `WorkTicket` is invisible from inside the singleton.

```java
BrokenCoordinator c = ctx.getBean(BrokenCoordinator.class);
assertThat(c.startWork()).isEqualTo(c.startWork());   // SAME ticket -- the bug
```

This is one of the most frequently rediscovered surprises in Spring. The rule:

> **A singleton's dependencies are resolved once, at the singleton's construction.** Scope of the dependency doesn't change that.

## The fix: `ObjectProvider`

To get a fresh prototype each time, inject a *factory for it* instead of the prototype itself:

```java
@Component
public class FixedCoordinator {

    private final ObjectProvider<WorkTicket> tickets;

    public FixedCoordinator(ObjectProvider<WorkTicket> tickets) {
        this.tickets = tickets;
    }

    public String startWork() {
        WorkTicket fresh = tickets.getObject();     // asks the container EACH call
        return "starting with ticket " + fresh.id();
    }
}
```

`ObjectProvider<T>` is Spring's lazy lookup type. Each call to `.getObject()` goes back to the container and triggers a fresh `getBean(WorkTicket.class)`, which (because `WorkTicket` is prototype) builds a new instance. The coordinator stays a singleton; its tickets are fresh per call.

There are two other ways to achieve this (`@Lookup`-annotated methods, `Provider<T>` from JSR-330), both legitimate. `ObjectProvider` is the most readable Spring-idiomatic option in modern code and the one you should reach for first.

## When you'd actually use prototype

The honest answer: not often. Almost all backend services in a Spring app are stateless and singleton-friendly. Prototype scope is for the small set of cases where per-use state is intrinsic — a stateful builder, a request-scoped DTO assembler, a mutable accumulator. If you find yourself reaching for prototype frequently, look at whether your design is putting mutable state on services that shouldn't have it.

## The web scopes (preview)

When Spring is running inside a web framework, two extra scopes become available:

- **`request`** — one instance per HTTP request. Useful for per-request data like an audit-trail builder.
- **`session`** — one instance per HTTP session. Useful for per-user state held server-side.

These aren't usable in a plain `AnnotationConfigApplicationContext` like this module's demos — they need the web variant (`AnnotationConfigWebApplicationContext`). You'll meet them in module 09 (REST APIs). Recognize the names now so you don't think `@Scope("request")` is magic later.

(There's also `application` scope for per-`ServletContext` instances and `websocket` scope. Both rare; recognize the names and move on.)

## Custom scopes

You can register your own scope by implementing `org.springframework.beans.factory.config.Scope`. Useful exactly twice in a career, usually for things like "per-thread" or "per-tenant." Not worth a separate section here; if you need it, Spring's docs have a precise recipe.

## Common pitfalls

- **Prototype dependency inside a singleton, no `ObjectProvider`.** The classic trap shown above. The dependency is resolved once and the prototype scope is silently ignored.
- **Mutating a singleton from a request handler.** Two requests hit the same instance simultaneously and step on each other. Either make the bean stateless, or put the per-request data in a `request`-scoped bean.
- **Calling `getBean(...)` repeatedly to get a fresh prototype, inside hot code.** It works but pays the resolution cost on every call. Inject an `ObjectProvider` and call `getObject()` — same effect, clearer intent, sometimes cheaper.
- **Expecting `@Scope("prototype")` to give you a new bean every method call.** Only every `getBean` (or `ObjectProvider.getObject()`) call. The act of *calling a method* on an existing bean doesn't trigger a new instance.
- **Adding `@Scope("singleton")` "to be explicit."** It's the default. Adding it is noise; reviewers wonder if you meant something else.

---

## Try this yourself

1. Open `scopes-demo`. Run `mvn test`. Notice that `brokenCoordinatorReusesTheSameTicket` is a *passing* test -- it documents the bug as a confirmed observation, not a failure. The contrast is `fixedCoordinatorGetsAFreshTicketEachCall`.
2. Add `@Scope("prototype")` to `Counter`. Re-run. Notice that the singleton-counter test now fails -- two `getBean` calls return two `Counter`s, each starting at zero. Notice also that the *consumer* code didn't change; only the producer's scope did.
3. In `BrokenCoordinator`, change the constructor parameter type from `WorkTicket` to `ObjectProvider<WorkTicket>` and update `startWork()` to call `.getObject()`. Re-run -- the broken test now reports fresh tickets too. Notice that the fix is fully local to the consumer.

## Self-check

1. What is the precise definition of "singleton" in Spring? How is it different from the Gang-of-Four singleton pattern?
2. A `@Service` injects a `@Scope("prototype")` bean directly into its constructor. Each call to a service method uses the same prototype instance. Why? What's the standard fix?
3. Why are singleton beans required to be thread-safe?

---

## Code examples

In reading order:

- `scopes-demo/src/main/java/com/example/Counter.java` -- a default (singleton) bean that holds mutable state to demonstrate sharing.
- `.../WorkTicket.java` -- `@Scope("prototype")`; each instance has a unique UUID.
- `.../BrokenCoordinator.java` -- singleton that injects `WorkTicket` directly. Always reuses the same one.
- `.../FixedCoordinator.java` -- singleton that injects `ObjectProvider<WorkTicket>` and asks for a fresh one each call.
- `.../AppConfig.java`, `.../Main.java`
- `src/test/java/com/example/ScopesTest.java` -- asserts the singleton-sharing, the prototype-fresh behavior, the bug, and the fix.

Run with `mvn -q test` and `mvn -q compile exec:java`.
