# 08 — The `static` Keyword

A `static` member belongs to the **class**, not to any individual object. There's one copy, shared by everyone, accessible without creating an instance.

That's the whole idea. The details — when to reach for it, what it means for memory and testing — are what matter.

---

## Static fields

Without `static`, every object gets its own copy of a field:

```java
class Student {
    String name;          // per-object
}

Student a = new Student(); a.name = "alice";
Student b = new Student(); b.name = "bob";
// Two Student objects. Two name fields. Two separate strings.
```

With `static`, there's one copy shared by every object of the class — and accessible without an object at all:

```java
class Student {
    static String school = "Lincoln High";   // shared
    String name;                              // per-object
}

System.out.println(Student.school);   // "Lincoln High" — no `new` needed
```

You access static fields through the **class name**, not an object. `student.school` works but is misleading — it makes the reader think `school` is per-object. Always write `Student.school`.

---

## Static methods

Same rule: belongs to the class, callable without an instance.

```java
class MathUtil {
    static int square(int n) { return n * n; }
}

int s = MathUtil.square(5);    // no `new MathUtil()` anywhere
```

You meet a static method on day one without realizing it: `Math.max`, `Math.sqrt`, `Integer.parseInt`. They're all static utility methods.

A static method cannot use `this` (there's no current object). It also cannot directly access *non-static* fields or methods — those require an instance. Try, and you get "non-static field cannot be referenced from a static context."

```java
class Counter {
    int count;                              // instance field

    static void reset() {
        count = 0;                          // compile error — which counter?
    }
}
```

---

## Why `main` is static

```java
public static void main(String[] args) { ... }
```

The JVM has to call `main` to start your program. At that point no objects exist yet. If `main` weren't static, the JVM would have to create an instance of your class to call it — but how would it know how? The static modifier means "you can call this without an instance," so the JVM just calls `YourClass.main(...)` directly.

---

## Static blocks

Code that runs **once**, when the class is first loaded by the JVM. Useful for one-time initialization that's more complex than a field initializer can handle.

```java
class Config {
    static Map<String, String> defaults;

    static {
        defaults = new HashMap<>();
        defaults.put("timeout", "30");
        defaults.put("retries", "3");
        System.out.println("Config initialized");
    }
}
```

Static blocks run in source order, top to bottom. They're rare in modern code — most initialization fits in field initializers (`static Map<...> defaults = Map.of(...)`) — but you'll see them in older or framework code.

---

## Constants

The classic static use: a value that never changes, shared by everyone.

```java
class HttpStatus {
    public static final int OK            = 200;
    public static final int NOT_FOUND     = 404;
    public static final int INTERNAL_ERROR = 500;
}

if (response.code == HttpStatus.NOT_FOUND) { ... }
```

`public static final` is the constant idiom: visible to everyone, shared, never reassigned. The Java convention is `ALL_CAPS_WITH_UNDERSCORES` for constants.

---

## When static is the right tool

- **Utility methods that don't depend on object state** — `Math.max`, `Arrays.sort`, `Objects.requireNonNull`. Pure functions.
- **Constants** — `Integer.MAX_VALUE`, `HttpStatus.OK`.
- **Factory methods** — `List.of(1, 2, 3)`, `Optional.empty()`. They create objects but don't need an instance to do so.
- **Counters or registries shared across instances** — rare, but legitimate. Be careful about thread safety.

---

## When static is the wrong tool

- **Hiding global state.** A static field that holds *mutable* application state (a list, a map, a config object) is a global variable in disguise. It survives across tests, leaks between requests in a server, can't be mocked, and bites you eventually.
- **Coupling a class to a concrete implementation.** `UserService.repo = new MySqlUserRepository();` ties everyone to MySQL forever. Use instance fields and dependency injection.
- **Avoiding `new`.** Some people use static methods just because `new` feels heavy. Don't. Object creation is cheap; static methods bypass polymorphism.

The rough test: "could two different parts of the system reasonably want different values of this thing?" If yes, it shouldn't be static.

---

## Common pitfalls

- **Accessing a static through an instance.** Compiles, but reads wrong. `Math.PI` good; `someMathObject.PI` confusing.
- **Static fields hiding shared state.** A `static List<User> users` looks innocent. In a web server it grows forever — every request adds to it. Topic 17 (memory) revisits this.
- **Static methods that depend on instance state.** Can't compile, but beginners try. The fix is to remove `static`, not to add `static` to the field too.
- **Forgetting that `static` blocks run on class loading, not on object creation.** Loading happens once, the first time the class is touched. Side effects in a static block can fire at surprising times.

---

## Code examples

1. `StaticFields.java` — show that a static field is shared across instances, by changing it through one object and reading it through another.
2. `StaticMethods.java` — a utility class with no instance state. The classic `static` use case.
3. `StaticBlock.java` — one-time initialization that runs when the class loads.
4. `Constants.java` — `public static final` constants, the idiomatic shape.

---

## Try this yourself

1. In `StaticFields.java`, add an instance counter that increments in the constructor. Track total instances across the whole program in a separate `static` counter. Compare the two numbers.
2. Try calling a non-static method from a static one. Read the compile error carefully — it explains exactly what's wrong.
3. In `Constants.java`, change `public static final int OK = 200;` to `public static int OK = 200;` (drop the `final`). Reassign it from `main`. Now the constant isn't constant. Why is `final` essential here?

---

## Self-check

1. Why does the JVM require `main` to be `static`?
2. You have `static List<User> users = new ArrayList<>();` in a `UserService` class. In a long-running web server, what happens to that list over time?
3. Why can't a static method access an instance field directly?
