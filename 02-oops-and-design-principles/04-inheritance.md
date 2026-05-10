# Inheritance in Java

Inheritance is one of the core pillars of Object-Oriented Programming.

Inheritance allows:
# one class to acquire properties and behavior of another class.

It helps:
- code reuse
- hierarchy creation
- extensibility
- cleaner architecture

Inheritance is heavily used in:
- enterprise Java
- framework design
- Spring applications
- exception hierarchies

However:
inheritance must be used carefully.

Overusing inheritance may create tightly coupled systems.

---

# 1. What is Inheritance?

Inheritance means:
a child class acquires variables and methods from parent class.

Example:

```java
class Animal {

}

class Dog extends Animal {

}
```

Here:
- Animal → parent class
- Dog → child class

---

# 2. Parent and Child Classes

## Parent Class

Also called:
- superclass
- base class

Contains common behavior.

---

## Child Class

Also called:
- subclass
- derived class

Acquires parent behavior and may add new functionality.

---

# 3. extends Keyword

Inheritance is achieved using:

```java
extends
```

Example:

```java
class Dog extends Animal
```

---

# 4. Why Inheritance is Important?

Inheritance helps:
- reduce duplicate code
- improve reusability
- create hierarchy
- simplify maintenance

Very important OOP principle.

---

# 5. IS-A Relationship

Inheritance represents:
# IS-A relationship

Examples:
- Dog IS-A Animal
- Car IS-A Vehicle
- Manager IS-A Employee

Very important concept.

---

# 6. Inherited Members

Child classes inherit:
- variables
- methods

Example:

```java
displayInfo()
```

defined in parent class can be reused in child class.

---

# 7. Types of Inheritance in Java

## Single Inheritance

One child inherits from one parent.

---

## Multilevel Inheritance

Inheritance chain across multiple levels.

Example:

```text
Animal → Dog → Puppy
```

---

## Hierarchical Inheritance

Multiple child classes inherit from same parent.

Example:

```text
Vehicle → Car
Vehicle → Bike
```

---

# 8. Multiple Inheritance in Java

Java does NOT support multiple inheritance using classes.

Example not allowed:

```text
class A
class B
class C extends A, B
```

This avoids ambiguity problems.

Java supports multiple inheritance using interfaces.

---

# 9. Constructor Behavior in Inheritance

When child object is created:
parent constructor executes first.

Very important inheritance behavior.

Later:
`super()` keyword handles this explicitly.

---

# 10. Real-World Backend Examples

Inheritance is used in:
- framework extension
- exception hierarchies
- reusable models
- enterprise architectures

Examples:
- RuntimeException
- IOException
- JpaRepository

---

# 11. Problems with Excessive Inheritance

Too much inheritance may create:
- tight coupling
- rigid architecture
- difficult debugging
- maintainability problems

Modern backend systems often prefer:
# composition over inheritance

Very important engineering principle.

---

# 12. Inheritance vs Composition

Inheritance:
IS-A relationship.

Composition:
HAS-A relationship.

Composition usually provides:
- better flexibility
- loose coupling
- easier maintenance

Very important backend engineering concept.

---

# 13. Common Beginner Confusions

Beginners often:
- overuse inheritance
- create deep inheritance trees
- confuse inheritance with composition

Good software design requires careful inheritance usage.

---

# 14. Industry Relevance

Inheritance is foundational for:
- enterprise Java
- framework design
- Spring Framework
- exception handling systems
- reusable architectures

However:
modern scalable systems use inheritance carefully.