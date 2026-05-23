# 04 — Loops

A loop runs the same block of code multiple times. That's it. Once you've seen the four forms (`for`, `while`, `do-while`, enhanced-`for`), the rest is just knowing which to pick.

The interesting part isn't the syntax — it's the bugs. Off-by-one errors, infinite loops, modifying a collection while iterating it. We'll spend time on those.

---

## The four loop forms

**Classic `for`** — when you know how many iterations or want to count.

```java
for (int i = 0; i < 5; i++) {
    System.out.println(i);    // 0, 1, 2, 3, 4
}
```

Three parts in the header, separated by `;`:
1. *Initialization* — runs once, before the first iteration. `int i = 0` declares and initializes the counter.
2. *Condition* — checked before each iteration. If false, the loop ends. `i < 5` means "keep going while i is less than 5."
3. *Update* — runs after each iteration. `i++` increments by 1.

**`while`** — when you don't know how many iterations, only the condition.

```java
int n = 100;
while (n > 1) {
    System.out.println(n);
    n = n / 2;       // halve until we hit 1
}
```

**`do-while`** — like `while` but checks the condition *after* running the body. Guarantees at least one execution.

```java
String input;
do {
    input = prompt("Enter quit to exit:");
} while (!input.equals("quit"));
```

Rare in practice — most reads-then-checks are clearer as plain `while` with a flag.

**Enhanced `for`** (also called for-each) — iterate over a collection or array without managing an index.

```java
int[] scores = {90, 75, 60};
for (int s : scores) {
    System.out.println(s);
}
```

Read it as "for each `s` in `scores`." Cleaner than the classic form when you don't need the index.

---

## When to use which

- Counting from 0 to N: classic `for`.
- "Until some condition becomes true": `while`.
- Iterating every element of a collection or array: enhanced `for`.
- Doing it at least once, then checking: `do-while`.

When in doubt, use enhanced `for` for iteration and classic `for` for counting.

---

## break and continue

`break` exits the loop immediately. `continue` skips the rest of *this* iteration and jumps to the next one.

```java
for (int i = 0; i < 10; i++) {
    if (i == 5) break;           // stop entirely at 5
    if (i % 2 == 0) continue;    // skip even numbers
    System.out.println(i);       // prints 1, 3
}
```

Both are fine in moderation. A function with `break` deep in nested loops is usually a sign you should extract it into a method and `return` instead — cleaner.

---

## Broken / buggy patterns

**Bug 1 — infinite loop.**

```java
int i = 0;
while (i < 5) {
    System.out.println(i);
    // forgot i++ — the condition never becomes false
}
```

Classic for-while bug. The terminal prints forever. `Ctrl+C` to kill.

**Bug 2 — off-by-one.**

```java
int[] arr = {1, 2, 3};
for (int i = 0; i <= arr.length; i++) {   // <= instead of <
    System.out.println(arr[i]);            // throws on i=3
}
```

`<=` should be `<` when iterating zero-based indices. `arr.length` is the *count*, not the *last index*.

**Bug 3 — modifying a collection during enhanced-`for`.**

```java
List<String> names = new ArrayList<>(List.of("a", "b", "c"));
for (String name : names) {
    if (name.equals("b")) names.remove(name);   // ConcurrentModificationException
}
```

The enhanced `for` uses an iterator under the hood. Mutating the list from outside breaks its tracking. The fix is to use the iterator's own `remove` or a different approach (a removeIf, or a filtered new list). Topic 13 (collections) covers this.

**Bug 4 — looping over the wrong variable.**

```java
for (int i = 0; i < 10; i++) {
    for (int j = 0; j < 10; i++) {   // typo: should be j++
        ...                          // outer i increments too much, inner never terminates predictably
    }
}
```

Always proofread the update expression in nested loops. Pick distinct letters when they're related (i, j, k) and don't reuse them.

---

## Code examples

1. `ForLoop.java` — classic counting `for`, with the off-by-one bug shown and fixed.
2. `WhileLoop.java` — `while` until condition. Includes a deliberate infinite-loop bug, with a safety counter.
3. `DoWhile.java` — minimal `do-while` example. When to reach for it (and when not to).
4. `EnhancedFor.java` — the for-each form on an array and on a list.
5. `BreakAndContinue.java` — both flow-control keywords in one program.
6. `ConcurrentModificationBroken.java` paired with `ConcurrentModificationFixed.java` — demonstrate the runtime error, then the fix using an iterator.

---

## Try this yourself

1. Run `ConcurrentModificationBroken.java` and watch the exception. Then run `ConcurrentModificationFixed.java`. Make sure you can articulate why the broken version fails.
2. Modify `ForLoop.java` to print only the odd numbers between 1 and 50. Try it three ways: with a `for` step of `i += 2`, with an `if` and `continue`, and with `if (i % 2 != 0)`.
3. In `BreakAndContinue.java`, add an outer loop. Try using `break` to exit only the inner loop, and then using `break label;` syntax to exit both.

---

## Self-check

1. Why does `for (int i = 0; i <= arr.length; i++)` blow up but `for (int i = 0; i < arr.length; i++)` work?
2. What's the difference between `break` and `continue`? When would you reach for each?
3. You iterate a `List<User>` with for-each and try to `list.remove(user)` inside. Why does the JVM throw, and what should you use instead?
