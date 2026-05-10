# Encapsulation in Java

Encapsulation is one of the core principles of Object-Oriented Programming.

It helps:
- protect object data
- control access
- improve maintainability
- improve security
- create stable backend systems

Encapsulation is heavily used in:
- Spring Boot applications
- DTOs
- entities
- service classes
- enterprise systems

---

# 1. What is Encapsulation?

Encapsulation means:
# wrapping data and methods together into single unit

AND:
# restricting direct access to object data.

Usually achieved using:
- private variables
- public getter/setter methods

---

# 2. Why Encapsulation is Important?

Without encapsulation:
objects may become:
- inconsistent
- insecure
- difficult to maintain

Example:

```java
student.marks = -100;
```

This creates invalid object state.

Encapsulation helps prevent such problems.

---

# 3. Data Hiding

Encapsulation supports:
# data hiding

Internal object data should not be modified directly from outside class.

Example:

```java
private int marks;
```

Now direct access is restricted.

---

# 4. Private Variables

Private variables:
- accessible only inside same class
- protect internal state

Example:

```java
private String name;
```

This is one of the most important OOP practices.

---

# 5. Getter Methods

Getter methods are used to READ private data.

Example:

```java
public String getName() {

    return name;
}
```

---

# 6. Setter Methods

Setter methods are used to MODIFY private data.

Example:

```java
public void setName(String name) {

    this.name = name;
}
```

---

# 7. Validation Using Setters

Setters allow validation before updating data.

Example:

```java
if (marks >= 0)
```

This helps:
- maintain valid object state
- prevent invalid data
- enforce business rules

Very important for backend systems.

---

# 8. Controlled Access

Encapsulation provides:
# controlled access

Instead of allowing unrestricted modification.

This improves:
- security
- debugging
- maintainability

---

# 9. Real-World Backend Examples

Encapsulation is heavily used in:
- DTO classes
- entities
- request models
- response models
- service classes

Example:

```text
User
Product
Employee
Order
```

These backend entities usually use private fields with getters/setters.

---

# 10. Encapsulation and Security

Encapsulation protects:
- sensitive data
- object integrity
- internal implementation

This is important in enterprise backend systems.

---

# 11. Encapsulation vs Abstraction

Encapsulation:
Focuses on data protection and controlled access.

Abstraction:
Focuses on hiding implementation complexity.

Both are important OOP principles.

---

# 12. Common Beginner Confusions

Beginners often think:
# Encapsulation means only getters/setters

But real encapsulation means:
# Protecting object validity and integrity

Very important distinction.

---

# 13. Industry Relevance

Encapsulation is foundational for:
- Spring Boot
- Hibernate
- enterprise Java
- REST APIs
- scalable backend systems

Without encapsulation:
backend systems become difficult to maintain.