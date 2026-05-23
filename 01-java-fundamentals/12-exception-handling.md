# 12 — Exception Handling

Things go wrong. Files don't exist. Network calls time out. User input is garbage. Exception handling is how Java code reacts when "the normal path" can't continue.

The mechanics are simple — `try`, `catch`, `finally`, `throw`, `throws`. The hard part is judgment: when to catch, when to let the exception bubble up, when to wrap and rethrow, when to log. This topic focuses on the mechanics and the most common judgment calls.

---

## What an exception is

When something goes wrong, Java *throws* an **exception object** — an instance of `Throwable` or one of its subclasses. Throwing an exception interrupts normal control flow: instead of running the next statement, the JVM walks up the call stack looking for code that can handle the exception (a `catch` block of the right type).

If nothing catches it, the JVM prints a stack trace and the program exits.

```java
int[] arr = {1, 2, 3};
System.out.println(arr[5]);     // throws ArrayIndexOutOfBoundsException

// Output if not caught:
//   Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException:
//     Index 5 out of bounds for length 3
//     at Demo.main(Demo.java:3)
```

That stack trace is gold — it tells you *what* went wrong and *where*. Read it carefully when debugging.

---

## try / catch

Wrap risky code in `try`. Provide a `catch` for each kind of exception you can handle.

```java
try {
    int[] arr = {1, 2, 3};
    System.out.println(arr[5]);
} catch (ArrayIndexOutOfBoundsException e) {
    System.out.println("bad index: " + e.getMessage());
}
```

You can have multiple catches for different exception types. They're tried in order:

```java
try {
    process();
} catch (FileNotFoundException e) {
    System.out.println("file missing: " + e.getMessage());
} catch (IOException e) {
    System.out.println("I/O failure: " + e.getMessage());
}
```

Order matters: a `catch` for a parent class catches all its subclasses. Put the more specific ones first.

You can also combine multiple types in one catch (Java 7+):

```java
try {
    ...
} catch (IOException | SQLException e) {
    log("operation failed", e);
}
```

---

## finally

Code in `finally` runs **whether or not** the `try` block threw, and whether or not a `catch` ran. Useful for cleanup that absolutely has to happen.

```java
Connection conn = openConnection();
try {
    doStuff(conn);
} finally {
    conn.close();   // runs even if doStuff threw
}
```

In modern code, the better pattern for resources is **try-with-resources** (covered in topic 15) which closes things automatically. `finally` is still useful for non-`AutoCloseable` cleanup.

---

## Checked vs. unchecked exceptions

This is the one Java distinction that catches people out.

- **Checked exceptions** (subclasses of `Exception` but not `RuntimeException`) — the compiler forces you to either catch them or declare them with `throws`. Examples: `IOException`, `SQLException`.

- **Unchecked exceptions** (subclasses of `RuntimeException`) — no compile-time enforcement. Examples: `NullPointerException`, `IllegalArgumentException`, `ArrayIndexOutOfBoundsException`, most things you'll encounter day-to-day.

```text
Throwable
 ├── Error                       ← serious JVM problems; don't catch
 │    └── OutOfMemoryError
 └── Exception
      ├── IOException             ← CHECKED
      ├── SQLException            ← CHECKED
      └── RuntimeException        ← UNCHECKED (and all its subclasses)
           ├── NullPointerException
           ├── IllegalArgumentException
           └── ArrayIndexOutOfBoundsException
```

If a method might throw a checked exception, you must either catch it or declare it:

```java
// Either catch it...
void load() {
    try {
        readFile();
    } catch (IOException e) {
        // handle it
    }
}

// ...or declare you might propagate it.
void load() throws IOException {
    readFile();
}
```

Modern frameworks (Spring) wrap checked exceptions into runtime exceptions to keep method signatures clean. In your own code, the convention has shifted toward unchecked exceptions for most domain errors.

---

## throw

You throw an exception when your code detects a state it can't handle.

```java
void setAge(int age) {
    if (age < 0) {
        throw new IllegalArgumentException("age cannot be negative: " + age);
    }
    this.age = age;
}
```

The exception bubbles up until something catches it. Throw what's most specific — `IllegalArgumentException` is better than `RuntimeException`, which is better than `Exception`.

---

## throws (in method signatures)

`throws` declares "this method may throw these exception types, so callers had better deal with it." It only matters for *checked* exceptions — unchecked ones don't need to be declared.

```java
public String loadConfig(String path) throws IOException {
    return Files.readString(Path.of(path));    // Files.readString throws IOException
}
```

The caller now sees the warning and must catch or declare in turn.

---

## Custom exceptions

When the existing types don't capture your domain, define your own.

```java
class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

void withdraw(double amount) {
    if (amount > balance) {
        throw new InsufficientFundsException("requested " + amount + ", balance " + balance);
    }
}
```

Extend `RuntimeException` for unchecked (the common modern choice), or `Exception` for checked. Add a constructor that takes a message; usually one taking a `(String, Throwable)` too, so you can wrap a lower-level cause.

---

## Common pitfalls

- **Empty catch blocks.** `catch (Exception e) { }` — the bug just disappears. No log, no rethrow. Hours of debugging later, you find this. Always at least log.
- **Catching `Exception` (or `Throwable`).** Too broad — you'll swallow things you didn't mean to handle (NPEs, your own custom exceptions). Catch the specific types.
- **Using exceptions for control flow.** `try { Integer.parseInt(s); return true; } catch (...) { return false; }` works, but is slow and reads poorly. Prefer a validate-then-parse pattern when you can.
- **Losing the cause when rewrapping.** `throw new BusinessException("save failed");` — you lost the SQLException stack. Use `throw new BusinessException("save failed", e);` to preserve the chain.
- **`throws Exception` everywhere.** Catch-all in method signatures defeats the point. Declare only what you actually throw.

---

## Code examples

1. `TryCatch.java` — basic catching, multi-catch, exception hierarchy.
2. `FinallyAlwaysRuns.java` — `finally` runs whether or not the `try` succeeded.
3. `ThrowAndThrows.java` — throwing an exception from a method; declaring `throws` on the method signature.
4. `CustomException.java` — defining a domain-specific exception with a useful message and cause.
5. `EmptyCatchBroken.java` paired with `LogAndRethrowProper.java` — the silent-swallow antipattern and the proper fix.

---

## Try this yourself

1. In `TryCatch.java`, reorder the catch blocks so the parent class comes first. Read the compile error (Java refuses unreachable catches).
2. In `ThrowAndThrows.java`, remove the `throws IOException` from the method signature. Read the compile error. Now wrap the inner call in a try/catch instead — confirm both fixes work.
3. In `CustomException.java`, throw your custom exception with a cause (`throw new InsufficientFundsException("...", originalException)`). Print the stack trace — you should see both exceptions chained.

---

## Self-check

1. What's the difference between a checked and unchecked exception? Which one does the compiler force you to handle?
2. `catch (Exception e) { }` is the empty-catch antipattern. Why is it so dangerous? What's a minimum better version?
3. A method internally calls `Files.readString(...)`, which throws `IOException`. You don't want to handle it locally. What are your two options? Give the code for each.
