# 01 — Classes and Objects

Object-oriented programming is built on one idea: bundle the *data* and the *behavior that operates on that data* together. In Java that bundle is a **class**, and instances of it are **objects**.

This topic makes sure you have the mental model right before anything else. The rest of the module rests on it.

---

## The two-line definition

- A **class** is a blueprint. It defines what data its objects will hold and what they can do.
- An **object** (also called an *instance*) is the actual thing built from that blueprint. It has its own copy of the data.

You write the class once. You create as many objects as you like from it.

```java
class Car {
    String brand;
    int year;

    void describe() {
        System.out.println(year + " " + brand);
    }
}

Car a = new Car();
a.brand = "Toyota"; a.year = 2018; a.describe();   // 2018 Toyota

Car b = new Car();
b.brand = "Ford";   b.year = 2020; b.describe();   // 2020 Ford
```

One class (`Car`). Two objects (`a` and `b`). Each object has its own `brand` and `year`. Both objects use the same `describe()` method, but the method reads *that object's* data when called.

This is the entire foundation.

---

## What `new` actually does

```java
Car a = new Car();
```

Reading that line:

1. `new Car()` — allocates a fresh chunk of memory for a `Car` object. Initializes each field to a default value (`null` for objects, `0` for numbers, `false` for booleans). Returns a reference to the new object.
2. `Car a` — declares a variable named `a` that can hold a reference to a `Car`.
3. `=` — stores the reference (returned by `new`) into the variable.

So `a` doesn't *contain* the `Car`. It contains a **reference** (a pointer) to the `Car`, which lives somewhere in memory.

This distinction matters the moment you do:

```java
Car a = new Car();
a.brand = "Toyota";
Car b = a;              // copies the reference, NOT the object
b.brand = "Ford";       // changes the same Car
System.out.println(a.brand);   // prints "Ford"
```

`a` and `b` are two variables pointing at the **same object**. If this is news to you, run the `ReferenceVsValue.java` example below — seeing the output makes it stick.

---

## Object identity, not value

Two objects with identical field values are still distinct objects:

```java
Car x = new Car(); x.brand = "Toyota";
Car y = new Car(); y.brand = "Toyota";
System.out.println(x == y);   // false — different objects, same data
```

The `==` operator compares *references*. To compare *values*, you need `equals()` — covered in topic 10.

---

## Instance fields vs. local variables

```java
class Car {
    String brand;          // instance field — one per Car

    void rename(String s) {
        String temp = s;   // local variable — exists only while rename() runs
        brand = temp;
    }
}
```

- **Instance fields** live as long as the object lives. Each object gets its own copy.
- **Local variables** live only during the method call they appear in. They disappear when the method returns.

Forget this and you'll be confused why "saving a value" only works sometimes. Save to an instance field if you want it to persist; use a local variable for scratch work.

---

## Null: a reference pointing to nothing

```java
Car c;                 // declared but not initialized → defaults to null (for fields)
c.brand = "Toyota";    // NullPointerException
```

A reference variable can hold `null` — meaning "this variable doesn't refer to any object." Calling a method or accessing a field through a `null` reference throws `NullPointerException` (NPE), the most famous Java bug.

Two defenses:

1. **Initialize references when you declare them.** `Car c = new Car();` instead of `Car c;`.
2. **Check for null before dereferencing**, when there's a legitimate chance the reference is null.

---

## Where the data lives (one level deeper)

Java uses two main memory regions:

- **Stack**: holds local variables and references. Fast, automatically cleaned up when methods return.
- **Heap**: holds the actual objects you create with `new`. Garbage-collected when no references remain.

```java
Car a = new Car();
```

`a` (the reference) lives on the stack. The `Car` object itself lives on the heap. When the method ends, `a` goes away. If no other reference points to the `Car`, the garbage collector eventually reclaims it.

You'll see this picture again in module 03 (JVM internals). For now: variables on the stack point to objects on the heap.

---

## Common pitfalls

- **Confusing reference assignment with copying.** `b = a` doesn't duplicate the object. To copy an object's data, you write a copy constructor or use a `clone`-like method.
- **Comparing objects with `==`.** This only checks "same reference." Use `.equals()` for value equality.
- **Treating uninitialized references as safe.** An uninitialized instance field defaults to `null`, and that NPE is waiting.
- **Putting things in instance fields that should be local.** A field used only inside one method should usually just be a local variable in that method.

---

## Code examples

1. `ClassAndObject.java` — define a class, create one object, set its fields, call its method.
2. `MultipleInstances.java` — create three objects of the same class, show they have independent state.
3. `ReferenceVsValue.java` — assign one reference to another and modify; observe the shared mutation.
4. `NullReference.java` — what happens when you use a `null` reference, with the fix.

---

## Try this yourself

1. In `MultipleInstances.java`, add a static counter field that increments every time a new object is created. Print the total at the end.
2. In `ReferenceVsValue.java`, write a `copy(Car other)` method that copies all fields from `other` into `this`, then show that modifying one no longer affects the other.
3. In `NullReference.java`, deliberately call a method on the `null` reference and catch the `NullPointerException`. Print a clean error message instead of crashing.

---

## Self-check

1. You write `Car a = new Car()`. Walk through what `new` does, step by step.
2. After `Car b = a; b.brand = "Ford";` — what happened? Did anything change about `a`?
3. Why does `x == y` return `false` for two `Car` objects with identical field values?
