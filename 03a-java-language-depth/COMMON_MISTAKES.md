# Common Mistakes — Module 03a (Java Language Depth)

Real bugs you'll hit (or have hit) with exceptions, generics, and the collections framework. Code, what goes wrong, the fix.

---

## 1. Catching `Exception` and swallowing it (topic 01)

```java
try { service.call(); }
catch (Exception e) { /* nothing */ }
```

You've replaced a real error with silent corruption. The caller thinks it succeeded, the next step uses stale data, a customer report doesn't match the bank.

**Fix.** Catch narrowly. Log with context (`log.error("calling X for user {}", id, e)`). If you can't handle it here, let it propagate. If you re-throw a different exception, pass the original as the cause: `throw new MyError("X failed", e)`.

---

## 2. `String` concatenation of an exception's message (topic 01)

```java
catch (IOException e) {
    throw new RuntimeException("X failed: " + e.getMessage());
}
```

The stack trace of the original is gone. You see `RuntimeException: X failed: null` and never learn it was actually a disk-full error 6 frames down.

**Fix.** `throw new RuntimeException("X failed", e)`. The 2-arg constructor preserves `e` as the cause; full stack chain is in the logs.

---

## 3. Raw types in a new codebase (topic 03)

```java
List names = new ArrayList();
names.add("alice");
names.add(42);                  // compiles
String s = (String) names.get(1);   // ClassCastException at runtime
```

The compile-time type system isn't doing its job. You'll find this in code maintained from pre-Java-5 days. There's no good reason to write new raw-type code.

**Fix.** Always parameterize: `List<String> names = new ArrayList<>();`.

---

## 4. `ArrayList.remove(int)` vs `remove(Object)` (topic 06)

```java
List<Integer> ids = new ArrayList<>(List.of(10, 20, 30));
ids.remove(20);    // removes ELEMENT 20? No -- removes INDEX 20, throws IndexOutOfBounds
```

`remove(int)` and `remove(Object)` are both there. The autoboxing rules pick the `int` overload when you pass a primitive literal.

**Fix.** Box explicitly: `ids.remove(Integer.valueOf(20))`. Or use `ids.removeIf(i -> i == 20)`.

---

## 5. Mutable key in a `HashMap` (topic 09)

```java
class Key { int v; }                       // mutable, hashCode based on v
Map<Key, String> map = new HashMap<>();
Key k = new Key(); k.v = 1;
map.put(k, "one");
k.v = 2;                                   // key mutated AFTER insertion
map.get(k);                                // returns null -- it's in the wrong bucket
```

`hashCode` was computed once at insertion. The map looks for the entry in a bucket determined by the new hash. It's not there.

**Fix.** Make keys immutable. `String`, `Integer`, `UUID`, `LocalDate` — all immutable, all safe.

---

## 6. `HashMap` under concurrent writes (topics 09, 11)

```java
Map<Integer, Integer> map = new HashMap<>();
// 8 threads writing concurrently...
// silently loses entries, or in older JVMs spins in an infinite loop in the linked-list rehash.
```

`HashMap` is not thread-safe. Period.

**Fix.** `ConcurrentHashMap`. Use `computeIfAbsent` / `merge` for compound updates; `put` + `containsKey` is NOT atomic even on `ConcurrentHashMap`.
