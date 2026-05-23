# 02 — Variables and Memory

A variable is a name for a chunk of data. That's the easy part. The part that catches people out is that Java has **two completely different kinds of variables** — *primitives* and *references* — and they behave differently when you assign them or pass them to methods.

Get this distinction right now and you'll save yourself months of "why did my object change?" confusion later.

---

## The problem this solves

In your program you'll constantly need to remember things: a user's age, an order total, the list of items in a cart, the user object you just loaded from the database. Each of those needs a name, a place to live in memory, and a type so the compiler can stop you doing nonsense like multiplying a name by an order.

A variable is the language's tool for all three at once.

```java
int age = 25;
String userName = "alice";
double balance = 199.50;
```

Each line declares a variable: a type, a name, an initial value. From that point on, you can read the value (`age`) or update it (`age = 26`).

---

## Primitive types

Primitives store the value *directly* in the variable. They have a fixed size and a fixed default value (when used as instance fields).

| Type      | Size      | Range / Example                     | Default |
|-----------|-----------|-------------------------------------|---------|
| `byte`    | 1 byte    | -128 to 127                         | `0`     |
| `short`   | 2 bytes   | -32,768 to 32,767                   | `0`     |
| `int`     | 4 bytes   | ±2.1 billion (the everyday integer) | `0`     |
| `long`    | 8 bytes   | ±9.2 quintillion (`100L`)           | `0L`    |
| `float`   | 4 bytes   | 32-bit floating point (`1.5f`)      | `0.0f`  |
| `double`  | 8 bytes   | 64-bit floating point (the everyday decimal) | `0.0` |
| `char`    | 2 bytes   | One Unicode character (`'A'`)       | `'\0'`  |
| `boolean` | JVM-defined | `true` or `false`                 | `false` |

In practice: use `int` for whole numbers, `long` when you need bigger (timestamps, IDs), `double` for decimals, `boolean` for true/false. The other types matter sometimes but you'll know when.

```java
int a = 5;
int b = a;     // b gets a COPY of the value 5
b = 10;        // change b — a is still 5
```

This is value semantics. Each `int` variable owns its own data. Assignment copies the value.

---

## Reference types

Everything that isn't a primitive is a **reference type** — `String`, arrays, your own classes, collections, everything. A reference variable doesn't hold the object itself; it holds the *memory address* of the object (we usually just say "a reference").

```java
String name = "alice";       // name holds a reference to the String object "alice"
int[] scores = new int[3];   // scores holds a reference to an array on the heap
```

The reference is what gets copied when you do `b = a`. The object stays put.

```java
int[] a = {1, 2, 3};
int[] b = a;                 // b now points to the SAME array as a
b[0] = 99;                   // mutating through b changes the array a sees
System.out.println(a[0]);    // prints 99 — there is only one array
```

If you come from a language where `=` always copies (like C structs), this is the single most important behavior to internalize. If you come from Python or JavaScript, this is the same model — objects are referenced.

---

## Stack and heap, briefly

Java uses two main memory regions. You'll meet them in detail in topic 17 (JVM memory and GC); this is enough for now:

- **Stack** — fast, small, per-thread. Holds method calls, local variables, and *the references themselves*. Cleaned up automatically when a method returns.
- **Heap** — slower, much larger, shared across threads. Holds the actual objects you create with `new`. Cleaned up by the **garbage collector** when nothing references the object anymore.

```java
void doStuff() {
    int x = 5;                   // x lives on the stack, value 5 stored directly
    int[] arr = new int[]{1,2};  // arr lives on the stack, the int[] lives on the heap
}                                // method returns → x and arr disappear from stack
                                 // the heap array is now unreferenced and will be GC'd
```

Picture: a small box (`arr`) on the stack with an arrow pointing into the heap at a bigger box (`{1, 2}`). That arrow is the reference.

---

## Local variables must be initialized before use

The compiler enforces this. It's saving you from a category of bugs that languages like C let through.

```java
int x;
System.out.println(x);    // compile error: variable x might not have been initialized
```

For instance fields (variables inside a class but outside any method), Java gives them the type's default automatically — `0`, `null`, `false`. This is a real difference: `int x;` inside a method is illegal to read; `int x;` as a field starts at `0`.

---

## `var` — let the compiler figure out the type

Since Java 10, you can use `var` for local variables. The compiler infers the type from the right-hand side.

```java
var name = "alice";              // inferred String
var users = new ArrayList<User>();// inferred ArrayList<User>
var count = 5;                   // inferred int
```

`var` is **not** like JavaScript's — the variable is still strongly typed; you just didn't have to spell the type out. Use it when the right-hand side makes the type obvious, especially with long generics. Don't use it when readers will have to puzzle over what the type actually is.

---

## Common pitfalls

- **Treating object assignment as a copy.** `User b = a;` does NOT copy the user; it copies the reference. Both names now point to the same user.
- **Reading a local variable before assigning it.** The compiler catches this, but the error message ("might not have been initialized") confuses beginners. The fix is to give it an initial value at the declaration.
- **Integer overflow.** `int` maxes out at ~2.1 billion. Multiplying two large ints silently wraps around to a negative number. Use `long` when you might be in that range (timestamps in milliseconds are very close).
- **Comparing floats with `==`.** `0.1 + 0.2 != 0.3` in Java just like in JavaScript. Use a tolerance (`Math.abs(a - b) < 1e-9`) or `BigDecimal` for money.

---

## Code examples

1. `PrimitivesAndDefaults.java` — declare every primitive, print their values, show what defaults look like for instance fields.
2. `StackVsHeap.java` — show with code that primitives copy by value but references don't, and explain the diagram in print output.
3. `IntegerOverflow.java` — demonstrate `int` wrapping around at its max, and the fix with `long`.
4. `VarKeyword.java` — `var` for local type inference, with cases where it helps and cases where it hurts readability.

---

## Try this yourself

1. In `StackVsHeap.java`, change the array assignment so `b` is a true copy of `a` (use `a.clone()`). Confirm that mutating `b` no longer affects `a`.
2. Modify `IntegerOverflow.java` to multiply two ten-billion-ish numbers. Watch the wrap. Then change the types to `long` and confirm the math is correct.
3. Write a tiny program that declares an `int` field on a class and an `int` local variable, and try to print both without assigning either. Note which one compiles and which one doesn't.

---

## Self-check

1. `int a = 5; int b = a; b = 10;` — what is `a`? `int[] x = {1}; int[] y = x; y[0] = 9;` — what is `x[0]`? Why are they different?
2. You declare `String s;` inside `main` and `String s;` as a field of a class. Reading the first is a compile error; reading the second prints `null`. Why the inconsistency?
3. What lives on the stack and what lives on the heap, in `String name = "alice";`? Draw the picture in your head.
