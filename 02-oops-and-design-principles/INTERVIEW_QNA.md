# Interview Questions — OOP and Design Principles

Real interview questions on Java OOP. Each answer is what an interviewer is hoping to hear — not a one-line restatement of the question.

If you can answer these in your own words without looking, you're prepared for the OOP section of any Java backend interview.

---

## Fundamentals

### Q1. What's the difference between a class and an object?

A class is a blueprint — it defines what fields and methods objects of that type have. An object is an actual instance created from the blueprint with `new`. You write the class once; you create many objects. Each object has its own copy of the instance fields.

### Q2. What happens at `new Car()`?

The JVM allocates memory on the heap for a `Car`, initializes each field to its default (`null`/`0`/`false`), runs the constructor chain (parent constructors first, via `super(...)`), and returns a reference to the new object. The variable on the left of the `=` then holds that reference, not the object itself.

### Q3. Why does `Car a = new Car(); Car b = a; b.brand = "Ford";` change what `a` sees?

Because `b = a` copies the reference, not the object. Both variables now point to the same `Car` on the heap. Mutating through either variable changes the same underlying object. This is "reference assignment" — common gotcha for people coming from value-semantic languages.

### Q4. What's the difference between stack and heap memory?

The stack holds local variables and references — fast, automatically cleared when methods return. The heap holds the actual objects you create with `new` — garbage-collected when no references remain. A variable `Car a = new Car()` puts the reference `a` on the stack and the `Car` object on the heap.

---

## Constructors

### Q5. What's a constructor and why do you need one?

A constructor is a special method that runs when an object is created. Its job is to put the new object in a valid initial state. Without constructors (or with empty default ones), objects start with default field values and have to be set up by the caller — error-prone and easy to forget.

### Q6. Why does writing your own constructor remove the no-arg default?

Java only generates a default no-arg constructor when there are *no* constructors at all. Writing your own signals intent — you want construction to require specific inputs. If you also want a no-arg form, declare it explicitly.

### Q7. What's `this(...)` in a constructor?

It calls another constructor of the same class. Used for constructor chaining — one main constructor does the work, others delegate after filling in defaults. Must be the first statement (only one of `this(...)` or `super(...)` per constructor).

### Q8. Should you call methods from a constructor?

Private final methods, yes. Overridable methods, no — a subclass's override might run before the subclass is fully initialized, accessing uninitialized fields. Famously subtle bug.

---

## Encapsulation

### Q9. What is encapsulation, beyond "use `private`"?

Hiding internal state and exposing only the operations that maintain invariants. The point isn't `private` — the point is that the class controls how its state can change, so rules like "balance can't go negative" can actually be enforced. Public fields can't enforce anything; methods can.

### Q10. Your teammate writes a class with `public` fields "because we control all the callers." What's the problem?

"All the callers" today won't be the same in 6 months. Once the field is public, the class can never add a rule about how it's mutated without breaking callers. Encapsulation is cheap insurance against the future — making it `private` with appropriate methods costs ~5 minutes and keeps options open forever.

### Q11. A class has `private List<Item> items` and `public List<Item> getItems()`. Why might this still leak encapsulation?

The getter hands out a reference to the actual internal list. Callers can `.add(...)` or `.clear()` it, bypassing any rules. The class is private in name, mutable through the back door. Fix: return an unmodifiable view or a defensive copy.

---

## Inheritance

### Q12. When should you use inheritance vs. composition?

Inheritance when the relationship is genuinely "is-a" AND you want the parent's full public interface to be part of the child's. Composition for "has-a" relationships, or when you want fine control over what's exposed. Default to composition — it has fewer pitfalls, more flexibility, and is what every popular framework (Spring, Guice) uses heavily.

### Q13. What's wrong with `Stack extends ArrayList`?

A `Stack` has a LIFO contract; an `ArrayList` does not. By inheriting, `Stack` also inherits `add(index, item)`, `remove(index)`, `get(index)` — operations that violate LIFO and can corrupt the stack from outside. Fix: composition. `Stack` should *contain* a list, not *be* one.

### Q14. Explain Liskov Substitution Principle in your own words.

If `B extends A`, then anywhere code expects an `A`, you should be able to substitute a `B` and the program should still work correctly. The subtype must respect the parent's behavioral contract, not just its method signatures. A famous counterexample: `Square extends Rectangle` — geometrically correct, code-wise broken because Square's `setWidth` also changes height, violating Rectangle's contract.

### Q15. Why must `super(...)` be the first statement in a constructor?

So the parent class is fully constructed before any subclass code runs. If you could do work before `super(...)`, you might access fields the parent hasn't initialized, or call methods that depend on parent state. Java's rule prevents that whole category of bug.

---

## Polymorphism

### Q16. What's the difference between overloading and overriding?

Overloading: same method name, different parameter lists, in the same class. Resolved at compile time by the argument types. Overriding: subclass provides its own version of a method inherited from the parent. Resolved at runtime by the actual object type. Different mechanisms, often confused.

### Q17. `Animal a = new Dog(); a.speak();` — explain what happens.

At compile time, the compiler sees `a` typed as `Animal` and checks that `Animal` has a `speak()` method (yes). At runtime, the JVM looks at the actual object — a `Dog` — and dispatches to `Dog.speak()` (the override). This is runtime polymorphism. The reference type controls what you can call; the object type controls what actually runs.

### Q18. Why is `@Override` important even though it's optional?

It tells the compiler "this is intended to override a parent method — error out if it doesn't." Catches typos and signature mismatches that silently turn an override into a new method. Costs nothing to add; prevents real bugs.

### Q19. Can a static method be overridden?

No. Static methods belong to the class, not the instance, so they're resolved by reference type, not object type. A static method in a subclass with the same name *hides* the parent's, but isn't an override. There's no runtime dispatch for it.

### Q20. You have a `for` loop with `if (a instanceof Dog) ... else if (a instanceof Cat) ...`. What's the design smell?

You're doing manual dispatch by type — exactly what polymorphism is supposed to handle. Push the type-specific behavior onto the common parent or interface (`abstract void speak()`), and the loop becomes `for (Animal a : animals) a.speak();`. No casts needed.

---

## Abstraction

### Q21. When would you use an abstract class vs. an interface?

Abstract class when subtypes share state (fields) or non-trivial implementation. Interface when you're describing a capability that might be implemented by unrelated classes, or when you want a class to have multiple capabilities (Java allows multiple interface inheritance, only single class inheritance). When unsure, start with an interface — lower commitment.

### Q22. Can an abstract class have a constructor?

Yes. It can't be called directly (you can't `new` an abstract class), but it runs when a concrete subclass is constructed (via implicit or explicit `super(...)`). Often used to enforce that subclasses provide certain values up front.

### Q23. What's a default method on an interface and why was it added?

A method declared on an interface with an implementation (`default void foo() { ... }`). Added in Java 8 to let interfaces evolve without breaking every existing implementation — you can add a new default method and all existing implementations inherit the default. Before Java 8, adding a method to a widely-used interface was a flag day.

### Q24. What's a functional interface?

An interface with exactly one abstract method. Special status: you can implement it with a lambda expression. Examples: `Runnable`, `Comparator`, `Function`, `Predicate`. The `@FunctionalInterface` annotation is optional but tells the compiler to enforce the single-abstract-method rule.

---

## Interfaces

### Q25. "Program to an interface, not an implementation" — what does that mean in practice?

Code should depend on the abstract type (`List`, `UserRepository`) rather than a specific class (`ArrayList`, `MySqlUserRepository`). That way you can swap the implementation later — for tests, for different databases, for performance — without changing the dependent code. This is the foundation of dependency injection and the **D** in SOLID.

### Q26. A class implements two interfaces, both with `default void log()`. What happens?

Compile error — Java can't pick. The class must override `log()` and explicitly call one (or do something else): `A.super.log();` or `B.super.log();`. The "diamond problem" exists in Java's default-method world too; Java requires you resolve it explicitly.

---

## Object methods

### Q27. What's the default `equals()` behavior, and why is it usually not what you want?

Default `equals(Object o)` returns `this == o` — reference equality, same as `==`. For most domain classes you want value equality (two Users with the same name and age are equal). You override `equals` to compare fields.

### Q28. State the equals/hashCode contract.

If `a.equals(b)` is true, then `a.hashCode() == b.hashCode()` must also be true. The reverse doesn't need to hold (different objects can share a hash). If you override `equals` without `hashCode`, a `HashSet` or `HashMap` will misplace your objects — `set.contains(equivalentObject)` returns false.

### Q29. What does Java's `record` give you for free?

A `final` class with `final` fields, a canonical constructor, accessor methods (named after the components — `name()` not `getName()`), `equals`/`hashCode`/`toString` based on all fields, and immutability. Perfect for value classes / DTOs. Can't be subclassed.

---

## Immutability

### Q30. List the conditions for a class to be truly immutable.

1. The class is `final`.
2. All fields are `private final`.
3. No setters or mutating methods.
4. Mutable fields are defensively copied on the way in and on the way out (or wrapped in an unmodifiable view).
5. The constructor fully initializes all fields.

Miss any one and the class isn't really immutable.

### Q31. Why is immutability valuable in concurrent code?

Immutable objects can be shared across threads without locks. There's no state to corrupt, no visibility issue (the JVM's happens-before guarantees that final fields are visible after construction). For multithreaded backend code, immutable value objects are by far the easiest to reason about.

### Q32. `final List<String> names = new ArrayList<>(); names.add("X");` — does this compile?

Yes. `final` makes the reference immutable, not the object it points to. The variable can't be reassigned, but the underlying list is still mutable. For true immutability, use `List.of(...)` or `List.copyOf(...)`.

---

## SOLID

### Q33. Explain each letter of SOLID in one sentence.

- **S** (Single Responsibility): a class should have only one reason to change.
- **O** (Open/Closed): open for extension, closed for modification — add new behavior via new classes, not by editing existing ones.
- **L** (Liskov): subtypes must be substitutable for their base types without breaking correctness.
- **I** (Interface Segregation): many small focused interfaces are better than one big general one.
- **D** (Dependency Inversion): depend on abstractions, not concrete implementations.

### Q34. Give a concrete example of violating Single Responsibility, then fixing it.

`InvoiceService` does calculation, PDF rendering, email sending, and database persistence. Four reasons to change. Fix: split into `InvoiceCalculator`, `InvoicePdfRenderer`, `InvoiceEmailSender`, `InvoiceRepository` — each independently testable and changeable.

### Q35. How does Spring embody Dependency Inversion?

`UserService` declares a constructor parameter of type `UserRepository` (interface). Spring at startup finds an implementation (e.g. `MySqlUserRepository`), creates it, and passes it in. The service depends only on the abstraction. For tests, an in-memory fake is injected instead. The service code never changes.

### Q36. What's the cost of over-applying SOLID?

Over-engineering. Five tiny interfaces and seven classes for what could be one focused method. The goal isn't to maximize abstraction — it's to make code easier to change safely. Apply SOLID at boundaries (public APIs, things multiple modules depend on); be pragmatic on internal details.

---

## Rapid-fire (one-line answers)

37. **Can Java have multiple inheritance?** Of state (classes), no. Of behavior (interfaces), yes.
38. **What is `this` in a method?** A reference to the current object.
39. **Difference between `final` and `static`?** `final` = can't change. `static` = belongs to the class, not an instance. Different things.
40. **What's a marker interface?** An interface with no methods, used to tag a class with some property (e.g. `Serializable`).
41. **Can constructors be private?** Yes. Common for singletons and factory-method-only classes.
42. **Can interfaces have constructors?** No.
43. **Can records be subclassed?** No. Records are implicitly `final`.
44. **Difference between `extends` and `implements`?** `extends` for classes (single inheritance). `implements` for interfaces (multiple allowed).
45. **What does `super.method()` do?** Calls the parent class's version of an overridden method.
46. **Can `this` and `super` both appear as the first line of a constructor?** No, only one.
47. **What's an anonymous class?** A class defined and instantiated inline, often used before lambdas existed for one-off implementations.
48. **What's pass-by-value vs pass-by-reference in Java?** Java is always pass-by-value. For object types, the value passed is the reference (not a copy of the object).
49. **Can `equals()` and `hashCode()` be different methods on different classes?** Yes — they only need to be consistent with each other within a class.
50. **What's a record's "compact constructor"?** A constructor form that doesn't redeclare the parameters; runs after Java assigns the fields, used mostly for validation.
