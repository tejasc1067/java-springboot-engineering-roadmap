# 13 — Collections Framework

Arrays are fine when you know the size upfront. In real backend code, you usually don't. You're holding a list of users that grows as requests come in. You're caching a lookup by ID. You're tracking which roles a user has. For all of that, you reach for a **collection** — `ArrayList`, `HashMap`, `HashSet`, `LinkedList`, and the rest.

This topic is the most important one in module 01 for day-to-day backend work. Most of your code will be moving things in and out of these.

---

## The big picture

Java's collections live under `java.util` and are organized by interface:

```text
Collection                              Map
 ├── List          (ordered, dups OK)    ├── HashMap     (unordered)
 │    ├── ArrayList                       ├── LinkedHashMap (insertion order)
 │    └── LinkedList                      └── TreeMap     (sorted by key)
 ├── Set           (no duplicates)
 │    ├── HashSet
 │    ├── LinkedHashSet
 │    └── TreeSet
 └── Queue         (FIFO-ish)
      └── ArrayDeque
```

Read it as: **List = ordered sequence with duplicates**. **Set = no duplicates**. **Map = key→value lookup**. **Queue = ordered processing (FIFO usually)**.

`Map` is *not* a `Collection`; it lives in its own hierarchy.

---

## ArrayList — the everyday list

Backed by a resizable array. Fast at the end, slow at the front (inserting at index 0 means shifting everything).

```java
List<String> names = new ArrayList<>();
names.add("alice");
names.add("bob");
names.add("carol");

names.get(1);            // "bob"
names.set(1, "BOB");     // replace at index 1
names.remove(0);         // remove "alice", shifting others
names.contains("carol"); // true
names.size();            // 2
names.isEmpty();         // false
```

The declared type on the left is `List<String>` (the interface), the constructor on the right is `new ArrayList<>()`. This is idiomatic — **program to the interface**. If you decide to switch to `LinkedList` later, only one line changes.

`List.of("a", "b", "c")` creates an **immutable** list. Useful for constants and method arguments. You can't add or remove from it.

---

## LinkedList — when?

Backed by a doubly-linked node chain. Fast at both ends, slow at random index access.

```java
LinkedList<String> queue = new LinkedList<>();
queue.addLast("a");
queue.addFirst("b");
queue.removeFirst();    // O(1) — no shifting
```

In practice you reach for it rarely. `ArrayList` is the default; `ArrayDeque` is better than `LinkedList` for queue/deque use cases. `LinkedList` shows up mostly in interview questions.

---

## HashSet — uniqueness

Stores unique values. No ordering. Add a duplicate and it's silently ignored.

```java
Set<String> seen = new HashSet<>();
seen.add("alice");
seen.add("bob");
seen.add("alice");      // ignored — already in the set
seen.size();            // 2
seen.contains("alice"); // true
```

Use a `HashSet` when you need to ask "have I seen this?" or "is this one of the allowed values?". Lookup, add, and remove are all O(1) on average.

Order matters? Use `LinkedHashSet` (insertion order) or `TreeSet` (sorted).

---

## HashMap — the workhorse

Key→value lookup. The most useful collection in Java. Average O(1) for `get`, `put`, `remove`.

```java
Map<String, Integer> ages = new HashMap<>();
ages.put("alice", 30);
ages.put("bob", 25);

ages.get("alice");           // 30
ages.get("nobody");          // null  ← careful
ages.getOrDefault("nobody", -1);   // -1
ages.containsKey("alice");   // true
ages.remove("bob");
ages.size();                 // 1
```

The classic trap: `get` returns `null` for missing keys. Use `getOrDefault` or `containsKey` to avoid an NPE downstream.

Iterating:

```java
for (Map.Entry<String, Integer> entry : ages.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}

ages.forEach((name, age) -> System.out.println(name + " is " + age));
```

`HashMap` makes no order guarantees. Need insertion order? `LinkedHashMap`. Need sorted by key? `TreeMap`.

---

## Queue and Stack

`Queue` is FIFO (first in, first out). `Deque` (double-ended queue) supports both ends. **Use `ArrayDeque`** for both queue and stack purposes — it's faster than the older `LinkedList` or `Stack` classes.

```java
Deque<String> q = new ArrayDeque<>();
q.offer("a");        // add to back
q.offer("b");
q.offer("c");
q.poll();            // "a" — remove from front

// Stack usage (LIFO)
Deque<String> stack = new ArrayDeque<>();
stack.push("a");
stack.push("b");
stack.pop();         // "b"
```

The `java.util.Stack` class still exists but is legacy. Use `ArrayDeque` for new code.

---

## Generics

The `<String>`, `<Integer>` part is **generics** — the type parameter for the collection.

```java
List<String> names = new ArrayList<>();
names.add("alice");
names.add(42);           // compile error — wrong type
```

Without generics (the old "raw type"), you'd be able to put anything in and would have to cast on the way out. Don't write raw types in new code:

```java
List names = new ArrayList();        // raw — avoid
names.add("alice"); names.add(42);   // compiles
String s = (String) names.get(1);    // ClassCastException at runtime
```

Collections require **reference types** for their generic parameter, not primitives. Use the wrapper types — `Integer`, `Long`, `Double`, `Boolean`. Java auto-converts between `int` and `Integer` (autoboxing/unboxing) so this is mostly invisible.

```java
List<Integer> numbers = new ArrayList<>();
numbers.add(5);          // autoboxed from int to Integer
int n = numbers.get(0);  // unboxed back to int
```

---

## Picking the right collection

| You want to...                              | Reach for...     |
|---------------------------------------------|------------------|
| Hold an ordered list, allow duplicates      | `ArrayList`      |
| Ask "is X in this set" repeatedly           | `HashSet`        |
| Look something up by a key                  | `HashMap`        |
| Keep insertion order in a set/map           | `LinkedHashSet`/`LinkedHashMap` |
| Keep entries sorted by key                  | `TreeMap` (or `TreeSet`) |
| Process items FIFO (queue) or LIFO (stack)  | `ArrayDeque`     |
| Hold something that never changes           | `List.of(...)`, `Set.of(...)`, `Map.of(...)` |

Default to `ArrayList` and `HashMap` unless you have a specific reason to pick something else.

---

## Common pitfalls

- **`map.get(key)` returns null for missing keys, and you NPE downstream.** Use `getOrDefault` or `containsKey`.
- **Iterating with for-each and removing.** ConcurrentModificationException (see topic 04). Use `Iterator.remove`, `removeIf`, or build a new collection.
- **Using a mutable object as a `HashMap` key.** If the key's `hashCode` changes after insertion (e.g., mutated field), you can't find it again. Keys should be effectively immutable.
- **Forgetting that `HashSet`/`HashMap` need `equals` and `hashCode`.** Default `Object.equals` is identity-based. Two `User` objects with the same name aren't "equal" unless you override `equals` and `hashCode` together. Module 02 (object methods topic) covers this.
- **Putting `int` in `List<Integer>`, then `==` comparing.** Wrapper objects don't compare with `==`. Use `.equals()` or unbox to `int` first.

---

## Code examples

1. `ArrayListBasics.java` — declare, add, get, remove, contains, iterate.
2. `HashMapBasics.java` — put, get, getOrDefault, iterate with entrySet and forEach.
3. `HashSetUniqueness.java` — adding duplicates is a no-op, fast lookup.
4. `QueueAndStack.java` — ArrayDeque used both ways.
5. `ChooseTheCollection.java` — a small benchmark / decision demo: counting word occurrences using HashMap; deduplicating using HashSet.
6. `GenericsAndAutoboxing.java` — generics for type safety, autoboxing int ↔ Integer.

---

## Try this yourself

1. In `HashMapBasics.java`, count word frequencies in a sentence. Use `getOrDefault(word, 0) + 1`. The whole loop is 3 lines.
2. In `HashSetUniqueness.java`, swap `HashSet` to `LinkedHashSet`. Observe that the iteration order now matches insertion. Try `TreeSet` — the order is alphabetical.
3. Take a `List<Integer>`, remove every value above 100 *during iteration*. Try with for-each (CME), with Iterator, and with `list.removeIf(...)`. Confirm the last two work.

---

## Self-check

1. You need "is this username taken?" — `ArrayList` or `HashSet`? Why?
2. `map.get("foo")` returns null. Does that mean "foo isn't a key" or "foo's value is null"? How do you tell the difference?
3. You iterate a `List<String>` with for-each and try to `list.remove(item)` inside the loop. What exception fires, and what should you do instead?
