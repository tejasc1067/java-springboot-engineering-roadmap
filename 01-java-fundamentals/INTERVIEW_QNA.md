# Interview Questions — Java Fundamentals

Real questions you'll be asked in a Java backend interview. Each answer is what a good candidate would say in their own words — not a one-line definition you could quote off a flash card.

If you can answer these aloud without looking, you're prepared for the fundamentals section of any Java interview.

---

## Execution and the JVM

### Q1. What's the difference between JDK, JRE, and JVM?

The JVM is the program that runs Java bytecode — it's what actually executes your code. The JRE bundles the JVM with the standard libraries the bytecode needs at runtime. The JDK bundles the JRE with developer tools (compiler, debugger, doc generator). You install a JDK on a developer machine; you install a JRE (or a JDK) on a server. In modern Java (11+), most distributions just ship JDKs everywhere because the difference in size is small.

### Q2. How does a Java program go from source to running output?

You write `.java` source. `javac` compiles it to `.class` files containing bytecode — a portable format that no CPU runs directly. When you run `java Foo`, the JVM loads `Foo.class`, verifies the bytecode, and either interprets it or JIT-compiles hot paths to native machine code. Output appears as a side effect of executing those instructions.

### Q3. Why is Java called platform-independent?

The compiler produces bytecode, which isn't tied to any operating system or CPU. Different OSes have their own JVMs, but the same `.class` files run on all of them. "Write once, run anywhere" — although in practice it's "write once, debug everywhere" once native concerns sneak in.

### Q4. What's the difference between `java HelloWorld` and `java HelloWorld.java`?

`java HelloWorld` runs the precompiled `HelloWorld.class`. `java HelloWorld.java` (Java 11+) compiles the source and runs it in one step — useful for single-file demos. Behind the scenes, the second one still produces bytecode; you just don't have to manage the `.class` file.

---

## Variables and memory

### Q5. Difference between primitive and reference types?

Primitives (`int`, `double`, `boolean`, ...) store the value directly in the variable. Assignment copies the value. References hold a memory address pointing at an object on the heap. Assignment copies the reference, not the object — so two reference variables can end up pointing at the same object.

### Q6. What lives on the stack vs. the heap?

Stack: method-call frames, local variables, references — fast, per-thread, automatically cleared on method return. Heap: the actual objects you create with `new`, shared across threads, garbage collected. For `String s = "hi"`, the `s` reference is on the stack; the String object is on the heap.

### Q7. `int a = 5; int b = a; b = 10;` — what's `a`? Then `int[] x = {1}; int[] y = x; y[0] = 9;` — what's `x[0]`? Why?

`a` is 5 — primitives copy by value, so `b` got its own 5. `x[0]` is 9 — `y = x` copied the reference; both names point at the same array. The asymmetry catches everyone out at first. The rule: Java is always pass-by-value, but for objects the value is the reference.

### Q8. What's a `NullPointerException`?

You called a method or accessed a field through a reference that was null. The JVM has nothing to dispatch to, so it throws. Most NPEs come from forgetting that a method might return null (e.g., `map.get(key)`) or from never initializing a field.

### Q9. What's autoboxing? Why is `Integer a = 200; Integer b = 200; a == b` false?

Autoboxing automatically converts a primitive to its wrapper class (`int` → `Integer`) when context requires it; unboxing goes the other way. `a == b` is false because `Integer 200` and `Integer 200` are two different objects on the heap — `==` compares references. They happen to be `==` for values from -128 to 127 (JVM caches those), which is a coincidence, not a guarantee. Always use `.equals` for wrappers.

---

## Operators and control flow

### Q10. `==` vs `.equals()`?

`==` on primitives compares values. `==` on objects compares references — same object in memory. `.equals()` compares contents (if the class overrides it; the default `Object.equals` is the same as `==`). For Strings and any class with meaningful value equality, use `.equals`.

### Q11. What's short-circuit evaluation?

`&&` stops evaluating as soon as the answer is determined: if the left side is false, the right side never runs. Same for `||` with true. This makes idioms like `if (user != null && user.isActive())` safe — `user.isActive()` is only called when `user` is not null.

### Q12. What's the difference between `==` and `.equals()` for Strings?

`==` checks "same object in memory." `.equals()` checks "same characters." Java pools string literals, so `String a = "x"; String b = "x"; a == b` happens to be true. But `String c = new String("x"); a == c` is false. Always use `.equals()` for content comparison.

### Q13. What's the gotcha with classic `switch`?

Missing `break` causes fall-through to the next case. The compiler doesn't warn. The modern arrow-form `switch` expression makes this impossible — and produces a value you can assign in one statement.

---

## Methods

### Q14. Java is pass-by-value or pass-by-reference?

Always pass-by-value. For object types, the *value* being copied is the reference. So a method can mutate the object the caller passed in (because both copies point at the same object), but cannot replace the caller's variable with a different object.

### Q15. What's method overloading?

Same method name, different parameter lists, in the same class. The compiler picks which one to call based on the argument types at the call site. Different from overriding (which is the runtime mechanism for subclasses to provide their own version of an inherited method).

### Q16. Why doesn't return type alone count for overloading?

Because the compiler picks the overload based on the call site. `foo(5)` doesn't tell the compiler whether you want `int foo(int)` or `String foo(int)`. The language ducks the ambiguity by refusing overloads that differ only in return type.

### Q17. What's the difference between a parameter and an argument?

Parameter: the variable in the method's declaration. Argument: the value passed at the call site. `int square(int n)` has a parameter `n`; `square(5)` has the argument `5`.

---

## Arrays and Strings

### Q18. `arr.length` vs `arr.length()` — why are they different?

For arrays, `length` is a field (no parens). For Strings and other classes, `length()` is a method (parens). Java is inconsistent here for historical reasons. You'll trip on it once, then internalize it.

### Q19. Why are Strings immutable in Java?

Three big reasons: thread safety (multiple threads can share a String without locks), safe hash keys (a String in a HashMap can't have its hashCode change underneath you), and security (a class can keep a reference to a passed-in String without worrying it'll be mutated later). The cost is that every "modification" creates a new object — manageable in normal use, painful in tight loops where you want StringBuilder.

### Q20. Difference between String, StringBuilder, StringBuffer?

String is immutable. StringBuilder is mutable, not thread-safe, fast — your default for building strings up. StringBuffer is mutable AND synchronized for thread safety — slower, rarely needed today.

### Q21. Why does `s += " world"` in a loop perform badly?

Each `+=` creates a new String object, copies the accumulated content, and reassigns. For n iterations, total work is O(n²). StringBuilder uses a growable buffer — O(n) total. Use StringBuilder inside loops; the compiler handles single-line concatenations.

---

## Static and this

### Q22. Why must `main` be `static`?

The JVM has to call `main` to start your program. At that point there's no object to call it on. `static` means "callable on the class, no instance required" — so the JVM can just call `YourClass.main(args)` directly.

### Q23. Can a static method access a non-static field directly?

No. Non-static fields belong to objects. A static method has no current object, so no "which one." The compiler refuses ("non-static field cannot be referenced from a static context"). You can access them by being passed an instance explicitly.

### Q24. What's the difference between a static field and an instance field?

Static field: one shared copy per class. Read/write by anyone, no instance needed. Instance field: each object has its own copy. Changing one object's field doesn't affect another.

### Q25. When does a static block run?

Once, the first time the class is loaded by the JVM. Useful for one-time initialization that's too complex for a field initializer. Runs in source order. Rare in modern code.

### Q26. Why is `this(...)` required to be the first statement in a constructor?

So the chained constructor runs to completion before this one does any work. Java's rule prevents you from running code that depends on the chain having already initialized fields.

### Q27. Why can't `this` appear in a static method?

There's no current object — the method belongs to the class. `this` has nothing to refer to.

---

## Access modifiers and packages

### Q28. What are the four access levels in Java?

`private`: same class only. (No keyword / default): same package only ("package-private"). `protected`: same package plus subclasses (even in other packages). `public`: anywhere. Each is strictly broader than the one before it.

### Q29. Why prefer private fields with getters/setters over public fields?

A `public` field is a permanent escape hatch — no class-level rules can be enforced on it. Once you've shipped `public String email`, you can never add validation without breaking every caller that assigns to it. Private fields with methods cost a few extra lines and preserve every option you might want later: validation, logging, lazy computation, changing the underlying storage.

### Q30. What's the difference between `protected` and (no modifier / package-private)?

Both are visible to other classes in the same package. `protected` additionally lets subclasses in *other* packages see the member. So protected is broader. People often confuse them because no-modifier is unfamiliar; "package-private" is the unambiguous name.

### Q31. What's a package, and why does the folder structure have to match the package name?

A package is a namespace and a unit of visibility. The Java rule that `package com.acme.shop;` must live in `src/com/acme/shop/` lets the compiler and classloader find classes by name without a manifest. It's a convention enforced for tooling simplicity.

---

## Exceptions

### Q32. Checked vs unchecked exceptions?

Checked exceptions (subclasses of `Exception` but not `RuntimeException`) must be either caught or declared in a `throws` clause — the compiler enforces it. Examples: `IOException`, `SQLException`. Unchecked exceptions (subclasses of `RuntimeException`) don't require either. Examples: `NullPointerException`, `IllegalArgumentException`. Modern code leans heavily on unchecked.

### Q33. What's wrong with `catch (Exception e) { }`?

The empty catch silently eats the exception. No log, no stack trace, no clue. Hours of "why doesn't this work" later, someone finds the empty catch. At minimum log and rethrow; ideally catch only the specific types you can actually handle.

### Q34. `throw` vs `throws`?

`throw` is a statement that raises an exception: `throw new IllegalArgumentException(...)`. `throws` is a clause on a method signature declaring which checked exceptions might propagate: `void load() throws IOException`. The compiler uses `throws` to enforce "callers must handle this."

### Q35. What does `finally` do, and when does it run?

`finally` runs whether the try block completed normally, threw an exception (caught or not), or returned. Used for cleanup — though `try-with-resources` covers the most common case (closeables) better.

### Q36. When would you create a custom exception?

When the standard ones don't capture your domain. `InsufficientFundsException` reads better than `IllegalStateException`, and lets callers catch precisely the case they care about. Extend `RuntimeException` for unchecked (the modern default), provide constructors that take a message and optionally a cause.

### Q37. What's `try-with-resources`?

Syntax that auto-closes any resource implementing `AutoCloseable` (files, sockets, DB connections) when the block ends — normal completion, exception, or return. Cleaner and safer than `try/finally { close(); }`. Always use it for closeable resources.

---

## Collections

### Q38. ArrayList vs LinkedList?

ArrayList is backed by a resizable array — O(1) random access, O(n) worst-case insertion in the middle. LinkedList is a doubly-linked node chain — O(1) add/remove at ends, O(n) random access. ArrayList is the everyday default; LinkedList shows up mostly in textbooks. For queue/stack usage, `ArrayDeque` is better than either.

### Q39. List vs Set?

List preserves order and allows duplicates (`ArrayList`, `LinkedList`). Set enforces uniqueness with no order guarantee by default (`HashSet`), or insertion order (`LinkedHashSet`), or sorted (`TreeSet`).

### Q40. How does HashMap work?

Internally an array of buckets. To put a key, it hashes the key, picks a bucket, and stores the entry there. Lookups hash the key, find the bucket, walk the (usually short) list of entries in that bucket comparing with `equals`. Average O(1). Failure mode: bad `hashCode` implementations bunch entries into one bucket and degrade to O(n).

### Q41. What causes ConcurrentModificationException?

Modifying a collection from outside while iterating it. The iterator tracks a modification count; if it changes between two iterator calls, the next call throws. Fix: use the iterator's own `remove()`, use `removeIf`, or build a new collection.

### Q42. What's the difference between fail-fast and fail-safe iterators?

Fail-fast iterators throw ConcurrentModificationException if the underlying collection is modified during iteration (the default for `ArrayList`, `HashMap`, ...). Fail-safe iterators work on a snapshot of the collection — no exception, but you may see stale data (`ConcurrentHashMap`'s iterators, `CopyOnWriteArrayList`).

### Q43. Why do collections use wrapper classes (Integer) instead of primitives (int)?

Generics in Java only work with reference types. The JVM converts between `int` and `Integer` automatically (autoboxing), so the inconvenience is mostly invisible — except for the `==` trap on wrappers and the small per-element overhead.

### Q44. When would you reach for HashMap vs TreeMap?

HashMap when you just need fast lookup — O(1) average, no order. TreeMap when you need entries sorted by key, or operations like `firstKey`, `lastKey`, `headMap` (entries with key < X). TreeMap is O(log n).

### Q45. What's the contract between `equals` and `hashCode`?

If `a.equals(b)` then `a.hashCode() == b.hashCode()` — required. The reverse doesn't need to hold (hash collisions are allowed). Break this and hash-based collections (HashMap, HashSet) silently misbehave: you `put` an entry, can't find it again.

---

## Java 8 features

### Q46. What's a functional interface?

An interface with exactly one abstract method. Examples: `Runnable`, `Comparator`, `Function`. Lambdas implement functional interfaces — the compiler infers which interface from context. The `@FunctionalInterface` annotation makes the intent explicit and lets the compiler stop you from accidentally adding a second abstract method.

### Q47. What's a lambda expression?

A short syntax for implementing a functional interface in place. `s -> s.length()` is shorthand for `new Function<String,Integer>() { public Integer apply(String s) { return s.length(); } }`. Same bytecode, much less typing.

### Q48. What's a method reference?

An even shorter syntax for a lambda that just calls an existing method. `String::length` is `s -> s.length()`. Four forms: static method (`Integer::parseInt`), bound instance method (`System.out::println`), unbound instance method (`String::length`), constructor (`ArrayList::new`).

### Q49. What's the Stream API?

A pipeline of operations on a sequence of values. Start from a source, chain intermediate operations (`filter`, `map`, `sorted`), end with a terminal operation (`toList`, `count`, `reduce`). Streams are lazy — nothing runs until you ask for the result.

### Q50. What's the difference between `map` and `filter`?

`filter` keeps only elements matching a predicate — same type, fewer elements. `map` transforms each element into something else — possibly different type, same count.

### Q51. What's Optional, and when should you use it?

`Optional<T>` wraps "a T or nothing." It's for return types of methods that might not produce a result — `Optional<User> findByEmail(...)` is clearer than returning null and hoping the caller checks. Don't use it as a field, parameter, or in a collection — those uses fight the API.

### Q52. Why is `Optional.get()` dangerous?

It throws `NoSuchElementException` if the Optional is empty. Using `get()` without checking effectively recreates the null-NPE problem you used Optional to avoid. Use `orElse`, `orElseThrow`, `ifPresent`, or `map(...).orElse(...)` instead.

---

## Files

### Q53. Why is `try-with-resources` better than `finally`?

The compiler-generated cleanup correctly handles edge cases: close() throwing during exception unwind doesn't lose the original exception, multiple resources close in the right order, you can't forget. The `try/finally { close(); }` pattern was a common source of bugs.

### Q54. Difference between FileReader and BufferedReader?

FileReader reads one character at a time — slow due to lots of small system calls. BufferedReader wraps a Reader, reads chunks into an in-memory buffer, serves them quickly. For any non-trivial file, you want BufferedReader.

### Q55. Why avoid hardcoded file paths?

`C:\\Users\\alice\\file.txt` doesn't exist on a teammate's Mac, a CI runner, or a Linux server. Use relative paths (so deployment scripts pick the base), environment variables, or config files. `Path.of("data", "x.txt")` also handles path separators portably.

---

## Multithreading

### Q56. What's the difference between extending Thread and implementing Runnable?

Both work. Implementing Runnable is preferred because Java allows only single inheritance — `extends Thread` burns the slot. Also, Runnable separates "what to do" (the task) from "how to run it" (Thread vs. ExecutorService vs. virtual thread), which is the modern way.

### Q57. What's a race condition?

When the correctness of a program depends on the unpredictable timing of multiple threads. Two threads both run `count++` (read-modify-write); their reads and writes interleave; some updates are lost. The result is non-deterministic and hard to reproduce.

### Q58. What does `synchronized` do?

Acquires a lock before entering, releases on exit. Only one thread can hold a given lock at a time. A synchronized instance method locks on `this`; a synchronized static method locks on the class object. Synchronization makes shared state safe at the cost of contention and some risk of deadlock.

### Q59. Difference between `Thread.sleep()` and `Thread.join()`?

`sleep` pauses the current thread for a duration. `join` makes the current thread wait until another thread finishes. Different purposes — don't `sleep` for coordination; use `join` (or a higher-level primitive).

### Q60. Why is calling `t.run()` instead of `t.start()` a bug?

`run()` executes the task body synchronously on the calling thread — no new thread is created. The program runs but does it sequentially, silently defeating the point.

### Q61. Why is `AtomicInteger.incrementAndGet()` preferable to `synchronized` for a counter?

Atomic types use CPU compare-and-swap instructions — lock-free, less overhead, no lock contention. For a single shared counter, they're the simpler and faster choice. For more complex atomic operations across multiple fields, you still need synchronized or a higher-level mechanism.

---

## JVM memory and GC

### Q62. Stack vs heap?

Stack: method frames, locals, references; per-thread; fast; auto-cleaned on method return. Heap: objects; shared across threads; garbage-collected; usually much larger than stack. The reference variable lives on the stack and points into the heap.

### Q63. What is garbage collection?

The JVM automatically reclaims heap memory for objects no longer reachable from any "root" (local variables on a live stack frame, static fields, etc.). You don't call `free()` like in C. The GC chooses when to run; you can't force it (`System.gc()` is a hint).

### Q64. When does an object become eligible for GC?

When there's no longer any chain of references from a root to the object. Setting a reference to null *can* make an object eligible if it was the last reference; setting it to null when other references still exist doesn't.

### Q65. Can you have memory leaks in Java despite GC?

Yes. A "leak" in Java is an unintentionally-retained reference — code keeps pointing at something you no longer need, so the GC can't reclaim it. Classic patterns: static collections that grow forever, listeners never unregistered, caches without eviction.

### Q66. What's `OutOfMemoryError`? How do you debug it?

The JVM couldn't allocate more memory. Most often "Java heap space" — the heap is full. Other variants: "GC overhead limit exceeded" (GC is spinning, freeing almost nothing — usually a leak), "Metaspace" (too many classes loaded). To debug: enable a heap dump on OOM (`-XX:+HeapDumpOnOutOfMemoryError`), analyze with a tool like Eclipse MAT or VisualVM, look for the dominant retainer.

### Q67. What's the difference between StackOverflowError and OutOfMemoryError?

StackOverflowError means a thread's stack ran out — typically unbounded recursion. OutOfMemoryError means the heap (or metaspace) is exhausted — leaks, too-small heap, allocation pressure. Stack and heap have separate limits, separate failure modes, separate fixes.

### Q68. Why is heavy use of static dangerous for memory?

Static fields are roots for GC — anything reachable from them is never collected. A static `Map` you keep adding to grows for the entire program lifetime. In a long-running server, this is the classic "slow OOM" pattern.
