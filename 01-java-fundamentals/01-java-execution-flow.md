# 01 — How Java Programs Actually Run

Other languages you may have heard of are *interpreted* (Python, JavaScript) — the runtime reads source code directly. Some are *compiled to machine code* (C, Go) — there's a build step that produces a `.exe` you can run directly. Java is neither. It's in between, and once you understand the in-between, a lot of Java quirks stop being mysterious.

This topic is short on syntax and heavy on "what is actually happening when I type `java HelloWorld`." Get this right and the rest of the module makes much more sense.

---

## The problem this solves

In the 1990s, "write once, run anywhere" was the dream. You wanted to write code on a Windows machine and have it run on a Linux server without rebuilding. Compiled languages couldn't do this — a Windows `.exe` doesn't run on Linux. Interpreted languages could, but were slow.

Java's answer: compile source code to a **portable intermediate format** called *bytecode*. Bytecode isn't machine code — no CPU understands it directly. But on every operating system, a small program called the **JVM (Java Virtual Machine)** reads the bytecode and executes it for that OS. The same `.class` file runs on Windows, Linux, and macOS, as long as each has its own JVM.

---

## How it works

The flow has three stages:

```text
1. You write source code:        HelloWorld.java   (human-readable text)
                                       │
                                       │  javac (the Java compiler)
                                       ▼
2. Compiler produces bytecode:   HelloWorld.class  (binary, portable)
                                       │
                                       │  java (the JVM)
                                       ▼
3. JVM executes bytecode:        program runs, output appears
```

Step 1 is a text file. Step 2 is a binary file that contains *instructions for a fictional CPU* — the JVM. Step 3 is the JVM translating those instructions into real CPU operations *as the program runs*.

The "as the program runs" part is important. Modern JVMs don't just interpret bytecode line by line; they watch which methods get called a lot and JIT-compile (Just-In-Time) those into native machine code on the fly. That's why Java is fast despite the extra layer — the hot paths end up running as real machine code anyway.

---

## JDK, JRE, JVM — what's the difference?

These three terms get confused constantly. Here's the clean breakdown:

- **JVM** (Java Virtual Machine) — the program that *runs* bytecode. Just the runtime.
- **JRE** (Java Runtime Environment) — JVM + the standard libraries (`java.lang`, `java.util`, etc.) that your bytecode needs at runtime. You install a JRE if you only need to *run* Java programs, not write them.
- **JDK** (Java Development Kit) — JRE + the tools to *build* Java code: `javac` (compiler), `jar` (packager), `jdb` (debugger), and so on. Install a JDK on your laptop. Servers usually only need a JRE, though modern Java distributions ship JDKs everywhere because the difference is small.

```text
       JDK
   ┌──────────────────────────────┐
   │  javac, jar, jdb, javadoc    │  ← developer tools
   │  ┌─────────────────────────┐ │
   │  │       JRE               │ │
   │  │  standard libraries     │ │
   │  │  ┌──────────────────┐   │ │
   │  │  │       JVM        │   │ │
   │  │  │  bytecode engine │   │ │
   │  │  └──────────────────┘   │ │
   │  └─────────────────────────┘ │
   └──────────────────────────────┘
```

If someone asks "do I need the JDK or JRE on my server?" the honest answer in 2024+ is "just install a JDK." But you should know the distinction exists.

---

## The classpath: where Java looks for classes

When you run `java HelloWorld`, the JVM needs to find `HelloWorld.class`. It also needs to find any classes that `HelloWorld` uses, transitively. The **classpath** tells the JVM where to look.

```bash
java -cp ./build HelloWorld
java -cp ./build:./libs/mysql.jar HelloWorld     # multiple entries (colon on Linux/mac, semicolon on Windows)
```

The default classpath is the current directory. That's why `java HelloWorld` from the folder containing `HelloWorld.class` just works.

You won't set classpaths by hand often. Build tools (Maven, Gradle — coming in module 05) compute the classpath for you. But when something says `ClassNotFoundException: foo.Bar` in production at 2 AM, "the classpath is wrong" is the first thing to check.

---

## Single-file source-code launch (Java 11+)

Since Java 11, you can skip the explicit compile step for small programs:

```bash
java HelloWorld.java
```

The JVM compiles and runs in one step. This module uses that style — every `.java` file under `CODE_EXAMPLES/` can be run that way directly. Under the hood, the compile-to-bytecode step still happens; you just don't have to manage the `.class` file yourself.

---

## Common pitfalls

- **Running `java HelloWorld.class`** — wrong. You pass the class name (`HelloWorld`), not the file name. The JVM appends `.class` and looks on the classpath.
- **Forgetting the `main` method signature must be exactly `public static void main(String[] args)`.** Wrong signature → JVM can't find an entry point → `Error: Main method not found`.
- **Editing the source but running an old `.class` file.** Always recompile after editing, or use the single-file launch (`java HelloWorld.java`) so you can't forget.
- **Confusing the *file* `HelloWorld.java` with the *class* `HelloWorld`.** Java's rule: a public class must live in a file with the same name. So `public class HelloWorld` *must* be in `HelloWorld.java`.

---

## Code examples

1. `HelloWorld.java` — the smallest possible Java program. Compile and run it.
2. `BytecodeWalkthrough.java` — same program with a step-by-step look at what `javac` produces and what the `java` command does. Includes the `javap` command to peek at bytecode.

---

## Try this yourself

1. Compile `HelloWorld.java` manually with `javac HelloWorld.java`. Look at the resulting `.class` file — it exists, it's binary. Open it in a text editor and confirm it's not human-readable.
2. Rename `HelloWorld.class` to `Hello.class` and try `java Hello`. Watch it fail. Restore the name. Why does the JVM care?
3. Run `javap -c HelloWorld` after compiling. That's the bytecode disassembler — it shows the JVM instructions your one-line program compiled into. You don't need to understand the opcodes; just see that they exist.

---

## Self-check

1. A teammate says "JVM is the compiler." Correct them in one sentence.
2. You hand someone a `.class` file built on your Windows laptop. They put it on a Linux server. It runs. Why does that work?
3. Why does `java HelloWorld` succeed but `java helloworld` fail, on Linux at least? (Hint: classpath lookup is case-sensitive on case-sensitive filesystems.)
