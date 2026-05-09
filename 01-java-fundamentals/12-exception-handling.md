# Exception Handling in Java

Exception handling is used to handle runtime errors gracefully.

Backend applications constantly face:
- invalid user input
- database failures
- API failures
- null values
- file errors

Exception handling prevents applications from crashing and improves reliability.

---

# 1. What is an Exception?

An exception is an unexpected event that interrupts normal program execution.

Example:
- dividing by zero
- accessing invalid array index
- null values

---

# 2. Why Exception Handling is Important?

Without exception handling:
- applications terminate unexpectedly
- users see system failures
- debugging becomes difficult

Exception handling improves:
- application stability
- debugging
- reliability

---

# 3. try-catch Block

`try` contains risky code.

`catch` handles exceptions.

Example:

```java
try {

    int result = 10 / 0;

} catch (ArithmeticException exception) {

    System.out.println("Cannot divide by zero");
}
```

---

# 4. finally Block

finally block always executes.

Used for:
- resource cleanup
- closing files
- closing database connections

---

# 5. throw Keyword

Used to manually throw exceptions.

Example:

```java
throw new ArithmeticException("Invalid Age");
```

---

# 6. throws Keyword

Used to declare exceptions at method level.

Example:

```java
public void process() throws IOException
```

---

# 7. Multiple Catch Blocks

Different exceptions can be handled separately.

Example:

```java
catch (NullPointerException exception)

catch (ArithmeticException exception)
```

---

# 8. Custom Exceptions

Developers can create custom exceptions.

Example:

```java
class InvalidSalaryException extends Exception
```

Used heavily in enterprise applications.

---

# 9. Exception Hierarchy Basics

Java exception hierarchy:

```text
Throwable
 ├── Error
 └── Exception
```

Important categories:
- checked exceptions
- unchecked exceptions

---

# 10. Real Backend Engineering Importance

Exception handling is heavily used in:
- REST APIs
- database operations
- validations
- authentication systems
- file handling

Spring Boot applications heavily depend on global exception handling.

---

# 11. Industry Relevance

Strong exception handling skills help developers:
- build reliable systems
- debug efficiently
- create resilient APIs
- improve backend stability

Exception handling is a core backend engineering skill.
