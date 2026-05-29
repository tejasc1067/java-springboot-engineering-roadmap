# Application Events

Some things should happen *because* an order was placed but shouldn't be the order-placing code's responsibility: audit logging, sending a confirmation email, posting a metric, updating a recommendation engine. If `OrderService` calls `auditLogger.log(...)` and `emailService.send(...)` and `metrics.increment(...)` directly, it has accumulated four responsibilities and four constructor parameters. Spring's **application events** let one bean *announce* that something happened and any number of other beans listen — without those listeners being mentioned in the publishing code at all.

By the end of this topic you can define a custom event, publish it from one bean, and listen for it in another.

---

## The problem this solves

The straightforward version of "an order was placed, so do five things":

```java
public Order placeOrder(...) {
    Order order = build(...);
    repository.save(order);
    auditLogger.log("placed: " + order.id());            // dependency 1
    emailService.sendConfirmation(order);                 // dependency 2
    metrics.increment("orders.placed");                   // dependency 3
    recommendations.recordPurchase(order);                // dependency 4
    return order;
}
```

`OrderService` now has five constructor dependencies — one for its job and four for the side-effects. Worse, adding "and also notify the shipping team" means editing `OrderService` again, even though shipping isn't part of placing an order. The list grows without bound.

Application events flip the relationship. `OrderService` publishes a single event:

```java
public Order placeOrder(...) {
    Order order = build(...);
    repository.save(order);
    eventPublisher.publishEvent(new OrderPlacedEvent(order.id(), order.customer()));
    return order;
}
```

Each side-effect lives in its own listener bean:

```java
@Component
public class AuditLogger {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) { ... }
}
```

`OrderService` doesn't know auditing exists. Auditing doesn't know `OrderService` calls it. Both depend only on the *event*, which is a value object.

## How it works

Three pieces.

**1. The event class.** Plain Java; in modern code, a `record` is ideal because events are immutable value objects:

```java
public record OrderPlacedEvent(String orderId, String customer) { }
```

There is no base class to extend. (Older Spring required `ApplicationEvent` — modern Spring works with any POJO.)

**2. The publisher.** Inject `ApplicationEventPublisher` and call `publishEvent`:

```java
@Service
public class OrderService {

    private final ApplicationEventPublisher events;

    public OrderService(ApplicationEventPublisher events) {
        this.events = events;
    }

    public String place(String customer) {
        String id = UUID.randomUUID().toString();
        events.publishEvent(new OrderPlacedEvent(id, customer));
        return id;
    }
}
```

`ApplicationEventPublisher` is a bean Spring provides automatically — every `ApplicationContext` implements it. You don't register it; you just inject it.

**3. One or more listeners.** Annotate a method with `@EventListener`; the method's *parameter type* picks which events it receives:

```java
@Component
public class AuditLogger {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        System.out.println("AUDIT: order " + event.orderId() + " placed for " + event.customer());
    }
}
```

When `events.publishEvent(...)` runs, Spring finds every `@EventListener` whose parameter is assignable from the event's type and invokes each one. Listeners on `Object.class` would receive everything (rare); a listener on `OrderPlacedEvent` receives only that event type.

## Synchronous by default

`publishEvent` blocks until every listener has run, on the publishing thread. That has implications:

- If a listener throws, the exception propagates back to the publisher (which can crash the originating call).
- If a listener is slow, the publisher waits.
- The publisher and all listeners share the same transaction context (matters once you add Spring's transaction support in module 10).

That synchronous behavior is the right default for "audit this thing" — you want the audit log entry written before the order is reported as placed. For "send a notification email," the email taking 200ms would slow every order. The cure is `@Async` plus `@EnableAsync`, which makes the listener run on a separate thread:

```java
@Component
public class EmailNotifier {

    @EventListener
    @Async
    public void onOrderPlaced(OrderPlacedEvent event) { ... }
}
```

`@Async` requires `@EnableAsync` somewhere in your configuration, plus the standard advice about thread pools. Module 08 covers it properly when Spring Boot configures the executor for you. Until then, treat events as synchronous.

## Conditional listeners

`@EventListener` accepts a SpEL `condition` attribute that fires only when an expression is true:

```java
@EventListener(condition = "#event.customer.endsWith('@enterprise.com')")
public void onEnterpriseOrder(OrderPlacedEvent event) { ... }
```

Rarely needed; mostly useful when you want one listener method to handle a subset rather than splitting into multiple listeners. Lean on the type system first (publish a more specific event type for the enterprise case), reach for `condition` only when types can't carry the distinction.

## Event hierarchies

Listeners match by *type assignability*, so a listener on a superclass event receives subclass events too:

```java
public abstract class OrderEvent { ... }
public class OrderPlacedEvent extends OrderEvent { ... }
public class OrderCancelledEvent extends OrderEvent { ... }

@EventListener
public void anyOrderChange(OrderEvent event) { ... }    // receives both
```

A `record` cannot extend another record, but it can implement a marker interface. If you want the "receive everything in this family" listener, declare a common interface and have your records implement it:

```java
public sealed interface OrderEvent permits OrderPlacedEvent, OrderCancelledEvent { }
public record OrderPlacedEvent(...) implements OrderEvent { }
```

The interface gives you the type relationship without giving up `record`'s immutability.

## Spring's own application events (briefly)

The container itself publishes a small number of events you can listen for:

- `ContextRefreshedEvent` — context has finished refreshing.
- `ContextStartedEvent` / `ContextStoppedEvent` — for explicit start/stop.
- `ContextClosedEvent` — context is closing; useful as a `@PreDestroy`-equivalent at the *context* level.

You'll occasionally write `@EventListener(ContextRefreshedEvent.class)` to run code once the whole context is ready — different timing than `@PostConstruct` (which is per-bean, not whole-context).

## When events are wrong

Events are a decoupling tool. They earn their keep when there are *multiple* listeners or when the publisher genuinely should not know about a downstream concern. They cost you traceability — a stack trace from inside a listener won't include the publisher's frame in the obvious way, and "who handles this event?" becomes a search problem instead of a "follow the type" problem.

Avoid when:

- There is exactly one downstream concern and it always will be one. Just call it directly; less indirection, easier debugging.
- The downstream needs to *change* the result the publisher returns. Events can't do that cleanly (a listener can throw, but it can't transform the return value).
- You're using events to pass *commands* around. "DoTheThingEvent" is a code smell — that's a method call disguised as an event. Events should report something that happened, not request something be done.

## Common pitfalls

- **Listener with no `@EventListener`.** A method that takes an event parameter but lacks the annotation is just a method. No registration; no firing.
- **Two listeners for the same event, order matters.** Add `@Order(N)` to guarantee invocation order; otherwise the order is undefined.
- **Listener throws and the publisher's `placeOrder` fails for an unrelated reason.** Sync events propagate listener exceptions. Either swallow inside the listener (with proper logging), or make the listener `@Async` if its failures are tolerable.
- **Calling `publishEvent` from a constructor or `@PostConstruct`.** The context isn't fully refreshed; some listeners may not be registered yet. Wait for `ContextRefreshedEvent` or do this only at runtime, not at startup.
- **Publishing huge events.** The event object is held by Spring while listeners run. Don't put megabytes of data in events; reference them by id and let listeners look the data up.

---

## Try this yourself

1. Open `events-demo`. Run `mvn -q test`. The test places one order and asserts that two listeners (audit + email) both observed the same event.
2. Add a third listener `MetricsRecorder` that increments a counter on each `OrderPlacedEvent`. Re-run the test; with no change to `OrderService`, the metrics now run.
3. Throw a `RuntimeException` from one of the listeners. Re-run the *test* that exercises `place(...)`. Notice that the publisher's call also fails -- sync events propagate. Move the throwing listener to `@Async` (requires adding `@EnableAsync`) and observe the difference.

## Self-check

1. What is the relationship between a publisher and its listeners in Spring's event system? Who depends on whom?
2. Sync vs async events: name one concern for which sync is correct and one for which async is correct.
3. Why is it usually a bad idea to model an event as "DoTheThingEvent"? What does the name reveal?

---

## Code examples

In reading order:

- `events-demo/src/main/java/com/example/OrderPlacedEvent.java` -- a `record`; that's the whole event class.
- `.../OrderService.java` -- publishes the event after placing.
- `.../AuditLogger.java` -- one listener.
- `.../NotificationSender.java` -- second listener.
- `.../AppConfig.java`, `.../Main.java`.
- `src/test/java/com/example/EventTest.java` -- asserts both listeners received the published event.

Run with `mvn -q test` and `mvn -q compile exec:java`.
