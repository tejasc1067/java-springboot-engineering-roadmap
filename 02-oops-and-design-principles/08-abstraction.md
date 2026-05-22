# 08 — Abstraction

Abstraction means **exposing what something does without exposing how it does it**. The user of a class works against its public contract; they don't see, and don't need to see, the internal implementation.

In Java, abstraction has two main tools: **abstract classes** (this topic) and **interfaces** (topic 09). They overlap but differ in important ways.

---

## The everyday version

You already use abstraction whenever you call a method on someone else's class:

```java
List<String> list = new ArrayList<>();
list.add("hi");
```

You know `add` adds the element. You don't know whether `ArrayList` resizes by doubling or by 1.5×, whether it copies into a new array or uses some other trick. You don't need to — the *abstraction* is the `List` interface, the *implementation* is `ArrayList`.

When you design your own classes, you do the same thing: present a small, intent-revealing surface; hide the details.

---

## `abstract` classes

An **abstract class** is a class that *can't be instantiated directly*. It's meant to be extended.

```java
abstract class Shape {
    String color;

    Shape(String color) { this.color = color; }

    // Concrete method — every shape can do this the same way.
    void describe() {
        System.out.println("A " + color + " " + shapeName() + " with area " + area());
    }

    // Abstract methods — subclasses MUST implement these.
    abstract double area();
    abstract String shapeName();
}

class Circle extends Shape {
    double radius;
    Circle(String color, double radius) {
        super(color);
        this.radius = radius;
    }
    @Override double area()       { return Math.PI * radius * radius; }
    @Override String shapeName()  { return "circle"; }
}

class Rectangle extends Shape {
    double w, h;
    Rectangle(String color, double w, double h) {
        super(color);
        this.w = w; this.h = h;
    }
    @Override double area()       { return w * h; }
    @Override String shapeName()  { return "rectangle"; }
}
```

`Shape` knows the shape of every shape — they have a color and an area. But there's no way to compute area without knowing which specific shape this is, so `area()` is `abstract`. The concrete subclasses provide the implementation.

You can't write `new Shape("red")` — the compiler refuses because `Shape` has unimplemented abstract methods. You *must* go through a concrete subclass.

---

## What an abstract class can and can't do

| Can have | Cannot have |
|----------|-------------|
| Constructors (called by subclasses via `super(...)`) | Direct instantiation with `new` |
| Concrete (non-abstract) methods | An abstract method body |
| `private` and `protected` members | Anything that fundamentally requires a concrete class |
| Abstract methods (the point) | Static abstract methods (static and abstract are contradictory) |
| Instance fields | |

A common pattern: a few concrete methods that define a *workflow*, with abstract methods filling in the variable parts. The user just calls one method on a subclass; everything else is shared.

---

## The "template method" pattern

A classic use of abstract classes: define the *skeleton* of an algorithm in the parent, let subclasses fill in steps.

```java
abstract class ReportGenerator {
    // Public method — fixed workflow.
    public final void generate() {
        fetchData();
        format();
        save();
    }
    // Subclasses customize these.
    protected abstract void fetchData();
    protected abstract void format();
    protected abstract void save();
}

class PdfReport extends ReportGenerator {
    protected void fetchData() { /* PDF-specific */ }
    protected void format()    { /* PDF-specific */ }
    protected void save()      { /* PDF-specific */ }
}

class CsvReport extends ReportGenerator { /* ... */ }
```

Every report follows the same three steps. The order is locked in (`generate()` is `final`). Each subclass changes *what* happens at each step, not *the order*.

You'll see this pattern throughout Spring (e.g. `AbstractAuthenticationProcessingFilter`) and the JDK (`AbstractList`, `AbstractMap`).

---

## Abstract classes vs. interfaces

Two ways to declare "must implement these methods." Quick comparison:

| | Abstract class | Interface |
|--|----------------|-----------|
| Multiple inheritance | One per class | A class can implement many interfaces |
| State (fields) | Yes, instance fields allowed | Only `public static final` constants (and `default`/`static` methods since Java 8) |
| Constructors | Yes | No |
| Best for | "A is a kind of B, sharing some state and behavior" | "A is *capable of* doing X" |
| Backward-compat additions | Add a concrete method — every subclass still works | `default` methods (Java 8+) allow this, but it's awkward |

Rough rule:

- Use an **abstract class** when subtypes share state or non-trivial implementation.
- Use an **interface** when you're describing a capability, especially if a class might fulfill multiple capabilities.

Both are valid; both have a place. Topic 09 goes into interfaces specifically.

---

## Why this matters in real code

In a Spring application, you'll write a `UserService` that depends on a `UserRepository`. The `UserRepository` is an abstraction — you write the *contract* (the methods), and Spring picks the implementation. For tests, you swap in a fake. For production, the real database-backed one is wired in. The `UserService` code never changes.

That's abstraction at the architectural level. The mechanisms — abstract classes and interfaces — are how you spell it in Java code.

---

## Common pitfalls

- **Making everything abstract.** If `Shape` has *every* method abstract, it's really just an interface — switch to one. Abstract classes earn their existence by sharing concrete implementation.
- **Abstract class with no abstract methods.** Then why is it abstract? Either give it abstract methods or make it concrete.
- **Trying to instantiate.** `new Shape(...)` is the first mistake every beginner makes. The compiler stops you.
- **Forgetting that abstract classes can have fields and constructors.** Many people use them like interfaces. The presence of state is *why* you reach for an abstract class.

---

## Code examples

1. `BasicAbstractClass.java` — `Shape` with abstract `area()`; `Circle` and `Rectangle` implement it.
2. `MixAbstractAndConcrete.java` — abstract class with both kinds of methods.
3. `TemplateMethod.java` — fixed workflow in parent, customizable steps in subclasses.
4. `CannotInstantiate_Broken.java` — what happens if you try `new` on an abstract class.

---

## Try this yourself

1. In `BasicAbstractClass.java`, add a `Triangle` class. The compiler will force you to implement `area()` and `shapeName()`.
2. In `TemplateMethod.java`, add a new step (`compress`) to the workflow. Notice every subclass must now implement it.
3. In `MixAbstractAndConcrete.java`, try to make a subclass that doesn't implement one of the abstract methods. Read the compile error.

---

## Self-check

1. Why is `Shape` abstract? Couldn't you just make it a regular class?
2. An abstract class can have a constructor. Who calls it, and when?
3. You have an abstract class with no abstract methods. What's wrong with the design?
