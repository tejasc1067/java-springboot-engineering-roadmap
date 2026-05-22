# 07 — Polymorphism

The word means "many forms." In Java it means: code written against a general type (`Animal`) works correctly with any specific subtype (`Dog`, `Cat`, `Bird`) — and each subtype's behavior is invoked at runtime.

It's the mechanism that makes frameworks like Spring possible. A method that accepts `UserRepository` works the same whether the actual object is a database-backed repository, an in-memory one, or a mock for tests. The caller doesn't know or care.

---

## Two kinds, briefly

You've already seen both:

- **Compile-time polymorphism (overloading)** — multiple methods with the same name, picked by argument types. Topic 06.
- **Runtime polymorphism (overriding)** — a subclass's method runs when called through a parent-typed reference. The heart of this topic.

When someone says "polymorphism" without qualification, they usually mean runtime polymorphism.

---

## Runtime polymorphism in one example

```java
class Animal {
    void speak() { System.out.println("animal sound"); }
}
class Dog extends Animal {
    @Override void speak() { System.out.println("Woof"); }
}
class Cat extends Animal {
    @Override void speak() { System.out.println("Meow"); }
}

Animal[] zoo = { new Dog(), new Cat(), new Animal() };

for (Animal a : zoo) {
    a.speak();
}
// Output:
//   Woof
//   Meow
//   animal sound
```

The loop is written against `Animal`. It doesn't know or care about `Dog` vs. `Cat`. But each `speak()` call dispatches to the correct version at runtime, based on the actual object behind the reference.

This is the entire idea. Generic code, specific behavior.

---

## Reference type vs. object type

A variable has two types:

- **Reference type** (declared): what methods the compiler will let you call on the variable.
- **Object type** (actual): which class's methods actually run at the call site.

```java
Animal a = new Dog();    // reference type Animal, object type Dog
```

- `a.speak()` works — `Animal` has a `speak` method (compiler check), and the actual `Dog.speak()` runs (runtime dispatch).
- `a.bark()` would NOT compile — `Animal` doesn't have `bark`, even though the underlying `Dog` does.

To call `Dog`-specific methods, you'd need to **cast back down**:

```java
if (a instanceof Dog) {
    Dog d = (Dog) a;
    d.bark();
}
```

The `instanceof` check guards against ClassCastException. With Java 16+ you can do this in one go ("pattern matching for instanceof"):

```java
if (a instanceof Dog d) {
    d.bark();
}
```

**Note:** needing to cast is usually a code smell. If you're constantly checking "is this actually a Dog?", you've designed the polymorphism wrong. Better: put the behavior on `Animal` (or an interface), and let each subtype implement it.

---

## Polymorphism via method parameters

The most common use: write a method that takes a general type, and let callers pass any subtype.

```java
void feed(Animal a) {
    a.speak();             // dispatches to whatever the actual object is
    System.out.println("here's some food");
}

feed(new Dog());           // works
feed(new Cat());           // works
feed(new Bird());          // works tomorrow when you add Bird
```

The `feed` method doesn't change as you add new animals. New subtypes "just work." This is what makes polymorphism a **scaling** technique — you can grow the system without modifying existing code (the **O** in SOLID; topic 14).

---

## Polymorphism via collections

```java
List<Animal> zoo = new ArrayList<>();
zoo.add(new Dog());
zoo.add(new Cat());

for (Animal a : zoo) {
    a.speak();
}
```

A heterogeneous collection of "things that are an Animal" — useful for any "do operation X on a list of things" pattern. Spring uses this for things like "find all `@Component`-annotated beans of type `Validator` and run each one."

---

## How runtime dispatch actually works (one level deeper)

Each class has a hidden table of method pointers (sometimes called a vtable). When you call `a.speak()`:

1. The JVM looks at the actual object's class (not the variable's declared type).
2. It finds `speak` in that class's table, which points to the overridden version.
3. It calls that.

You don't have to think about the table. The takeaway: **method calls go through one level of indirection so the correct override can be picked at runtime.**

This is the same mechanism behind C++ virtual functions, Python method dispatch, and most other OO languages. Java makes every non-`final`, non-`static`, non-`private` method virtual by default.

---

## Polymorphism and design

Polymorphism unlocks a few design patterns you'll see constantly:

- **Strategy** — a method takes a "strategy" interface; you pass different implementations for different behaviors.
- **Template method** — a parent class defines the algorithm in `runWorkflow()`; subclasses override specific steps.
- **Dependency injection** — a class depends on an interface; the framework supplies the actual implementation at startup. *This is the entire premise of Spring.*

Each of these depends on the fact that code written against a parent type or interface runs correctly with any subtype.

---

## Common pitfalls

- **Casting all over the place.** If you're constantly doing `if (a instanceof Dog) ((Dog) a).bark();` your polymorphism is broken — push `bark` (or a more general method) onto `Animal` instead.
- **Fields don't polymorphism the same way methods do.** A subclass field with the same name as a parent field *hides* it (topic 5), but doesn't override. Field access is resolved at compile time, by the reference type.
- **Static methods don't override.** They're resolved by reference type, not object type. A `Dog`'s static method is a different method from `Animal`'s, not a polymorphic override.
- **Private methods don't override.** They're invisible to the subclass entirely.

---

## Code examples

1. `RuntimePolymorphism.java` — heterogeneous `Animal[]`; calls dispatch correctly.
2. `ReferenceVsObjectType.java` — what you can/can't call through a parent-typed variable.
3. `InstanceOfAndCasting.java` — the right and wrong ways to "go back down."
4. `PolymorphicArgument.java` — one method accepts any `Animal` subtype.

---

## Try this yourself

1. In `RuntimePolymorphism.java`, add a `Bird` class with its own `speak()`. Add a `new Bird()` to the array. The loop doesn't change.
2. In `InstanceOfAndCasting.java`, refactor so the cast is no longer needed — by moving the `Dog`-specific behavior up to `Animal` (with a default), or by using an interface.
3. In `PolymorphicArgument.java`, write a `feedAll(List<Animal>)` method that calls `feed` on each. Test it with a mixed list.

---

## Self-check

1. `Animal a = new Dog(); a.speak();` — explain what happens at compile time and what happens at runtime.
2. Why can't you call `a.bark()` directly on a variable declared as `Animal`, even if the underlying object is a `Dog`?
3. You're writing a method that processes a list of objects, each of which might be one of 5 unrelated classes. What's the polymorphism-friendly way to structure this?
