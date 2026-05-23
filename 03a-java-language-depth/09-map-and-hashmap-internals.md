# 09 — `Map` and `HashMap` Internals

`HashMap` is everywhere — caches, indexes, configuration, JSON deserialization. It's the default key→value structure in Java. This topic is about what it actually does internally: the bucket array, the linked-list-then-tree bucket structure, resizing, the load factor, and the bugs that come from misunderstanding any of these.

This is also the data structure you'll be asked about in interviews — possibly the most common Java interview topic.

---

## The problem this solves

A `Map<K, V>` is a key→value lookup. You want average O(1) `get` and `put` for any key type — not just integers. `HashMap` achieves this by routing every key through `hashCode()` to pick a bucket, then using `equals()` inside the bucket to find the exact entry. The interplay of hash, bucket count, and load factor is what makes it fast on average and not-too-bad in the worst case.

---

## The layout

```text
table: [bucket 0] → null
       [bucket 1] → (Alice → 30)
       [bucket 2] → null
       [bucket 3] → (Bob → 25) → (Charlie → 28)       ← collision: linked list
       [bucket 4] → null
       ...
       [bucket 15] → null
```

A `HashMap` holds an array of buckets (often called `table`). Each bucket starts as `null` and grows into a linked list (or a red-black tree — see treeification below) as entries are added.

Default capacity: 16. Default load factor: 0.75. So a fresh `HashMap` resizes when the 13th entry is added.

---

## `put(k, v)` step by step

```java
map.put("Bob", 25);
```

1. Compute `h = hash("Bob")`. Java's `HashMap` re-mixes the user's hashCode to spread bits: `(h = key.hashCode()) ^ (h >>> 16)`. This protects against badly-distributed hashCodes.
2. Compute the bucket: `index = (table.length - 1) & h`. Capacity is always a power of 2, so this is a fast bit-mask.
3. If `table[index]` is null, insert here.
4. Otherwise, walk the chain. For each existing entry, check:
   - `entry.hash == h && (entry.key == k || entry.key.equals(k))` → same key → overwrite value.
   - Otherwise → keep walking.
5. If we reach the end without finding it, append a new entry.

`get` is the same walk: hash → bucket → walk chain using `equals`.

---

## Treeification (Java 8+)

If a bucket's chain reaches 8 entries (and total capacity is at least 64), `HashMap` converts that one bucket from a linked list to a red-black tree. Operations on that bucket drop from O(n) to O(log n). When the chain shrinks back below 6, it un-treeifies.

This is purely a defense against bad hash functions or adversarial inputs (someone deliberately crafting keys that all hash to the same bucket — a real concern for public-facing APIs).

You usually don't see it in code. You see it in *not having a denial-of-service vulnerability* on pathological inputs.

---

## Resize and the load factor

When `size > capacity * loadFactor`, `HashMap` doubles its bucket array and **re-hashes every existing entry** into the new buckets. That's an O(n) operation, but it happens log n times over the life of the map, so amortized `put` stays O(1).

Cost of resize: very visible if you put 1M entries into a map with the default capacity 16 — you'll trigger ~17 resizes. Pre-size to skip them:

```java
// you expect ~1M entries
Map<String, User> users = new HashMap<>(1_500_000, 0.75f);
//                       capacity is enough to hold 1M without resizing
```

Capacity hint should be roughly `expectedSize / loadFactor` to actually prevent a resize when you hit `expectedSize`.

---

## Iteration order is undefined

`HashMap` does not promise *any* iteration order. The order depends on:

- Each key's hashCode.
- The current capacity (which changes on resize).
- Treeification state of each bucket.

Iteration order **can change between JVM versions, between runs, and after a resize within one run**. Never rely on it.

If you need order:

- `LinkedHashMap` — insertion order (or access order, if configured).
- `TreeMap` — sorted by key (natural order or `Comparator`).

---

## `LinkedHashMap` (insertion or access order)

Same `HashMap` storage, plus a doubly-linked list threading entries in insertion order. Iteration walks the linked list, not the buckets — predictable and stable.

```java
Map<String, Integer> m = new LinkedHashMap<>();
m.put("c", 3);
m.put("a", 1);
m.put("b", 2);
for (var e : m.entrySet()) System.out.println(e);   // c=3, a=1, b=2
```

Constructor option `accessOrder = true` turns it into an LRU-friendly structure: every `get` moves the entry to the tail. Combined with overriding `removeEldestEntry`, that's the simplest LRU cache in Java.

---

## `TreeMap` (sorted)

A red-black tree keyed by sort order. All operations are O(log n). You also get range queries: `headMap(k)`, `tailMap(k)`, `subMap(lo, hi)`, plus `firstKey()`, `lastKey()`, `floorKey(k)`, `ceilingKey(k)`.

Use it when you need ordered iteration or range scans on keys. Cost is O(log n) instead of O(1), but for many backend tasks (sorted ledger entries, time-series buckets) the order is the point.

---

## Common pitfalls

- **Mutable keys.** Same trap as `HashSet`: mutate a field that's in the key's `hashCode` and the entry becomes unfindable. Prefer immutable key types — records are perfect.
- **Custom key class with no `equals`/`hashCode`.** Every `put` goes to a "different" bucket because identity-hash differs. Lookup never finds anything.
- **`map.containsKey(k) ? map.get(k) : default`** — two hash lookups when one would do. Use `map.getOrDefault(k, default)`.
- **`map.put` to count occurrences.** `map.put(k, map.getOrDefault(k, 0) + 1)` — two lookups. Use `map.merge(k, 1, Integer::sum)` (one lookup).
- **Iterating and mutating.** Same CME rules as collections. `entrySet().removeIf(...)` or `iterator.remove()`.
- **`null` keys / values.** `HashMap` allows one null key and any number of null values. `ConcurrentHashMap` and `TreeMap` reject nulls. Easy mistake when switching implementations.

---

## Code examples

1. `HashMapPutWalkthrough.java` — manual walk through hash → bucket → chain.
2. `LoadFactorAndResize.java` — observing the resize point by tracking size at growth.
3. `LinkedHashMapAsLruCache.java` — `accessOrder = true` + `removeEldestEntry` = LRU cache in 15 lines.
4. `TreeMapRangeQueries.java` — `headMap`, `tailMap`, `subMap`, `floorKey`.
5. `MergeAndComputeIfAbsent.java` — modern map APIs that beat the get/put pattern.
6. `MutableKeyBug.java` — mutating a key after insertion makes the entry unreachable.

---

## Try this yourself

1. In `HashMapPutWalkthrough.java`, change the keys to use a class with a constant `hashCode()`. Watch every key land in bucket 0. Then run the same loop with `LinkedHashMap` — the bug masks itself because iteration follows the linked list, not buckets.
2. In `LoadFactorAndResize.java`, vary the initial capacity and observe the resize-trigger size. Compute `capacity * 0.75` and predict it before running.
3. Convert `MergeAndComputeIfAbsent.java` to use the old `containsKey + put` pattern. Diff them — the new APIs are about half the LOC and one hash lookup instead of two.

---

## Self-check

1. Walk through what `HashMap.get("Bob")` does internally. Where does `hashCode` matter? Where does `equals` matter?
2. You expect a map to hold ~10,000 entries. What initial capacity should you give it (default load factor)? Why does that matter?
3. `HashMap` iteration order is unspecified. Give two reasons it might change between two runs of the same program with the same inputs.
