# Common Mistakes — Java Fundamentals

Real bugs in code that compiles and looks reasonable. Each entry shows the bad pattern, what actually goes wrong, and the fix.

---

## Execution and setup

### 1. Running `java HelloWorld.class`

```bash
java HelloWorld.class       # error: Could not find or load main class HelloWorld.class
```

The `java` command takes a **class name**, not a file name. It appends `.class` itself.

**Fix:**
```bash
java HelloWorld             # without the .class extension
# or, Java 11+:
java HelloWorld.java        # single-file source-code launch (compiles + runs)
```

### 2. Public class name doesn't match the file name

```java
// File: hello.java
public class HelloWorld { ... }      // compile error: class HelloWorld is public, should be in HelloWorld.java
```

**Fix:** rename the file to match the public class, or remove `public` to make it package-private.

---

## Variables and types

### 3. Reading a local variable before assigning

```java
int x;
System.out.println(x);    // compile error: variable x might not have been initialized
```

The compiler refuses. Field declarations get type defaults automatically; locals don't.

**Fix:** initialize at declaration. `int x = 0;`.

### 4. Integer overflow in millisecond math

```java
int seconds = 60 * 60 * 24 * 365;       // fits in int
int millis = seconds * 1000;             // overflows int, wraps to a negative
long correct = (long) seconds * 1000;    // promote BEFORE the multiply
```

The compiler does not warn. Wrap-around is silent.

**Fix:** use `long` for anything in millisecond / nanosecond range, large IDs, money in cents.

### 5. Comparing doubles with `==`

```java
double a = 0.1 + 0.2;
if (a == 0.3) { ... }      // false — a is 0.30000000000000004
```

**Fix:** `Math.abs(a - b) < 1e-9`, or `BigDecimal` for money.

---

## Operators and conditions

### 6. `=` instead of `==` in a boolean condition

```java
boolean loggedIn = false;
if (loggedIn = true) {     // assignment, evaluates to true → always enters
    ...
}
```

For non-boolean assignments the compiler catches it. For booleans it doesn't.

**Fix:** read every `if` condition twice. Linters and IDEs flag this.

### 7. Forgetting `break` in classic switch

```java
switch (status) {
    case "OK":    handleOk();        // no break — falls through
    case "ERROR": handleError();      // both branches run
}
```

**Fix:** add `break;` after each branch — or, better, use the arrow-form `switch` expression, which makes fall-through impossible.

### 8. `if` with no braces, indentation lies

```java
if (score >= 90)
    System.out.println("excellent");
    awardPrize();             // runs UNCONDITIONALLY
```

Indentation isn't syntax. The second line is outside the `if`.

**Fix:** always use braces, even for single-statement bodies.

---

## Strings

### 9. `==` to compare String contents

```java
Scanner sc = new Scanner(System.in);
String input = sc.nextLine();
if (input == "quit") { ... }    // never true
```

Runtime strings aren't pooled. `==` compares references.

**Fix:** `input.equals("quit")` — or safer, `"quit".equals(input)` to avoid NPE on null input.

### 10. Calling `s.trim()` and ignoring the return

```java
String name = "  alice  ";
name.trim();
System.out.println("[" + name + "]");   // still "[  alice  ]"
```

Strings are immutable. Methods return a new String; the original doesn't change.

**Fix:** `name = name.trim();`.

### 11. `+=` in a loop for huge strings

```java
String s = "";
for (int i = 0; i < 100_000; i++) s += i;
```

Each `+=` allocates a new String. The total work is O(n²).

**Fix:** use `StringBuilder` inside loops.

---

## Arrays

### 12. Off-by-one in array iteration

```java
int[] arr = {1, 2, 3};
for (int i = 0; i <= arr.length; i++) {   // <= ; should be <
    System.out.println(arr[i]);            // throws ArrayIndexOutOfBoundsException at i=3
}
```

**Fix:** `i < arr.length`. The last valid index is `length - 1`.

### 13. `System.out.println(arr)` prints garbage

```java
int[] arr = {1, 2, 3};
System.out.println(arr);     // something like "[I@1540e19d"
```

The default `toString` on an array isn't useful.

**Fix:** `System.out.println(Arrays.toString(arr));`.

### 14. `String[] arr = new String[3]; arr[0].length();`

All three slots are null. Calling a method on `arr[0]` throws NPE.

**Fix:** initialize each slot before reading. Or use a `List<String>` with an `ArrayList` whose constructor populates it.

---

## Methods

### 15. Expecting a primitive parameter to be modified

```java
void increment(int x) { x++; }

int n = 5;
increment(n);
// n is still 5
```

Java is pass-by-value. The callee gets a copy.

**Fix:** return the new value: `n = increment(n);`. Or wrap in an object if you really need pass-by-reference semantics.

### 16. Reassigning a reference parameter and expecting the caller to see it

```java
void replace(StringBuilder sb) {
    sb = new StringBuilder("new");    // local rebinding only
}
```

The caller's reference is unchanged.

**Fix:** mutate the passed-in object's state, or return the new object.

---

## Static and this

### 17. Forgetting `this.` in a constructor

```java
class User {
    String name;
    User(String name) {
        name = name;          // assigns parameter to itself; field stays null
    }
}
```

No warning. Silent bug.

**Fix:** `this.name = name;`.

### 18. `this(...)` not as the first statement

```java
User() {
    System.out.println("creating user");
    this("default");          // compile error
}
```

**Fix:** `this(...)` and `super(...)` must come first. Move any logging or work after.

### 19. Calling a non-static method from `main`

```java
public class Demo {
    void greet() { ... }              // instance method
    public static void main(String[] args) {
        greet();                       // compile error
    }
}
```

`main` is static; `greet` isn't. No current object to call `greet` on.

**Fix:** either make `greet` static, or `new Demo().greet()`.

### 20. Static field as global mutable state

```java
class UserService {
    static List<User> all = new ArrayList<>();
}
```

In a long-running server, `all` grows forever. Memory leak.

**Fix:** make it an instance field. If you really do need cross-instance state, bound the size (e.g., LRU cache).

---

## Exceptions

### 21. Empty catch block

```java
try {
    risky();
} catch (Exception e) {
    // silence
}
```

The bug just disappears. Future-you debugs for hours.

**Fix:** at minimum log. Usually rethrow or recover meaningfully.

### 22. Catching `Exception` everywhere

```java
catch (Exception e) { ... }
```

Swallows NPEs, your own custom exceptions, and everything else you didn't mean to handle.

**Fix:** catch the specific type. Reserve broad `catch (Exception e)` for the outermost layer that's supposed to handle anything.

### 23. Losing the cause when wrapping

```java
try {
    doSql();
} catch (SQLException e) {
    throw new BusinessException("save failed");   // ← lost the SQL stack
}
```

**Fix:** `throw new BusinessException("save failed", e);` so the original cause is chained.

### 24. Forgetting that checked exceptions must be handled

```java
String text = Files.readString(path);    // compile error if not in throws or try/catch
```

**Fix:** either catch (`try { ... } catch (IOException e) { ... }`) or declare (`throws IOException` on the method).

---

## Collections

### 25. Modifying a list during for-each iteration

```java
for (String s : list) {
    if (s.startsWith("x")) list.remove(s);    // ConcurrentModificationException
}
```

**Fix:** use an explicit Iterator and `it.remove()`, or `list.removeIf(...)`, or build a new list of survivors.

### 26. `map.get(key)` returns null, you NPE downstream

```java
int age = ages.get("dave");       // ages.get("dave") might be null
                                  // unboxing to int → NullPointerException
```

**Fix:** `getOrDefault`, `containsKey` check, or `Optional.ofNullable(ages.get("dave"))`.

### 27. Using a mutable object as a HashMap key

```java
User u = new User("alice");
map.put(u, "data");
u.setName("bob");                  // hashCode changes
map.get(u);                        // null — can't find it any more
```

**Fix:** keys should be effectively immutable. If using your own class, don't expose setters for fields used in `equals`/`hashCode`.

### 28. Comparing Integer wrappers with `==`

```java
Integer a = 200, b = 200;
if (a == b) { ... }                // false (different objects)
if (a.equals(b)) { ... }           // true
```

Works "by luck" for small numbers (-128 to 127 are cached). Don't rely on it.

**Fix:** use `.equals` for wrapper types, or unbox to `int` first.

### 29. Raw types (no generics)

```java
List names = new ArrayList();      // raw
names.add("alice");
names.add(42);                      // no compile error
String s = (String) names.get(1);  // ClassCastException at runtime
```

**Fix:** always parameterize: `List<String> names = new ArrayList<>();`.

---

## Java 8 and Streams

### 30. `Optional.get()` without checking

```java
Optional<User> u = findByName("dave");
User x = u.get();                  // NoSuchElementException if empty
```

**Fix:** `orElse`, `orElseThrow`, `ifPresent`, or `map(...).orElse(...)`.

### 31. Side effects inside a stream

```java
List<String> results = new ArrayList<>();
list.stream()
    .filter(...)
    .map(...)
    .forEach(results::add);        // side-effect collection — fragile
```

Breaks on parallel streams. Confuses readers.

**Fix:** terminate with `.toList()` or `.collect(...)`.

### 32. `Optional` as a field or parameter

```java
class User {
    Optional<String> middleName;   // don't
}
```

Optional is designed for return types. Fields should be nullable (and documented) or just the value type itself.

---

## Files

### 33. No try-with-resources

```java
BufferedReader r = Files.newBufferedReader(path);
String line = r.readLine();
// ... never close r
```

Leaks an OS file descriptor.

**Fix:** wrap in `try (BufferedReader r = ...) { ... }`.

### 34. `readAllLines` on a large file

```java
List<String> lines = Files.readAllLines(Path.of("huge.log"));   // OOM
```

Loads the whole file into memory.

**Fix:** stream line by line with `BufferedReader.readLine()` or `Files.lines(path)` (closed via try-with-resources).

### 35. Hardcoded absolute paths

```java
Path p = Path.of("C:\\Users\\alice\\data.txt");
```

Doesn't work for anyone else.

**Fix:** relative paths and configurable paths via environment or config file.

---

## Threading

### 36. Calling `run()` instead of `start()`

```java
Thread t = new Thread(task);
t.run();         // runs on the CURRENT thread — no parallelism
```

Looks identical at first glance. Hides the bug.

**Fix:** `t.start();`.

### 37. Race on shared counter

```java
static int count;
Runnable task = () -> {
    for (int i = 0; i < 100_000; i++) count++;
};
// two threads → final count is less than 200,000
```

`count++` is read-modify-write, not atomic.

**Fix:** `AtomicInteger` (simplest), or `synchronized`.

### 38. Swallowing `InterruptedException`

```java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    // ignored
}
```

When a thread is interrupted, you've eaten the cancellation signal. Future-you debugs "why doesn't this thread shut down?".

**Fix:** at minimum `Thread.currentThread().interrupt();` to restore the flag. Usually rethrow or exit the loop.

---

## Memory

### 39. Static collection growing forever

```java
class Service {
    static Map<String, Result> cache = new HashMap<>();
    // we put, we never evict
}
```

In a web server, this is a slow OOM.

**Fix:** bound the cache (LRU, size-cap), use a library like Caffeine, or move to instance scope.

### 40. Calling `System.gc()` "to free memory"

```java
processBigBatch();
System.gc();      // hint, not a command. Often does nothing useful.
```

Just removes a stop-the-world pause from your control. Doesn't fix leaks.

**Fix:** let the JVM run GC when it sees fit. If memory is a problem, find the actual retention path with a heap dump.
