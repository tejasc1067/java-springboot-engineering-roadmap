# 03 — Operators and Conditions

Most lines of code are either *computing* a value or *deciding* what to do next. Operators are how you compute. Conditional statements (`if`, `switch`) are how you decide. They're the smallest possible "logic" your program can do.

This topic is short because the syntax is simple. The traps are in the corner cases — short-circuit evaluation, integer division, `==` vs `equals`, switch fall-through. We'll spend time on those.

---

## Arithmetic operators

`+`, `-`, `*`, `/`, `%` — the usual suspects. Two gotchas:

**Integer division throws away the remainder.**

```java
int a = 7 / 2;       // 3, not 3.5
double b = 7 / 2;    // 3.0 — because the division happened in int FIRST, then widened
double c = 7.0 / 2;  // 3.5 — one operand is double, whole expression promoted
```

**`%` is the modulo (remainder) operator.** Very useful: `n % 2 == 0` is "is n even", `i % 10 == 0` is "every 10th item."

```java
System.out.println(17 % 5);   // 2
```

---

## Comparison operators

`==`, `!=`, `<`, `<=`, `>`, `>=` — produce a `boolean`.

`==` on primitives compares values. `==` on objects compares *references* — almost never what you want. For object value equality, use `.equals()`. Topic 07 covers this in detail for strings.

```java
int a = 5, b = 5;
System.out.println(a == b);       // true — value compare

String x = new String("hi");
String y = new String("hi");
System.out.println(x == y);       // false — different objects
System.out.println(x.equals(y));  // true — value compare
```

---

## Logical operators

`&&` (and), `||` (or), `!` (not). They produce booleans and combine boolean expressions.

```java
if (age >= 18 && hasLicense) { ... }
if (isAdmin || isOwner) { ... }
if (!isLocked) { ... }
```

The important detail: `&&` and `||` **short-circuit**. They stop evaluating as soon as the answer is determined.

```java
if (user != null && user.isActive()) { ... }
```

If `user` is null, `&&` doesn't even evaluate `user.isActive()` — which would throw an NPE. This is a defensive idiom you'll write hundreds of times.

The non-short-circuit versions `&` and `|` exist for booleans too, but they always evaluate both sides. You almost never want them. The exception is bitwise operations on integers — `5 & 3` is bitwise AND on the binary representation, which is a different feature with the same symbol.

---

## Operator precedence (the part to memorize)

You don't need a full chart. Memorize the rough order:

1. `!`, unary `-`
2. `*`, `/`, `%`
3. `+`, `-`
4. `<`, `<=`, `>`, `>=`, `==`, `!=`
5. `&&`
6. `||`
7. `=`, `+=`, ...

In other words: arithmetic before comparison, comparison before `&&`, `&&` before `||`. When in doubt, **add parentheses**. A reader thanks you, the compiler doesn't care.

```java
if ((a + b) * 2 > 100 && !done) { ... }   // unambiguous to anyone reading
```

---

## if, else if, else

```java
if (score >= 90) {
    grade = "A";
} else if (score >= 75) {
    grade = "B";
} else {
    grade = "F";
}
```

The conditions are checked top to bottom. The first one that matches wins; the rest are skipped. Use braces even for one-line bodies — it prevents a class of bugs where someone adds a second line and the indentation lies about which statements are inside.

---

## The ternary operator

A short form of `if/else` that produces a value.

```java
String label = (age >= 18) ? "adult" : "minor";
```

Equivalent to:

```java
String label;
if (age >= 18) label = "adult";
else label = "minor";
```

Use ternary for *one* value-producing decision. Don't nest them — `a ? b : c ? d : e` is fine in theory and unreadable in practice.

---

## switch

`switch` is for choosing one of many fixed values. You'll see two forms in modern Java.

**Classic form (the one you'll meet in legacy code):**

```java
String day;
switch (dayNumber) {
    case 1:  day = "Monday";  break;
    case 2:  day = "Tuesday"; break;
    case 3:  day = "Wednesday"; break;
    default: day = "unknown"; break;
}
```

The `break` matters. Without it, control "falls through" into the next case — sometimes deliberate, almost always a bug. The compiler doesn't warn you.

**Switch expression (Java 14+, the form you should use in new code):**

```java
String day = switch (dayNumber) {
    case 1 -> "Monday";
    case 2 -> "Tuesday";
    case 3 -> "Wednesday";
    default -> "unknown";
};
```

No `break`. Cases are arrow-style. The whole `switch` produces a value, which you assign in one go. Much harder to write a fall-through bug.

Switch works on `int`, `String`, enums, and (since Java 21) on pattern-matched types. Not on arbitrary objects.

---

## Vulnerable / broken patterns

**Bug 1 — assignment in a condition.**

```java
if (loggedIn = true) { ... }    // assigns true to loggedIn, expression evaluates to true → always enters
```

Java's compiler catches this for non-boolean assignments (`if (a = 5)` won't compile), but `if (b = true)` does compile because the assignment evaluates to a boolean. Always double-check `==` in conditions.

**Bug 2 — float comparison with `==`.**

```java
double a = 0.1 + 0.2;
if (a == 0.3) { ... }   // false; a is 0.30000000000000004
```

Use a tolerance, or use `BigDecimal` for money.

**Bug 3 — forgetting `break` in classic switch.**

```java
switch (status) {
    case "OK":   handleOk();    // no break — falls into ERROR
    case "ERROR": handleError();
}
```

A real bug surface. The arrow-form switch expression eliminates this class entirely.

---

## Code examples

1. `Operators.java` — arithmetic, comparison, logical, modulo. The integer-division surprise. Short-circuit demonstration.
2. `IfElse.java` — score-to-grade decision, with the "always use braces" rule shown.
3. `Ternary.java` — short-form if/else producing a value.
4. `SwitchClassic.java` — old-style switch with `break`. Includes a deliberate fall-through bug, then the fix.
5. `SwitchExpression.java` — the modern arrow form. Cleaner, same result.

---

## Try this yourself

1. Change `Operators.java`'s integer division to use doubles. Watch the output change. Then make only *one* operand a double — observe that still works (the int gets promoted).
2. In `SwitchClassic.java`, remove a `break`. Run it and see what unexpected output appears. Add it back.
3. Rewrite `IfElse.java`'s grade ladder as a `switch` expression that takes the first digit of the score. (Hint: `score / 10`.)

---

## Self-check

1. Why does `7 / 2` produce `3` in Java but `3.5` in most calculators?
2. Walk through what happens in `user != null && user.getName().isEmpty()` when `user` is null. Why doesn't this throw?
3. A teammate writes `if (status = "OK")`. Will it compile? What's the bug, and what would the fix be?
