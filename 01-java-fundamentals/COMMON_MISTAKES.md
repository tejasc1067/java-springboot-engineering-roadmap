# Common Mistakes in Java Fundamentals

## 1. Forgetting semicolon

Incorrect:

System.out.println("Hello")

Correct:

System.out.println("Hello");

---

## 2. Wrong main method signature

Correct:

public static void main(String[] args)

---

## 3. Case sensitivity

Java is case-sensitive.

Incorrect:
system.out.println()

Correct:
System.out.println()

## 4. Confusing JDK, JRE, and JVM

JDK:
Development toolkit

JRE:
Runtime environment

JVM:
Executes bytecode

---

## 5. Thinking Java code runs directly

Java code first gets compiled into bytecode before JVM executes it.

---

## 6. Confusing Primitive and Reference Types

Primitive types store actual values.

Reference types store object references.

---

## 7. Forgetting Variable Initialization

Local variables must be initialized before usage.

Incorrect:

int age;
System.out.println(age);

Correct:

int age = 25;

---

## 8. Using = instead of ==

Incorrect:

if (a = 5)

Correct:

if (a == 5)

---

## 9. Forgetting break in switch

Missing break causes fall-through behavior.

---

## 10. Confusing && and ||

&& → AND

|| → OR

---