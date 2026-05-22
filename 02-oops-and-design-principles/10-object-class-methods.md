# 10 — Object Class Methods

Every Java class implicitly extends `java.lang.Object`, which means every class inherits a handful of methods. The three that matter for everyday code are **`equals()`**, **`hashCode()`**, and **`toString()`**. Getting them right is one of the most common sources of subtle bugs.

This is also one of the most-asked topics in Java interviews.

---

## What `Object` gives every class

| Method | Default behavior | What you usually want |
|--------|------------------|----------------------|
| `equals(Object o)` | Reference equality (`==`) | Value-based equality |
| `hashCode()` | Memory-address-derived int | Consistent with `equals` |
| `toString()` | `ClassName@hexcode` | Something human-readable |
| `getClass()` | Returns the runtime class | (rarely overridden) |
| `clone()` | Shallow copy if `Cloneable` | Avoid; use copy constructors instead |
| `finalize()` | Deprecated | Don't use |
| `wait()`, `notify()`, `notifyAll()` | Thread coordination | Covered in module 03 |

The default `equals()` and `hashCode()` are almost never what you want. The default `toString()` is useless for debugging.

---

## `equals()`: value equality vs. reference equality

```java
class User {
    String name;
    int age;
    User(String name, int age) { this.name = name; this.age = age; }
}

User a = new User("Alice", 30);
User b = new User("Alice", 30);

System.out.println(a == b);            // false — different objects
System.out.println(a.equals(b));       // false — default equals uses ==
```

Two `User` objects with identical data are "not equal" by default. The inherited `equals` just checks whether the references point to the same memory.

For most domain classes, you want value equality: two `User`s with the same name and age *are* the same user, even if they're different object instances.

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User other)) return false;
    return age == other.age && Objects.equals(name, other.name);
}
```

Now `a.equals(b)` returns `true` when their fields match.

---

## `hashCode()`: required partner of `equals()`

If you override `equals()`, you **must** override `hashCode()` too. They're a pair, governed by a contract:

**If `a.equals(b)` is true, then `a.hashCode() == b.hashCode()` must also be true.**

(The reverse doesn't have to hold — different objects can share a hash code.)

Why does this matter? `HashMap`, `HashSet`, and most other collections use `hashCode()` to figure out which bucket an object lives in, and `equals()` to confirm within that bucket. If you override `equals()` without `hashCode()`, you get this:

```java
Set<User> users = new HashSet<>();
users.add(new User("Alice", 30));
System.out.println(users.contains(new User("Alice", 30)));   // false!
```

The set puts the first `Alice` in bucket X (based on default `hashCode`). When you check for an equivalent `Alice`, its `hashCode` lands it in bucket Y. The set checks bucket Y, finds nothing, returns `false`. The two objects *are* equal by your `equals()`, but the set doesn't get that far.

**Fix:** override `hashCode()` consistently. The standard pattern:

```java
@Override
public int hashCode() {
    return Objects.hash(name, age);
}
```

`Objects.hash(...)` combines its arguments into a single int. Pass the same fields you use in `equals()`.

---

## The equals contract (memorize)

`a.equals(b)` must be:

1. **Reflexive:** `a.equals(a)` is always true.
2. **Symmetric:** `a.equals(b)` ⇔ `b.equals(a)`.
3. **Transitive:** if `a.equals(b)` and `b.equals(c)`, then `a.equals(c)`.
4. **Consistent:** repeated calls return the same answer (if no fields changed).
5. **Non-null:** `a.equals(null)` is false.

The skeleton at the top of every well-written `equals`:

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;                 // reflexive + perf shortcut
    if (!(o instanceof User other)) return false;   // null check + type check
    return /* field-by-field comparison */;
}
```

Modern Java 16+ uses the pattern-matching form (`instanceof User other`). Pre-16 you'd write `if (!(o instanceof User)) return false; User other = (User) o;`.

---

## `toString()`: the debugging tool you always wish you had

```java
User u = new User("Alice", 30);
System.out.println(u);   // User@1540e19d   (the default — useless)
```

Default `toString()` returns the class name plus a memory hash. Helpful for almost nothing. Override it:

```java
@Override
public String toString() {
    return "User{name=" + name + ", age=" + age + "}";
}
```

Now `System.out.println(u)` shows what you actually want.

`toString()` is called implicitly when you concatenate an object into a string, print it, or look at it in the debugger. A class with a good `toString` is dramatically easier to debug.

**Conventions:**

- Include the class name (so you know what you're looking at).
- Include the fields, especially the identifying ones.
- Don't include sensitive data (passwords, full credit card numbers, etc.) — they often end up in logs.

---

## Records: the easy way (Java 16+)

If your class is mostly data, **records** generate `equals`, `hashCode`, `toString`, and accessors for you:

```java
record User(String name, int age) { }
```

That's the whole class. You get:

- A constructor accepting all fields.
- `name()` and `age()` accessors.
- `equals` and `hashCode` based on all fields.
- A reasonable `toString` (`User[name=Alice, age=30]`).

Records are immutable by design — fields are `final`. They're perfect for DTOs, value objects, and most cases where the class is "just data."

For classes that are *behavior + state* (services, controllers), you still want a regular class.

---

## A complete example

```java
public final class User {
    private final String name;
    private final int age;

    public User(String name, int age) {
        this.name = Objects.requireNonNull(name);
        if (age < 0) throw new IllegalArgumentException();
        this.age = age;
    }

    public String getName() { return name; }
    public int getAge() { return age; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return age == other.age && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }

    @Override
    public String toString() {
        return "User{name=" + name + ", age=" + age + "}";
    }
}
```

A solid, well-behaved value class. (Or, equivalently, `record User(String name, int age) { }` if your project targets Java 16+.)

---

## Common pitfalls

- **Overriding `equals` without `hashCode`** (or vice versa). This is *the* classic bug. They're a pair; never override one alone.
- **Comparing strings with `==`.** Sometimes works (because of string interning) and sometimes doesn't. Always use `.equals` for strings.
- **`equals` that throws on null.** A correct `equals(null)` returns `false`, not throws. The `instanceof` pattern handles this automatically.
- **Putting mutable fields in `equals` and `hashCode`.** If a field changes, the object's hash changes, and it gets lost inside a `HashSet`. Either use only immutable fields, or don't put the object in a hashed collection.
- **Not overriding `toString`.** Costs you nothing to add; helps every future debugging session.

---

## Code examples

1. `DefaultObjectMethods.java` — what you get from `Object` for free, and why it's not enough.
2. `EqualsAndHashCodeProper.java` — a class that gets both right.
3. `EqualsWithoutHashCode_Broken.java` — exactly how forgetting `hashCode` breaks `HashSet`.
4. `ToStringForDebugging.java` — same class with and without a custom `toString`.
5. `RecordAutoGenerated.java` — Java records get all three for free.

---

## Try this yourself

1. In `EqualsAndHashCodeProper.java`, deliberately make `hashCode` return a different constant from `equals`. Re-run the `HashSet` check.
2. In `RecordAutoGenerated.java`, switch the `record` to a regular `class` (with no overrides) and re-run. Observe what breaks.
3. Modify `ToStringForDebugging.java` to include the user's `password` field in `toString`. Then *don't* (PII shouldn't end up in logs).

---

## Self-check

1. The default `Object.equals(Object o)` does what? Why is it usually not what you want?
2. State the equals/hashCode contract in your own words. What happens if you override one but not the other?
3. You have a `record Point(int x, int y)`. List three methods Java generates for you automatically.
