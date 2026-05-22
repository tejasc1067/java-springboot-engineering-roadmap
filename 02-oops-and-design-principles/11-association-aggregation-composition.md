# 11 — Association, Aggregation, Composition

Three names for "this class has a relationship with another class," with shades of meaning. The distinction matters when you're modeling a real domain: is the relationship loose, or is the dependent object's lifecycle tied to the parent?

Same words as topic 02 from module 04 (relational modeling) — the parallel between OOP and database design is not a coincidence.

---

## Association: any "uses" relationship

The loosest form. Class A *uses* class B — usually as a method parameter or short-lived collaborator.

```java
class EmailSender {
    void send(EmailMessage msg) {     // uses an EmailMessage
        // ...
    }
}
```

`EmailSender` is *associated with* `EmailMessage`. It doesn't own one; it just operates on them. Neither lifecycle depends on the other.

Most "X interacts with Y" relationships in a system are associations. The pattern is so common that "association" by itself is rarely the design choice — it's just *what relationships are*. The interesting distinctions are below.

---

## Aggregation: "has-a," but loose

A "whole-part" relationship where the parts can exist independently of the whole.

```java
class Library {
    private List<Book> books;

    Library(List<Book> books) {
        this.books = books;
    }
}

class Book {
    String title;
}

// Books exist on their own.
Book b1 = new Book(); b1.title = "Dune";
Book b2 = new Book(); b2.title = "Foundation";

// The library holds them — but they could be moved to another library,
// sold, lent out, etc. Their existence isn't tied to this Library.
Library lib = new Library(List.of(b1, b2));
```

If you destroy the `Library` object, `b1` and `b2` still exist. They were passed in from outside; the library doesn't own them.

**UML term:** "aggregation" (often drawn with a hollow diamond).

---

## Composition: "has-a," tight

A "whole-part" relationship where the parts cannot exist without the whole. The parent **owns** the parts; destroying the parent destroys them.

```java
class House {
    private final List<Room> rooms;

    House() {
        // Rooms are CREATED inside the house. They don't exist outside it.
        this.rooms = List.of(
            new Room("kitchen"),
            new Room("bedroom"),
            new Room("bathroom")
        );
    }
}

class Room {
    private final String name;
    Room(String name) { this.name = name; }
}
```

The `Room` instances are born and die with the `House`. They aren't shared, they aren't passed in, they aren't accessible to anyone else. The relationship is tight.

**UML term:** "composition" (filled diamond).

---

## Aggregation vs. composition — the practical test

Ask: **can the part exist independently of the whole, and be transferred to another whole?**

- "A Library has Books" — yes, books can be moved between libraries. **Aggregation.**
- "A House has Rooms" — no, you can't move a room to another house. **Composition.**
- "An Order has LineItems" — no, line items don't exist outside their order. **Composition.**
- "A Team has Players" — yes, players get traded. **Aggregation.**
- "A Person has a Heart" — sort of (transplants). Mostly composition, but the world is messy.

The line isn't always crisp. The point is to think clearly about whose responsibility it is to create, destroy, and own the related objects.

---

## "Composition over inheritance"

The most repeated piece of OOP design advice. We touched it in topic 04. Here's the fuller picture.

Two ways to reuse a class's behavior in another class:

```java
// Inheritance: Manager IS-A Employee
class Manager extends Employee { ... }

// Composition: Manager HAS-A Employee
class Manager {
    private final Employee profile;     // a Manager has an Employee profile
    Manager(Employee profile) { this.profile = profile; }
}
```

Inheritance has problems:

- **Tight coupling.** Every change to `Employee`'s public surface ripples to `Manager`.
- **No flexibility.** A `Manager` is committed to *being* an `Employee` for its entire life. You can't change it.
- **Multiple inheritance not allowed.** A `Manager` can extend only one class.
- **Exposes too much surface.** All of `Employee`'s public methods become part of `Manager`'s interface, even ones that don't make sense.

Composition avoids all of these:

- The `Manager` exposes only the methods *it chooses* to expose.
- The composed `Employee` can be swapped out (e.g. a `MockEmployee` for tests).
- The `Manager` can compose multiple objects, not just one parent.
- Coupling is limited to the public methods you actually call.

**Default to composition.** Use inheritance only when the *is-a* relationship is real AND you want the parent's whole interface to be part of the child's.

Spring is built almost entirely on composition (dependency injection wires composed objects together). Spring uses inheritance very sparingly.

---

## Practical patterns

**Service composes repository:**
```java
class UserService {
    private final UserRepository repo;    // composition
    UserService(UserRepository repo) { this.repo = repo; }
}
```

**Controller composes service:**
```java
class UserController {
    private final UserService service;    // composition
    UserController(UserService service) { this.service = service; }
}
```

**Decorator pattern (composition + interface):**
```java
class CachingUserRepository implements UserRepository {
    private final UserRepository delegate;     // wraps another repository
    private final Cache cache;
    // adds caching behavior without modifying the wrapped class
}
```

These shapes are everywhere in real backend code. They're all composition.

---

## Common pitfalls

- **Pedantically obsessing over aggregation vs. composition.** They blur in real codebases. The bigger question is: *who owns the lifecycle*, and have you been deliberate about it?
- **Using inheritance to share code with a class that's not a real subtype.** Composition with delegation does this without the inheritance baggage.
- **Composing too widely.** A class that holds 8 collaborators is doing too much — split it (the **S** in SOLID; topic 14).
- **Composing through public fields.** Same problem as topic 03 — outside code can replace the collaborator at will. Compose through `final` private fields, set in the constructor.

---

## Code examples

1. `Association.java` — `EmailSender` uses `EmailMessage` as a parameter.
2. `Aggregation.java` — `Library` aggregates `Book`s passed from outside.
3. `Composition.java` — `House` creates and owns its `Room`s.
4. `CompositionOverInheritance.java` — same goal expressed both ways for comparison.

---

## Try this yourself

1. In `Aggregation.java`, transfer a book to a new library. Show that the original library no longer has it, but the book itself is unchanged.
2. In `Composition.java`, try to expose the internal `Room` list with a getter. Then return an unmodifiable view instead (topic 03 pattern).
3. In `CompositionOverInheritance.java`, add a method that should be on `Manager` but doesn't make sense to inherit from `Employee`. See how composition lets you control what's exposed.

---

## Self-check

1. Aggregation vs. composition — give one example of each from real life (not from the markdown).
2. "Composition over inheritance" — name one specific cost of inheritance that composition avoids.
3. In Spring, a `UserService` field of type `UserRepository` is set via constructor. Is this association, aggregation, or composition? Why?
