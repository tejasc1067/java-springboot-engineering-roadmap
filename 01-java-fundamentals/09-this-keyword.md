# 09 — The `this` Keyword

Inside an instance method or constructor, `this` is a reference to the current object — the specific instance the method is running on. It has three real uses: disambiguating parameter vs. field names, passing the current object somewhere, and chaining constructors.

Once you've seen the three patterns, `this` becomes invisible — you'll use it without thinking.

---

## Use 1: disambiguating field vs. parameter

The most common use. When a parameter has the same name as an instance field, plain `name` refers to the parameter (closer scope wins). `this.name` refers to the field.

```java
class Student {
    String name;

    Student(String name) {           // parameter `name` shadows the field
        this.name = name;            // copy the parameter into the field
    }
}
```

This pattern is so common that you'll see it in every constructor that takes its initial values as arguments.

You could rename the parameter (`Student(String n)`) to avoid the shadow, but matching names is the idiomatic Java way — it documents the relationship.

---

## Use 2: passing the current object

You sometimes need to hand `this` to something else.

```java
class Order {
    Customer owner;

    Order(Customer owner) {
        this.owner = owner;
        owner.addOrder(this);        // tell the customer about us
    }
}
```

Or you might need to return `this` from a method to allow chaining:

```java
class StringBuilder {
    StringBuilder append(String s) {
        // ... add to internal buffer ...
        return this;
    }
}

new StringBuilder().append("hi").append(" ").append("there");
```

This is the "builder pattern" — methods that modify state and return `this` so calls can be chained on one line.

---

## Use 3: constructor chaining with `this(...)`

Inside a constructor, `this(...)` calls *another* constructor of the same class. Useful when one constructor is the "real" one and others delegate to it after filling in defaults.

```java
class Student {
    String name;
    int age;

    Student() {
        this("unknown", 0);           // delegate to the two-arg constructor
    }

    Student(String name) {
        this(name, 0);                // delegate with a default age
    }

    Student(String name, int age) {   // the actual work happens here
        this.name = name;
        this.age = age;
    }
}
```

Two rules:
- `this(...)` must be the **first statement** in the constructor. Compile error otherwise.
- A constructor can call `this(...)` *or* `super(...)` (parent class — topic in module 02), not both.

The point: don't repeat the field-assignment logic in three constructors. Write it once, delegate from the others.

---

## When you can NOT use `this`

Static methods don't have a current object — they belong to the class. Inside one, `this` is a compile error.

```java
class Counter {
    int count;
    static void reset() {
        this.count = 0;        // compile error — no current object
    }
}
```

If you find yourself wanting `this` in a static method, the method probably shouldn't be static. Or the field shouldn't be an instance field. Pick one.

---

## Common pitfalls

- **Forgetting `this.` and getting a silent bug.** Without `this.`, the assignment `name = name;` is the parameter assigning to itself. The field stays null. The compiler doesn't warn.

  ```java
  Student(String name) {
      name = name;          // no-op; this.name stays null
  }
  ```

- **`this(...)` not as the first statement.** Easy to forget when refactoring. Compile error is clear: "call to this must be first statement in constructor."

- **Using `this` in a static method.** "Non-static variable this cannot be referenced from a static context." It's telling you the method belongs to the class, not to an object.

- **Calling overridable methods from a constructor.** Not strictly a `this` issue, but related — `this.someMethod()` during construction may run a subclass's override that depends on fields the subclass hasn't initialized yet. Module 02 (constructors topic) covers this in detail.

---

## Code examples

1. `ThisForFields.java` — the constructor parameter / field disambiguation pattern.
2. `ThisAsArgument.java` — passing `this` to another object (back-reference idiom).
3. `ConstructorChaining.java` — multiple constructors delegating via `this(...)`.
4. `ThisStaticError.java` — a deliberate compile error: `this` inside a static method. Read the message, then comment the line out.

---

## Try this yourself

1. In `ThisForFields.java`, remove `this.` from one assignment. Print the field afterward — it should be null. Confirm the compiler does NOT warn.
2. In `ConstructorChaining.java`, try putting a `System.out.println("before");` line *before* `this(...)`. Read the compile error. Move the println after the chained call — it now compiles.
3. Build a small chain-style class: `Greeter g = new Greeter().say("hello").say("world").done();`. Each `say` returns `this`.

---

## Self-check

1. In `Student(String name) { this.name = name; }` — which `name` is which? What would happen if you wrote `name = name;` instead?
2. Why must `this(...)` be the first statement in a constructor?
3. Why can't you use `this` inside a static method?
