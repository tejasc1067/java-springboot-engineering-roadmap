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

## 21. Using == for String Comparison

Incorrect:

if (a == b)

Correct:

if (a.equals(b))

---

## 22. Ignoring String Immutability

Methods like concat() create new String objects.

Original String remains unchanged.

---

## 23. Excessive String Concatenation

Using `+` repeatedly inside loops may create performance issues.

Use StringBuilder instead.

---

## 24. Accessing Non-Static Members from Static Context

Static methods cannot directly access non-static members.

---

## 25. Excessive Static Usage

Too much static usage may create tightly coupled applications.

---

## 26. Confusing Class-Level and Object-Level Data

Static variables are shared among all objects.

---

## 27. Forgetting this During Variable Ambiguity

Local variables may hide instance variables.

---

## 28. Using this Inside Static Methods

Static methods cannot directly use `this`.

---

## 29. Incorrect Constructor Chaining

`this()` must be first statement inside constructor.

---

## 30. Making Sensitive Variables Public

Sensitive data should usually remain private.

---

## 31. Confusing protected and default Access

protected allows child class access.

default does not.

---

## 32. Overusing public Variables

Too many public members reduce encapsulation and security.

---

## 33. Incorrect Package Declaration Position

Package declaration must be first statement in Java file.

---

## 34. Folder Structure Not Matching Package Name

Package hierarchy must match folder structure.

---

## 35. Overcrowding Single Package

Too many unrelated classes inside one package reduces maintainability.

---

## 36. Catching Generic Exception Everywhere

Overusing generic Exception reduces debugging clarity.

---

## 37. Ignoring Exception Messages

Exception messages provide important debugging information.

---

## 38. Empty Catch Blocks

Never leave catch blocks empty.

Bad practice:
catch(Exception e) {}

---

## 39. Forgetting finally for Resource Cleanup

Resources should be properly closed using finally block.

---

## 40. Using Wrong Collection Type

Choose collection based on use case.

---

## 41. Ignoring Duplicate Behavior in Set

Set automatically removes duplicates.

---

## 42. Forgetting Null Checks During Retrieval

Map retrieval may return null.

---

## 43. Concurrent Modification During Iteration

Modifying collection during iteration may throw exceptions.

---

## 44. Using Primitive Types in Collections

Collections require wrapper classes.

Incorrect:
ArrayList<int>

Correct:
ArrayList<Integer>

---

## 45. Forgetting Null Checks in HashMap

map.get() may return null.

---

## 46. Modifying Collection During Iteration

Improper modification may cause ConcurrentModificationException.

---

## 47. Confusing Ordered and Unordered Collections

HashSet and HashMap do not guarantee ordering.

---

## 48. Choosing Wrong Collection Type

Wrong collection choice may reduce performance and scalability.

---

## 49. Using == Instead of equals() in Collections

String/object comparison inside collections should usually use equals().

---

## 50. Ignoring Generics

Using raw collections reduces type safety.

Incorrect:

ArrayList list = new ArrayList();

Correct:

ArrayList<String> list = new ArrayList<>();

---

## 51. Excessive Nested Loops on Collections

Poor iteration logic may reduce backend performance.

---

## 52. Removing Elements Improperly During for-each Loop

This may cause ConcurrentModificationException.

Use Iterator instead.

---

## 53. Ignoring Collection Performance

Wrong collection selection may reduce backend scalability.

---

## 54. Not Using Iterator for Safe Removal

Direct removal during iteration may cause ConcurrentModificationException.

---

## 55. Ignoring Immutability Concepts

Improper mutable object handling may create backend bugs.

---

## 56. Overusing Static Variables

Excessive static usage may create memory and testing issues.

---

## 57. Hardcoding Values Instead of Constants

Hardcoded values reduce maintainability.

---

## 58. Poor Package Organization

Improper package structure creates maintainability problems in large backend applications.

---

## 59. Ignoring Exception Propagation

Improper exception handling hides real backend issues.

---

## 60. Using Raw Collections

Raw collections reduce type safety and increase runtime risks.

---

## 61. Overusing Streams for Complex Logic

Complex streams reduce readability and debugging capability.

---

## 62. Ignoring Null Safety in Streams

Null values may still cause NullPointerException.

---

## 63. Using Optional Incorrectly

Avoid calling get() without checking presence.

---

## 64. Writing Very Long Stream Chains

Excessive chaining reduces maintainability.

---

## 65. Ignoring Stream Performance

Streams improve readability but may not always improve performance.

---

## 66. Confusing map() and filter()

filter():
selects data

map():
transforms data

---

## 67. Forgetting Streams Are Immutable Operations

Streams create new transformed pipelines.

Original collection remains unchanged.

---

## 68. Using Streams for Everything

Traditional loops are sometimes simpler and more readable.

---

## 69. Forgetting to Close File Resources

Improper resource closing may cause memory leaks.

---

## 70. Using Hardcoded File Paths

Hardcoded paths reduce portability.

---

## 71. Ignoring Exception Handling During File Operations

File operations may fail unexpectedly.

---

## 72. Reading Large Files Inefficiently

BufferedReader improves large file reading performance.

---

## 73. Forgetting try-with-resources

Automatic resource handling is safer and cleaner.

---

## 74. Ignoring File Permissions

Applications may fail if permissions are insufficient.

---

## 75. Overwriting Important Files Accidentally

Improper write operations may overwrite existing data.

---

## 76. Ignoring Relative Path Usage

Relative paths improve portability and deployment flexibility.

---