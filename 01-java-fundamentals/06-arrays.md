# 06 — Arrays

An array is a fixed-size sequence of values, all of the same type. It's the simplest container in Java and the building block under everything else — `ArrayList`, strings, even how method arguments arrive (`String[] args`).

In real backend code you'll usually reach for `ArrayList` instead (topic 13 covers it). But arrays show up constantly in low-level code, in APIs, and in the questions interviewers love. Worth getting right.

---

## Declaring and creating an array

Two ways.

**Literal — use when you know the values upfront:**

```java
int[] scores = {90, 85, 78, 60, 45};
String[] names = {"alice", "bob", "carol"};
```

**Allocation — use when you'll fill it in later:**

```java
int[] scores = new int[5];        // length 5, all zeros
String[] names = new String[3];   // length 3, all null
scores[0] = 90;
scores[1] = 85;
```

Once an array is created its **length is fixed**. You cannot resize it. If you need to grow, you create a new bigger array and copy — or use `ArrayList`.

---

## Accessing elements

Indices start at **0**. The last valid index is `length - 1`.

```java
int[] scores = {90, 85, 78};
System.out.println(scores[0]);   // 90
System.out.println(scores[2]);   // 78
System.out.println(scores.length); // 3
```

Note `length` is a *field*, not a method — no parentheses. (`String.length()` *is* a method. Yes, Java is inconsistent here. Live with it.)

Going past the end throws `ArrayIndexOutOfBoundsException`:

```java
scores[3];   // throws — last valid index is 2
```

---

## Iterating an array

The two common forms:

```java
int[] scores = {90, 85, 78};

// Classic for — use when you need the index
for (int i = 0; i < scores.length; i++) {
    System.out.println(i + ": " + scores[i]);
}

// Enhanced for — use when you don't
for (int s : scores) {
    System.out.println(s);
}
```

---

## Defaults

A newly allocated array is filled with the type's default value:

- `int[]`, `long[]`, `byte[]`, `short[]` → all zeros
- `double[]`, `float[]` → all `0.0`
- `boolean[]` → all `false`
- `char[]` → all `'\0'` (the null char)
- Reference types (`String[]`, `User[]`, ...) → all `null`

This is why `new String[3]` gives you an array of three nulls, and calling a method on `arr[0]` immediately throws an NPE.

---

## Two-dimensional arrays

Arrays of arrays. Think "table" or "matrix."

```java
int[][] grid = {
    {1, 2, 3},
    {4, 5, 6},
    {7, 8, 9}
};

System.out.println(grid[1][2]);   // 6 — row 1, column 2
System.out.println(grid.length);     // 3 — number of rows
System.out.println(grid[0].length);  // 3 — number of columns in row 0
```

Note: Java's "2D" array is really a 1D array of references to row arrays. The rows don't have to be the same length (a *jagged* array is allowed).

---

## Useful utilities

`java.util.Arrays` has helpers you'll reach for constantly:

```java
import java.util.Arrays;

int[] a = {3, 1, 4, 1, 5, 9, 2, 6};
Arrays.sort(a);                          // in-place sort
System.out.println(Arrays.toString(a));  // [1, 1, 2, 3, 4, 5, 6, 9]

int[] copy = Arrays.copyOf(a, a.length); // independent copy
boolean equal = Arrays.equals(a, copy);  // true — value-equal

int[] zeros = new int[5];
Arrays.fill(zeros, 7);                   // {7,7,7,7,7}
```

`Arrays.toString` is the only reasonable way to print an array — `System.out.println(arr)` prints something like `[I@1540e19d` (the internal class name and hash).

---

## Common pitfalls

- **Off-by-one in the loop bound.** `i <= arr.length` walks past the end. Use `i < arr.length`.
- **Forgetting `length` is a field, not a method.** `arr.length()` → compile error. `arr.length` → correct.
- **Printing the array directly.** `System.out.println(arr)` gives garbage. Use `Arrays.toString(arr)`.
- **Assuming `==` compares contents.** It compares references — `new int[]{1} == new int[]{1}` is `false`. Use `Arrays.equals` for value equality.
- **`String[] = new String[5]` then immediately calling methods on it.** All five slots are null until you assign them. NPE waiting.

---

## When to use an array vs. an `ArrayList`

- **Array** — you know the size upfront, performance matters, you're working at a low level (image pixels, byte buffers, primitive math).
- **ArrayList** — you don't know the final size, you want to add/remove, you want utility methods. This is the everyday default in backend code.

When in doubt, use `ArrayList`. Going from `int[]` to `List<Integer>` later is annoying.

---

## Code examples

1. `ArrayBasics.java` — declaration, indexing, iteration, length. `Arrays.toString` to print.
2. `TwoDimensionalArray.java` — a 3x3 grid, nested loops to read it.
3. `ArrayDefaults.java` — show what each type defaults to in `new T[5]`.
4. `ArrayUtilities.java` — `Arrays.sort`, `copyOf`, `fill`, `equals`. The standard toolbox.

---

## Try this yourself

1. In `ArrayBasics.java`, write a method `int sum(int[] arr)` that returns the total. Call it from `main`. Try empty arrays — make sure it returns 0, not crashes.
2. Convert `TwoDimensionalArray.java` from a regular grid to a jagged one — first row 3 elements, second row 5, third row 2. Iterate it with `grid[i].length` per row.
3. In `ArrayUtilities.java`, demonstrate that `Arrays.copyOf(a, a.length + 3)` produces an array with three extra zeros at the end.

---

## Self-check

1. `arr.length` vs `arr.length()` — which one is correct for an array, and which one is for a `String`? Why is the language inconsistent?
2. `new String[3]` produces an array of what value? What happens if you call `.length()` on the first element?
3. Why does `System.out.println(arr)` print garbage, and what's the fix?
