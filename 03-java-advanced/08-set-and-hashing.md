# 08 — `Set` and Hashing

A `Set` stores unique elements. Sounds simple. The implementation, though, is where the most consequential interview question in Java lives: **the `equals`/`hashCode` contract**. Get it wrong and your `HashSet` will silently contain duplicates, your `HashMap` will lose entries, and you'll spend a day not believing your own eyes.

This topic covers what `HashSet` actually does under the hood, the exact contract you must honor, and the implementations: `HashSet`, `LinkedHashSet`, `TreeSet`.

---

## The problem this solves

You want to track which users you've already processed. With a `List`, every `contains(user)` is O(n) — for a 100k-user list, that's a 100k-comparison scan per check. Reduce 100k checks to milliseconds:

```java
Set<User> processed = new HashSet<>();
if (!processed.add(user)) { /* duplicate, skip */ }
```

`HashSet.add` and `HashSet.contains` are O(1) **average case**. The trick: they don't search the elements. They compute a hash to jump straight to the bucket.

---

## How `HashSet` works (and `HashMap` underneath)

`HashSet` is internally a `HashMap` with the values ignored. So understanding `HashMap` (topic 09) is understanding both.

```text
        ┌────────────┐
hash    │  bucket 0  │ → null
of      │  bucket 1  │ → [entry: "Alice" → present]
element │  bucket 2  │ → null
→       │  bucket 3  │ → [entry: "Bob" → present] → [entry: "Charlie" → present]
        │  bucket 4  │ → null
        │   ...      │
        └────────────┘
```

To add `"Alice"`:
1. Call `"Alice".hashCode()` — get an int.
2. Map it to a bucket index (modulo capacity).
3. Walk the bucket's chain. Use `equals` to check if it's already there.
4. If not, append.

To check `contains("Alice")`:
1. Same hash computation.
2. Walk the bucket's chain, comparing with `equals`.

`hashCode` decides **which bucket**. `equals` decides **whether two objects in the same bucket are the same**. Both must agree, or the set is broken.

---

## The `equals`/`hashCode` contract

Three rules. Memorize them.

1. **If `a.equals(b)`, then `a.hashCode() == b.hashCode()`.**
   Otherwise the lookup goes to the wrong bucket and misses.
2. **If `a.hashCode() == b.hashCode()`, `a.equals(b)` *might* be true or false.**
   Hash collisions are fine — the bucket-walk resolves them.
3. **`hashCode()` must be stable while the object lives in the set.**
   Mutate a field that participates in `hashCode`, and the object becomes "lost" — same identity, different bucket.

The classic bug: override `equals` but forget `hashCode`. Now two "equal" objects have different hashes, land in different buckets, and the set holds both.

---

## What a correct `equals`/`hashCode` looks like

```java
public class User {
    private final long id;
    private final String email;

    public User(long id, String email) {
        this.id = id;
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id == other.id && Objects.equals(email, other.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }
}
```

Properties:

- Same fields drive both methods.
- `equals` checks type, handles nulls (`Objects.equals` does the right thing).
- `hashCode` uses `Objects.hash(...)` — combines all relevant fields. Don't try to be clever; this is fine for nearly everything.

Records (Java 16+) generate both methods automatically from the components. Use records for value-type DTOs whenever you can.

```java
public record User(long id, String email) {}        // equals/hashCode auto-generated
```

---

## Mutability is the trap

If a field participates in `hashCode`, **don't mutate it while the object is in a set**.

```java
Set<User> users = new HashSet<>();
User u = new User(1, "old@example.com");
users.add(u);
u.setEmail("new@example.com");                       // hashCode changed
users.contains(u);                                   // FALSE — lookup goes to a different bucket
```

The object is still in the set. You just can't find it anymore. This is why value-type fields used in equality should be `final`. Records enforce this for you.

---

## `HashSet` vs `LinkedHashSet` vs `TreeSet`

| | Order | Add/Contains | Memory |
|--|-------|--------------|--------|
| `HashSet` | undefined (depends on hash + capacity) | O(1) average | minimal |
| `LinkedHashSet` | **insertion order** (linked list overlay) | O(1) average | small overhead per entry |
| `TreeSet` | **sorted** (natural order or `Comparator`) | O(log n) | red-black tree, moderate overhead |

`LinkedHashSet` is what you want when you need deduplication *and* the order in which things were first seen — perfect for "unique tags in the order I encountered them."

`TreeSet` is what you want when you need ordered iteration (e.g. "all events between two timestamps") or operations like `first()`, `last()`, `headSet(x)`, `tailSet(x)`. Cost is O(log n) instead of O(1), but you get sortedness "for free."

---

## When hashing goes wrong: the pathological case

If `hashCode()` always returns the same value:

```java
public int hashCode() { return 0; }          // legal but ruinous
```

Every element lands in the same bucket. Lookup becomes a linear scan of every element. Add/contains degrade from O(1) to O(log n) in Java 8+ (because long bucket chains are converted to red-black trees — "treeification"), and O(n) in older JVMs.

Don't return a constant. Don't return `id` if your id is always 0 in tests. Use `Objects.hash(...)`.

---

## Common pitfalls

- **Override `equals`, forget `hashCode`.** Set holds duplicates. This is *the* Java bug.
- **Mutate fields used in `hashCode`.** Object becomes unfindable.
- **Use `equals` with arrays.** Arrays use reference equality. `Arrays.equals(a, b)` for content equality; `Arrays.deepEquals` for nested arrays.
- **`HashSet` for tracking insertion order.** Don't — use `LinkedHashSet`. `HashSet`'s iteration order is undefined and changes between JVM versions and on resize.
- **Forgetting `instanceof` null-safety.** `instanceof` is null-safe; explicit null + `getClass` checks aren't. Use the pattern `o instanceof User other` (Java 16+) — null and wrong-type both fall through.

---

## Code examples

1. `EqualsHashCodeContractBroken.java` paired with `EqualsHashCodeContractFixed.java` — the classic bug and the canonical fix.
2. `MutationLosesElement.java` — mutating a field that's part of `hashCode` makes an object unfindable.
3. `RecordAutoEquals.java` — records generate the right methods.
4. `HashSetVsLinkedHashSetVsTreeSet.java` — same data, three iteration orders.
5. `HashCollisionDegrades.java` — what happens when `hashCode` is constant.

---

## Try this yourself

1. In `EqualsHashCodeContractBroken.java`, add a fix line by line until `set.size()` becomes 1 instead of 2. Notice exactly which override turns the bug into correctness.
2. In `MutationLosesElement.java`, switch the User class to a record (and drop the setter). Try the same mutation — the compiler stops you.
3. In `HashSetVsLinkedHashSetVsTreeSet.java`, insert strings in random order and print each set's iteration. Run twice — only `HashSet` may change between runs (and even that is unlikely with simple String hashes).

---

## Self-check

1. State the equals/hashCode contract in your own words. Which rule does "override equals but forget hashCode" violate?
2. Why is mutating a field used in `hashCode` while an object is in a `HashSet` a disaster? Trace what happens to `contains` afterwards.
3. You want unique tags, kept in the order you first saw them. Which `Set` implementation, and why not `HashSet`?
