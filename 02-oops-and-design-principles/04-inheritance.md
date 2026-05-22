# 04 — Inheritance

Inheritance lets one class take all the fields and methods of another and add to them. The keyword is `extends`. The relationship is called "subclass extends superclass" or "child extends parent."

It's one of the four OOP pillars, and it's also the one most often misused. Most code that *looks* like it wants inheritance actually wants composition. We'll cover both — and how to tell which you need.

---

## The mechanics

```java
class Animal {
    String name;

    void eat() {
        System.out.println(name + " is eating");
    }
}

class Dog extends Animal {
    void bark() {
        System.out.println(name + " is barking");
    }
}

Dog d = new Dog();
d.name = "Rex";
d.eat();      // inherited from Animal
d.bark();     // defined on Dog
```

`Dog` inherits `name` and `eat()` from `Animal` automatically. It also defines `bark()` of its own. A `Dog` is everything an `Animal` is, plus more.

Java supports **single inheritance** only — a class can extend exactly one superclass. (For multiple inheritance of behavior, Java uses interfaces — topic 09.)

---

## Every class extends `Object`

If you don't write `extends X`, you implicitly extend `Object`:

```java
class Animal { }
// equivalent to:
class Animal extends Object { }
```

That's why every Java object has `toString()`, `equals()`, `hashCode()` — they come from `Object`. (Topic 10 goes into these.)

---

## Constructor chaining: `super(...)`

When you create a subclass instance, the *superclass's* constructor runs first. Java does this for you implicitly if the superclass has a no-arg constructor:

```java
class Animal {
    Animal() {
        System.out.println("Animal constructor");
    }
}

class Dog extends Animal {
    Dog() {
        System.out.println("Dog constructor");
    }
}

new Dog();
// Output:
//   Animal constructor
//   Dog constructor
```

If the superclass has *only* parameterized constructors, you must call one explicitly with `super(...)`:

```java
class Animal {
    String name;
    Animal(String name) { this.name = name; }
}

class Dog extends Animal {
    Dog(String name) {
        super(name);              // must be first statement; no default ctor exists
    }
}
```

Constructor chaining is covered in detail in topic 05 (`super` keyword).

---

## What gets inherited

- **`public` and `protected`** members of the parent — yes.
- **package-private** members — yes if the subclass is in the same package.
- **`private`** members — present in memory, but not directly accessible from subclass code. (You'd need a `protected` getter or similar to read them.)
- **Constructors** — *not* inherited. The subclass must define its own (or get the implicit default).

---

## The "is-a" test

Before extending, ask: **"Is a Dog an Animal?"** If yes, inheritance is fine. If no, it's wrong.

This sounds obvious but is the source of nearly every inheritance disaster. Some examples:

| Relationship | Use inheritance? | Reason |
|--------------|------------------|--------|
| Dog **is-a** Animal | yes | Dog truly is a kind of Animal. |
| SavingsAccount **is-a** Account | yes | Genuine subtype. |
| Stack **is-a** ArrayList | **no** | Stack isn't "a kind of resizable array." It's *implemented using* one. |
| Engine **is-a** Car | no | Engine is part of a Car (composition). |
| HashMap **is-a** Hashtable | sort of, in Java's standard library; widely considered a historical mistake. |

The "Stack extends Vector" pattern in Java's own standard library is the textbook bad-inheritance example. `Stack` inherited every list method, including `add(0, x)`, `remove(index)` — operations that don't make sense for a stack. The class became unfixable.

**Rule of thumb:** if subtyping just to reuse code (not because the *is-a* relationship is real), prefer composition (topic 11).

---

## The Liskov Substitution Principle (in plain English)

A subclass should be usable wherever its superclass is expected, without breaking the program. This is the **L** in SOLID (topic 14).

Concretely: if a method expects an `Animal` and you pass a `Dog`, everything that worked with `Animal` should still work with `Dog`. A `Dog` shouldn't suddenly refuse to `eat()`, or `eat()` in a way that violates the parent class's contract.

The famous counterexample: a `Square extends Rectangle`. A `Rectangle` allows setting width and height independently. A `Square` can't (they must stay equal), so setting width on a "square" silently breaks expectations of code that was written against `Rectangle`. The hierarchy is unsound.

If subclassing forces you to violate the parent's contract, the inheritance is wrong. Step back and reconsider — maybe an interface (topic 09), or composition.

---

## Inheritance vs. composition

Two ways to reuse code from another class:

**Inheritance (is-a):**

```java
class Dog extends Animal {
    // Dog automatically has everything Animal has
}
```

**Composition (has-a):**

```java
class Car {
    private final Engine engine;       // a Car HAS an Engine
    Car(Engine engine) { this.engine = engine; }
    void start() { engine.start(); }   // Car uses Engine's behavior
}
```

Composition gives you the same code reuse without the "every subclass automatically gets everything" coupling. You expose only the operations *you* want, even if internally you delegate to another object.

**Default to composition.** Use inheritance only when (a) the is-a test passes, and (b) you genuinely want every parent method to be part of the subclass's contract.

This is one of the most important design heuristics in OOP. Topic 11 goes deeper.

---

## Common pitfalls

- **Inheritance for code reuse.** "I have a method I want to reuse, so I'll extend." Composition does this without the inheritance baggage.
- **Inheritance hierarchies more than ~3 levels deep.** Hard to reason about. Each level multiplies behavior; subtle bugs hide.
- **Subclasses that override methods to do "nothing" or "the opposite."** A sign the inheritance relationship is wrong — the parent's contract isn't right for this subclass.
- **Adding fields to the parent that only some subclasses need.** Every subclass pays for the field. Push the field down to the subclass that actually uses it, or restructure.

---

## Code examples

1. `SimpleInheritance.java` — Animal/Dog, the textbook example.
2. `InheritanceChain.java` — three levels: Animal → Mammal → Dog.
3. `ConstructorOrder.java` — prove that superclass constructors run before subclass ones.
4. `WrongInheritanceBroken.java` — a Stack-like class extending ArrayList; show how inheritance leaks operations that shouldn't exist.
5. `CompositionFixed.java` — same Stack rebuilt as a class that *contains* an ArrayList instead of being one.

---

## Try this yourself

1. In `InheritanceChain.java`, add a `Puppy extends Dog` class and override one of its methods. Run and trace the call.
2. In `WrongInheritanceBroken.java`, call the methods inherited from `ArrayList` that don't make sense for a stack (like `add(0, x)`, `get(0)`). Confirm they exist and corrupt the stack.
3. In `CompositionFixed.java`, try to use any of those leaky methods — they're not on the composed class.

---

## Self-check

1. Apply the "is-a" test to: `Manager extends Employee`. `SortedList extends ArrayList`. `Truck extends Vehicle`. Which are sound?
2. Why does Java force you to call `super(...)` first in a constructor, before any other statements?
3. Give one specific reason "composition over inheritance" is the standard advice.
