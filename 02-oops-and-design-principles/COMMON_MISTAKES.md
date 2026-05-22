# Common Mistakes — OOP and Design Principles

Real bugs in OOP-shaped code: the kind that compile, look right, pass casual review, and produce subtle failures later. Each mistake names a specific bad pattern and shows the fix.

---

## Classes and objects

### 1. Confusing reference assignment with copying

```java
// BAD — caller mutates the original
List<String> internal = new ArrayList<>();
internal.add("Alice");
List<String> copy = internal;          // not a copy; same list
copy.add("Bob");
// internal now also has "Bob"
```

**Fix:** create a real copy when you mean "copy."
```java
List<String> copy = new ArrayList<>(internal);
```

### 2. Comparing objects with `==`

```java
String a = readUserInput();
String b = "expected";
if (a == b) { ... }       // sometimes works, often doesn't
```

`==` compares references. For strings (and any other class with meaningful value equality) use `.equals()`.

**Fix:**
```java
if (a.equals(b)) { ... }
// Or, null-safe:
if (Objects.equals(a, b)) { ... }
```

### 3. Treating uninitialized references as safe

```java
class Service {
    UserRepository repo;        // never assigned → null
    User load(long id) {
        return repo.findById(id);   // NPE
    }
}
```

**Fix:** initialize in the constructor (or use dependency injection). Make fields `final` when possible — the compiler forces you to set them.

---

## Constructors

### 4. Writing your own constructor and forgetting the implicit no-arg is gone

```java
class Car {
    Car(String brand) { ... }
}

new Car();    // compile error — no zero-arg constructor exists
```

Common surprise when you upgrade `Car` from "no constructors" to "one constructor." Every existing `new Car()` breaks.

**Fix:** if you want a no-arg form, declare it explicitly: `Car() { this("unknown"); }`.

### 5. Constructors that do heavy work

```java
class UserService {
    UserService() {
        loadConfigFromDisk();
        connectToDatabase();
        warmCaches();
    }
}
```

Construction can now fail in many ways, takes seconds, blocks the calling thread, and is hard to test.

**Fix:** constructor just assigns fields; provide a static factory or `init()` method for heavy work. Better, inject dependencies fully-formed.

### 6. Calling overridable methods from a constructor

```java
class Parent {
    Parent() { onCreated(); }   // calls subclass override before subclass is initialized
    protected void onCreated() { }
}
class Child extends Parent {
    private final List<String> tags = new ArrayList<>();
    @Override protected void onCreated() {
        tags.add("hi");          // NPE — `tags` not assigned yet
    }
}
```

Famously subtle. The parent constructor runs first, then field initializers in the child, then the child constructor body. If the parent calls `onCreated()`, the child's override runs against uninitialized fields.

**Fix:** never call overridable methods from constructors. If you must, mark them `final`.

---

## Encapsulation

### 7. Public fields

`public String name;` means anyone can set it to anything, anytime. Rules can't be enforced.

**Fix:** `private` fields, methods that maintain invariants.

### 8. Setters that don't actually validate

```java
public void setAge(int age) {
    this.age = age;            // accepts -50, 9999, anything
}
```

Encapsulation in name only.

**Fix:** if a setter exists, it should enforce rules. If there are no rules, ask whether the field should even be mutable.

### 9. Returning the internal mutable collection

```java
public List<Item> getItems() { return items; }   // caller can clear/add at will
```

**Fix:** return an unmodifiable view or a defensive copy.
```java
return Collections.unmodifiableList(items);
// or
return new ArrayList<>(items);
```

---

## Inheritance

### 10. Inheriting for code reuse, not "is-a"

```java
class Stack<T> extends ArrayList<T> { ... }     // Stack inherits add(int, T), get(int), remove(int)
```

The relationship "Stack is a kind of ArrayList" isn't actually true — a Stack has a contract (LIFO) the ArrayList doesn't enforce.

**Fix:** composition. `class Stack<T> { private final List<T> items = new ArrayList<>(); ... }`.

### 11. Calling super(...) too late

```java
Dog(String name, String breed) {
    this.breed = breed;     // ← can't be here
    super(name);            // ← must be first
}
```

`super(...)` and `this(...)` must be the first statement. Compile error.

**Fix:** call `super(...)` first, then do your work.

### 12. Overriding without `@Override`

```java
class Dog extends Animal {
    void Speak() { ... }      // typo — capital S; doesn't override anything
}
```

The compiler can't catch this without `@Override`. The "override" is silently a brand-new method that nobody calls.

**Fix:** always annotate overrides with `@Override`. The compiler verifies.

### 13. Narrowing access in an override

```java
class Parent {
    public void process() { ... }
}
class Child extends Parent {
    private void process() { ... }    // compile error
}
```

You can widen access in an override (`protected` → `public`), never narrow.

**Fix:** match or widen access. If you really don't want subclasses to expose it, mark the parent `final`.

---

## Polymorphism

### 14. Excessive `instanceof` checks

```java
for (Animal a : animals) {
    if (a instanceof Dog d) d.bark();
    else if (a instanceof Cat c) c.purr();
    else if (a instanceof Cow cw) cw.moo();
}
```

This is a giant dispatch table you wrote by hand — exactly what polymorphism is meant to replace.

**Fix:** put a common method on `Animal` (or an interface):
```java
abstract void makeSound();
// each subclass implements it
for (Animal a : animals) a.makeSound();
```

### 15. Static methods masquerading as overrides

```java
class Animal { static void info() { System.out.println("animal"); } }
class Dog extends Animal { static void info() { System.out.println("dog"); } }

Animal a = new Dog();
a.info();   // prints "animal" — static methods aren't polymorphic
```

Static methods are resolved by reference type, not object type. They don't override.

**Fix:** if you need polymorphism, use an instance method.

---

## Object methods

### 16. Overriding `equals` without `hashCode`

The classic bug. The HashSet check returns false even though `equals` says yes.

**Fix:** always override both together. Use the same fields in each. `Objects.hash(...)` makes `hashCode` trivial.

### 17. Mutable fields participating in `equals`/`hashCode`

```java
class User {
    String name;        // mutable
    @Override public int hashCode() { return name.hashCode(); }
}
Set<User> set = new HashSet<>(); set.add(user); user.name = "X";
set.contains(user);   // false — hash changed
```

**Fix:** make fields used in `equals`/`hashCode` immutable, or don't put mutable objects in hashed collections.

---

## Immutability

### 18. `final` doesn't mean immutable

```java
final List<String> names = new ArrayList<>();
names.add("X");   // perfectly legal
```

**Fix:** use immutable types (`List.copyOf`, `List.of`) when you want unmodifiability.

### 19. Forgetting defensive copying

```java
record User(String name, List<String> tags) { }   // tags is mutable through caller's reference
```

Records don't defensively copy mutable fields.

**Fix:** copy in the compact constructor:
```java
record User(String name, List<String> tags) {
    public User {
        tags = List.copyOf(tags);
    }
}
```

### 20. Using `java.util.Date` (it's mutable)

`java.util.Date` is mutable, deprecated for most uses, and a constant source of "why did this date change?" bugs.

**Fix:** `java.time.LocalDate` / `Instant` / `LocalDateTime`. All immutable.

---

## Design / SOLID

### 21. God classes

A class with 30 fields and 50 methods doing five unrelated jobs. Hard to test, hard to change safely.

**Fix:** when a "Service" / "Manager" / "Helper" class pushes past ~200 lines, look for natural seams to split along.

### 22. Hardcoded dependencies (`new` inside a class)

```java
class UserService {
    private final UserRepository repo = new MySqlUserRepository();   // bolted to MySQL
}
```

Can't test, can't swap, can't add caching without modifying this class.

**Fix:** dependency injection.
```java
UserService(UserRepository repo) { this.repo = repo; }
```

### 23. Inheritance hierarchies more than 3 levels deep

```
GenericEntity → AuditableEntity → SoftDeletableEntity → VersionedEntity → User
```

Each level multiplies behavior. Subtle bugs hide in the interaction.

**Fix:** flatten. Use composition or annotations/aspects for cross-cutting concerns instead of stacking inheritance.

### 24. Subclasses that throw `UnsupportedOperationException`

```java
class ReadOnlyList extends ArrayList<String> {
    @Override public boolean add(String s) { throw new UnsupportedOperationException(); }
}
```

You inherited a contract you can't fulfill. Liskov violation.

**Fix:** either don't extend, or extend a parent whose contract you can honor. (Java's own `List.of` returns an unmodifiable list — fine — but it's documented as such and the JDK code carefully avoids contracts it can't keep.)

### 25. One giant "Utility" or "Helper" class

A static-methods grab bag (`Utils`, `Helper`, `Common`) that grows over time. Becomes a coupling magnet.

**Fix:** name and group by purpose. `StringFormatter`, `DateParser`, etc. Static utility classes are fine when they're focused.

---

## Records and interfaces

### 26. Treating records as classes with JavaBean getters

```java
record User(String name, int age) { }
user.getName();    // ← compile error; the accessor is name(), not getName()
```

**Fix:** records use `name()` style accessors. If your team's framework demands JavaBean-style, use a regular class.

### 27. Trying to subclass a record

Records are implicitly `final` — they can't be extended.

**Fix:** if you need subtypes, use an interface or abstract class instead.

### 28. Interface with exactly one implementor that nobody mocks

```java
interface UserService { ... }
class UserServiceImpl implements UserService { ... }
```

Sometimes legitimate. Often over-engineering.

**Fix:** add the interface when there's a real reason — multiple implementations, testing isolation, public API stability. Otherwise just use the concrete class.

### 29. "Constants interface"

```java
interface Limits {
    int MAX_PAGE_SIZE = 100;
}
class Foo implements Limits { ... }   // "for short names"
```

The `implements` is for behavior, not import shorthand. Pollutes the type hierarchy.

**Fix:** put constants on a regular class with a private constructor; use `Limits.MAX_PAGE_SIZE` or `import static`.

### 30. Default method colliding across two interfaces

```java
interface A { default void op() { ... } }
interface B { default void op() { ... } }
class C implements A, B { }    // compile error — ambiguous
```

**Fix:** the implementing class must override and choose which to call:
```java
class C implements A, B {
    @Override public void op() { A.super.op(); }
}
```
