# 13 — Immutability

An **immutable** object's state cannot change after it's constructed. Build it once, observe it forever — but never modify it.

This sounds restrictive. It's actually one of the most powerful design moves you have. Immutable objects are easier to reason about, safe to share across threads, safe to use as `HashMap` keys, and produce a whole category of fewer bugs than their mutable equivalents.

`String`, `Integer`, `LocalDate`, and most of `java.time` are immutable. Records (Java 16+) are immutable by default. This isn't a coincidence — it's the standard library deliberately picking the safer pattern wherever possible.

---

## What "immutable" actually requires

For a class to be truly immutable, ALL of these must hold:

1. The class is `final` (no subclass can break the rules).
2. All fields are `private final`.
3. No setters — and no methods that mutate state.
4. If the class holds references to mutable objects (lists, dates, arrays), the class **defensively copies** them on the way in and on the way out.
5. The constructor fully initializes all fields.

Miss any one of these and your class isn't really immutable — it just looks like it.

---

## A mutable class for contrast

```java
class User {
    private String name;
    private int age;

    public User(String name, int age) { this.name = name; this.age = age; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}
```

Standard "JavaBean" style. Anyone can call `setName(null)` or `setAge(-5)`. The object's state is a moving target. Two threads can update it concurrently and corrupt it. Storing it in a `HashSet` is risky — if `name` changes, its `hashCode` changes, and the set loses track of it.

---

## The same class, immutable

```java
public final class User {
    private final String name;
    private final int age;

    public User(String name, int age) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException();
        if (age < 0)                        throw new IllegalArgumentException();
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public int getAge()     { return age; }
}
```

What changed:

- `final class` — no subclass can sneak in mutable state.
- `final` fields — they can't be reassigned after the constructor.
- No setters — outside code has no way to mutate.
- Validation in the constructor — invariants checked once.

Now a `User` is a snapshot. Once you have a reference to one, its name and age will never surprise you.

---

## "Modifying" an immutable object

Sometimes you do want a `User` with a different age. The pattern: return a *new* immutable object with the updated value.

```java
public User withAge(int newAge) {
    return new User(this.name, newAge);
}

User alice    = new User("Alice", 30);
User olderAlice = alice.withAge(31);
// alice is unchanged.
```

The original is unchanged. Anyone holding a reference to it still sees what they saw before. The "modification" produced a new object.

This pattern is everywhere in `java.time`:

```java
LocalDate today = LocalDate.now();
LocalDate tomorrow = today.plusDays(1);
// today is still today.
```

`plusDays` returns a new `LocalDate`. The original is unchanged.

---

## Defensive copying: the trickiest part

If your immutable class holds a *mutable* object (a `List`, an array, a `java.util.Date`), the immutability is only skin-deep. The caller could mutate the inside.

### The leak

```java
public final class Team {
    private final List<String> members;

    public Team(List<String> members) {
        this.members = members;             // ← takes the caller's list
    }

    public List<String> getMembers() {
        return members;                     // ← hands it back to anyone
    }
}

List<String> input = new ArrayList<>(List.of("Alice"));
Team t = new Team(input);
input.add("Mallory");                       // mutates Team's internal list
t.getMembers().add("Eve");                  // mutates it again from the outside
```

The `Team` is no longer immutable. Two ways the outside world is poking at its insides.

### The fix: copy in, copy out

```java
public final class Team {
    private final List<String> members;

    public Team(List<String> members) {
        this.members = List.copyOf(members);     // copy on the way IN
    }

    public List<String> getMembers() {
        return members;                          // List.copyOf returned an unmodifiable list
    }
}
```

`List.copyOf(...)` returns an unmodifiable copy. The caller's list and the internal list are now disconnected, AND the returned list can't be mutated.

For other mutable types:

- Arrays: `Arrays.copyOf(...)` or `array.clone()`.
- Dates: use `java.time` (`LocalDate`, etc.) — they're already immutable. Avoid `java.util.Date`.
- Sets and Maps: `Set.copyOf(...)`, `Map.copyOf(...)`.

If you can use immutable types end-to-end, there's nothing to defensively copy. Prefer that.

---

## Why immutability matters in backend code

**Thread safety, for free.**

An immutable object can be shared across any number of threads without any locks. Nobody can change it; nobody can observe an inconsistent state. This is huge for concurrent code (covered properly in module 03).

**HashMap and HashSet keys.**

A mutable key whose `hashCode` changes silently breaks any hashed collection. Immutable keys are always safe.

**API surfaces.**

A function returning an immutable result lets the caller use it freely without worrying about side effects on the producer. A function accepting an immutable parameter doesn't worry about the caller mutating it later.

**Cache safety.**

You can cache an immutable result and return the same instance to many callers. None can change it on each other.

**Easier reasoning.**

"This `User` came in with name=Alice. I called three methods. Now I want to read the name." With mutability, who knows — one of those methods might have changed it. With immutability, it's still Alice.

---

## When NOT to use immutability

Some objects are inherently stateful — a connection pool, a counter, a cache, an entity being loaded from the database. Forcing immutability on these is awkward.

The split that usually works:

- **Value objects** (`User`, `Money`, `Address`, `DateRange`) — immutable.
- **Domain entities with identity and lifecycle** (`Account` with changing balance, `Order` moving through states) — controlled mutation through methods (encapsulation, topic 03).
- **Services and infrastructure** (`UserRepository`, `EmailSender`) — usually stateless or with carefully-managed state.

When in doubt, **start immutable** and add mutability only when you have a real need.

---

## Records (Java 16+): immutability by default

```java
public record User(String name, int age) { }
```

Generates:

- `final` class with `final` fields.
- Constructor.
- `name()` and `age()` accessors (no setters).
- `equals`, `hashCode`, `toString`.

You can add validation in a "compact constructor":

```java
public record User(String name, int age) {
    public User {
        if (name == null || name.isBlank()) throw new IllegalArgumentException();
        if (age < 0)                        throw new IllegalArgumentException();
    }
}
```

For new code that's primarily holding data, records are usually the right choice. The only caveat: records *don't* defensively copy mutable fields. If your record holds a `List`, you still need to be careful.

---

## Common pitfalls

- **`final` on the reference but mutable contents.** A common false sense of security — `final List<String>` is still mutable. Topic 12.
- **Forgetting defensive copying.** Most "supposedly immutable" classes that hold a `List` or array are actually mutable through the back door.
- **Using `java.util.Date`.** It's mutable. Use `java.time.LocalDate` / `Instant` / etc. instead.
- **Trying to make stateful things immutable.** Some classes need state that changes. Forcing immutability where it doesn't fit makes the design worse.
- **Treating records as "mutable shortcuts."** Records are immutable. If you wanted a mutable bag of fields, write a regular class.

---

## Code examples

1. `MutableUserBroken.java` — JavaBean-style class showing how state can be corrupted at any time.
2. `ImmutableUser.java` — the same class redone properly.
3. `DefensiveCopyMutableField.java` — when a field is a mutable type, copy in and out.
4. `ImmutableRecord.java` — record-based version with validation.

---

## Try this yourself

1. In `ImmutableUser.java`, try to add a `setName` method. Then try to mutate `name` directly. Both should fail (one at compile time, one because the field is `final`).
2. In `DefensiveCopyMutableField.java`, remove the `List.copyOf` in the constructor. Demonstrate that the caller can now mutate the internal list.
3. In `ImmutableRecord.java`, try `new User("", 30)`. What exception do you get?

---

## Self-check

1. List the four (or five) things a class must do to be properly immutable.
2. `Team` has `private final List<String> members`. Why might this still not be truly immutable?
3. Why is it safe to share an immutable object across threads without locks?
