# 05 — Collections Framework Overview

The Collections Framework is the set of interfaces and classes Java ships for holding groups of objects: `List`, `Set`, `Map`, `Queue`, `Deque`, and their concrete implementations (`ArrayList`, `HashMap`, etc.). Module 01 introduced the everyday ones. This module digs into the structure — what implements what, what each interface promises, and how to pick the right one without guessing.

If "use ArrayList for everything" is your current strategy, this topic is for you.

---

## The problem this solves

You need to store users. Should that be `ArrayList`, `LinkedList`, `HashSet`, `TreeSet`, `HashMap`, or `LinkedHashMap`? The answer depends on what operations you'll do most often:

| Need | Best fit |
|------|----------|
| Indexed access (`get(i)`), append | `ArrayList` |
| Lots of insertion in the middle | `LinkedList` or `ArrayDeque` |
| Uniqueness, fast `contains` | `HashSet` |
| Uniqueness + sorted | `TreeSet` |
| Key → value lookup | `HashMap` |
| Key → value, sorted by key | `TreeMap` |
| Key → value, insertion order preserved | `LinkedHashMap` |
| FIFO / LIFO | `ArrayDeque` |
| Thread-safe key/value | `ConcurrentHashMap` |

Picking right gives you the right Big-O for free. Picking wrong is one of the most common silent performance bugs.

---

## The interface hierarchy

```text
Iterable
└── Collection
    ├── List          — ordered, duplicates allowed, indexed
    │    ├── ArrayList         (array-backed)
    │    ├── LinkedList        (doubly-linked, also a Deque)
    │    └── Vector            (legacy, synchronized — don't use)
    ├── Set           — no duplicates
    │    ├── HashSet           (hash-based, unordered)
    │    ├── LinkedHashSet     (hash + insertion order)
    │    └── TreeSet           (sorted, NavigableSet)
    └── Queue         — first-in stuff at one end
         └── Deque              (double-ended)
              ├── ArrayDeque    (array-backed, fast)
              └── LinkedList    (doubly-linked, also a List)

Map (NOT a Collection — separate root)
├── HashMap                    (hash-based, unordered)
├── LinkedHashMap              (hash + insertion order)
├── TreeMap                    (sorted by key, NavigableMap)
└── Hashtable                  (legacy, synchronized — don't use)
```

Why is `Map` outside `Collection`? Because it stores *pairs*, not single elements. Iterating it gives you entries or keys or values, not "the elements themselves." A historical wart, but you remember it once and move on.

---

## Big-O of the operations that matter

| Operation                       | ArrayList | LinkedList | HashSet | TreeSet | HashMap | TreeMap |
|---------------------------------|-----------|------------|---------|---------|---------|---------|
| `get(index)` / random access    | O(1)      | O(n)       | —       | —       | —       | —       |
| `add` at end                    | amortized O(1) | O(1)  | —       | —       | —       | —       |
| `add` at start / middle         | O(n)      | O(1) once at the node | — | — | — | — |
| `contains` / `get(key)`         | O(n)      | O(n)       | O(1)    | O(log n)| O(1)    | O(log n)|
| `remove(element)`               | O(n)      | O(n)       | O(1)    | O(log n)| O(1)    | O(log n)|
| iteration                       | O(n), cache-friendly | O(n), not cache-friendly | O(n) | O(n), sorted | O(n) | O(n), sorted by key |

A few real-world points the table hides:

- `ArrayList.add` at end is *amortized* O(1) — every so often it doubles its array, which is O(n). Day to day, treat it as O(1).
- `LinkedList.add` is O(1) **only if you have the position handle**. `linkedList.add(5000, x)` is O(n) because it has to walk to index 5000.
- `HashMap.get` is O(1) **only with a decent hashCode**. With a broken `hashCode()` that always returns 0, every key lands in the same bucket and you get O(log n) (Java 8+ treeifies) or O(n) (older). Topic 09 covers this in depth.

---

## When does each interface promise what?

`List` — **ordered + indexable + duplicates allowed.** Order is whatever you put in; indices stay stable between modifications.

`Set` — **no duplicates.** Iteration order depends on the implementation: `HashSet` random, `LinkedHashSet` insertion, `TreeSet` sorted.

`Map` — **unique keys → values.** Same iteration-order rules as Set (by key).

`Queue` / `Deque` — **a head and a tail** (Deque = both). FIFO or LIFO depending on which methods you call.

The interface names are the contract. If you write `void process(List<User> users)`, you're saying "give me anything ordered+indexable." Don't accidentally write `ArrayList<User> users` in your parameter — you've locked the caller into one implementation for no good reason. Always declare the most general type the method actually needs.

---

## Iteration: three flavors

```java
// 1. Index-based (for List only)
for (int i = 0; i < list.size(); i++) { ... list.get(i) ... }

// 2. Enhanced-for (uses Iterator under the hood)
for (User u : list) { ... }

// 3. Iterator explicitly — needed if you want to remove during iteration
Iterator<User> it = list.iterator();
while (it.hasNext()) {
    User u = it.next();
    if (u.isInactive()) it.remove();          // safe — uses the Iterator's remove
}
```

Removing from a collection during a `for`-each loop throws `ConcurrentModificationException`. The Iterator's own `remove()` is the only safe way during iteration (or use `removeIf` for a one-liner).

---

## Utility classes worth knowing

- `Collections.sort(list)`, `Collections.sort(list, comparator)` — covered in topic 10.
- `Collections.unmodifiableList(list)` — wraps in a read-only view. Doesn't copy; the underlying list can still be mutated by whoever has the original reference. Genuine immutables: `List.of(...)`, `Set.of(...)`, `Map.of(...)` (Java 9+).
- `Collections.emptyList()`, `emptySet()`, `emptyMap()` — singleton empty collections. Cheaper than `new ArrayList<>()` when returning empties.
- `Arrays.asList("a", "b")` — fixed-size list backed by the varargs array; can't `add`/`remove` but can `set`. `List.of("a", "b")` is the modern, fully-immutable alternative.

---

## Common pitfalls

- **`Arrays.asList(intArray)`.** With a primitive array, the result is `List<int[]>` of size 1 — not what you wanted. Use `IntStream.of(...).boxed().toList()` instead.
- **Returning the field directly.** `public List<User> getUsers() { return users; }` — callers can now mutate your internal list. Wrap with `Collections.unmodifiableList` or `List.copyOf`.
- **`List` as a method parameter where you don't need order.** Asking for `Set<User>` when you really want uniqueness is more honest and lets the caller use what they have.
- **Mistaking `Vector` and `Hashtable` for safe choices.** They're synchronized but their API is awkward and they're legacy. For thread-safety, use `Collections.synchronizedList()` (coarse) or concurrent collections from topic 11 (fine-grained).
- **Iterating + modifying.** `ConcurrentModificationException` waits. Use the Iterator's `remove()`, `removeIf()`, or copy-and-modify.

---

## Code examples

1. `ChooseTheRightCollection.java` — same problem solved with `ArrayList`, `LinkedList`, `HashSet`, `TreeSet`, `HashMap`. Observe which fits.
2. `IterationStyles.java` — three iteration styles with the same list.
3. `ConcurrentModException.java` paired with `IteratorRemoveProper.java` — the classic CME and the fix.
4. `UnmodifiableVsImmutable.java` — wrappers vs. genuinely immutable factories.
5. `ProgramToInterface.java` — `List<User>` vs `ArrayList<User>` as a parameter type, and what flexibility it buys.

---

## Try this yourself

1. In `ChooseTheRightCollection.java`, time the `contains` check on a 100k-element `ArrayList` vs `HashSet`. Note the gap — that's why "use ArrayList for everything" doesn't scale.
2. In `IterationStyles.java`, try to call `list.remove(item)` inside the enhanced-for loop. See the `ConcurrentModificationException` and switch to `IteratorRemoveProper.java`.
3. In `UnmodifiableVsImmutable.java`, prove that mutating the original list shows up through the "unmodifiable" wrapper. Then switch to `List.copyOf(...)` and watch that channel close.

---

## Self-check

1. You need fast key→value lookup with insertion order preserved. Which implementation? Why not `HashMap`?
2. `linkedList.add(5000, value)` — what's the actual time complexity, and why is it different from what beginners assume about `LinkedList`?
3. Why is `Map` not a `Collection`?
