# 01 — Java Fundamentals

This is where the roadmap starts. By the end of this module you should be able to read a small Java program, run it from the command line, and explain — out loud — what every line does and why.

The goal is not to memorize syntax. The goal is to build the **mental model** that everything else (OOP in module 02, JDBC in module 04, Spring later) sits on top of: how Java code is compiled and run, what lives in memory and where, how variables behave when they hold references, and what tools the language gives you to organize code.

## Who this is for

- Total beginners who have never written Java (or any programming) before.
- Developers from other domains (frontend, data, QA, devops) who can program but have never used Java.

If you've already written a few small Java programs from scratch — class, methods, a couple of collections — and you're comfortable with the command line, you can skim this module and move to module 02.

## What you'll be able to do at the end

Concrete outcomes. Check each off as you go.

- [ ] Compile and run a Java program from the command line with `javac` and `java`.
- [ ] Explain, in your own words, what JVM, JRE, and JDK each are — and which one you'd install on a server vs. a developer laptop.
- [ ] Show with code why `int a = b` copies a value but `Car a = b` does not.
- [ ] Predict whether a piece of code prints what you expect, when it mixes primitives, references, and `==` vs. `equals`.
- [ ] Write a method, overload it with a different parameter list, and explain how the compiler picks which one to call.
- [ ] Iterate an `ArrayList` safely while removing items, and explain why a `for-each` loop would have thrown.
- [ ] Read a file line by line using try-with-resources, and explain what `try-with-resources` actually does for you.
- [ ] Start two threads, observe a race condition, and fix it with `synchronized`.
- [ ] Look at a piece of code and identify what's on the stack vs. on the heap.

## Prerequisites

- A JDK 17 or newer installed and on your `PATH`. `java -version` and `javac -version` should both work in your terminal.
- A text editor or IDE. IntelliJ IDEA Community Edition is free and the standard choice; VS Code with the Java extension also works.
- Basic comfort with running commands in a terminal (opening a folder, running an executable, navigating with `cd`).

No prior Java knowledge is assumed.

## How this module is organized

Topics 01–02 cover how Java actually runs and where data lives.

Topics 03–07 cover the day-to-day syntax: operators, loops, methods, arrays, strings.

Topics 08–11 cover the parts of Java that let you organize code: `static`, `this`, access modifiers, packages.

Topics 12–14 cover the backend essentials: exceptions, the collections framework, and the Java 8 features (lambdas, streams, Optional) that modern code uses everywhere.

Topics 15–17 cover the systems-level material you'll need once your code starts touching files, threads, and production memory.

```text
01-java-execution-flow                         ← how source becomes a running program
02-variables-and-memory                        ← primitives, references, stack vs heap
03-operators-and-conditions
04-loops
05-methods
06-arrays
07-strings                                     ← immutability, equals vs ==, StringBuilder
08-static-keyword                              ← class-level state
09-this-keyword                                ← the current object
10-access-modifiers
11-packages
12-exception-handling                          ← backend essentials begin
13-collections-framework
14-java-8-features                             ← lambdas, streams, Optional
15-file-handling
16-multithreading-basics
17-jvm-memory-and-garbage-collection           ← what happens under the hood
```

## How to run the code

No external dependencies — just the JDK. From the repo root:

```bash
java 01-java-fundamentals/CODE_EXAMPLES/04-loops/ForLoop.java
```

Java 11+ supports single-file source-code launch: it compiles and runs in one step. If you're on an older JDK, compile first with `javac File.java` and then run `java File`.

Inside each topic's `CODE_EXAMPLES/` folder, files are named in PascalCase. Reading order is given in each topic markdown's "Code examples" section — alphabetical file order does not match reading order. Files with `Broken` in their names are intentionally bad — the next file listed fixes them. Run both, in the markdown's listed order.

## Working through a topic

For each topic file:

1. Read the markdown.
2. Run the first listed code example. Read it. Modify it.
3. Run the next listed example. Compare.
4. Do the "Try this yourself" exercise at the end of the markdown.
5. Answer the "Self-check" questions out loud, in your own words.

If you can't answer all three self-check questions, re-read the topic before moving on. The next topic usually assumes you can.

## Module checkpoint

Before moving on to module 02 (OOP and design principles), you should be able to answer these aloud:

1. You hand a colleague a `.java` file. Walk them through what has to happen — step by step — before any output appears on the screen.
2. `int a = 5; int b = a; b = 10;` — what is `a` now? `Car a = new Car(); Car b = a; b.brand = "Ford";` — what does `a.brand` print? Explain the difference.
3. You see `if (name == "Alice")` in code review. Why is this a bug, and what's the fix?
4. You override a method's signature but spell the method name slightly wrong. The compiler is happy. The override doesn't fire. What annotation would have caught this?
5. You iterate a `List<String>` with a for-each loop and call `list.remove(item)` inside. Why does this blow up? What should you do instead?
6. Two threads increment the same `int counter` a million times each. The result is less than two million. Why, and what's the smallest change that fixes it?
7. Your service has a `static Map<String, User> cache = new HashMap<>();` that never gets cleared. The heap grows forever. Why is this a leak, even though garbage collection exists?

If those are easy, you're ready. If any feel shaky, the corresponding topic is 01, 02, 07, 05 (and module 02 topic 06), 13, 16, and 17.
