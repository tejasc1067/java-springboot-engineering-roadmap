# Classes and Objects in Java

Object-Oriented Programming (OOP) is one of the most important concepts in Java.

Backend systems are built using:
- classes
- objects
- object interactions

Understanding classes and objects properly is essential for backend engineering.

---

# 1. What is a Class?

A class is a blueprint or template used to create objects.

A class defines:
- state (variables)
- behavior (methods)

Example:

```java
class Student {

    String name;

    void display() {

        System.out.println(name);
    }
}
```

---

# 2. What is an Object?

An object is a real instance of a class.

Example:

```java
Student student1 = new Student();
```

Here:
- Student → class
- student1 → object

Objects contain actual data.

---

# 3. State and Behavior

Objects contain:

## State
Represented using variables/fields.

Example:
- name
- age
- salary

## Behavior
Represented using methods.

Example:
- displayInfo()
- calculateSalary()
- login()

Very important OOP concept.

---

# 4. Creating Objects

Objects are created using `new` keyword.

Example:

```java
Student student1 = new Student();
```

The `new` keyword:
- allocates memory
- creates object
- returns object reference

---

# 5. Accessing Object Members

Dot operator (`.`) is used to access:
- variables
- methods

Example:

```java
student1.name = "Tejas";

student1.display();
```

---

# 6. Multiple Objects

A class can create multiple objects.

Example:

```java
Student student1 = new Student();

Student student2 = new Student();
```

Each object maintains separate state.

---

# 7. Object References

Objects are stored in heap memory.

Variables like:

```java
Student student1
```

store references to objects.

This is very important for understanding:
- memory
- object sharing
- backend behavior

---

# 8. Heap Memory Basics

Example:

```java
Student student1 = new Student();
```

What happens internally:
1. object created in heap memory
2. reference stored in stack memory
3. object becomes accessible through reference

---

# 9. Object Interaction

Objects can interact with other objects.

Example:
- Order object uses Product object
- Payment object uses User object

Backend systems are built using object collaboration.

---

# 10. Why Classes Matter in Backend Engineering?

Spring Boot applications heavily depend on classes.

Examples:
- Controllers
- Services
- Repositories
- DTOs
- Entities

Backend engineering is fundamentally object-oriented.

---

# 11. Real-World Backend Examples

Examples of backend classes:

```text
User
Product
Order
Payment
Employee
Invoice
Customer
```

These classes represent business entities.

---

# 12. Common Beginner Confusions

Many beginners confuse:
- class
- object
- reference

Remember:

Class:
Blueprint

Object:
Real instance

Reference:
Variable pointing to object

---

# 13. Industry Relevance

Classes and objects are foundational for:
- Spring Boot
- Hibernate
- microservices
- REST APIs
- enterprise backend systems

Without strong OOP understanding:
backend engineering becomes difficult.