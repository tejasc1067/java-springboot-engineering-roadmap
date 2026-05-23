# 06 — `List` and `ArrayList`

`ArrayList` is the workhorse list of Java. It's the right default 90% of the time. This topic is about understanding *why* — the array-backed implementation, the resize behavior, how operations actually perform, and the things that surprise people (concurrent modification, sublist views, primitive boxing).

If you've used `ArrayList` for years without ever opening `ArrayList.java`, this is the topic that pays that off.

---

## The problem this solves

A `List` interface says "ordered + indexable + duplicates allowed." `ArrayList` is the implementation that gives you those guarantees with the fastest constant factors for the operations people actually do: append, random access by index, and iterate.

The trade-off is that insertion or removal in the middle is O(n), because everything after that index has to shift. If your workload is "append a lot, scan, occasionally lookup by index" — `ArrayList` is unbeatable. If it's "lots of insert at front/middle" — see [topic 07](07-linkedlist-and-vector.md).

---

## How `ArrayList` works under the hood

Internally: a `Object[] elementData` array, plus an `int size` counter.

```text
elementData: ["A", "B", "C", "D", null, null, null, null]
size:        4
capacity:    8     (length of the backing array)
```

- `get(i)` — direct array access, O(1).
- `add(e)` — put at `elementData[size]`, increment size. O(1) if there's room.
- `add(i, e)` — shift `elementData[i..size-1]` right by one, insert at `i`, increment size. O(n − i).
- `remove(i)` — shift `elementData[i+1..size-1]` left by one, decrement size. O(n − i).

### Resize behavior

When `size == capacity` and you `add`, `ArrayList` allocates a larger array (default Java 8+: `oldCapacity + (oldCapacity >> 1)` ≈ 1.5×), copies elements over, and discards the old one. That copy is O(n) — but it happens rarely enough that the *amortized* cost of `add` stays O(1).

Pre-sizing matters: if you know you'll add 100k elements, `new ArrayList<>(100_000)` avoids all the resize copies. Demonstrated in code.

---

## Capacity vs. size

These are different things:

- **size** — the number of elements actually in the list. `list.size()` returns this.
- **capacity** — the length of the backing array. Not exposed in the public API; `trimToSize()` is the only knob.

`new ArrayList<>()` starts with capacity 0 (in modern Java; first add bumps to 10). `new ArrayList<>(100)` starts with capacity 100 but size 0 — that's pre-allocation.

Beginners sometimes do `new ArrayList<>(100)` thinking it creates 100 nulls. It doesn't. You have to `add` to fill it.

---

## `ArrayList` vs. arrays

| | `int[]` | `ArrayList<Integer>` |
|--|--|--|
| Size | fixed at creation | grows dynamically |
| Type safety | primitive, fast | boxed `Integer`, autoboxing tax |
| Access | `arr[i]` | `list.get(i)` (same JIT-compiled speed) |
| Resizing | manual (`Arrays.copyOf`) | automatic |
| API | thin | rich (`contains`, `indexOf`, `subList`, `sort`...) |

Use arrays when you have many millions of primitive values and the boxing cost matters, or for fixed-size data. Use `ArrayList` for almost everything else.

For unboxed primitives + dynamic resize, the standard library has nothing; libraries like Eclipse Collections (`IntList`) or `IntStream.toArray()` are the workarounds.

---

## `subList` is a *view*, not a copy

This trips people up.

```java
List<String> all = new ArrayList<>(List.of("A", "B", "C", "D", "E"));
List<String> middle = all.subList(1, 4);          // view of indices 1..3
middle.set(0, "X");
System.out.println(all);                          // [A, X, C, D, E] ← original mutated
```

It's a window into the same backing array. Mutating either side affects the other. And **modifying the parent list structurally (add/remove) invalidates the sublist view** — next operation on it throws `ConcurrentModificationException`.

If you want an independent copy: `new ArrayList<>(all.subList(1, 4))`.

---

## Autoboxing tax

`ArrayList<Integer>` doesn't actually hold `int` values — it holds `Integer` objects. Adding a primitive `int` triggers autoboxing. Iterating triggers unboxing for arithmetic. For huge lists this matters:

```java
List<Integer> nums = new ArrayList<>();
for (int i = 0; i < 10_000_000; i++) nums.add(i);    // 10M Integer objects allocated

int sum = 0;
for (int n : nums) sum += n;                          // 10M unboxes
```

If you measure and it's a bottleneck, switch to `int[]` or `IntStream`. Don't pre-optimize — for most workloads the boxing is invisible.

---

## Common pitfalls

- **`subList` aliasing.** Mutating the parent corrupts the sublist. If you need independence, copy: `new ArrayList<>(list.subList(...))`.
- **`remove(int)` vs `remove(Object)` overload trap.** `list.remove(2)` removes index 2. `list.remove(Integer.valueOf(2))` removes the element `2`. Easy to get wrong with `List<Integer>`.
- **`list.equals(otherList)` ignoring concrete type.** Two `List`s are equal if same size, same elements, same order — even if one's `ArrayList` and one's `LinkedList`. Usually what you want, occasionally not.
- **Boxing in tight loops.** `nums.get(i) + 1` re-boxes the result. If profiling fingers this, switch to a primitive array.
- **`Arrays.asList(...)` "is" an ArrayList — but a different one.** It's `java.util.Arrays.ArrayList`, fixed-size, backed by the array. `add` and `remove` throw. Use `new ArrayList<>(Arrays.asList(...))` to get a mutable one, or `List.of(...)` for fully immutable.

---

## Code examples

1. `ArrayListInternals.java` — manual visualization of size vs capacity, resize triggers.
2. `PreSizeForPerf.java` — `new ArrayList<>(100_000)` vs. default capacity; time the difference.
3. `RemoveOverloadTrap.java` — `remove(int)` vs `remove(Object)`, the classic bug.
4. `SubListIsAView.java` — mutations alias, structural change throws CME.
5. `AsListIsFixedSize.java` — `Arrays.asList` vs `new ArrayList<>(...)` vs `List.of`.

---

## Try this yourself

1. In `PreSizeForPerf.java`, change the pre-allocation size by 10× and 100×. Note how the resize copies disappear once you start large enough.
2. In `SubListIsAView.java`, after taking the sublist, `list.add("new")` on the parent. Then touch the sublist — observe the CME.
3. In `RemoveOverloadTrap.java`, run with `List<Integer>` and `List<Long>`. Notice which one autoboxes and which one doesn't. Predict which `remove` overload is called in each.

---

## Self-check

1. What's the difference between an `ArrayList`'s size and its capacity? Which one can you query in the standard API?
2. `list.remove(2)` on a `List<Integer>` — which overload is called? Write the call that explicitly removes the element with value `2`.
3. Why is `subList()` cheap (no copy), and what bug does that aliasing cause?
