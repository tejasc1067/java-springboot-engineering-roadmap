# 10 — `Comparable` vs `Comparator`

Two interfaces, both about ordering. `Comparable<T>` is the type's *natural* order — "by default, sort users by their ID." `Comparator<T>` is an *ad-hoc* order plugged in at the call site — "for this report, sort users by signup date descending, then by name."

This topic also covers the modern `Comparator.comparing(...).thenComparing(...)` chains that make multi-field sorting one line of code instead of a custom class.

---

## The problem this solves

You have a `List<User>`. You want to sort. Sort by what?

- If there's an obvious answer ("by ID"), declare it once on the class — `User implements Comparable<User>`. Now `Collections.sort(users)` or `users.sort(null)` works.
- If there's no single answer ("today by signup date; tomorrow by name"), pass the rule explicitly — `users.sort(byName)`.

You don't pick one or the other globally. Most domain types have a natural order *plus* several useful ad-hoc orders. Declare the natural one; build comparators on demand.

---

## `Comparable<T>`: natural order

A type declares "this is how I compare to others of my kind" by implementing `Comparable<T>`. One method: `int compareTo(T other)`.

```java
public class User implements Comparable<User> {
    private final long id;
    private final String name;

    public User(long id, String name) { this.id = id; this.name = name; }

    @Override
    public int compareTo(User other) {
        return Long.compare(this.id, other.id);    // negative, zero, positive
    }
}
```

Contract:

- Negative if `this < other`, zero if equal, positive if greater.
- Must be **consistent with equals**: `a.compareTo(b) == 0 ⟺ a.equals(b)`. Violating this is technically legal but breaks `TreeSet`, `TreeMap`, and other ordered structures in subtle ways.
- Must be transitive and antisymmetric.

`Integer`, `String`, `LocalDate`, `BigDecimal`, `Long` — all implement `Comparable` with their obvious natural order.

---

## `Comparator<T>`: pluggable order

Comparators are objects you create on demand and pass to sort methods. The simplest one:

```java
Comparator<User> byName = (a, b) -> a.getName().compareTo(b.getName());
users.sort(byName);
```

Or use the factory methods:

```java
Comparator<User> byName = Comparator.comparing(User::getName);
Comparator<User> byIdDesc = Comparator.comparingLong(User::getId).reversed();
```

`Comparator.comparing(keyExtractor)` builds a comparator from a function that extracts the sort key. It's how 95% of modern Java sorting code is written.

---

## Multi-field sorting: `thenComparing`

You need: "by status, then by signup date descending, then by name."

```java
Comparator<User> order = Comparator
    .comparing(User::getStatus)
    .thenComparing(User::getSignupDate, Comparator.reverseOrder())
    .thenComparing(User::getName);

users.sort(order);
```

Each `thenComparing` only matters when the previous one ties. Reads top-to-bottom like a sorting spec.

For nullable fields:

```java
Comparator<User> byNicknameNullsLast = Comparator
    .comparing(User::getNickname, Comparator.nullsLast(Comparator.naturalOrder()));
```

`nullsFirst` / `nullsLast` wrap an existing comparator and decide where null keys go.

---

## How sorting actually works

`Collections.sort(list)` and `list.sort(comparator)` use **TimSort**: an adaptive merge sort. It's:

- O(n log n) worst case, O(n) on already-sorted input.
- **Stable**: equal elements keep their relative order. This is what makes `thenComparing` chains work — each pass is stable, so a later comparator only re-orders the ties.

`Arrays.sort(primitiveArray)` uses dual-pivot quicksort (faster for primitives, not stable — doesn't matter for ints).

---

## When to use which

| Situation | Use |
|-----------|-----|
| One canonical order exists for the type, and it should be the default | `Comparable<T>` |
| Sorting changes per use case | `Comparator<T>` |
| Sorting by a field that's not in your class (e.g. a wrapper) | `Comparator<T>` |
| Both — natural order plus ad-hoc views | Declare both |

You can ignore `Comparable` and always pass a `Comparator`. Modern code often does. But if your type has a clear natural order, declaring it once spares every call site from re-stating it.

---

## Common pitfalls

- **`compareTo` inconsistent with `equals`.** `TreeSet` will treat two "equal-by-compareTo" objects as duplicates and silently drop one. Make them consistent.
- **`int` subtraction in `compareTo`.** `a - b` overflows for large values: `Integer.MIN_VALUE - 1` wraps positive. Use `Integer.compare(a, b)`.
- **Comparing strings with `<`.** `"a" < "b"` doesn't compile — strings aren't primitives. Use `a.compareTo(b)` or `Comparator.naturalOrder()`.
- **Sorting an immutable list.** `List.of("c", "a", "b").sort(...)` throws `UnsupportedOperationException`. Copy to a mutable list first.
- **Building a comparator with `new Comparator() { ... }` boilerplate.** Java 8+ has the static factories; modern code is `Comparator.comparing(...)`.

---

## Code examples

1. `ComparableNaturalOrder.java` — a class implementing `Comparable<T>`.
2. `ComparatorAdHoc.java` — sorting the same data three different ways with different comparators.
3. `ThenComparingChain.java` — multi-field sort spelled out top-to-bottom.
4. `NullsFirstLast.java` — handling nullable sort keys cleanly.
5. `IntSubtractionOverflow.java` paired with `IntegerCompareSafe.java` — the subtle overflow bug.

---

## Try this yourself

1. In `ThenComparingChain.java`, reverse the second-level comparator. Note how the tie-breaking flips without disturbing the first level.
2. In `ComparableNaturalOrder.java`, deliberately make `compareTo` inconsistent with `equals` (e.g. compare only by ID, while `equals` uses ID+email). Insert two "equal-by-id, different-by-email" users into a `TreeSet`. One vanishes.
3. In `IntSubtractionOverflow.java`, pick values near `Integer.MAX_VALUE` and `Integer.MIN_VALUE`. Trace what `a - b` produces vs `Integer.compare`.

---

## Self-check

1. What's the difference between `Comparable<T>` and `Comparator<T>`? When would you implement both?
2. Why is `return a - b;` an unsafe way to write `compareTo` for ints? What's the correct call?
3. You want users sorted by status, then by signup date (newest first), then by name. Write the chained comparator.
