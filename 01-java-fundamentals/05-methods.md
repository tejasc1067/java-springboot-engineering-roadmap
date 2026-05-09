# Methods in Java

Methods are one of the most important building blocks in Java programming.

Methods help developers:
- organize code
- reuse logic
- improve readability
- reduce duplication
- build modular applications

Backend applications heavily rely on methods for:
- business logic
- API processing
- validation
- database operations
- service layer implementation

---

# 1. What is a Method?

A method is a block of code designed to perform a specific task.

Example:

```java
public static void greetUser() {

    System.out.println("Welcome User");
}
```

Here:
- `greetUser` is method name
- method performs greeting task

---

# 2. Why Methods are Important?

Without methods:
- code becomes repetitive
- applications become difficult to maintain
- debugging becomes harder

Methods help in:
- code reusability
- modular programming
- clean architecture

---

# 3. Method Syntax

Basic syntax:

```java
returnType methodName(parameters) {

    // method body
}
```

Example:

```java
public static void displayMessage() {

    System.out.println("Learning Java Methods");
}
```

---

# 4. Calling a Method

Methods execute only when they are called.

Example:

```java
displayMessage();
```

This invokes the method.

---

# 5. Method Parameters

Parameters allow methods to receive input values.

Example:

```java
public static void greetUser(String name) {

    System.out.println("Welcome " + name);
}
```

Method call:

```java
greetUser("Tejas");
```

---

# 6. Return Type

Methods can return values.

Example:

```java
public static int addNumbers(int a, int b) {

    return a + b;
}
```

Returned value:

```java
int result = addNumbers(10, 20);
```

---

# 7. void Methods

Methods using `void` do not return anything.

Example:

```java
public static void printMessage() {

    System.out.println("Hello");
}
```

---

# 8. Method Overloading

Method overloading means creating multiple methods with same name but different parameters.

Example:

```java
public static int add(int a, int b)

public static double add(double a, double b)
```

This improves flexibility and readability.

---

# 9. Real Backend Engineering Importance

Methods are heavily used in backend development for:
- service methods
- controller methods
- validation methods
- utility functions
- business logic
- API handling

Spring Boot applications are built heavily around methods.

---

# 10. Best Practices

Good methods should:
- perform one task
- have meaningful names
- avoid unnecessary complexity
- improve readability

Good Example:

```java
calculateTotalPrice()
```

Bad Example:

```java
doTask()
```

---

# 11. Industry Relevance

Large backend systems may contain:
- thousands of methods
- layered method calls
- reusable utility methods

Strong method design is foundational for:
- clean code
- maintainability
- scalable backend systems
