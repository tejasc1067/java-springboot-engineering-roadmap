# Java Compilation Demo

This example demonstrates how Java source code gets compiled and executed.

---

# Step 1 — Compile Java Source Code

Run the following command:

```bash
  javac HelloWorld.java
```

What happens here?

- `javac` is the Java compiler
- It converts Java source code (`.java`) into bytecode (`.class`)

Generated file:

```text
HelloWorld.class
```

---

# Step 2 — Run Java Program

Run the following command:

```bash
  java HelloWorld
```

What happens here?

- JVM loads the `HelloWorld.class` file
- JVM executes the bytecode
- Output gets displayed on the console

---

# Output

```text
Hello World
```

---

# Important Notes

- `.java` → source code
- `.class` → bytecode
- JVM executes bytecode
- Java becomes platform independent because of JVM
