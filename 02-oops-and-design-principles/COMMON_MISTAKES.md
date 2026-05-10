## 1. Confusing Class and Object

Class:
Blueprint.

Object:
Actual instance.

---

## 2. Forgetting Object Creation

Methods and variables cannot be accessed without object creation for non-static members.

---

## 3. Accessing Object Members Incorrectly

Dot operator is required to access object members.

---

## 4. Assuming Multiple Objects Share State Automatically

Each object maintains separate state unless references are shared.

---

## 5. Confusing Object Reference with Actual Object

Reference variable points to object.

It is not the object itself.

---

## 6. Forgetting new Keyword

Without `new`, object is not created.

---

## 7. Misunderstanding Reference Assignment

Assigning one object reference to another may point both references to same object.

---

## 8. Ignoring Object Modeling Quality

Poor object design leads to poor backend architecture.

---

## 9. Giving Return Type to Constructor

Constructors cannot have return type.

---

## 10. Constructor Name Not Matching Class Name

Constructor name must exactly match class name.

---

## 11. Forgetting Object Initialization

Objects should be initialized properly using constructors.

---

## 12. Misusing Constructor Overloading

Too many overloaded constructors may reduce readability.

---

## 13. Incorrect Constructor Chaining

`this()` must be first statement inside constructor.

---

## 14. Creating Invalid Object State

Constructors should initialize meaningful values.

---

## 15. Confusing Constructor with Method

Constructors initialize objects.

Methods perform behavior.

---

## 16. Ignoring Backend Object Validity

Improper initialization may create unstable backend systems.

---

## 17. Making All Variables Public

Public variables reduce encapsulation and security.

---

## 18. Using Setters Without Validation

Setters should validate important business rules.

---

## 19. Confusing Encapsulation with Only Getters/Setters

Real encapsulation protects object integrity.

---

## 20. Exposing Sensitive Data Directly

Sensitive information should not be directly modifiable.

---

## 21. Creating Uncontrolled Object State

Objects should always remain in valid state.

---

## 22. Excessive Setter Usage

Too many setters may weaken object safety.

---

## 23. Ignoring Backend Validation Logic

Validation is critical in enterprise systems.

---

## 24. Violating Object Integrity

Objects should not allow invalid updates.

---

## 25. Overusing Inheritance

Too much inheritance creates tightly coupled systems.

---

## 26. Creating Deep Inheritance Hierarchies

Deep inheritance trees reduce maintainability.

---

## 27. Confusing IS-A and HAS-A Relationships

Inheritance represents IS-A relationship.

Composition represents HAS-A relationship.

---

## 28. Misusing Inheritance for Code Reuse Only

Inheritance should model logical hierarchy, not just reuse.

---

## 29. Ignoring Composition

Modern backend systems often prefer composition over inheritance.

---

## 30. Accessing Inherited Members Incorrectly

Child objects can access inherited public/protected members only.

---

## 31. Expecting Multiple Inheritance with Classes

Java does not support multiple inheritance using classes.

---

## 32. Creating Rigid Architectures

Poor inheritance design reduces scalability and flexibility.

---

## 33. Confusing this and super

this:
Current object.

super:
Parent object.

---

## 34. Calling super() Incorrectly

`super()` must be first statement inside constructor.

---

## 35. Assuming super Accesses Private Members

Private parent members cannot be directly accessed.

---

## 36. Forgetting Parent Constructor Execution Order

Parent constructor executes before child constructor.

---

## 37. Overusing Inheritance with super

Excessive inheritance creates rigid architectures.

---

## 38. Ignoring Constructor Chaining Behavior

Improper constructor chaining may create invalid initialization flow.

---

## 39. Shadowing Variables Unnecessarily

Using same variable names in parent and child may reduce readability.

---

## 40. Creating Complex Inheritance Hierarchies

Complex inheritance trees become difficult to maintain.

---

## 41. Confusing Overloading and Overriding

Overloading:
Different parameters.

Overriding:
Same signature with inheritance.

---

## 42. Changing Only Return Type in Overloading

Return type alone cannot overload methods.

---

## 43. Forgetting Inheritance Requirement for Overriding

Overriding requires parent-child relationship.

---

## 44. Incorrect Method Signature in Overriding

Method signature must match parent method.

---

## 45. Ignoring @Override Annotation

Using @Override helps prevent mistakes.

---

## 46. Confusing Compile-Time and Runtime Polymorphism

Overloading:
Compile-time.

Overriding:
Runtime.

---

## 47. Overusing Method Overloading

Too many overloaded methods may reduce readability.

---

## 48. Misunderstanding Runtime Dispatch

Actual overridden method is selected during runtime.

---

## 49. Confusing Inheritance with Polymorphism

Inheritance creates hierarchy.

Polymorphism provides flexible behavior.

---

## 50. Ignoring Parent Reference Child Object Concept

This is foundational for runtime polymorphism.

---

## 51. Assuming Parent Reference Can Access All Child Members

Parent reference accesses only parent-visible members.

---

## 52. Misunderstanding Dynamic Dispatch

Actual overridden method is selected during runtime.

---

## 53. Creating Tightly Coupled Systems

Without polymorphism systems become rigid.

---

## 54. Ignoring Loose Coupling Principles

Loose coupling improves scalability and maintainability.

---

## 55. Overusing Concrete Implementations

Programming directly to implementations reduces flexibility.

---

## 56. Ignoring Interface-Driven Design

Modern backend systems heavily depend on abstractions and polymorphism.

---

## 57. Confusing Abstraction with Encapsulation

Encapsulation protects data.

Abstraction hides complexity.

---

## 58. Trying to Instantiate Abstract Class

Abstract classes cannot be instantiated directly.

---

## 59. Forgetting to Implement Abstract Methods

Child classes must implement inherited abstract methods.

---

## 60. Overusing Inheritance Instead of Proper Abstraction

Poor abstraction design reduces maintainability.

---

## 61. Ignoring Abstraction Layers

Large backend systems require layered abstraction.

---

## 62. Creating Highly Coupled Architectures

Without abstraction systems become rigid and tightly coupled.

---

## 63. Exposing Internal Complexity

Abstraction should simplify system usage.

---

## 64. Ignoring Enterprise Design Principles

Modern frameworks heavily depend on abstraction-driven architecture.

---

## 65. Confusing Interface and Abstract Class

Interface:
behavior contract.

Abstract class:
partial implementation.

---

## 66. Forgetting to Implement Interface Methods

Implementation classes must implement required methods.

---

## 67. Ignoring Loose Coupling Principles

Interfaces help reduce tight coupling.

---

## 68. Programming Directly to Implementations

Programming to interfaces improves flexibility.

---

## 69. Overusing Concrete Classes

Concrete-only architecture becomes rigid.

---

## 70. Ignoring Dependency Injection Benefits

Interfaces are foundational for dependency injection.

---

## 71. Misunderstanding Multiple Inheritance

Java supports multiple inheritance using interfaces only.

---

## 72. Creating Tightly Coupled Backend Services

Without interfaces systems become difficult to scale and maintain.

---

## 73. Confusing == and equals()

==:
reference comparison.

equals():
logical comparison.

---

## 74. Overriding equals() Without hashCode()

This breaks collection behavior.

---

## 75. Breaking equals() and hashCode() Contract

Equal objects must have same hashCode.

---

## 76. Ignoring toString() Override

Readable object output improves debugging and logging.

---

## 77. Using Mutable Fields in hashCode() Incorrectly

Mutable fields may create inconsistent hashing behavior.

---

## 78. Incorrect Type Casting in equals()

Unsafe casting may cause runtime exceptions.

---

## 79. Ignoring Collection Behavior Impact

equals() and hashCode() directly affect HashMap and HashSet behavior.

---

## 80. Poor Entity Equality Design

Improper equality logic may create severe backend bugs.

---

## 81. Overusing Inheritance Instead of Composition

Modern systems usually prefer composition.

---

## 82. Confusing Aggregation and Composition

Aggregation:
weak ownership.

Composition:
strong ownership.

---

## 83. Ignoring Lifecycle Dependency

Composition child lifecycle depends on parent.

---

## 84. Creating Tightly Coupled Relationships

Poor object relationships reduce scalability.

---

## 85. Misunderstanding HAS-A Relationship

HAS-A relationship represents object containment.

---

## 86. Using Inheritance for Everything

Inheritance should not replace proper composition.

---

## 87. Ignoring Backend Entity Modeling

Proper relationships are critical in backend architecture.

---

## 88. Designing Poor Domain Models

Weak object modeling creates maintainability issues.

---

## 89. Confusing final Reference with Immutable Object

final reference does not guarantee immutable object state.

---

## 90. Trying to Override final Methods

final methods cannot be overridden.

---

## 91. Trying to Extend final Classes

final classes cannot be inherited.

---

## 92. Confusing final, finally, and finalize()

These are completely different concepts.

---

## 93. Ignoring Immutability Benefits

Immutable design improves thread safety and stability.

---

## 94. Overusing Mutable Shared Objects

Shared mutable state creates backend risks.

---

## 95. Assuming final Makes Entire Object Immutable

Only reference/value restriction occurs.

---

## 96. Ignoring Enterprise Stability Principles

Modern backend systems heavily depend on stable immutable design.

---

## 97. Confusing final Reference with Immutable Object

final reference does not guarantee immutable object state.

---

## 98. Adding Setters in Immutable Class

Immutable classes should avoid setters.

---

## 99. Forgetting Defensive Copying

Mutable internal objects should be protected.

---

## 100. Returning Internal Mutable Objects Directly

This breaks immutability.

---

## 101. Ignoring Thread Safety Benefits

Immutable objects naturally improve thread safety.

---

## 102. Using Shared Mutable State Excessively

Shared mutable state creates concurrency risks.

---

## 103. Forgetting private final Fields

Immutable classes should use private final fields.

---

## 104. Ignoring Enterprise Immutable Design Principles

Modern scalable systems heavily prefer immutable architectures.

---

## 105. Creating God Classes

One class handling too many responsibilities violates SRP.

---

## 106. Modifying Stable Code Repeatedly

Violates Open/Closed Principle.

---

## 107. Designing Unsafe Inheritance Hierarchies

Violates Liskov Substitution Principle.

---

## 108. Creating Giant Interfaces

Violates Interface Segregation Principle.

---

## 109. Depending Directly on Concrete Implementations

Violates Dependency Inversion Principle.

---

## 110. Creating Tightly Coupled Services

Tightly coupled systems are difficult to scale and maintain.

---

## 111. Ignoring Abstractions

Abstractions improve flexibility and maintainability.

---

## 112. Ignoring Clean Architecture Principles

Modern backend systems heavily depend on SOLID-driven architecture.

---