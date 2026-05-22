# 14 — SOLID Principles

Five rules of thumb for OOP design, named by Robert C. Martin and refined into the standard backend-engineering checklist. They aren't laws; they're heuristics that tend to make code easier to change without breaking other parts.

You've already used several of these implicitly throughout the module. This topic names them and shows the failure modes.

The acronym:

- **S** — Single Responsibility Principle
- **O** — Open/Closed Principle
- **L** — Liskov Substitution Principle
- **I** — Interface Segregation Principle
- **D** — Dependency Inversion Principle

---

## S — Single Responsibility Principle (SRP)

> A class should have **one reason to change**.

If a class does invoice calculation AND PDF rendering AND email sending, three different forces can demand changes to it. Each new requirement risks breaking the others.

### Violation

```java
class InvoiceService {
    void calculateTotal(Invoice inv) { ... }
    void renderPdf(Invoice inv) { ... }
    void sendEmail(Invoice inv, String to) { ... }
    void saveToDatabase(Invoice inv) { ... }
}
```

Four unrelated responsibilities. Change the PDF library → touch this class. Change SMTP provider → touch this class. Tax law changes → touch this class. The class is a magnet for unrelated change.

### Fix

```java
class InvoiceCalculator { ... }
class InvoicePdfRenderer { ... }
class InvoiceEmailSender { ... }
class InvoiceRepository { ... }
```

Each class has one reason to change. The pieces compose for the workflow (`InvoiceWorkflow` could use all four), but each is independently testable and changeable.

**Sniff test:** if you describe the class with "and," it might have too many responsibilities.

---

## O — Open/Closed Principle (OCP)

> A class should be **open for extension, closed for modification**.

You should be able to add new behavior to a system without editing existing classes. Existing code stays stable; new requirements come in as new types.

### Violation

```java
class PaymentProcessor {
    void process(Payment p) {
        if (p.type == CARD)  { /* credit card logic */ }
        else if (p.type == PAYPAL) { /* paypal logic */ }
        else if (p.type == CRYPTO) { /* crypto logic */ }
        // adding Apple Pay means EDITING this class
    }
}
```

Every new payment type forces a modification — which risks breaking the existing types.

### Fix

```java
interface PaymentMethod {
    void process(Payment p);
}

class CardPayment    implements PaymentMethod { ... }
class PaypalPayment  implements PaymentMethod { ... }
class CryptoPayment  implements PaymentMethod { ... }
// Apple Pay = new class. PaymentProcessor doesn't change.

class PaymentProcessor {
    void process(PaymentMethod method, Payment p) {
        method.process(p);
    }
}
```

Adding Apple Pay = `class ApplePayPayment implements PaymentMethod { ... }`. Existing code is untouched. **Extension via polymorphism, not modification via if/else.**

This is the reason interfaces and polymorphism exist as a design tool.

---

## L — Liskov Substitution Principle (LSP)

> A subtype should be **usable wherever its supertype is expected**, without breaking the program's correctness.

If `Manager extends Employee`, then code written against `Employee` must work correctly when given a `Manager`. The subtype isn't supposed to violate the parent's contract.

### The classic violation: Square extends Rectangle

```java
class Rectangle {
    int width, height;
    void setWidth(int w)  { this.width = w; }
    void setHeight(int h) { this.height = h; }
    int area() { return width * height; }
}

class Square extends Rectangle {
    @Override
    void setWidth(int w)  { this.width = w; this.height = w; }
    @Override
    void setHeight(int h) { this.width = h; this.height = h; }
}
```

Mathematically a square is a rectangle. In code, the inheritance is broken:

```java
void test(Rectangle r) {
    r.setWidth(5);
    r.setHeight(4);
    assert r.area() == 20;   // ← fails when r is a Square (area is 16)
}
```

A function that works on every `Rectangle` doesn't work on `Square`. The inheritance violates the parent's contract.

Fix: `Square` and `Rectangle` shouldn't share inheritance. Either model them as separate types implementing a common `Shape` interface, or accept that the math doesn't translate directly to code.

**Sniff test:** when you override a method in a subclass, are you *adding* behavior, or *contradicting* the parent's? Adding is fine. Contradicting violates LSP.

---

## I — Interface Segregation Principle (ISP)

> Clients shouldn't be forced to depend on methods they don't use.

Better to have many small, focused interfaces than one big "kitchen sink" interface.

### Violation

```java
interface Worker {
    void work();
    void eat();
    void sleep();
}
```

Every implementation must provide all three. What about a `Robot` that doesn't eat or sleep?

```java
class Robot implements Worker {
    public void work() { ... }
    public void eat()  { throw new UnsupportedOperationException(); }
    public void sleep() { throw new UnsupportedOperationException(); }
}
```

The interface forced `Robot` to fake methods it can't honestly implement. Calling code may legitimately call `eat()` and crash unexpectedly.

### Fix

```java
interface Workable { void work(); }
interface Eatable  { void eat();  }
interface Sleepable { void sleep(); }

class Human implements Workable, Eatable, Sleepable { ... }
class Robot implements Workable { ... }
```

`Robot` implements only what it actually does. Clients that depend on `Workable` can use both. Clients that depend on `Eatable` get only humans, dogs, etc. — no robots that throw when asked to eat.

**Sniff test:** if implementations of an interface end up throwing `UnsupportedOperationException` or no-op-ing certain methods, the interface is probably too wide.

---

## D — Dependency Inversion Principle (DIP)

> High-level modules shouldn't depend on low-level modules. **Both should depend on abstractions.**

In plain terms: a class that uses another class should depend on the *interface*, not the concrete implementation. The framework (or test, or alternative implementation) wires the actual class in.

### Violation

```java
class UserService {
    private final MySqlUserRepository repo = new MySqlUserRepository();

    public User findById(long id) {
        return repo.findById(id);
    }
}
```

`UserService` is bolted to MySQL forever. Can't test with an in-memory repository, can't switch to PostgreSQL, can't add caching.

### Fix

```java
interface UserRepository {
    User findById(long id);
}

class MySqlUserRepository    implements UserRepository { ... }
class PostgresUserRepository implements UserRepository { ... }
class InMemoryUserRepository implements UserRepository { ... }   // for tests

class UserService {
    private final UserRepository repo;

    UserService(UserRepository repo) {
        this.repo = repo;
    }
}
```

Now `UserService` depends on the abstraction `UserRepository`. The actual implementation is supplied at construction time (by a test, by the framework, by configuration).

**This is dependency injection.** Every Spring app does this for every service. Once you internalize DIP, Spring's design clicks.

---

## SOLID in one paragraph

Each class should do one thing (S), be extensible without modification (O), behave consistently when substituted for its parent (L), expose only the methods clients actually need (I), and depend on abstractions rather than concrete implementations (D).

If those five sound like a description of good Spring code — that's because Spring's design is the most widely-used embodiment of SOLID in the Java world.

---

## When to apply SOLID — and when not to

SOLID is a tool, not a religion. Apply each principle when the cost is low and the benefit is real:

- **For a 5-line script:** SOLID is overkill. One class doing five things is fine.
- **For a backend service that'll be maintained for years:** SOLID prevents the slow death of code-rot.
- **When in doubt, lean SOLID for boundaries** (interfaces, public APIs, anything multiple things depend on), and lean pragmatic for internal details.

**Over-applying SOLID** is its own problem — five tiny interfaces and seven classes for what should be one method is its own form of bad design.

---

## Common pitfalls

- **Splitting too eagerly.** A class that does two related things (`UserCreator.create()` and `UserUpdater.update()`) often is fine as one `UserService`. SRP is about reasons-to-change, not literal method counts.
- **Inheritance + LSP violations.** The most common LSP bug: subclasses that override to throw `UnsupportedOperationException` ("this operation makes no sense for me"). If a subclass refuses to honor the parent's contract, the inheritance is wrong.
- **DIP without DI.** Declaring interfaces is half the battle. Actually receiving them through constructors (rather than `new`-ing implementations inside) is the other half.
- **Treating SOLID as a goal.** The goal is *code that's easy to change safely*. SOLID is one way to get there.

---

## Code examples

1. `SingleResponsibilityViolation.java` — God class doing four jobs. Followed by `SingleResponsibilityFixed.java`.
2. `OpenClosedExample.java` — adding new payment methods without modifying the processor.
3. `LiskovViolation_SquareRectangle.java` — the classic LSP failure.
4. `InterfaceSegregation.java` — fat interface vs. split interfaces.
5. `DependencyInversion.java` — the Spring pattern of service depending on a repository interface.

---

## Try this yourself

1. In `SingleResponsibilityViolation.java`, add a logging requirement. Notice how many places you'd touch in the violating version vs. the fixed version.
2. In `OpenClosedExample.java`, add a new payment method (`ApplePayPayment`). Confirm the processor doesn't change.
3. In `DependencyInversion.java`, swap the database-backed repository for the in-memory one in a test. The service class isn't touched.

---

## Self-check

1. State each of S, O, L, I, D in one sentence.
2. Your `OrderService` class has methods `createOrder`, `sendConfirmationEmail`, `generatePdfInvoice`, `chargeCard`. Which SOLID principle is being violated and why?
3. "Depend on abstractions, not implementations" is the D in SOLID. Give a 3-line example showing the contrast.
