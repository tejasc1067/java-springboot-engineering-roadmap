# 02 — Constructors

A constructor is the special method that runs when an object is created. Its job: put the new object into a *valid* initial state. Without constructors, every object would start half-built, and every method that touched it would have to defensively check "is this field even set?"

Done well, constructors prevent entire categories of bugs by making "invalid object" impossible.

---

## The default constructor

If you don't write any constructor, Java gives you one for free — a no-argument constructor that initializes every field to its default value (`null` for references, `0` for numbers, `false` for booleans).

```java
class Car {
    String brand;
    int year;
}

Car c = new Car();   // uses the implicit default constructor
                     // c.brand = null, c.year = 0
```

This works but leaves you setting fields one by one afterwards, every time. And nothing stops you from forgetting one.

---

## Parameterized constructors

You can write your own. The constructor has the **same name as the class** and **no return type** (not even `void`):

```java
class Car {
    String brand;
    int year;

    Car(String brand, int year) {
        this.brand = brand;
        this.year = year;
    }
}

Car c = new Car("Toyota", 2018);
```

Now you can't create a half-built `Car` — every `Car` has a brand and year from the moment it exists.

### `this.brand = brand`

The parameter and the field have the same name. `brand` (unqualified) refers to the parameter; `this.brand` refers to the field. Without `this.`, Java would see `brand = brand` — assigning the parameter to itself — and the field would never get set.

You'll see `this.field = field` constantly. Now you know why.

---

## Watch out: writing one constructor removes the default

```java
class Car {
    String brand;
    int year;

    Car(String brand, int year) {       // your custom constructor
        this.brand = brand;
        this.year = year;
    }
}

Car c = new Car();   // ❌ compile error — no zero-arg constructor exists
```

As soon as you write *any* constructor, Java stops generating the default. If you still want to allow `new Car()` with no arguments, you must declare it explicitly.

---

## Constructor overloading

A class can have multiple constructors as long as their parameter lists differ:

```java
class Car {
    String brand;
    int year;

    Car() {                                       // zero-arg
        this.brand = "unknown";
        this.year = 0;
    }

    Car(String brand) {                           // one-arg
        this.brand = brand;
        this.year = 2024;
    }

    Car(String brand, int year) {                 // two-arg
        this.brand = brand;
        this.year = year;
    }
}
```

The right constructor is chosen by which arguments you pass:

```java
new Car();                      // uses Car()
new Car("Toyota");              // uses Car(String)
new Car("Toyota", 2018);        // uses Car(String, int)
```

This is "constructor overloading" — same name, different parameter lists.

---

## Constructor chaining with `this(...)`

The duplication in the example above is ugly — three constructors all assigning `this.brand` and `this.year`. You can have one constructor call another:

```java
class Car {
    String brand;
    int year;

    Car(String brand, int year) {       // the "main" constructor
        this.brand = brand;
        this.year = year;
    }

    Car(String brand) {
        this(brand, 2024);              // delegate to Car(String, int)
    }

    Car() {
        this("unknown");                // delegate to Car(String)
    }
}
```

`this(...)` invokes another constructor of the same class. Rules:

- Must be the **first** statement in the constructor.
- Can only call constructors of the same class. To call a parent constructor, use `super(...)` — covered in topic 05.

The pattern: one constructor does the real work; the others fill in defaults and delegate.

---

## The real reason constructors matter: invariants

The bigger purpose of a constructor isn't "set the fields." It's to enforce **invariants** — rules every instance must satisfy:

```java
class Account {
    String owner;
    double balance;

    Account(String owner, double initialBalance) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("initial balance cannot be negative");
        }
        this.owner = owner;
        this.balance = initialBalance;
    }
}
```

Now no `Account` with a blank owner or negative balance can ever exist. Every method in the class can trust that `owner != null` and `balance >= 0`, without checking. The constructor is the single point where these rules are enforced.

This is one of the most important ideas in object-oriented design. **Constructors are where you make invalid states impossible.**

---

## What NOT to do in a constructor

Constructors should be fast and predictable. Avoid:

- **Calling overridable methods.** A subclass's override may run before the subclass is fully initialized — a famously subtle bug.
- **Heavy work** (network calls, file I/O, big computations). If construction can fail or take seconds, consider a static factory method (`Account.create(...)`) that does the work and returns the constructed object.
- **Leaking `this`** to other threads. Until the constructor finishes, the object is in an undefined state; other code seeing it can observe half-initialized fields.

These are advanced concerns. Until you trip over them, "no logic in the constructor besides field assignment + validation" is a safe rule.

---

## Code examples

1. `DefaultConstructor.java` — what Java's implicit constructor gives you.
2. `ParameterizedConstructor.java` — write your own to require fields up front.
3. `ConstructorOverloading.java` — multiple constructors for different call patterns.
4. `ConstructorChaining.java` — `this(...)` to avoid duplication.
5. `InvariantsAtConstruction.java` — using the constructor to enforce business rules; `_Broken` version shows what happens without them.

---

## Try this yourself

1. In `ParameterizedConstructor.java`, comment out the user-defined constructor and try `new Car()` again. Read the compile error.
2. In `ConstructorChaining.java`, try putting a `System.out.println("before this()")` *before* the `this(...)` call. What error do you get?
3. Modify `InvariantsAtConstruction.java` to validate that `owner` has at least two characters. What happens if `owner = "A"` is passed?

---

## Self-check

1. Why does writing your own constructor remove access to `new MyClass()` with no arguments?
2. In `Car(String brand) { this.brand = brand; }`, why is the `this.` necessary?
3. What's the difference between "checking validity in a setter" and "checking validity in the constructor"?
