# 05 — The `super` Keyword

When a subclass needs to reach back to its parent — to call the parent's constructor, run the parent's version of a method, or read a field the subclass shadowed — the keyword is `super`.

It has three uses, in roughly descending order of importance:

1. `super(...)` — call a parent class's constructor.
2. `super.method(...)` — call the parent's version of a method that the subclass has overridden.
3. `super.field` — read a parent field hidden by a same-named subclass field.

---

## `super(...)` for constructor chaining

When you `new` a subclass, the parent constructor must run first. Topic 04 showed Java does this implicitly when the parent has a no-arg constructor. When the parent *only* has parameterized constructors, you must call one explicitly with `super(...)`:

```java
class Animal {
    String name;
    Animal(String name) {
        this.name = name;
    }
    // No no-arg constructor.
}

class Dog extends Animal {
    String breed;
    Dog(String name, String breed) {
        super(name);             // ← required; calls Animal(String)
        this.breed = breed;
    }
}
```

Rules:

- `super(...)` must be the **first statement** in the constructor.
- A constructor can call `super(...)` OR `this(...)` (delegating to another constructor of the same class), not both.
- If you write neither, Java inserts implicit `super()` (the parent's no-arg constructor). If no such constructor exists, you get a compile error.

This is why a parameterized-only parent constructor forces every subclass to think about what to pass.

---

## `super.method()` for accessing the parent's version

When a subclass **overrides** a method (topic 06), it usually replaces the parent's behavior. Sometimes you want both — do what the parent did, then add more. `super.method()` runs the parent's version.

```java
class Animal {
    void describe() {
        System.out.println("I am an animal");
    }
}

class Dog extends Animal {
    @Override
    void describe() {
        super.describe();                            // parent's version first
        System.out.println("Specifically, I am a dog");   // then extra detail
    }
}

new Dog().describe();
// I am an animal
// Specifically, I am a dog
```

Common pattern in real code: a subclass's `toString()` calls `super.toString()` to include the parent's representation, then appends the subclass's extra fields.

Without `super.`, calling `describe()` from inside `Dog.describe()` would just recurse into itself infinitely.

---

## `super.field` for accessing a hidden field

If a subclass declares a field with the same name as a parent field, the subclass field **hides** (not overrides — fields aren't polymorphic) the parent's. `super.field` reads the parent's.

```java
class Animal {
    String type = "Animal";
}

class Dog extends Animal {
    String type = "Dog";

    void describe() {
        System.out.println("this.type  = " + this.type);    // "Dog"
        System.out.println("super.type = " + super.type);   // "Animal"
    }
}
```

This is mostly a curiosity — **hiding fields is almost always a mistake.** If you find yourself reaching for `super.field`, ask whether the subclass field should have a different name, or whether it should be removed entirely.

---

## What `super` is NOT

- `super` is **not** an object. You can't say `Animal a = super;`. It's a syntactic mechanism for accessing the parent's view of `this`.
- There's no `super.super.method()`. Java only lets you reach one level up. If you need to skip a level, the design is probably wrong.
- `super(...)` can't be used to "skip" a class in the hierarchy. It calls *the immediate parent's* constructor.

---

## Why `super(...)` must be first

The parent class must be fully constructed before the subclass adds its own initialization. If the subclass did anything before `super(...)`, it might use fields that the parent hasn't initialized yet, or call overridden methods that depend on parent state. Java's rule "super first" prevents that whole category of bugs.

(The single exception: `this(...)` calling another constructor of the same class — also must be first. Eventually one of those constructors calls `super(...)`.)

---

## A complete example

```java
class Animal {
    String name;
    Animal(String name) { this.name = name; }
    void describe() { System.out.println("Animal: " + name); }
}

class Dog extends Animal {
    String breed;

    Dog(String name, String breed) {
        super(name);
        this.breed = breed;
    }

    @Override
    void describe() {
        super.describe();                                // parent does its part
        System.out.println("Breed: " + breed);           // we add ours
    }
}

new Dog("Rex", "Labrador").describe();
// Animal: Rex
// Breed: Labrador
```

`super(name)` chains construction. `super.describe()` chains behavior. Both are very common patterns.

---

## Common pitfalls

- **Forgetting `super.method()` in an override.** If the parent's version was doing something important (initializing state, logging, etc.) and your override silently drops it, the bug can be subtle.
- **Putting code before `super(...)`.** Compile error. Often happens when a beginner tries to "compute something to pass to the parent." The trick: do it inline as the argument to `super(...)`, or in a static helper method.
- **Hiding fields by name reuse.** If the parent has `int count` and the subclass also declares `int count`, you now have two unrelated fields. Almost always a mistake.
- **Reaching for `super.super.method()`.** Doesn't exist. If you want that, your inheritance hierarchy is wrong.

---

## Code examples

1. `SuperConstructor.java` — explicit `super(...)` to a parameterized parent.
2. `SuperMethod.java` — overriding while still calling the parent's version.
3. `SuperFieldShadowing.java` — accessing a hidden parent field. Mostly a "don't do this" demo.
4. `SuperInThreeLevels.java` — three-level chain showing `super` at each level.

---

## Try this yourself

1. In `SuperConstructor.java`, remove the `super(...)` call from the subclass. Read the compile error.
2. In `SuperMethod.java`, remove the `super.describe()` call. Notice what behavior is lost.
3. In `SuperFieldShadowing.java`, rename the subclass field to remove the shadowing. Does the program still need `super.`?

---

## Self-check

1. Why must `super(...)` be the first statement in a constructor?
2. When would you call `super.method()` inside an override?
3. What does `super.field` give you, and why is needing it usually a code smell?
