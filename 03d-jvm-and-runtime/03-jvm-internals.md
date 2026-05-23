# 03 — JVM Internals

The JVM is the runtime that turns your `.class` files into running code. It's a virtual machine in two ways: it executes a portable bytecode instead of x86/ARM directly, and it manages its own memory and threads above the OS. This topic covers the parts a backend engineer is expected to understand:

- The compile → load → execute pipeline.
- Class loading: phases, the three standard loaders, the delegation model.
- Bytecode: what your Java compiles to and how to read it.
- Execution: interpreter, JIT compilation, HotSpot tiers.
- Runtime areas inside the JVM process.

Memory specifics live in topic 01. GC lives in topic 02. JVM tuning for production lives in topic 10.

---

## Compile to bytecode

```
Foo.java  --javac-->  Foo.class    (bytecode for the JVM)
```

`.class` is portable. It does *not* contain x86 or ARM instructions. It contains JVM bytecode — a stack-based instruction set defined in the JVM specification. Any compliant JVM on any OS / CPU can load that file and run it. This is why you write Java once and run it on a developer's Mac, a CI Linux box, and a production container without recompiling.

You can read bytecode with `javap -c YourClass.class`. We do this in the code examples.

---

## Class loading

A class doesn't exist in the JVM until it's loaded. Loading happens lazily — the JVM loads a class the first time something needs it (a `new`, a `static` access, a method invocation). Then **linking**, then **initialization**.

### The three phases

| Phase | What happens |
|-------|-------------|
| **Loading** | Read the `.class` bytes, build a `Class<?>` object in memory. |
| **Linking** | Three sub-steps: verify (is the bytecode legal?), prepare (allocate static fields with defaults like 0, null), resolve (resolve symbolic references to other classes — may load them recursively). |
| **Initialization** | Run the `<clinit>` method: static field initializers and `static {}` blocks, in source order. |

`<clinit>` runs exactly once per class. The JVM holds a lock during it — two threads triggering initialization of the same class will serialize.

### The classloader hierarchy

Three standard classloaders (Java 9+):

| Loader | Loads | Source |
|--------|-------|--------|
| **Bootstrap** | core JDK (`java.lang.*`, `java.util.*`, etc.) | written in C/C++ inside the JVM |
| **Platform** | JDK modules outside `java.base` (`java.sql`, etc.) | Java class |
| **Application** (System) | your app code from the `--class-path` / `--module-path` | Java class |

Plus any custom classloaders that web servers, OSGi, or Spring Boot fat-jars create.

### Delegation model

When a loader is asked to load a class, it **delegates to its parent first**. Only if the parent can't find it does the child try. This is why you can't ship your own `java.lang.String` — the bootstrap loader gets asked first and always finds the real one.

```
                  Bootstrap   (java.lang.String, ...)
                    /\
                    | parent
                    |
                  Platform    (java.sql.*, ...)
                    /\
                    | parent
                    |
                  Application (your code)
```

A `ClassNotFoundException` means: every loader from the requester up to the bootstrap was asked, none found it.

A `LinkageError` (specifically `NoClassDefFoundError`) means: it was found at compile time, but not at runtime — usually a dependency version mismatch.

---

## Bytecode in one minute

JVM bytecode is **stack-based**. Each method has an operand stack and a local-variable array. Instructions push/pop values to/from the stack.

```java
int x = 1 + 2;
```

compiles to roughly:

```
iconst_1        // push int 1 onto stack
iconst_2        // push int 2
iadd            // pop two ints, push their sum
istore_1        // pop, store into local slot 1 (x)
```

You don't need to read bytecode often, but knowing it exists — and that `javap -c` will show you — is useful when you're debugging compiler weirdness or chasing JIT behavior.

---

## Execution: interpreter + JIT

The JVM starts by **interpreting** bytecode — running it instruction by instruction. Interpretation is portable and starts fast but is slow per instruction.

For **hot** code (loops, frequently called methods), HotSpot's JIT compilers compile bytecode to native machine code at runtime. Subsequent calls run the native version directly.

### HotSpot tiers

HotSpot has multiple JIT tiers:

| Tier | What |
|------|------|
| 0 | Interpreter |
| 1–3 | C1 ("client") — fast compile, light optimization |
| 4 | C2 ("server") — slow compile, heavy optimization (inlining, escape analysis, vectorization) |

By default the JVM uses **tiered compilation**: methods start interpreted, get C1-compiled when they warm up, get C2-compiled when they're truly hot. Methods that suddenly behave differently can be **deoptimized** back to interpreter and re-profiled.

This is why microbenchmarks need warm-up: the first 10,000 calls measure the interpreter, not the C2-compiled version your production traffic actually runs.

### Profiling and inlining

C2 keeps statistics on each call site: which target classes are seen, which branches are taken. Based on those it inlines aggressively. A virtual call to a method that's always been on the same class becomes a direct call. If a new subclass shows up later, the JVM throws out the optimization and recompiles.

You can see this happening with `-XX:+PrintCompilation`.

---

## Runtime memory areas

Recap from topic 01, this time framed as JVM internals:

| Area | Per-thread or shared | What |
|------|---------------------|------|
| **Heap** | Shared | All objects. Subject to GC. |
| **Stack** | Per thread | Stack frames for live method calls. |
| **PC Register** | Per thread | Address of the current bytecode instruction. |
| **Native Method Stack** | Per thread | C/C++ frames for JNI calls. |
| **Metaspace** | Shared | Class metadata: methods, fields, constant pool, bytecode itself. Native memory, not heap. |
| **Code Cache** | Shared | JIT-compiled native code. Has its own size limit (`-XX:ReservedCodeCacheSize`). |

The Code Cache filling up is a real failure mode for large services with lots of generated code (heavy reflection, dynamic proxies). Symptoms: gradual performance degradation, `CodeCache is full. Compiler has been disabled.` in the logs.

---

## Stack frames

When a method is called, a new stack frame is pushed onto the calling thread's stack. Each frame contains:

- **Local variable array** — method parameters and locals (indexed by slot).
- **Operand stack** — working stack for arithmetic and method calls.
- **Frame data** — return address back to the caller, reference to the constant pool.

When the method returns, the frame is popped. This is why local variables vanish: their backing storage is gone.

A `StackTraceElement[]` is a snapshot of the live frames at the moment of `Throwable` construction.

---

## Common pitfalls

- **Assuming microbenchmarks measure JIT-optimized code.** Without warmup they measure interpreted code. Use JMH (`org.openjdk.jmh`) for real benchmarks.
- **Adding a different `java.lang.X` to the classpath.** Delegation finds the real one first; your shadow class is never loaded.
- **Static initialization with side effects.** `<clinit>` order can surprise — if class A's static block calls class B, B initializes first. Cyclic static deps deadlock.
- **`Class.forName(...)` triggers initialization.** `getClass()` returns the `Class<?>` without forcing init. Choose accordingly.
- **Generated classes pile up in Metaspace.** Bytecode generators (Spring AOP, mockito) create classes per session. Default Metaspace is unlimited but it's still native memory. In containers without `-XX:MaxMetaspaceSize`, this can OOM the host before the JVM notices.

---

## Code examples

1. `ClassInitializationOrder.java` — observe `<clinit>` running once, and the order of static blocks.
2. `ClassForNameVsLoadClass.java` — `Class.forName` triggers init; `loadClass` doesn't.
3. `JitWarmupEffect.java` — same loop run twice; the second run is faster because JIT has compiled.
4. `BytecodeDisassembled.java` — uses `Compiler` API? No — uses runtime `javap` invocation? Simpler: prints reminders and provides a sample method whose bytecode is interesting to view with `javap -c`.
5. `ClassLoaderHierarchy.java` — walk the loader chain of various classes.

---

## Try this yourself

1. In `JitWarmupEffect.java`, change the warmup iterations to 0. The first measured timing is dominated by the interpreter; the difference is large.
2. Compile `BytecodeDisassembled.java` and run `javap -c BytecodeDisassembled` — read the bytecode for the small example method.
3. In `ClassLoaderHierarchy.java`, replace the `Foo` with a class from your own application and observe the application loader.

---

## Self-check

1. A class has a `static {}` block that prints "init". You import it but never use it. Then you call `MyClass.class.getName()`. Does the static block run? What if you call `Class.forName("MyClass")` instead?
2. You publish `java.lang.String` in your application JAR. Will the JVM use it? Why or why not — describe the delegation model.
3. You run a microbenchmark loop twice and the second run is 10x faster than the first. What changed inside the JVM between them?
