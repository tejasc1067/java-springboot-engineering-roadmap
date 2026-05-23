# Interview Q&A — Module 03a (Java Language Depth)

Each question has an answer that requires understanding, not vocabulary. Grouped by topic.

---

## Exception handling (topics 01-02)

### Q1. Checked vs unchecked exceptions — when would you choose to make a custom exception checked?

**A.** Java's split:

- **Checked** (`Exception` and its subclasses except `RuntimeException`) — caller is forced to handle or declare. Compiler enforces.
- **Unchecked** (`RuntimeException` and its subclasses) — caller may handle. No compile-time enforcement.

Make a custom exception **checked** when the caller can reasonably do something about it: a `FileNotFoundException`, a `TimeoutException`. The "do something" might be retry, fall back, ask the user.

Make it **unchecked** when:

- The error is a programming bug (`NullPointerException`, `IllegalStateException`).
- The error happens deep in code where every caller would just propagate — the checked-exception ceremony adds noise without adding handling.
- You're working inside a framework where most exceptions become 500 responses regardless.

Modern guidance leans toward unchecked for application errors. The original "checked makes APIs safer" idea pre-dated lambdas (which can't throw checked exceptions cleanly) and ended up generating more `throws Exception` than handling.

### Q2. What happens to a `finally` block if a `try` block returns?

**A.** The `finally` runs *before* the actual return happens. The return value is calculated, held; `finally` executes; then the value returns.

Trap: if `finally` itself has a `return` or throws, that *replaces* the original. The first value/exception is discarded.

```java
try { return 1; }
finally { return 2; }     // method returns 2; the 1 is lost
```

Don't put `return` or `throw` in `finally` unless you mean it.

---

## Generics (topics 03-04)

### Q3. What does `List<?>` mean, and what can you legally do with it?

**A.** `List<?>` is "a list of *some* unknown type." You can:

- Read elements as `Object`.
- Call `.size()`, `.isEmpty()`, `.clear()` etc. — anything not consuming a typed value.

You **cannot** `add(x)` anything except `null`. The compiler doesn't know what `?` is, and refuses to let you put a `String` into what might be `List<Integer>`.

Used as a method parameter when you want to accept any list and only read from it: `void print(List<?> items)`. Distinct from `List<Object>`, which is *only* a list of Objects — you can't pass a `List<String>` to `List<Object>` (Java's generics are invariant).

### Q4. PECS — when do you use `extends`, when do you use `super`?

**A.** **Producer-Extends, Consumer-Super.**

- **`? extends T`** when the parameter is a **producer** — you'll read from it. `Collection<? extends Number>` — you can read `Number`s out.
- **`? super T`** when the parameter is a **consumer** — you'll write into it. `Collection<? super Integer>` — you can put `Integer`s in.

`Collections.copy(dest, src)` declares as `copy(List<? super T> dest, List<? extends T> src)`. Source produces; destination consumes.

### Q5. Why is `Foo<T>` not assignable to `Foo<Object>`?

**A.** Generics are **invariant**. `List<String>` is *not* a subtype of `List<Object>`, even though `String` is a subtype of `Object`.

Reason: if it were, you could:

```java
List<String> ls = new ArrayList<>();
List<Object> lo = ls;          // pretend this were legal
lo.add(new Integer(42));        // legal -- it's a List<Object>
String s = ls.get(0);           // ClassCastException
```

Arrays are *covariant* (you can do `Object[] a = new String[1]; a[0] = new Integer(...);` — compiles, fails at runtime). Generics chose safety over flexibility.

---

## Collections (topics 05-11)

### Q6. How does `HashMap` look up an entry?

**A.**

1. Compute `hash = hashCode(key)`, run it through an internal mixing function (`h ^ (h >>> 16)`).
2. Index into the bucket array: `bucket = table[(n - 1) & hash]` where `n` is the array length.
3. Walk the bucket: linked list (or red-black tree if it grew past 8) of entries.
4. For each entry, compare with `equals` — return the value when match found.

Performance is amortized O(1). When the load factor (entries / capacity) exceeds 0.75, the table doubles in size and every entry rehashes.

Java 8 added the tree-when-collisions-pile-up optimization. Before 8, a bucket with N collisions was O(N) to look up; now it's O(log N) once N > 8.

### Q7. Why is `HashMap` unsafe under concurrent writes, and what corruption can result?

**A.** `HashMap` operations like `put`, `resize`, and `transfer` are not atomic. Two threads writing into the same bucket can:

- **Lose entries.** Both compute the same insertion point; one overwrites the other's link.
- **Corrupt the bucket list.** Both threads doing concurrent rehashing during resize can produce a cycle in the linked list of entries. Subsequent `get()` calls on that bucket spin forever — a real CPU-pegging hang.

The cycle bug was the famous "infinite loop in `HashMap.get`" production incident. Java 8's tree-bucket rewrite reduced the chance, but the structure is still not safe.

Use `ConcurrentHashMap` for shared mutation.

### Q8. `equals` and `hashCode` — what's the contract, and what breaks if you violate it?

**A.** The contract:

1. **Consistent**: same inputs, same answer (per object lifetime, assuming the fields used don't change).
2. **Symmetric**: `a.equals(b) <=> b.equals(a)`.
3. **Transitive**: `a.equals(b) && b.equals(c) => a.equals(c)`.
4. **`a.equals(b) => a.hashCode() == b.hashCode()`** — the rule that gets violated most.
5. Two unequal objects *may* have the same hash (collisions are allowed).

Violating #4 breaks every hash-based collection:

- `HashSet` accepts duplicates.
- `HashMap` returns null for keys you just put in (the get computes a different hash than the put).

Always override both together. IDEs and `record` do this for you.
