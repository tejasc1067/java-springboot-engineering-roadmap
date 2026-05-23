# 05 — Methods

A method is a named block of code. You give it inputs, it does work, and it (optionally) gives you back a result. Almost every line of Java you'll write goes inside a method.

The mechanics are easy. The interesting things to get right are: how arguments are passed (Java is **always pass-by-value**, and that surprises people), what overloading really means, and where the responsibility line lives for a "well-named method that does one thing."

---

## Anatomy of a method

```java
public  static  int  add(int a, int b) {     // <-- method signature
    return a + b;                            // <-- method body
}
```

- `public` — access modifier (topic 10). For now, "anyone can call it."
- `static` — belongs to the class, not to a specific object (topic 08).
- `int` — return type. The type of value this method gives back. `void` means "no return value."
- `add` — the method name. Verbs are conventional (`save`, `find`, `calculate`).
- `(int a, int b)` — the parameter list. Each parameter has a type and a name.
- `{ ... }` — the body.

You **call** the method by writing its name and arguments:

```java
int sum = add(3, 4);     // sum = 7
```

---

## Parameters vs. arguments

Two words that get used interchangeably but mean slightly different things:

- **Parameter** — the variable name in the method declaration (`int a`, `int b`).
- **Argument** — the actual value you pass when calling it (`3`, `4`).

`a` is a parameter. `3` is an argument bound to `a` for the duration of that call.

---

## Pass-by-value, even for objects

Java passes everything by value — including object references. This trips people up.

```java
void increment(int x) {
    x = x + 1;
}

int n = 5;
increment(n);
System.out.println(n);    // still 5
```

`increment` received a *copy* of `n`'s value (`5`). It modified its local copy. The caller's `n` is unchanged.

For objects, the value being copied is *the reference*. The reference still points to the same object, so the method *can* modify the object's state — but it cannot make the caller's variable point to a different object.

```java
void appendBang(StringBuilder s) {
    s.append("!");           // mutates the object the caller passed in
    s = new StringBuilder(); // changes only this local copy of the reference
}

StringBuilder sb = new StringBuilder("hi");
appendBang(sb);
System.out.println(sb);     // "hi!" — the object was mutated
```

Read it as: "the method gets a copy of the reference; both copies point at the same object." This is *exactly* the same model as Python and JavaScript.

---

## Return values

A method with a non-void return type must return a value on every path the JVM might take.

```java
int absolute(int n) {
    if (n >= 0) return n;
    return -n;
}
```

Forget one branch and the compiler refuses to compile it ("missing return statement"). That's a feature.

`void` methods don't return anything. You can write `return;` to exit early, but you can't return a value.

---

## Method overloading

You can have multiple methods with the same name in the same class, as long as their **parameter lists** differ. Java picks which one to call based on the argument types at the call site.

```java
int  add(int a, int b)         { return a + b; }
double add(double a, double b) { return a + b; }
String add(String a, String b) { return a + b; }

add(1, 2);         // calls int version
add(1.5, 2.5);     // calls double version
add("hi", " you"); // calls String version
```

The return type alone doesn't count — `int foo()` and `String foo()` can't coexist.

Overloading is resolved at *compile time* based on the declared (compile-time) types of the arguments. This is different from *overriding* (topic 06 in module 02), which is resolved at runtime based on the actual object type.

Use overloading sparingly. Two or three variations is fine. Ten overloads of `process(...)` is a code smell — usually you really wanted distinct names like `processOrder`, `processRefund`.

---

## Good method habits

- **One job per method.** If you have to write "and" in its name (`validateAndSave`), it's probably two methods.
- **Verbs for names.** `calculateTotal`, not `total`. `findUser`, not `user`.
- **Keep it short.** When a method passes ~30 lines, look for natural splits. Long methods hide bugs.
- **Avoid more than ~4 parameters.** Past that, callers can't remember the order. Use a small object or builder.

---

## Common pitfalls

- **Expecting `int` arguments to be modified by the callee.** They can't be — primitives are pass-by-value. Return a new value from the method instead.
- **Reassigning a reference parameter inside the method.** It changes nothing for the caller. If you want a different object back, return it.
- **Overloading where you should have given things different names.** `print(int)` vs `print(String)` is fine; `process(int, int)` vs `process(int, int, boolean)` invites confusion at the call site.
- **Forgetting `return`.** Easy in long methods. The compiler catches the missing return on the final path, but if you `return` only inside an `if`, you'll get the error.

---

## Code examples

1. `Methods.java` — declare and call a method, parameters, return values, `void` vs non-void.
2. `Overloading.java` — three versions of `add`, the compiler picking the right one.
3. `PassByValue.java` — primitive isn't modified by callee; object's state can be mutated, but the reference itself can't be redirected.

---

## Try this yourself

1. In `Overloading.java`, add a `String add(int a, int b)` overload that returns `a + " + " + b + " = " + (a + b)`. What happens at the call site `add(1, 2)`? Why?
2. In `PassByValue.java`, change `appendBang` so it tries to "replace" the caller's StringBuilder with a brand-new one. Confirm the caller still sees the original. Now change it to *return* the new one and reassign at the call site.
3. Write a method `int max(int... values)` that accepts a variable number of ints (varargs) and returns the largest. Call it with `max(1, 5, 3)` and `max(7, 2)`.

---

## Self-check

1. You write `void increment(int x) { x++; }` and call it with `int n = 5; increment(n);`. What is `n`? Why?
2. You write `void clear(List<String> list) { list.clear(); }`. The caller's list ends up empty. Doesn't this contradict pass-by-value?
3. You have two methods: `int foo(int n)` and `String foo(int n)`. Why won't this compile?
