# Loops in Java

Loops are used to execute a block of code repeatedly.

Loops help developers:
- avoid writing repetitive code
- process large amounts of data
- iterate through collections
- handle backend processing tasks efficiently

Backend systems frequently use loops for:
- processing API responses
- iterating database records
- batch operations
- filtering data
- handling collections

---

# 1. Why Loops are Important?

Imagine printing numbers from 1 to 100.

Without loops:
- developers would need to write repetitive code
- applications become difficult to maintain

Loops solve this problem by automating repetition.

---

# 2. Types of Loops in Java

Java supports:
- for loop
- while loop
- do-while loop

Each loop is used for different scenarios.

---

# 3. for Loop

The `for` loop is used when the number of iterations is known.

Syntax:

```java
for (initialization; condition; update) {

    // code block
}
```

Example:

```java
for (int i = 1; i <= 5; i++) {

    System.out.println(i);
}
```

Output:

```text
1
2
3
4
5
```

---

# 4. while Loop

The `while` loop executes code as long as condition remains true.

Syntax:

```java
while (condition) {

    // code block
}
```

Example:

```java
int i = 1;

while (i <= 5) {

    System.out.println(i);

    i++;
}
```

---

# 5. do-while Loop

The `do-while` loop executes code at least once before checking condition.

Syntax:

```java
do {

    // code block

} while (condition);
```

Example:

```java
int i = 1;

do {

    System.out.println(i);

    i++;

} while (i <= 5);
```

---

# 6. Nested Loops

A loop inside another loop is called nested loop.

Example:

```java
for (int i = 1; i <= 3; i++) {

    for (int j = 1; j <= 2; j++) {

        System.out.println(i + " " + j);
    }
}
```

Nested loops are commonly used in:
- matrix operations
- pattern generation
- advanced algorithms

---

# 7. break Statement

The `break` statement stops loop execution immediately.

Example:

```java
for (int i = 1; i <= 10; i++) {

    if (i == 5) {

        break;
    }

    System.out.println(i);
}
```

---

# 8. continue Statement

The `continue` statement skips current iteration.

Example:

```java
for (int i = 1; i <= 5; i++) {

    if (i == 3) {

        continue;
    }

    System.out.println(i);
}
```

---

# 9. Infinite Loops

A loop that never ends is called infinite loop.

Example:

```java
while (true) {

    System.out.println("Running forever");
}
```

Infinite loops can cause:
- high CPU usage
- application hangs
- memory issues

---

# 10. Real Backend Engineering Importance

Loops are heavily used in backend systems for:
- processing collections
- iterating API responses
- reading files
- batch processing
- handling database records
- processing logs
- validating large datasets

Loops become foundational for:
- collections framework
- streams
- multithreading
- data processing
- backend scalability
