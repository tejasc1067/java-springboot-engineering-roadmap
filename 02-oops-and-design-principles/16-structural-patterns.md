# 16 — Structural Patterns: Adapter, Decorator, Proxy

Structural patterns are about **how objects are put together**: one object wrapping another, translating for it, or standing in front of it. Topic 15 was about *creating* objects; this one is about *combining* them.

All three patterns here have the same shape: a class that **holds another object and implements an interface**. What differs is *why* it wraps. An adapter translates, a decorator adds behaviour, and a proxy controls access. That last one matters for Spring: every `@Transactional`, `@Cacheable`, and `@Async` annotation you'll write works because Spring puts a proxy in front of your bean.

---

## The problem this solves

- You use a library whose methods don't match what your code expects: it wants dollars, you have cents; it returns error codes, you want exceptions. Calling it directly from twenty places means translating in twenty places. **Adapter.**
- You want to add caching, logging, or retries to an object without editing its class, and without a subclass for every combination. **Decorator.**
- You want the *same* extra step (start a transaction, check a permission, time the call) around *every* method of an object, without writing it into each method. **Proxy.**

---

## Adapter — make a class fit the interface you want

### Broken: every caller talks to the vendor directly

Your code works in cents (`long`). A payment vendor's SDK, which you can't change, wants dollars (`double`) and returns an `int` status code. `DirectVendorCallsBroken.java` has two callers. One remembered to divide by 100:

```java
acme.makePayment(customerId, amountInCents / 100.0);   // CheckoutService
acme.makePayment(customerId, amountInCents);           // SubscriptionRenewal - forgot
```

The second line compiles, because Java quietly widens a `long` into a `double`:

```text
Checkout charged $19.99
Renewal  charged $1999.00
```

### Fixed: one class translates

Define the interface *your* code wants, and write one class that implements it by calling the vendor:

```java
interface PaymentGateway {
    void charge(String customerId, long amountInCents);
}

class AcmePaymentAdapter implements PaymentGateway {
    private final AcmePayClient acme;
    AcmePaymentAdapter(AcmePayClient acme) { this.acme = acme; }

    public void charge(String customerId, long amountInCents) {
        int status = acme.makePayment(customerId, amountInCents / 100.0);
        if (status != 0) {
            throw new PaymentFailedException("Acme rejected payment, code " + status);
        }
    }
}
```

`PaymentAdapter.java` runs both callers through the adapter: both charge $19.99, and the vendor's error code turns into an exception. The vendor's quirks now live in exactly one class. If you switch vendors, you write one new adapter and no caller changes. And because callers depend on `PaymentGateway`, a test can hand them a fake (SOLID's D, topic 14; mocking in module 06).

You've already used adapters. `InputStreamReader` adapts a byte stream (`InputStream`) to a character stream (`Reader`). `Arrays.asList(array)` adapts an array to the `List` interface.

---

## Decorator — add behaviour by wrapping

### Broken: one subclass per feature, per implementation

To add caching to `HttpPriceService`, the obvious move is a subclass: `CachingHttpPriceService extends HttpPriceService`. `SubclassPerFeatureBroken.java` shows where that leads:

```text
HttpPriceService + caching subclass: 1 remote call(s) for 3 lookups
Switched to WarehousePriceService:   3 remote call(s) for 3 lookups
```

The caching was tied to one class by `extends`. Switch to a different `PriceService` and it's gone. Add logging as a second feature and you need `LoggingHttp`, `LoggingCachingHttp`, and the same again for every other implementation. The number of subclasses multiplies.

### Fixed: a decorator

A decorator **implements the same interface** as the object it wraps, does its one extra job, and passes the call on:

```java
class CachingPriceService implements PriceService {
    private final PriceService delegate;               // any PriceService at all
    private final Map<String, Integer> cache = new HashMap<>();

    CachingPriceService(PriceService delegate) { this.delegate = delegate; }

    public int priceOf(String sku) {
        return cache.computeIfAbsent(sku, delegate::priceOf);
    }
}

PriceService prices = new LoggingPriceService(new CachingPriceService(new HttpPriceService()));
```

Because the decorator only knows the interface, one `CachingPriceService` works for every implementation, and decorators stack: logging around caching around HTTP. Two features need two classes, not one per combination.

`DecoratorPattern.java` also shows that **order matters**:

```text
Logging(Caching(http)) logged 3 lookups (every request)
Caching(Logging(http)) logged 1 lookup  (only cache misses)
```

Neither is wrong. They answer different questions ("what did callers ask for?" vs "what hit the slow service?"), so decide which one you want.

The JDK's I/O classes are decorators: `new BufferedReader(new InputStreamReader(new FileInputStream(f)))` stacks buffering on top of the byte-to-character adapter on top of a file. `Collections.unmodifiableList(list)` decorates a list so that writes throw. Topic 11's `CachingUserRepository` was a decorator too.

---

## Proxy — stand in front of an object and control access

A proxy has the same shape as a decorator: it implements the interface and holds the real object. The difference is intent. A proxy decides **whether, when, and under what conditions** the real call happens: inside a transaction, only for an authorised user, only when first needed (lazy loading), or on another machine (a remote call). The caller can't tell it isn't talking to the real object.

### Writing one proxy for every method: the dynamic proxy

Hand-writing a proxy means re-implementing every method of the interface. An interface with 30 methods gets 30 near-identical wrappers. Java can instead **generate the proxy class at runtime** and send every method call to one handler:

```java
InvocationHandler handler = (proxy, method, args) -> {
    audit.add("before " + method.getName());
    Object result = method.invoke(target, args);   // call the real object
    audit.add("after  " + method.getName());
    return result;
};
AccountService service = (AccountService) Proxy.newProxyInstance(
        AccountService.class.getClassLoader(), new Class<?>[] { AccountService.class }, handler);
```

`DynamicProxy.java` calls `deposit`, `withdraw`, and `balance` on the proxy. All three get audited by that one handler, and the object's class is something like `$Proxy0`, a class that exists in no source file.

**This is how Spring AOP works** (module 07, topic 10). When a bean has `@Transactional` methods, or an `@Aspect` matches it, Spring doesn't give other beans the real object. It gives them a proxy whose handler opens the transaction or runs your advice, and then calls the real method. Spring generates either a JDK dynamic proxy, like this one, or a generated subclass of your class (CGLIB). Spring Boot uses the subclass kind by default, but the behaviour you need to know is the same.

### Broken: calling your own method skips the proxy

The proxy only sees calls that go **through the proxy**. Inside the real object, `this` is the real object:

```java
class OrderServiceImpl implements OrderService {
    public void placeOrder(String id) {
        saveAuditRecord(id);   // this.saveAuditRecord(id) - the proxy never sees it
    }
    public void saveAuditRecord(String id) { ... }
}
```

`ProxySelfInvocationBroken.java`:

```text
Methods the proxy wrapped in a transaction: [placeOrder]
saveAuditRecord ran, but never passed through the proxy - no transaction.
```

This is the famous Spring bug. A `@Transactional` or `@Async` method called from another method *in the same class* runs as if the annotation weren't there. No error, no warning.

### Fixed: cross an object boundary

Move the inner method into its own class and inject the **proxied** version. `ProxySelfInvocationFixed.java` puts `saveAuditRecord` in an `AuditService`, and `OrderServiceImpl` calls it through its proxy:

```text
Methods the proxy wrapped in a transaction: [placeOrder, saveAuditRecord]
```

In Spring the fix is the same: move the method to another `@Service` and inject it.

---

## The other structural patterns

The GoF list has four more. You'll recognise them more than you'll write them:

- **Facade.** One simple interface in front of a complicated subsystem. SLF4J is the classic Java example (module 08, topic 10). Your code calls `log.info(...)` and doesn't care whether Logback or Log4j2 does the work.
- **Composite.** Treat a single object and a group of objects the same way, through one interface. Think of a file and a folder that both have `size()`, where a folder's size is the sum of its children's.
- **Flyweight.** Share one object instead of creating many identical ones. The `Integer.valueOf` cache from topic 15 is a flyweight.
- **Bridge.** Split "what" from "how" into two hierarchies that vary independently. JDBC is a bridge: your code uses the `Connection` interface, and each database ships its own driver behind it (module 04).

---

## Adapter vs decorator vs proxy

All three wrap an object. Ask *why* it wraps:

| | Interface it exposes | Why it wraps | Example |
|---|---|---|---|
| **Adapter** | A *different* interface from the wrapped object | To translate | `AcmePaymentAdapter`, `InputStreamReader` |
| **Decorator** | The *same* interface | To add behaviour | `CachingPriceService`, `BufferedReader` |
| **Proxy** | The *same* interface | To control access to the real call | Spring's `@Transactional` proxy, lazy-loaded JPA entities |

Decorator and proxy look identical in code. The difference is the job. A decorator *adds* something, and you usually stack several. A proxy *guards* the real object, and the caller usually doesn't even know it's there.

---

## When to use them / when not to

| Pattern | Use it when | Don't use it when |
|---|---|---|
| Adapter | You call a third-party or legacy API from more than one place, and its shape doesn't match yours. | You call it once. Translating in that one place is fine. |
| Decorator | You need optional behaviour (caching, logging, retries) that can combine and apply to several implementations. | Only one implementation and one feature will ever exist. Edit the class. |
| Proxy | The same rule applies to every method (transactions, security, timing). In Spring, let the framework generate it with an annotation or aspect. | Hand-rolling `java.lang.reflect.Proxy` in application code. Use Spring AOP, which builds on it. |

---

## Common pitfalls

- **A decorator that doesn't forward every method.** It silently drops behaviour. If `PriceService` gains `priceInCurrency(...)` and the decorator implements it as `return 0;`, the wrapped service is never asked. Forward to `delegate` in every method you don't change.
- **A self-call to a proxied method.** `this.method()` skips the proxy, so `@Transactional`, `@Async`, and `@Cacheable` do nothing. Move the method to another bean.
- **Leaking the vendor's types through the adapter.** If `PaymentGateway.charge` returns the vendor's `AcmeResponse`, every caller depends on the vendor again. Return your own types.
- **Stacking decorators in the wrong order.** Logging outside caching logs every request, and logging inside it logs only misses. Decide which you need and write the order on purpose.

---

## Code examples

Read and run in this order. Each `Broken` file is followed by its fix:

1. `DirectVendorCallsBroken.java` — two callers use a dollars-based SDK directly. One forgets to convert and charges $1999.00 instead of $19.99.
2. `PaymentAdapter.java` — an adapter converts cents and turns error codes into exceptions. Both callers charge $19.99.
3. `SubclassPerFeatureBroken.java` — caching added by subclassing is lost when the implementation changes.
4. `DecoratorPattern.java` — one caching decorator works for any `PriceService`. Stacking order changes what gets logged.
5. `DynamicProxy.java` — one `InvocationHandler` audits every method of an interface through a runtime-generated `$Proxy0` class.
6. `ProxySelfInvocationBroken.java` — a call to `this.saveAuditRecord()` skips the "transaction" proxy.
7. `ProxySelfInvocationFixed.java` — the method moves to its own proxied service, and both calls are wrapped.

Run any of them from the repo root:

```bash
java 02-oops-and-design-principles/CODE_EXAMPLES/16-structural-patterns/PaymentAdapter.java
```

---

## Try this yourself

1. In `PaymentAdapter.java`, write a second adapter, `FakePaymentGateway implements PaymentGateway`, that just records charges in a list. Run `CheckoutService` against it. Which classes did you have to change? (None of the callers. That's the point.)
2. In `DecoratorPattern.java`, add a `RetryingPriceService` decorator that calls the delegate up to 3 times if it throws. Make `HttpPriceService` fail on its first call and stack retrying inside caching.
3. In `DynamicProxy.java`, change the handler so `withdraw` throws `IllegalStateException("read-only account")` instead of calling the real method. You've just written an access-control proxy.

## Self-check

1. Adapter, decorator, and proxy all hold another object and implement an interface. What is the one question that tells them apart, and what is the answer for each?
2. A `@Transactional` method saves two rows, and the second save fails. It was called from another method in the same class, and the first row is still in the database. Using the word *proxy*, explain why the annotation had no effect, and give the fix.
3. Why does a decorator scale better than subclassing when you have 3 implementations and 3 optional features? Count the classes each approach needs.
4. `new BufferedReader(new InputStreamReader(System.in))`: which object is the adapter, which is the decorator, and what does each one add?
