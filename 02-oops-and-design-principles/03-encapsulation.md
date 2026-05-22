# 03 — Encapsulation

The first of the four OOP pillars. Encapsulation is the practice of **hiding the internal state of an object** and exposing only the operations that maintain its invariants.

Done well, your class becomes a small fortress: outside code can only ask it to do things in valid ways. Done badly, your class is just a public bag of fields, and every "client" is free to break it.

---

## The motivating example

A `BankAccount` class, naïvely written:

```java
class BankAccount {
    public String owner;
    public double balance;
}
```

Looks innocent. Now anyone can:

```java
BankAccount a = new BankAccount();
a.owner = "Alice";
a.balance = 1000;

a.balance = -999999;   // direct write — no rule preventing this
a.owner = null;        // also no rule preventing this
```

A negative balance was supposed to be impossible. With public fields, **the class has no way to enforce that rule**. Every piece of code that touches the field becomes a potential bug source.

---

## The fix: hide the data, expose operations

```java
class BankAccount {
    private String owner;     // ← inaccessible from outside
    private double balance;

    BankAccount(String owner, double initialBalance) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException();
        if (initialBalance < 0) throw new IllegalArgumentException();
        this.owner = owner;
        this.balance = initialBalance;
    }

    void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("must be positive");
        this.balance += amount;
    }

    void withdraw(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("must be positive");
        if (amount > balance) throw new IllegalStateException("insufficient funds");
        this.balance -= amount;
    }

    double getBalance() {
        return balance;
    }
}
```

Now outside code can't directly modify `balance`. To change it, you must call `deposit()` or `withdraw()`, and both enforce the rules. Negative balance is structurally impossible.

The class went from "anyone can do anything" to "anyone can do *the right thing*."

---

## The four access modifiers (quick reference)

| Modifier | Visible from |
|----------|--------------|
| `public` | Anywhere |
| `protected` | Same package + subclasses |
| (none — package-private) | Same package only |
| `private` | Same class only |

**Default for fields: `private`.** Promote to a wider access level only when you have a specific reason.

---

## Getters and setters: convention, not a rule

The classic pattern: `private` field + `public getX()` + `public setX(...)`:

```java
class User {
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

This is encapsulation in name only. The setter just blindly assigns whatever value comes in. You've kept the syntax of encapsulation but lost the *point* of it — no rules are being enforced.

Real encapsulation asks two questions for every field:

1. **Should outside code be able to read this?** If yes, provide a getter (or a method that uses the field).
2. **Should outside code be able to change this — and if so, under what rules?** If there are rules, the setter enforces them. If there are no rules, ask whether the field really needs to be mutable at all.

```java
class User {
    private String name;

    public String getName() { return name; }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("name too long");
        }
        this.name = name;
    }
}
```

Now the class enforces "name is non-blank and ≤100 chars" no matter who's calling the setter.

If the rules turn out to be "no, this field never changes after construction," just don't write a setter at all — make the field `final` and assign it once in the constructor. (More on this in topic 12 — `final` keyword.)

---

## Encapsulation isn't just about fields

The principle applies to methods too. Helper methods that are implementation details should be `private`, not `public`:

```java
class OrderService {
    public Order placeOrder(Cart cart, User user) {
        validate(cart, user);            // private helper
        Order order = build(cart);       // private helper
        save(order);                     // private helper
        return order;
    }

    private void validate(Cart cart, User user) { ... }
    private Order build(Cart cart) { ... }
    private void save(Order order) { ... }
}
```

Only `placeOrder` is the public API. Outside code can't call `build()` independently and bypass `validate()`. The class controls the order things happen in.

**General rule:** the public surface of a class should be as small as possible. Everything else `private`.

---

## The mutable-reference trap (defensive copying)

Making a field `private` isn't enough if its *value* is a mutable object you hand back to the caller:

```java
class Team {
    private List<String> members = new ArrayList<>();

    public List<String> getMembers() {
        return members;       // ← returns a reference to the internal list
    }
}

Team t = new Team();
t.getMembers().add("Alice");          // anyone can mutate the internal list
t.getMembers().clear();               // including wipe it
```

Even though `members` is `private`, the getter handed out a reference to the *actual* internal list. The caller can do anything to it.

Fixes:

```java
// Option 1: return an unmodifiable view
public List<String> getMembers() {
    return Collections.unmodifiableList(members);
}

// Option 2: return a defensive copy
public List<String> getMembers() {
    return new ArrayList<>(members);
}

// Option 3: don't return the list at all; expose specific operations
public void addMember(String name) { ... }
public int memberCount() { return members.size(); }
```

For module 04 readers: this is the same kind of "leaky abstraction" you see with `ResultSet` references escaping their `try-with-resources` block.

---

## Common pitfalls

- **Public fields "for convenience."** The convenience is real for ~5 lines of code; the cost is paid for the life of the codebase.
- **Getters/setters for everything, automatically.** IDEs generate them too eagerly. Ask whether the field really needs to be readable and writable from outside.
- **Returning mutable internals.** Lists, dates, maps — when handed out raw, they're a back door past your encapsulation.
- **Encapsulation without invariants.** A class with private fields but no rules in its methods is just hiding the bag, not protecting it.

---

## Code examples

1. `PublicFieldsBroken.java` — naïve class with public fields. Outside code corrupts the state.
2. `EncapsulatedAccount.java` — same class redone: private fields, methods enforce rules.
3. `GettersSettersWithRules.java` — setters that actually do something.
4. `DefensiveCopying.java` — returning a copy (or unmodifiable view) instead of the internal list.

---

## Try this yourself

1. In `EncapsulatedAccount.java`, add a `transfer(BankAccount other, double amount)` method that uses `withdraw` and `deposit` to move money between two accounts. What invariants does each step enforce?
2. In `DefensiveCopying.java`, remove the `unmodifiableList` wrapper and add `team.getMembers().add("Mallory")` in `main`. Confirm the internal list got mutated.
3. Modify `GettersSettersWithRules.java`'s `setEmail` to require an `@` character. Try passing an invalid email — what happens?

---

## Self-check

1. A teammate makes a field `public` "because we're the only ones using this class." What's wrong with that argument?
2. You have a class with `private List<Item> items` and `public List<Item> getItems()`. Why is the class still leaking encapsulation?
3. Encapsulation isn't really about `private` — what's it actually about?
