# 07 — Strings

Backend code is mostly moving strings around: parsing JSON, validating user input, building SQL queries, formatting logs. So getting Java's String model right pays off immediately.

The two essentials: **strings are immutable** (every operation returns a new string), and **`==` does not compare values** (use `.equals()`). These two rules generate more interview questions than any other Java topic.

---

## What a String actually is

A `String` is an object that holds a sequence of characters. It's not a primitive — even though it has a literal syntax (`"hello"`) that makes it look like one.

```java
String name = "alice";       // creates a String object, stores a reference in `name`
String empty = "";           // empty string — not null, just zero-length
String space = "   ";        // a string of three spaces
```

You can also create one with `new String(...)`, but you rarely should — the literal form is cleaner and faster.

```java
String a = "alice";
String b = new String("alice");   // forces a brand-new object, almost never what you want
```

---

## Strings are immutable

Once a `String` is created, its contents never change. Every method that "modifies" a string actually **returns a new string**.

```java
String s = "hello";
s.toUpperCase();             // returns "HELLO", but doesn't change s
System.out.println(s);       // still "hello"

s = s.toUpperCase();         // NOW s points to "HELLO"
System.out.println(s);       // "HELLO"
```

Forgetting this is the most common String bug. If a method returns a value, you must capture it.

Why immutable? Three reasons that matter:
- **Thread safety.** Multiple threads can share a String without coordination.
- **Safe hash keys.** Putting a String in a `HashMap` is safe — it can't change and break the hash.
- **Security.** A class can keep a reference to a String passed in without worrying that the caller will mutate it later.

The cost: every concatenation creates a new object. We'll get to `StringBuilder` for the rare case where that matters.

---

## `==` vs `.equals()` — the classic bug

`==` on Strings (or any object) compares **references**: are these two variables pointing at the same object? `.equals()` compares **contents**: do these two strings contain the same characters?

```java
String a = "alice";
String b = "alice";
String c = new String("alice");

System.out.println(a == b);          // true — see "string pool" below
System.out.println(a == c);          // false — different objects
System.out.println(a.equals(c));     // true — same contents
```

**Always use `.equals()` to compare String contents.** Even when `==` happens to work (because of the string pool), it's a fragile coincidence — change one literal to a variable later and the comparison silently flips to `false`.

For null-safety, use `Objects.equals(a, b)` — it returns `false` if either is null instead of throwing.

```java
import java.util.Objects;
Objects.equals(a, b);   // true if both null, false if one is null
```

---

## The string pool

When you write a string literal in source code, Java interns it: all identical literals point to the same underlying object. This is called the **string pool**.

```java
String a = "alice";
String b = "alice";
System.out.println(a == b);    // true — same pooled object
```

That's why `==` looked like it worked above. But:

```java
String c = new String("alice");
System.out.println(a == c);    // false — `new` forces a separate object
```

This is a memory optimization, not a guarantee you can rely on. Always use `.equals()`.

---

## Common String methods

```java
String s = "  Hello, Java  ";

s.length();                  // 15
s.trim();                    // "Hello, Java"  (returns new string)
s.toUpperCase();             // "  HELLO, JAVA  "
s.toLowerCase();             // "  hello, java  "
s.contains("Java");          // true
s.startsWith("  H");         // true
s.endsWith("  ");            // true
s.indexOf("Java");           // 9 (first index of substring, or -1 if not found)
s.replace("Java", "World");  // "  Hello, World  "
s.charAt(2);                 // 'H'
s.substring(7, 11);          // "Java"

"a,b,c".split(",");          // {"a", "b", "c"}
String.join("-", "a", "b");  // "a-b"
String.format("%d points", 5); // "5 points"
```

Every one of those returns a new String. The original `s` is unchanged.

---

## StringBuilder: when concatenation matters

This is the slow way:

```java
String result = "";
for (int i = 0; i < 10000; i++) {
    result = result + i;     // creates a new String each iteration
}
```

Each `+` creates a brand-new String object. Doing it 10,000 times wastes memory and time.

`StringBuilder` is mutable. It holds a growable internal buffer:

```java
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 10000; i++) {
    sb.append(i);            // mutates the existing buffer
}
String result = sb.toString();  // convert to String at the end
```

For one-off concatenations like `"hello " + name + "!"`, the compiler optimizes for you — don't bother with StringBuilder. Only reach for it inside loops or when building up a string from many parts.

There's also `StringBuffer` — same API, but synchronized (thread-safe). Slower. You'll rarely need it; use `StringBuilder` by default.

---

## Vulnerable / broken examples

**Bug 1 — calling a String method and ignoring the return value.**

```java
String name = "  alice  ";
name.trim();                          // returns "alice", but discards it
System.out.println("[" + name + "]"); // still "[  alice  ]"
```

Fix: `name = name.trim();`

**Bug 2 — `==` for content comparison.**

```java
Scanner sc = new Scanner(System.in);
String input = sc.nextLine();
if (input == "quit") { ... }          // never true
```

The user's input is a runtime string, not a pooled literal. `==` will always be false.

Fix: `input.equals("quit")` — or safer, `"quit".equals(input)` (no NPE if input is null).

**Bug 3 — StringBuilder you never converted.**

```java
StringBuilder sb = new StringBuilder();
sb.append("a").append("b");
return sb;        // ← caller wanted a String, got a StringBuilder
```

Fix: `return sb.toString();`

---

## Code examples

1. `StringBasics.java` — declaration, concatenation, length, common methods.
2. `StringEquality.java` — `==` vs `.equals()`, the string pool, what `new String(...)` actually does.
3. `StringImmutability.java` — show that `.toUpperCase()` doesn't change the original; the fix is to reassign.
4. `StringBuilderDemo.java` — the slow loop with `+`, the fast loop with `StringBuilder`. Time both.

---

## Try this yourself

1. In `StringEquality.java`, create a String from user-controlled input (or read from a different source like an array element) and compare to a literal with `==`. Confirm it's false even though contents match.
2. In `StringBuilderDemo.java`, raise the loop bound to 100,000 and watch the time difference grow.
3. Write a method `String censor(String s, String word)` that replaces every occurrence of `word` in `s` with `***`. Use the standard library — no manual loops needed.

---

## Self-check

1. Why does `s.trim()` not change `s`?
2. `String a = "x"; String b = "x"; String c = new String("x");` — for which of `a == b`, `a == c`, `a.equals(c)` is the result `true`? Why?
3. You concatenate 10,000 strings in a loop with `+`. The code works but is slow. Why? What's the fix?
