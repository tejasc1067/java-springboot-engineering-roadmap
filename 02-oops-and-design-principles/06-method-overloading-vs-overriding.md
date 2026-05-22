# 06 — Method Overloading vs. Overriding

Two completely different concepts. They sound similar, they share the word "method," and beginners mix them up constantly.

- **Overloading** — same method name, different parameter lists, in the *same class* (or inherited). Decided at compile time.
- **Overriding** — a subclass replaces a method inherited from its parent. Decided at runtime, based on the actual object type.

Different mechanisms, different uses, different rules. Memorize the distinction.

---

## Overloading

Multiple methods in the same class with the *same name* but *different parameter lists*:

```java
class Calculator {
    int add(int a, int b)             { return a + b; }
    double add(double a, double b)    { return a + b; }
    int add(int a, int b, int c)      { return a + b + c; }
    String add(String a, String b)    { return a + b; }
}
```

Four methods, all called `add`. The compiler picks one based on the *types* and *count* of arguments at the call site:

```java
Calculator c = new Calculator();
c.add(1, 2);            // → int  add(int, int)
c.add(1.0, 2.0);        // → double add(double, double)
c.add(1, 2, 3);         // → int  add(int, int, int)
c.add("hi", "ho");      // → String add(String, String)
```

### What makes a valid overload

Overloads must differ in at least one of:

- Number of parameters
- Type of parameters
- Order of parameter types

**Return type alone does NOT distinguish overloads.** Both `int foo()` and `double foo()` is a compile error — the compiler can't tell them apart from the call site.

### When to use it

- A method conceptually does the same thing but accepts different input shapes. `add(int, int)` and `add(double, double)` are natural overloads.
- Convenience overloads that delegate to a "main" method: `log(String msg)` calls `log(String msg, Level level)` with a default level.

When to *not* use it:

- When the methods do *different* things — give them different names. `parse(String)` and `parse(File)` are overloads. `parse(String)` and `serialize(String)` should not be.

---

## Overriding

A subclass redefines a method it inherited from its parent. Same signature (name + parameters + return type compatible) — different body.

```java
class Animal {
    void speak() {
        System.out.println("Some animal sound");
    }
}

class Dog extends Animal {
    @Override
    void speak() {
        System.out.println("Woof");
    }
}

Animal a = new Dog();
a.speak();   // prints "Woof"
```

Even though the variable is typed `Animal`, the actual object is a `Dog`, and the JVM dispatches `speak()` to the `Dog` version at runtime. This is **runtime polymorphism** (topic 07 goes deeper).

### Rules for overriding

For a subclass method to actually override (not accidentally do something else):

- **Method name and parameter list must match exactly.** A slight difference (e.g. wrong parameter type) silently creates an *overload*, not an override.
- **Return type** must be the same, or a subtype (called "covariant return type"). E.g. parent returns `Animal`, subclass can return `Dog`.
- **Access level can be wider but not narrower.** A parent's `protected` method can become `public` in the subclass, but not `private`.
- **Exceptions thrown can be narrower but not wider.** A subclass override can throw fewer checked exceptions than the parent — but not more.

### The `@Override` annotation

```java
@Override
void speak() { ... }
```

Tells the compiler: "I intend this to override a parent method. If it doesn't, error out."

Without `@Override`, a typo silently becomes a new method:

```java
class Dog extends Animal {
    void Speak() { ... }   // capital S — accidentally a new method, not an override
}
```

`new Dog().speak()` calls `Animal.speak()` and the `Dog.Speak()` you wrote is never called. With `@Override`, the compiler catches this immediately.

**Always use `@Override`.** It's a free check.

---

## The two side by side

```java
class Animal {
    void speak()             { System.out.println("animal sound"); }
    void speak(String detail){ System.out.println("animal sound with " + detail); }   // overload of speak
}

class Dog extends Animal {
    @Override
    void speak()             { System.out.println("woof"); }                          // overrides Animal.speak()
    void speak(int times)    { for (int i=0;i<times;i++) speak(); }                   // overloads — new param type
}
```

`Animal` has two `speak` methods (overloads). `Dog` overrides one of them and adds another overload.

```java
Dog d = new Dog();
d.speak();              // "woof"          (overridden version)
d.speak("growl");       // "animal sound with growl"  (inherited overload)
d.speak(3);             // three "woof"s    (new overload)
```

Look closely at `d.speak(3)`: it calls the new overload, which internally calls `speak()` — which resolves to the overridden version. The two mechanisms compose naturally.

---

## Compile-time vs. runtime resolution

The classic interview phrasing:

- **Overloading is resolved at compile time.** The compiler sees the argument types and picks one of the candidates. Static dispatch.
- **Overriding is resolved at runtime.** The JVM sees the actual object type and dispatches to the appropriate method. Dynamic dispatch.

```java
Animal a = new Dog();
a.speak();                    // overriding → resolved at runtime → "woof"

Calculator c = new Calculator();
c.add(1, 2);                  // overloading → resolved at compile time
```

This distinction explains every subtle bug around "I expected the subclass method to be called and it wasn't" — usually because what you thought was overriding was actually overloading, or vice versa.

---

## Common pitfalls

- **Accidentally overloading instead of overriding.** Slightly different parameter list → no error, but it's a new method. `@Override` prevents this.
- **Trying to overload by return type only.** Doesn't compile.
- **Narrowing access in an override.** A `public` parent method can't become `private` in the subclass. The compiler refuses.
- **Throwing wider exceptions in an override.** Same — compiler refuses, because code calling through the parent's signature wouldn't be prepared.
- **Overloading where polymorphism would be cleaner.** If you have `process(Dog)`, `process(Cat)`, `process(Bird)` all doing dispatch-by-type, consider making `process()` a method on a common `Animal` interface instead.

---

## Code examples

1. `MethodOverloading.java` — multiple `add` methods, picked at compile time.
2. `MethodOverriding.java` — `Dog` overrides `Animal.speak()`; dispatched at runtime.
3. `OverrideAnnotationCatchesBugs.java` — `@Override` prevents a silent typo bug.
4. `OverloadVsOverrideSideBySide.java` — one file showing both mechanisms working together.
5. `ReturnTypeAlone_Broken.java` — what happens when you try to overload by return type only.

---

## Try this yourself

1. In `MethodOverloading.java`, add an `add(int, double)` and an `add(double, int)`. Call them — confirm the compiler picks the right one.
2. In `OverrideAnnotationCatchesBugs.java`, fix the typo so it's a real override. Re-run.
3. In `MethodOverriding.java`, declare `Animal a = new Dog();` and call `a.speak()`. Now declare `Animal a = new Animal();` and call `a.speak()`. Why is the output different even though the variable type is the same?

---

## Self-check

1. The same method name appears twice in a class — when is that overloading vs. an error?
2. You override `parent.foo()` but forget `@Override`. The compiler accepts it. What can go wrong?
3. "Overloading is compile-time, overriding is runtime." Explain that statement to a colleague who's never heard it.
