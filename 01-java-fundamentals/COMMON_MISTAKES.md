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

## 11. Creating Infinite Loops

Incorrect:

while(true)

without termination condition.

---

## 12. Wrong Loop Condition

Incorrect conditions may:
- skip execution
- create infinite loops

---

## 13. Off-by-One Errors

Very common beginner mistake.

Example:

for (int i = 0; i <= length; i++)

may exceed array boundary.

---

## 14. Forgetting Method Call

Defining method alone does not execute it.

Method must be called.

---

## 15. Missing Return Statement

Methods with return type must return value.

---

## 16. Wrong Parameter Order

Incorrect parameter order may produce unexpected results.

---

## 17. Confusing Parameters and Arguments

Parameters are method variables.

Arguments are actual passed values.

---

## 18. Accessing Invalid Array Index

Incorrect:

marks[5]

when array size is smaller.

---

## 19. Wrong Loop Condition

Incorrect loop condition may skip elements or exceed array bounds.

---

## 20. Forgetting Array Length

Hardcoding loop limits instead of using `.length` reduces flexibility.

Correct:

for (int i = 0; i < array.length; i++)

---