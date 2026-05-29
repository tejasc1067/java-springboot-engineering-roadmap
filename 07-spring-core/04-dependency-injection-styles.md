# Dependency Injection Styles

Spring can hand a dependency to a bean in three ways: through its constructor, through a setter, or by writing it straight into a field via reflection. They look similar and produce a working app in all three cases. They are not equivalent for testing or for catching mistakes early — one is the right default and the other two are mostly artifacts of older Spring.

By the end of this topic you'll prefer constructor injection without thinking about it, and you'll know exactly what's wrong with field injection when you see it in someone else's code.

---

## The problem this solves

`OrderService` from topic 03 already used constructor injection — Spring read the constructor parameters and supplied the matching beans. That was the *right* choice, but the topic didn't say so out loud. We will here, by contrast with two alternatives Spring also supports.

```java
@Service
public class ConstructorGreeter {
    private final MessageFormatter formatter;
    public ConstructorGreeter(MessageFormatter formatter) { this.formatter = formatter; }
    public String greet(String name) { return formatter.format("Hello, " + name); }
}
```

```java
@Service
public class SetterGreeter {
    private MessageFormatter formatter;
    @Autowired
    public void setFormatter(MessageFormatter formatter) { this.formatter = formatter; }
    public String greet(String name) { return formatter.format("Hello, " + name); }
}
```

```java
@Service
public class FieldGreeter {
    @Autowired
    private MessageFormatter formatter;
    public String greet(String name) { return formatter.format("Hello, " + name); }
}
```

Same behavior at runtime, after Spring is done with them. Wildly different to construct in a test, wildly different in what they let you forget, wildly different in how invariants get enforced.

## Constructor injection (the answer)

```java
@Service
public class OrderService {
    private final InventoryService inventory;
    private final PriceCalculator priceCalculator;
    private final EmailNotifier notifier;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventory, PriceCalculator priceCalculator,
                        EmailNotifier notifier, OrderRepository orderRepository) {
        this.inventory = inventory;
        this.priceCalculator = priceCalculator;
        this.notifier = notifier;
        this.orderRepository = orderRepository;
    }
}
```

Four things make this the right default:

1. **Required dependencies are required.** You cannot call the constructor without supplying every argument. The compiler enforces it. The object cannot exist in a partially-built state.
2. **The fields are `final`.** Once set, they can't be reassigned, accidentally or otherwise. `final` is only possible with constructor injection.
3. **No `@Autowired` annotation needed.** A single constructor is used automatically. The class is honest about its dependencies in the most obvious place — the constructor signature.
4. **You can `new` it in a test.** No Spring involved. `new OrderService(new InventoryService(), new PriceCalculator(), ...)` is a one-liner, and topic 06.07's reasoning about testability all still applies.

That last point is the one that compounds. Every test you ever write against this class can sidestep Spring entirely; you only use the container when you actually want to test wiring. Unit tests stay fast and isolated; integration tests are the ones that bring the framework.

## Setter injection (rarely useful)

```java
@Service
public class SetterGreeter {
    private MessageFormatter formatter;          // NOT final
    @Autowired
    public void setFormatter(MessageFormatter formatter) { this.formatter = formatter; }
}
```

Setter injection exists for one legitimate case: a dependency that is genuinely **optional** (e.g., an optional metrics emitter that defaults to a no-op when not present, controlled by `@Autowired(required = false)`). For everything else, what it costs you is:

- **Two-step construction.** You can call `new SetterGreeter()` and end up with a `formatter` of `null`. The first `greet` call NPEs. The object existed in an invalid state between the constructor and the setter.
- **No `final`.** Anyone (including the bean itself) can later call `setFormatter(null)` and break the object. Constructor injection makes that impossible.
- **An extra `@Autowired` annotation.** You have to remember to put it on every setter that exists for injection — easy to miss.

If you genuinely need an optional dependency, prefer `Optional<MessageFormatter>` or a no-op default supplied via `@Bean`, both as constructor parameters. Setters are a last resort.

## Field injection (please don't)

```java
@Service
public class FieldGreeter {
    @Autowired
    private MessageFormatter formatter;          // NOT final, and only Spring can set it
}
```

Field injection looks the most concise — no constructor at all, no setter, just an annotated field. That's the trap. The same brevity is what makes it the worst choice in three ways:

### 1. You cannot unit-test it without Spring

`formatter` is `private`. It has no setter. There's no constructor that accepts it. The only ways to populate it are:

- **Boot a Spring context.** That promotes every unit test into an integration test.
- **Use `ReflectionTestUtils.setField(...)`** (or raw Java reflection) to write into the private field. This works but is the testing equivalent of breaking into the class through a window.

Try writing a plain JUnit test against `FieldGreeter`:

```java
@Test
void greets() {
    FieldGreeter greeter = new FieldGreeter();      // constructs fine
    greeter.greet("ada");                            // NullPointerException
}
```

The test fails, not because the logic is wrong, but because *there is no way* to put a `MessageFormatter` into the object without Spring's help. Compare with `ConstructorGreeter`:

```java
@Test
void greets() {
    var greeter = new ConstructorGreeter(new UppercaseFormatter());
    assertThat(greeter.greet("ada")).isEqualTo("HELLO, ADA");
}
```

Same intent, two lines, no Spring. That contrast is the lesson.

### 2. Dependencies are hidden

The class's needs are scattered across `private @Autowired` fields. To know what a class depends on, you have to read every field declaration. With constructor injection, one glance at the constructor tells you the full story.

### 3. `final` is impossible

A field can only be `final` if it's assigned in the constructor. Field injection assigns *after* construction (via reflection), which means the field cannot be `final`. The compiler can no longer help you keep the reference immutable.

### Why field injection feels good at first

It's brief. There is no constructor noise. The class fits in fewer lines. That brevity is exactly the cost: it hides the dependencies and forecloses on plain-Java testability.

When you encounter field injection in older code or tutorials, recognize it as a Spring 3-era convenience that survived for a decade because nobody got around to changing it. Modern Spring docs themselves recommend constructor injection.

## "But what if I have many dependencies?"

You'll occasionally hear "I use field injection because constructor injection makes my constructor ugly when I have eight dependencies." Two responses:

1. The ugly constructor is the **point**. It's the smell that tells you the class probably does too much — eight collaborators is a hint to extract responsibilities into smaller classes.
2. If the dependencies are genuinely irreducible (a coordinator service, say), use Lombok's `@RequiredArgsConstructor` or write a single constructor and accept the visual cost. The cost is paid once; the testability win is paid forever.

Field injection lets you ignore the smell. That doesn't make the smell go away — it just stops you noticing.

## When `@Autowired` is required vs implied

Modern Spring made `@Autowired` mostly unnecessary on constructors:

| Where | Is `@Autowired` required? |
|--|--|
| On a single constructor | No (since Spring 4.3). Used automatically. |
| On one of several constructors | Yes — Spring needs to know which one to use. |
| On a setter | Yes. |
| On a field | Yes. |

You'll still see `@Autowired` on single constructors in older code. It's redundant; you can delete it.

## Common pitfalls

- **Mixing styles in one class.** A class with one constructor *and* `@Autowired` fields is confusing. Spring will use the constructor and then also populate the fields. Pick a style per class.
- **Field injection in an immutable-looking class.** The fields aren't `final`. The class only *appears* immutable. Anyone can mutate it from another thread.
- **`@Autowired(required = false)` to "make a bug go away."** If a dependency is actually required, marking it optional just defers the NPE. Use it only for truly optional collaborators.
- **Writing `@Autowired` on a constructor with one constructor.** Harmless but redundant. Delete it.
- **Inheriting field-injected state.** A subclass that overrides a method which uses an `@Autowired` superclass field has to know what's in the parent. With constructor injection, the parent's constructor is the contract — visible, enforced.

---

## Try this yourself

1. Open `injection-styles-demo`. Run `mvn test`. Notice that `FieldGreeterTest` has an `@Disabled` test documenting *why* you can't unit-test `FieldGreeter` without Spring. Remove the `@Disabled` annotation. Run again. Read the NPE.
2. In `SetterGreeter`, comment out the `@Autowired` on the setter. Run the Spring wiring test. Read the error. Now you know what happens if you forget the annotation on a setter -- the dependency stays null and the first call NPEs.
3. Add a constructor to `FieldGreeter` that also takes a `MessageFormatter`, while *keeping* the `@Autowired` field. Notice that Spring uses the constructor and the field stays null too -- you've now got two paths to the same dependency and they don't agree. This is why mixing styles is confusing.

## Self-check

1. Name three specific things `final` on a constructor-injected field guarantees that a field-injected field cannot.
2. Why does the assertion "field injection is just shorter" miss the point? What's the actual cost it's optimizing away?
3. When is setter injection a defensible choice? When is it not?

---

## Code examples

In reading order (the formatter first, then the three Greeter variants, then the tests):

- `injection-styles-demo/src/main/java/com/example/MessageFormatter.java` -- the interface every Greeter depends on.
- `.../UppercaseFormatter.java` -- one `@Component` implementation.
- `.../ConstructorGreeter.java` -- the recommended style.
- `.../SetterGreeter.java` -- works, but exposes the two-step-construction problem.
- `.../FieldGreeter.java` -- antipattern; deliberately can't be unit-tested without Spring.
- `src/test/java/com/example/ConstructorGreeterTest.java` -- plain JUnit, no Spring.
- `.../SetterGreeterTest.java` -- shows the partially-constructed-object hazard.
- `.../FieldGreeterTest.java` -- `@Disabled` test documenting the testability bottom.
- `.../SpringWiringTest.java` -- proves all three work *when Spring is involved*; the point is the previous three files.

Run with `mvn -q test`.
