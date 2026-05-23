# 10 — Access Modifiers

Access modifiers — `private`, `default` (no keyword), `protected`, `public` — control who can see and use what. They're the language-level mechanism behind encapsulation: the idea that a class should expose what callers need and hide what they don't.

This topic is short on syntax (four keywords) and long on judgment (when to expose what).

---

## The four levels

```java
public    String name;       // anyone, anywhere
protected String name;       // same package + subclasses (even in other packages)
          String name;       // "package-private" — same package only (the default if you write no keyword)
private   String name;       // only inside this class
```

| Modifier   | Same class | Same package | Subclass (other package) | Anywhere |
|------------|:----------:|:------------:|:------------------------:|:--------:|
| `private`  | ✓          | ✗            | ✗                        | ✗        |
| (default)  | ✓          | ✓            | ✗                        | ✗        |
| `protected`| ✓          | ✓            | ✓                        | ✗        |
| `public`   | ✓          | ✓            | ✓                        | ✓        |

Each row strictly widens the previous one — `protected` is everything `default` allows plus subclasses.

Note: "package-private" (omit the keyword) is not the same as `public`. Many beginners write `class User` and assume that's public — it's package-private, meaning only classes in the same package can use it.

---

## When to use each

**`private`** — the default for fields and helper methods. Almost every field on a class should be private. Almost every method that exists "to support some other method on this class" should be private.

```java
class Account {
    private double balance;            // ← no caller should touch this directly
    private boolean canWithdraw(double amount) { ... }   // ← helper
    public void withdraw(double amount) { ... }          // ← the only public way to take money
}
```

**(default / package-private)** — for classes and helpers that a *package* needs to share, but the rest of the world doesn't. Used heavily in framework code. In application code, you'll see it less.

**`protected`** — for things subclasses need but external callers shouldn't see. Easy to overuse — a `protected` field means subclasses anywhere can read or modify it, which is almost as dangerous as `public`. Reach for `protected` deliberately.

**`public`** — for the API surface of your class: the methods callers actually need, the constants the world uses, the main entry points.

---

## The encapsulation rule of thumb

> **Fields private. Methods as narrow as they can be.**

If a class has `public String name;`, anyone can write `user.name = ""` or `user.name = null`, bypassing every rule you wanted to enforce. Once a field is public, you can never add validation without breaking callers.

The standard pattern is private fields with public getters/setters:

```java
class User {
    private String email;

    public String getEmail() { return email; }

    public void setEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("invalid email");
        }
        this.email = email;
    }
}
```

Now `email` can never be set to an invalid value. The cost: a few extra lines, replacing a direct field access with a method call. The benefit: the class controls its own state. Worth it almost every time.

Module 02 (encapsulation topic) goes deeper on this.

---

## Top-level class visibility

For *classes* (not members), only `public` and package-private (default) are legal. You can't have a `private class Foo` at the top level of a file.

Also: a `.java` file can contain **at most one `public` class**, and that class must match the file name.

```java
// File: Account.java
public class Account { ... }       // public — must match the filename
class Helper { ... }               // package-private — same file, different class
```

If you want a top-level "helper" class, drop the `public`. It's then visible only within the package.

---

## Common pitfalls

- **Everything public "for convenience."** Junior code smell. The convenience now costs flexibility later — every public field is a forever-promise that nothing will ever validate it.
- **`public` getter that returns a mutable internal collection.** `public List<Item> getItems() { return items; }` — the caller can now mutate `items` from outside, defeating private. Return an unmodifiable view (`Collections.unmodifiableList(items)`) or a defensive copy.
- **Confusing `default` (the keyword for interface methods, Java 8+) with default access (no keyword).** Different concepts. "Package-private" is the unambiguous name for "no access modifier."
- **`protected` on fields.** Tempting for "I want subclasses to use this." But protected fields can be modified by anyone in the same package OR any subclass anywhere — a wide net. Prefer protected *methods*, keep fields private.

---

## Code examples

1. `AccessLevels.java` — one class with one of each level. From `main`, try to access each. The compile errors show what's allowed and what isn't.
2. `EncapsulationGoodAndBad.java` — public field vs. private field with a validating setter. Show the bad version letting nonsense through.

---

## Try this yourself

1. In `AccessLevels.java`, uncomment the line that tries to access the private field from `main`. Read the compiler error.
2. In `EncapsulationGoodAndBad.java`, add a `setBalance` that allows negative values. Show that this breaks the "balance should never go below zero" invariant. Then fix it.
3. Make a top-level class `private` in a file. Watch the compiler reject it — explain why the rule exists. (Hint: a private top-level class would be unusable.)

---

## Self-check

1. What's the difference between (no modifier) and `public`? Why does a class with no modifier still "work" inside the same package?
2. You see `public String email;` in code review. What's the actual cost over `private String email; public String getEmail(); public void setEmail(...)`?
3. A teammate makes a field `protected` "in case subclasses need it." What's the alternative that gives subclasses controlled access without exposing the field?
