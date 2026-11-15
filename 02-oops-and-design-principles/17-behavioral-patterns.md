# 17 — Behavioral Patterns: Strategy, Template Method, Observer

Behavioral patterns are about **how objects divide up work**: which object decides, which one does the steps, and which one gets told when something happens. Topic 15 created objects and topic 16 combined them. This topic is about the conversations between them.

The three patterns here are the ones Spring is built on. `JdbcTemplate`, `RestTemplate`, and `TransactionTemplate` are template methods. `Comparator` and every injected interface are strategies. `@EventListener` is the observer pattern.

---

## The problem this solves

- One method contains every variant of a rule (`if student... else if black friday... else...`), so each new variant means editing it, and an unknown variant silently lands in the `else`. **Strategy.**
- Several classes copy the same skeleton (open, do the work, close, handle errors) and change only one step. Some copies get the skeleton wrong. **Template method.**
- When something happens (an order is placed), one class has to call every interested party itself, and it has to be kept up to date as that list grows. **Observer.**

---

## Strategy — pass the varying rule in

### Broken: every rule in one if/else chain

`DiscountIfElseBroken.java` has a `Checkout` that knows every discount rule:

```java
if (customerType.equals("STUDENT"))           return priceInCents * 90 / 100;
else if (customerType.equals("BLACK_FRIDAY")) return priceInCents * 70 / 100;
else                                          return priceInCents;
```

The sign-up team adds an `EMPLOYEE` customer type with 20% off, and nobody tells `Checkout`:

```text
EMPLOYEE pays 10000 (promised 8000)
No error, no warning. Every employee order is overcharged until someone complains.
```

### Fixed: the rule is an object

A **strategy** is an interface for "the part that varies". The code that uses it receives one and calls it, without knowing which one it got:

```java
@FunctionalInterface
interface DiscountStrategy {
    long apply(long priceInCents);
}

class Checkout {
    long total(long priceInCents, DiscountStrategy discount) {
        return discount.apply(priceInCents);
    }
}

checkout.total(10_000, price -> price * 80 / 100);
```

Because `DiscountStrategy` has a single method, any lambda with the right shape is a strategy (module 03b, topic 02 covers lambdas). `StrategyPattern.java` keeps the named rules in one `Map<String, DiscountStrategy>`. Adding `EMPLOYEE` is one line there, `Checkout` never changes, and an unknown name throws instead of falling into an `else`:

```text
EMPLOYEE pays 8000
Unknown type fails loudly: no discount rule for EMPLOYE
```

This is the same idea as the Open/Closed fix in topic 14, where `PaymentProcessor` received a `PaymentMethod`. Strategy is the name for that move.

### You already use strategies

`Comparator` is the JDK's best-known strategy. `list.sort(...)` is the fixed algorithm, and the comparator you pass decides the order:

```text
natural order:    [Bob, Charlie, al, dan]
by length:        [al, Bob, dan, Charlie]
case-insensitive: [al, Bob, Charlie, dan]
```

In Spring, every interface you inject is a strategy. `OrderService` takes a `PaymentGateway`, and configuration decides which implementation that is. Spring can also inject every implementation at once as a `Map<String, DiscountStrategy>` keyed by bean name, which replaces the hand-written map above (module 07, topic 05).

---

## Template method — fix the skeleton, vary the steps

### Broken: the skeleton copied into every class

Exporting a report always has the same shape: open a file, write a header, write each row, close the file even if something failed. `CopiedSkeletonBroken.java` has two exporters that each write that skeleton themselves. One closes the file in a `finally` block and the other doesn't:

```java
FakeFile file = new FakeFile();
file.write("id,name");
for (String row : rows) file.write(row);
file.close();               // skipped if a row throws
```

```text
Files left open after JSON export: 0
Files left open after CSV export:  1
```

On a server that runs this export a thousand times a day, that's a thousand leaked file handles, and eventually "Too many open files".

### Fixed: the base class owns the skeleton

The base class writes the skeleton **once**, in a `final` method. Subclasses only fill in the steps that differ:

```java
abstract class Exporter {
    final void export(List<String> rows) {        // the template method
        FakeFile file = new FakeFile();
        try {
            file.write(header());
            for (String row : rows) file.write(formatRow(row));
            String footer = footer();
            if (footer != null) file.write(footer);
        } finally {
            file.close();
        }
    }

    abstract String header();                   // steps subclasses MUST provide
    abstract String formatRow(String row);
    String footer() { return null; }            // a "hook": optional, has a default
}
```

`final` matters. A subclass can't reorder the steps or drop the `close()`, because it never writes the skeleton at all. `TemplateMethodPattern.java` makes both exporters fail on a bad row and asserts that no file is left open.

### The modern form: pass the step as a lambda (`JdbcTemplate`)

Subclassing for every variation gets heavy. The same idea works by passing the varying step in as a lambda, so a template method becomes an ordinary method that takes a callback. This is how Spring's `JdbcTemplate` works. In module 04 you wrote the JDBC skeleton by hand every time: get a connection, prepare the statement, loop over the `ResultSet`, close everything, handle `SQLException`. `JdbcTemplate` owns all of that, and you supply only how one row becomes one object:

```java
List<Book> books = jdbcTemplate.query(
        "select title, year from book",
        (rs, rowNum) -> new Book(rs.getString("title"), rs.getInt("year")));
```

`TemplateCallback.java` builds a miniature version, `MiniJdbcTemplate`, with no database. The row mapper hits a row it can't parse. The template still closes the connection and wraps the failure in one exception type, which is what `JdbcTemplate` does when it turns `SQLException` into Spring's `DataAccessException`:

```text
query failed: select title, year from book <- caused by java.lang.NumberFormatException: For input string: "not-a-year"
Connections left open: 0
```

Other Spring template classes work the same way: `TransactionTemplate` (begin, your code, commit or roll back) and `RestTemplate` (open the connection, your request, convert the response).

---

## Observer — announce it, let listeners react

### Broken: the publisher calls everyone itself

When an order is placed, an email goes out and loyalty points are awarded. In `DirectNotificationsBroken.java`, `OrderService` calls both itself, from two entry points. Loyalty points were added to one entry point later and forgotten in the other:

```java
void placeOrder(String id)         { email.confirm(id); loyalty.award(id); }
void placeOrderFromCart(String id) { email.confirm(id); }   // loyalty forgotten
```

```text
Loyalty points:      10 (expected 20)
Every cart checkout silently earns nothing.
```

The deeper problem is that `OrderService` has to know about every follow-up action. Each new one (analytics, a shipping notification) means another constructor parameter and another edit to code whose job is placing orders.

### Fixed: publish one event

In the observer pattern, the **subject** (also called the publisher) keeps a list of **observers** (listeners) and tells all of them when something happens. It doesn't know what they are or what they do:

```java
class OrderEvents {
    private final List<Consumer<String>> listeners = new ArrayList<>();
    void subscribe(Consumer<String> listener) { listeners.add(listener); }
    void publishOrderPlaced(String orderId) { listeners.forEach(l -> l.accept(orderId)); }
}

events.subscribe(email::confirm);     // wiring, once, at startup
events.subscribe(loyalty::award);

void placeOrderFromCart(String id) { events.publishOrderPlaced(id); }
```

`ObserverPattern.java` publishes from both entry points. Every listener hears about both orders, and adding a new reaction is one `subscribe` line. `OrderService` never changes.

This is exactly what Spring's application events do (module 07, topic 11). `ApplicationEventPublisher.publishEvent(...)` is the subject, each `@EventListener` method is an observer, and Spring does the `subscribe` for you when it finds the annotation. Messaging systems such as Kafka (module 16) apply the same pattern across services instead of inside one program.

Two behaviours carry straight over to Spring events:

- **Listeners run synchronously, one after another, on the publisher's thread.** A slow listener slows down `placeOrder`.
- **A listener that throws stops the loop.** Later listeners don't run, and the exception comes back to the publisher. Try it in exercise 3.

---

## When to use them / when not to

| Pattern | Use it when | Don't use it when |
|---|---|---|
| Strategy | One step of an algorithm varies by input, configuration, or customer, and the variants keep growing. | There are two fixed variants that never change. An `if` is clearer. |
| Template method | Several classes share a skeleton, especially one with cleanup or error handling that must not be skipped. Prefer the lambda (callback) form. | Only the skeleton is shared and the steps are trivial. A helper method is enough. |
| Observer | Several independent reactions to one event, and the publisher shouldn't know about them. | The "reaction" is part of the operation itself (saving the order row) or must succeed for the operation to count. Call it directly so a failure is obvious. |

Observer has a real cost: you can no longer read `placeOrder` and see everything that happens when an order is placed. Use it for genuinely independent side effects, not to hide the main flow.

---

## Common pitfalls

- **Strategy lookup with a silent default.** `DISCOUNTS.getOrDefault(type, price -> price)` brings back the `else` bug. Throw on unknown names.
- **A template method that isn't `final`.** A subclass overrides `export()` itself and drops the `finally`. Mark the template method `final`.
- **A listener throwing inside a synchronous publish.** The listeners after it never run, and the publisher's operation fails for a reason unrelated to it. Catch and log inside listeners whose failure is tolerable, or run them asynchronously (module 08, topic 13).
- **Listeners that rely on running in a particular order.** Registration order is an accident of startup. If B needs A's result, it isn't an independent listener. Call A, then B, directly.

---

## Code examples

Read and run in this order. Each `Broken` file is followed by its fix:

1. `DiscountIfElseBroken.java` — a new `EMPLOYEE` type falls into the `else` and pays full price.
2. `StrategyPattern.java` — discount rules as lambdas in one map, `Checkout` unchanged, an unknown type throws, and `Comparator` as a JDK strategy.
3. `CopiedSkeletonBroken.java` — two exporters copy the open/write/close skeleton, and one leaks a file when a row fails.
4. `TemplateMethodPattern.java` — a `final` template method owns the skeleton, so no exporter can leak.
5. `TemplateCallback.java` — the lambda form: a miniature `JdbcTemplate` that always closes its connection.
6. `DirectNotificationsBroken.java` — `OrderService` calls each follow-up itself and forgets loyalty points on one entry point.
7. `ObserverPattern.java` — `OrderService` publishes one event, and every listener hears every order.

Run any of them from the repo root:

```bash
java 02-oops-and-design-principles/CODE_EXAMPLES/17-behavioral-patterns/StrategyPattern.java
```

---

## Try this yourself

1. In `StrategyPattern.java`, add a `LOYAL_CUSTOMER` rule: 15% off, but never more than 2000 cents off. Did you have to touch `Checkout`?
2. In `TemplateMethodPattern.java`, add an `XmlExporter` with a header, a footer, and `<row>...</row>` formatting. Count how many lines of skeleton you wrote. (Zero.)
3. In `ObserverPattern.java`, subscribe a listener that throws *between* the email and loyalty listeners. Which listeners run? Then wrap each `listener.accept` in `publishOrderPlaced` with a try/catch that logs and continues. What did you just trade away?

## Self-check

1. `Checkout` has an `if/else if/else` over customer types. Describe the bug that's waiting, and how a strategy plus a lookup that throws on unknown names prevents it.
2. Why should a template method be `final`? What can go wrong if a subclass is allowed to override it?
3. `jdbcTemplate.query(sql, (rs, rowNum) -> ...)`: which part is the fixed skeleton, which part is yours, and name two things the skeleton does that you no longer have to remember.
4. An order listener sends an email and throws when the mail server is down. With synchronous events, what happens to the order and to the listeners after it? What are your two options?
