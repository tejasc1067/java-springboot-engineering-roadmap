# Bean Lifecycle: @PostConstruct and @PreDestroy

A bean is not just "an object Spring made." It has a small lifecycle: built, wired, initialized, ready, eventually destroyed. Most of the time you don't have to care; the container manages it and your bean is just "there." This topic is about the two hooks you do reach for — running setup code once the bean is ready, and tearing things down when the container shuts down.

By the end you can pick which lifecycle hook fits your need, write the code, and explain why a constructor is the wrong place for "load this at startup."

---

## The container's phases, for a single bean

When Spring builds a bean, it walks five steps:

1. **Instantiate.** Call the constructor.
2. **Inject dependencies.** Set fields if using field injection; call setters if using setter injection. (Constructor injection collapses 1 and 2 into one step.)
3. **`BeanPostProcessor`s, before init.** Framework-internal hooks. You will almost never write one yourself.
4. **Initialize.** Run any `@PostConstruct`-annotated methods, then `InitializingBean.afterPropertiesSet()` if implemented, then any `@Bean(initMethod = "...")` method.
5. **`BeanPostProcessor`s, after init.** This is where AOP proxies get wrapped around the bean (topic 10).

After step 5 the bean is **ready**. Container hands it out to whoever asks via `getBean(...)`.

When the container closes — `context.close()` in your `main`, or `SIGTERM` to a long-running app — it walks the destruction phase in reverse dependency order:

6. **`@PreDestroy`** methods.
7. **`DisposableBean.destroy()`** if implemented.
8. **`@Bean(destroyMethod = "...")`** if declared.

Prototype-scoped beans are an exception: Spring builds them and hands them out, but does **not** track them after that. `@PreDestroy` on a prototype bean is never called by the container.

## `@PostConstruct`: initialize once, after wiring

```java
import jakarta.annotation.PostConstruct;

@Component
public class WarmedCache {

    private final Map<String, String> entries = new HashMap<>();

    @PostConstruct
    public void warm() {
        entries.put("ABC", "alphabet");
        entries.put("XYZ", "ending");
        System.out.println("WarmedCache: warmed " + entries.size() + " entries");
    }

    public String get(String key) { return entries.get(key); }
}
```

When the container builds `WarmedCache`, the constructor runs first; *then* `warm()` runs. By the time anyone calls `get(...)` the cache is already populated.

Three rules that make `@PostConstruct` work:

- **Annotated method must be `void` and take no parameters.** Spring will not figure out arguments for it.
- **Must be a non-static method.** Spring calls it on the bean instance, not on the class.
- **Must come from `jakarta.annotation.PostConstruct`.** (The old `javax.annotation` package still works in Spring 6 but is on its way out. Use `jakarta`.)

`@PostConstruct` runs exactly once per bean instance, after every dependency is fully resolved. That is the contract; build your initialization on top of it.

## `@PreDestroy`: clean up at shutdown

```java
import jakarta.annotation.PreDestroy;

@Component
public class LifecycleResource {

    private boolean open;

    @PostConstruct
    public void open() {
        this.open = true;
        System.out.println("LifecycleResource: opened");
    }

    @PreDestroy
    public void close() {
        this.open = false;
        System.out.println("LifecycleResource: closed");
    }
}
```

When `context.close()` runs, `LifecycleResource.close()` runs. In a long-running app this is hooked up to JVM shutdown — Spring Boot does it for you; in a plain `main`, the `try-with-resources` around `AnnotationConfigApplicationContext` does it (topic 02 already used this).

`@PreDestroy` is for *clean* resource shutdown — closing files, returning connections to a pool, flushing buffered work, cancelling scheduled tasks. It is **not** a substitute for `finally` blocks inside business logic; per-method resources still belong in `try-with-resources`.

If the JVM is killed (`SIGKILL`, OOM, power loss), `@PreDestroy` does not run. Anything you write here should be a best-effort tidy-up, not a correctness requirement.

## Why not just the constructor?

You could in principle put `warm()`'s contents inside `WarmedCache`'s constructor. Three reasons not to:

1. **A class with dependencies still needs them set.** With constructor injection the dependencies *are* set, so a constructor body can already use them. But initialization logic often mixes with the *construction* logic and obscures what is invariant from what is one-time setup. Separating "build the object" from "ready the object" reads cleaner.
2. **Init logic can fail meaningfully.** A `@PostConstruct` that throws causes the *bean creation* to fail with a clear message: this bean's initialization threw, here is the cause. A constructor that throws can show up at the call site of any `new` (in tests, in factories) — less clean.
3. **AOP proxies aren't around yet.** If you decide to wrap `WarmedCache` with an aspect (topic 10), the proxy is created in step 5 above. A `this.someMethod()` call from the *constructor* doesn't go through the proxy. A call from `@PostConstruct`... well, also doesn't (because step 5 is after step 4), but the convention of "init logic outside the constructor" pays off when you later move logic to a method that *does* run after step 5.

The honest summary: for most beans there's no observable difference. The convention exists so that when you need it later you don't have to refactor a constructor full of logic.

## Order of initialization and destruction

Spring guarantees:

- **Initialization is dependency-first.** If `Repository` constructor-injects `Database`, then `Database`'s `@PostConstruct` runs before `Repository`'s `@PostConstruct`. By the time your code runs, every dependency is fully initialized.
- **Destruction is reverse.** `Repository`'s `@PreDestroy` runs first, then `Database`'s. So when `Database.close()` runs, `Repository` is guaranteed to have already shut down — you can close the database without worrying about a repository still trying to query it.

You almost never need to tweak this. If you do, `@DependsOn("otherBean")` lets you force an ordering constraint that's not visible to Spring from the constructor signature.

## When `@PostConstruct` is wrong

`@PostConstruct` is a *blocking, synchronous* step in the container's startup. Whatever you do in it, every other bean — and the whole application — waits. So:

- **Don't run long network or IO calls in `@PostConstruct`.** A 30-second remote-config fetch in `@PostConstruct` is a 30-second startup. Defer with a background task or lazy initialization.
- **Don't call other Spring beans expecting *their* `@PostConstruct` to have run unless they're dependencies of yours.** It usually will have, but the only thing guaranteed is the dependency order — if you reach for a bean Spring doesn't know you need, you may catch it half-built.
- **Don't put business-logic side effects in `@PostConstruct`.** If `OrderService.warmUp()` calls `notifier.send(...)`, you've coupled "the container started" to "an email went out." Reserve `@PostConstruct` for pure setup.

## Legacy and library cases

Two alternatives to `@PostConstruct` / `@PreDestroy` you'll see in older or library-facing code:

- **`InitializingBean` / `DisposableBean` interfaces** — implement them and override `afterPropertiesSet()` / `destroy()`. Pre-annotation Spring. Don't use in new code; you couple your domain class to a Spring interface.
- **`@Bean(initMethod = "open", destroyMethod = "close")`** — for *third-party* classes whose lifecycle methods are already named (e.g., a `DataSource` with a `close()` method) and which you can't annotate. The bean-definition tells Spring "after building, call `open`; before destroying, call `close`."

When you control the class, use `@PostConstruct` / `@PreDestroy`. When you don't, fall back to the `@Bean(initMethod=..., destroyMethod=...)` form.

## Common pitfalls

- **Forgetting the `jakarta` import.** `javax.annotation.PostConstruct` is the old name; `jakarta.annotation.PostConstruct` is the modern one. Spring 6 honors both but the future is jakarta.
- **`@PostConstruct` on a prototype bean expecting destroy hooks.** Prototype beans don't fire `@PreDestroy`. The container builds them, hands them out, and forgets them.
- **Throwing a checked exception from `@PostConstruct` without handling.** The method declared `throws SomeCheckedException`, the framework can't catch your declared checked exception in a generic way -- and your bean fails to initialize with a less clear stack trace. Declare unchecked, or wrap.
- **Side effects in `@PostConstruct` you also want a test to observe.** Tests that boot a context will trigger `@PostConstruct`, including any println/log/IO. Either accept that or move the side effect out.
- **`@PreDestroy` that depends on the cleanup order being something other than what Spring chose.** Destruction runs in *reverse dependency order*. If your `@PreDestroy` calls into another bean that Spring already destroyed, you'll get an error. Almost never an issue in practice; mentioned for completeness.

---

## Try this yourself

1. Open `lifecycle-demo`. Run `mvn -q compile exec:java`. Watch the console: "opened" and "warmed" print before any application work, and "closed" / "cleared" print after the try-with-resources releases the context.
2. Comment out `@PostConstruct` on `WarmedCache.warm()`. Run the test. Now `entries` is empty and `get("ABC")` returns `null`. Notice the test fails -- not the container, just the consumer's expectation.
3. Throw a `RuntimeException` from `LifecycleResource.open()`. Re-run the tests. The container fails to start *with the cause clearly named* -- read the message. Now you know why init logic is a good place to validate config.

## Self-check

1. The constructor of a bean runs before its `@PostConstruct`. Why have two phases at all? Name two specific things that are different about what's safe to do in each.
2. A bean that consumes a `@Repository` calls `repository.findAll()` from its `@PostConstruct`. Is that safe? What is Spring guaranteeing?
3. Under which circumstance does `@PreDestroy` *not* run? Give an example of when you'd notice.

---

## Code examples

In reading order:

- `lifecycle-demo/src/main/java/com/example/WarmedCache.java` -- `@PostConstruct` loads data; `@PreDestroy` clears it.
- `.../LifecycleResource.java` -- minimal open/close pair; the test checks the resource is closed after the context closes.
- `.../AppConfig.java`, `.../Main.java` -- the `try-with-resources` around `AnnotationConfigApplicationContext` is what triggers `@PreDestroy`.
- `src/test/java/com/example/LifecycleTest.java` -- asserts the cache is warmed by the time the bean is reachable, and that the resource is open during the context and closed after.

Run with `mvn -q test` and `mvn -q compile exec:java`.
