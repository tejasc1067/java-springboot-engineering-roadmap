# Spring AOP: Cross-Cutting Concerns

Sometimes the same code shows up in twenty places: "log when entering this method," "time how long it took," "check the user is allowed," "open a transaction, commit on success, roll back on exception." That repetition is not your code's fault — it's *cross-cutting* by nature, orthogonal to the business logic. **Aspect-Oriented Programming** is a way to declare those concerns once and have the framework apply them to every method that matches, without changing the methods themselves.

By the end of this topic you can write an aspect that times every service-method call, read the pointcut expression that picks which methods get timed, and recognize what Spring AOP can and cannot do.

---

## The problem this solves

Imagine you want to know how long every `*Service` method takes. The hand-rolled version:

```java
public Order placeOrder(String customer, List<String> items) {
    long start = System.nanoTime();
    try {
        // ...real logic...
        return order;
    } finally {
        log.info("placeOrder took {} ns", System.nanoTime() - start);
    }
}
```

Now do that on every method of every service. The boilerplate dwarfs the logic, the timing concern is tangled into business code, and the day someone wants to *also* record the user's identity you edit a hundred methods.

A Spring AOP **aspect** moves the timing into one place. The methods themselves stay unchanged:

```java
@Aspect
@Component
public class TimingAspect {

    @Around("execution(* com.example..*Service.*(..))")
    public Object time(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();             // run the real method
        } finally {
            long ns = System.nanoTime() - start;
            System.out.println(pjp.getSignature().toShortString() + " took " + ns + " ns");
        }
    }
}
```

That one aspect now wraps every method on every bean whose class name ends in `Service`. The services don't know it exists. You can turn the timing off by deleting one annotation.

## The vocabulary

Four words from the AOP lexicon, used precisely in Spring:

- **Aspect** — a class with `@Aspect`. Holds one or more pieces of advice.
- **Advice** — the code you want to run, plus when it should run (`@Before`, `@After`, `@Around`, `@AfterReturning`, `@AfterThrowing`).
- **Pointcut** — an expression that picks which method invocations the advice applies to.
- **Join point** — a single matched method invocation. In Spring AOP, join points are *method invocations only* (no field access, no constructors).

You don't need to memorize the words for them to make sense in code; the labels just help when reading docs.

## How to turn AOP on

Two ingredients in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aop</artifactId>
    <version>6.2.18</version>
</dependency>
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
    <version>1.9.25.1</version>
</dependency>
```

`spring-aop` is already transitively pulled in by `spring-context`, but declaring it makes the version explicit at the point you start using it. `aspectjweaver` brings the `@Aspect`, `@Around`, etc. annotations and the pointcut expression parser — Spring borrows AspectJ's *annotations and language*, even though Spring uses its own proxy-based runtime (not AspectJ weaving).

One annotation on the configuration:

```java
@Configuration
@ComponentScan(basePackages = "com.example")
@EnableAspectJAutoProxy
public class AppConfig { }
```

`@EnableAspectJAutoProxy` tells Spring to find `@Aspect`-annotated beans and wrap matching beans in proxies that route through them. After this, any service whose methods match an aspect's pointcut gets its calls intercepted at runtime.

## The five advice annotations

| Annotation | Runs |
|--|--|
| `@Before` | before the method runs |
| `@AfterReturning` | after the method returns normally |
| `@AfterThrowing` | after the method throws |
| `@After` | after the method returns or throws (like a `finally`) |
| `@Around` | wraps the method; you decide whether to call `pjp.proceed()` |

`@Around` is the most powerful and the only one that can *prevent* the method from running, *modify* its arguments, or *substitute* its return value. Pick the narrowest advice that does what you need: `@Before` reads cleaner than `@Around` for "just log before"; `@Around` is right when you actually need the timing or the chance to short-circuit.

```java
@Before("execution(* com.example..*Service.*(..))")
public void logBefore(JoinPoint jp) {
    System.out.println("entering " + jp.getSignature().toShortString());
}

@AfterThrowing(pointcut = "execution(* com.example..*Service.*(..))", throwing = "ex")
public void onError(JoinPoint jp, Throwable ex) {
    System.out.println("ERROR in " + jp.getSignature().toShortString() + ": " + ex);
}
```

`JoinPoint` (in `@Before`/`@After...` advices) lets you read the signature and arguments. `ProceedingJoinPoint` (only in `@Around`) is the same plus the `.proceed()` method that actually invokes the wrapped method.

## Pointcut expressions, in 30 seconds

The expression that goes inside the advice annotation tells Spring which methods to match. The most common form is `execution(...)`:

```
execution( <return-type> <package>..<class>.<method>(<args>) )
```

A few worked examples:

| Expression | Matches |
|--|--|
| `execution(* com.example..*Service.*(..))` | any method, any return type, on any class whose package is under `com.example`, class name ends in `Service`, any arguments |
| `execution(public * com.example.OrderService.placeOrder(..))` | only `OrderService.placeOrder`, regardless of parameters |
| `execution(* place*(..))` | any method starting with `place` (rarely a good idea -- too loose) |
| `within(com.example..*)` | any method on any class in `com.example` and subpackages |
| `@annotation(com.example.Audited)` | any method annotated with `@Audited` |

`..` between packages means "this package and any subpackage." `(..)` in the parameter list means "any arguments, any number." `*` is a wildcard for one thing — one return type, one method name, one class.

Most production aspects use either `execution(... *Service.*(..))` (a naming convention) or `@annotation(SomeMarker)` (an opt-in). Both are precise about *intent*; the wildcard versions can match more than you wanted.

You can name pointcuts to reuse them:

```java
@Pointcut("execution(* com.example..*Service.*(..))")
public void serviceMethods() {}

@Around("serviceMethods()")
public Object time(ProceedingJoinPoint pjp) throws Throwable { ... }
```

Naming pays off when the same pointcut is referenced from multiple advices.

## What Spring AOP can and cannot do

Spring AOP works by wrapping the bean in a proxy. That has two consequences worth remembering:

1. **Only public methods on Spring-managed beans are intercepted.**
   A `private` method, a static call, or a method on an object you `new`'d yourself — none of those go through the proxy and none of them get advised.
2. **Self-calls inside the same bean are not intercepted.**
   ```java
   @Service
   public class OrderService {
       public Order place() { return logAndCreate(); }       // proxy intercepts THIS
       public Order logAndCreate() { ... }                    // direct call, NOT intercepted
   }
   ```
   When `place()` calls `logAndCreate()`, the call uses `this.logAndCreate()` — it bypasses the proxy. The aspect doesn't fire on `logAndCreate()`. If you need the inner method advised, you have to call it through the proxy (inject the service into itself) or split the methods across classes.

If neither limitation is acceptable, full AspectJ load-time weaving exists. It's a separate setup with separate trade-offs and is rarely needed in standard backend code.

## When AOP earns its keep

Three cases where AOP is unambiguously good:

- **Cross-cutting and uniform.** Timing every service call, logging every controller request, asserting a security role on every `@Audited` method.
- **Already declared via an annotation.** `@Transactional` is the canonical example: you mark methods, Spring's `@Transactional` aspect opens/commits/rolls-back transactions around them. No business code knows about the transaction.
- **A behavior the business code should not even hear about.** Audit logging, metrics, retries on specific exceptions.

Three cases where AOP becomes a foot-gun:

- **Replacing well-named explicit calls with implicit magic.** If a reader can't tell from the code that something is happening, debugging it later is harder. Reserve AOP for the truly uniform.
- **Pointcut expressions that match more than you intended.** `execution(* save*(..))` matches `save`, `saveAll`, `saveDraft`, `saveToFile` — most of which you didn't mean. Prefer narrow expressions or marker annotations.
- **Heavy logic inside the advice.** Long-running advice slows every matched call. Keep advice fast and side-effect-free where possible.

## Common pitfalls

- **Forgot `@EnableAspectJAutoProxy`.** The aspect class exists, the bean is registered, nothing happens. Spring doesn't wrap proxies by default.
- **`@Aspect` without `@Component`.** The aspect class isn't a Spring bean, so Spring doesn't know to process it. Add `@Component` (or register via `@Bean`).
- **Advising a method on `this`.** Self-calls bypass the proxy. Either restructure (move the inner method to a collaborator bean) or look up the proxy explicitly via `AopContext.currentProxy()` -- ugly but works.
- **Advising private methods.** Spring AOP can't; the proxy can only intercept methods reachable through the interface or the public class API.
- **Loose pointcuts that match library code.** `execution(* *(..))` matches everything, including framework internals. Always namespace pointcuts to your code: `execution(* com.example..*(..))`.

---

## Try this yourself

1. Open `aop-demo`. Run `mvn -q test`. The aspect's recorded events list grows as the test invokes service methods. Notice the test asserts the aspect was invoked exactly twice -- once per service call.
2. Narrow the pointcut from `*Service.*(..)` to only `OrderService.place(..)`. Re-run. The `UserService.lookup(...)` call no longer fires the aspect.
3. Change `@Around` to `@Before` (you'll need to drop the return value and the `ProceedingJoinPoint`). Re-run. Confirm the aspect still fires but you no longer get the timing -- `@Before` doesn't know how long the method took.

## Self-check

1. In one sentence each, name three concerns that AOP fits well and three that it doesn't.
2. The pointcut `execution(* com.example..*Service.*(..))` matches what, exactly? Spell out every wildcard.
3. Why is a "self-call" inside a `@Service` *not* intercepted by an aspect that matches the called method? What's the standard fix?

---

## Code examples

In reading order:

- `aop-demo/src/main/java/com/example/TimingAspect.java` -- one `@Around` advice that records timings for any method matching the pointcut.
- `.../OrderService.java`, `.../UserService.java` -- two services; both get advised because both match the pointcut.
- `.../AppConfig.java` -- adds `@EnableAspectJAutoProxy`.
- `.../Main.java` -- demonstrates the aspect output.
- `src/test/java/com/example/AspectTest.java` -- asserts the aspect ran the expected number of times and recorded the expected method names.

Run with `mvn -q test` and `mvn -q compile exec:java`.
