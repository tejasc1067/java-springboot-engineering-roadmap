# 09 — Interfaces

An interface is a contract: a list of methods a class promises to provide, without dictating how. It's the most important abstraction tool in modern Java — and the foundation of dependency injection, mocking in tests, and most of what makes Spring tick.

If you only remember one thing from this module beyond "encapsulate your data": **program against interfaces, not against concrete classes**.

---

## Syntax

```java
interface Speaker {
    void speak();           // public abstract by default
}

class Dog implements Speaker {
    @Override
    public void speak() {
        System.out.println("Woof");
    }
}
```

- `interface` declares the contract.
- Methods inside are implicitly `public abstract` (pre–Java 8; default and static methods came later).
- A class uses `implements` (not `extends`) to commit to fulfilling the contract.
- The class must provide a body for every interface method.

You can then write code against the interface, not the class:

```java
Speaker s = new Dog();
s.speak();
```

The variable's type is `Speaker`. The caller doesn't need to know it's a `Dog` — just that it can `speak()`.

---

## Multiple interfaces

A class can implement many interfaces. This is how Java provides "multiple inheritance" without the messiness of multiple-parent classes:

```java
interface Swimmer { void swim(); }
interface Flyer   { void fly();  }

class Duck implements Swimmer, Flyer {
    public void swim() { System.out.println("paddling"); }
    public void fly()  { System.out.println("flapping"); }
}
```

`Duck` is a `Swimmer` and a `Flyer`. Methods that need either capability can accept it directly:

```java
void racePool(Swimmer s)   { s.swim(); }
void crossSky(Flyer f)     { f.fly();  }

Duck d = new Duck();
racePool(d);    // works — Duck is a Swimmer
crossSky(d);    // works — Duck is also a Flyer
```

---

## `default` methods (Java 8+)

By default, interface methods have no body — implementations must provide one. Java 8 added `default` methods, which *do* have a body. They give the interface a concrete fallback that implementations can use or override.

```java
interface Logger {
    void log(String message);

    default void warn(String message) {
        log("[WARN] " + message);
    }
    default void error(String message) {
        log("[ERROR] " + message);
    }
}
```

Anyone implementing `Logger` only needs to implement `log`. They get `warn` and `error` for free.

Why this matters: it lets you *add new methods to an interface without breaking existing implementations*. Before Java 8, adding a method to a widely-used interface meant a flag day — every implementation had to be updated. `default` methods give you a way to evolve interfaces over time.

---

## `static` methods on interfaces

Java 8 also added static methods on interfaces:

```java
interface Comparator<T> {
    int compare(T a, T b);

    static <T extends Comparable<T>> Comparator<T> naturalOrder() {
        return Comparable::compareTo;
    }
}
```

Static methods belong to the interface itself, not to implementations. They're called as `Comparator.naturalOrder()`. Useful for factory methods or helpers conceptually tied to the interface.

---

## Constants

Fields in an interface are implicitly `public static final` — constants, in effect:

```java
interface Limits {
    int MAX_RETRIES = 3;        // public static final, by default
}
```

Use this sparingly. "Constants interface" was once a popular pattern, but the modern recommendation is to use a regular class with `private` constructor and `public static final` fields. Interfaces are for behavior, not just shared constants.

---

## Marker interfaces

An interface with no methods, used to *tag* a class as supporting some property:

```java
interface Serializable { }   // really empty in java.io.Serializable

class User implements Serializable { ... }
```

A `User` is now "tagged as serializable." The Java serialization machinery checks `instanceof Serializable` before allowing serialization. The interface is a yes/no flag, not a behavior contract.

Modern Java prefers annotations (`@Cacheable`, `@Entity`, etc.) for the same use case. Marker interfaces still exist — `Cloneable`, `Serializable`, `RandomAccess` — but new code generally reaches for annotations.

---

## Functional interfaces

An interface with exactly *one* abstract method is called a **functional interface**. They have special status: you can use a lambda expression wherever one is expected.

```java
interface Greeter {
    String greet(String name);
}

Greeter g = name -> "Hello, " + name;
System.out.println(g.greet("Alice"));    // Hello, Alice
```

Java's standard library is full of these: `Runnable`, `Comparator`, `Function`, `Predicate`, etc. They're the foundation of Java 8+ stream API and the modern functional-style code you'll write later.

The `@FunctionalInterface` annotation is optional but useful — it tells the compiler to error if you accidentally add a second abstract method.

```java
@FunctionalInterface
interface Greeter {
    String greet(String name);
}
```

(Functional interfaces and lambdas are covered properly in module 03; this is a preview.)

---

## Interface vs. abstract class — the actual decision

Both can declare methods that subtypes must implement. So when do you use which?

- Use an **interface** when:
  - You're describing a *capability* (`Comparable`, `Iterable`, `Runnable`, `Closeable`).
  - You want a class to potentially have multiple such capabilities.
  - You don't need shared state or constructors.
- Use an **abstract class** when:
  - There's shared *state* across implementations (fields).
  - There's shared *implementation* (concrete helper methods that all subclasses should use).
  - There's a single, focused "what" that all implementations are kinds of.

**When in doubt, start with an interface.** It's the lower-commitment option. If you later need shared implementation, you can introduce an abstract class that implements the interface — many real codebases use both together (e.g. `AbstractList implements List`).

---

## Why interfaces matter in real backend code

```java
class UserService {
    private final UserRepository repo;       // interface, not a class

    UserService(UserRepository repo) {
        this.repo = repo;
    }

    User findById(long id) {
        return repo.findById(id);
    }
}
```

`UserRepository` is an interface. Its real implementation might talk to a PostgreSQL database. For tests, you swap in a fake in-memory implementation. The `UserService` doesn't change.

This is **dependency injection**. It works because `UserService` is written against the abstraction (`UserRepository`), not against any specific implementation. Spring's entire job is wiring these dependencies together.

Internalize this and the rest of the Spring world makes much more sense.

---

## Common pitfalls

- **Using interfaces with no implementations.** An interface with one implementor used in one place may be over-engineering. Add the interface when you have (or expect) a real need to vary the implementation.
- **Putting state in interfaces.** Interface fields are constants. If you find yourself wanting per-instance state, you want an abstract class instead.
- **"Constant interfaces."** Sticking a bunch of `public static final` fields in an interface so classes can `implement` it for short names. Don't — use a regular class with `private` constructor.
- **Treating `default` methods as a place to put real logic.** They're meant for evolution and small conveniences. Don't pack complex behavior into them.

---

## Code examples

1. `BasicInterface.java` — a `Speaker` interface and two implementations.
2. `MultipleInterfaces.java` — a `Duck` that's both `Swimmer` and `Flyer`.
3. `DefaultMethods.java` — interface evolution without breaking existing code.
4. `FunctionalInterfaceAndLambda.java` — single-abstract-method interface, lambda short form.
5. `InterfaceVsAbstractClass.java` — the same idea expressed both ways for comparison.

---

## Try this yourself

1. In `BasicInterface.java`, add a `Cat` and `Parrot`. Put them all in `Speaker[]` and call `speak()` on each.
2. In `DefaultMethods.java`, override the `warn` default method in one of the implementations. Confirm the other still uses the default.
3. In `FunctionalInterfaceAndLambda.java`, write your own functional interface (single abstract method) and use a lambda to provide its implementation.

---

## Self-check

1. Why is it considered better to "program against interfaces, not against concrete classes"?
2. A class implements two interfaces and both have a `default void log()` method. What problem does this create, and how does Java want you to solve it?
3. You have an `interface Runnable { void run(); }`. Why is this a "functional interface" and what does that let you do?
