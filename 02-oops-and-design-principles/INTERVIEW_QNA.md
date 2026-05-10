## Q1. What is a class in Java?

A class is a blueprint used to create objects.

It defines:
- variables
- methods
- object behavior

---

## Q2. What is an object in Java?

An object is an instance of a class.

Objects contain actual data and behavior.

---

## Q3. Difference between class and object?

Class:
Blueprint/template.

Object:
Real instance created from class.

---

## Q4. What are state and behavior in OOP?

State:
Variables/fields.

Behavior:
Methods/functions.

---

## Q5. What does the new keyword do?

The new keyword:
- creates object
- allocates heap memory
- returns object reference

---

## Q6. Where are objects stored in Java?

Objects are stored in heap memory.

---

## Q7. What is an object reference?

Reference variable stores memory address/reference of object.

---

## Q8. Can multiple references point to same object?

Yes.

Multiple references may point to same object in heap memory.

---

## Q9. Why are classes important in backend engineering?

Classes help model:
- users
- products
- orders
- services
- entities

Backend systems are heavily object-oriented.

---

## Q10. What is object-oriented programming?

OOP is programming paradigm based on:
- objects
- classes
- encapsulation
- inheritance
- polymorphism
- abstraction

---

## Q11. What is a constructor in Java?

A constructor is a special method used to initialize objects.

---

## Q12. What are the rules of constructors?

- same name as class
- no return type
- automatically executed

---

## Q13. What is default constructor?

If no constructor is defined,
Java provides default constructor automatically.

---

## Q14. What is parameterized constructor?

Constructor that accepts parameters for object initialization.

---

## Q15. What is constructor overloading?

Creating multiple constructors with different parameter lists.

---

## Q16. What is constructor chaining?

Calling one constructor from another using `this()`.

---

## Q17. What is the purpose of constructors?

Constructors initialize valid object state.

---

## Q18. Difference between constructor and method?

Constructors initialize objects.

Methods perform operations.

---

## Q19. Can constructor have return type?

No.

Constructors cannot have return type.

---

## Q20. Why are constructors important in backend engineering?

Constructors are heavily used in:
- dependency injection
- DTO creation
- immutable objects
- entity initialization

---

## Q21. What is encapsulation?

Encapsulation means:
- wrapping data and methods together
- restricting direct access to data

---

## Q22. Why is encapsulation important?

Encapsulation improves:
- security
- maintainability
- controlled access
- object integrity

---

## Q23. What is data hiding?

Restricting direct access to internal object data.

Usually achieved using private variables.

---

## Q24. Why are variables usually private in Java?

Private variables protect object integrity and prevent invalid modifications.

---

## Q25. What are getters and setters?

Getter:
Reads data.

Setter:
Updates data.

---

## Q26. Why are setters useful?

Setters allow validation before updating data.

---

## Q27. Difference between encapsulation and abstraction?

Encapsulation:
Data protection.

Abstraction:
Hiding implementation complexity.

---

## Q28. Why is encapsulation important in backend engineering?

Encapsulation protects:
- backend entities
- DTOs
- business rules
- object validity

---

## Q29. Can encapsulation improve security?

Yes.

Encapsulation prevents unauthorized or invalid data modification.

---

## Q30. What is controlled access?

Allowing access to object data only through defined methods.

---

## Q31. What is inheritance in Java?

Inheritance allows one class to acquire properties and behavior of another class.

---

## Q32. Which keyword is used for inheritance?

`extends`

---

## Q33. What is parent class?

Class whose properties are inherited by another class.

Also called superclass/base class.

---

## Q34. What is child class?

Class that inherits from parent class.

Also called subclass/derived class.

---

## Q35. What is IS-A relationship?

Inheritance represents IS-A relationship.

Example:
Dog IS-A Animal.

---

## Q36. What are advantages of inheritance?

- code reuse
- hierarchy creation
- maintainability
- extensibility

---

## Q37. What are disadvantages of inheritance?

- tight coupling
- rigid design
- maintainability problems if overused

---

## Q38. Does Java support multiple inheritance using classes?

No.

Java supports multiple inheritance using interfaces only.

---

## Q39. What are types of inheritance in Java?

- single inheritance
- multilevel inheritance
- hierarchical inheritance

---

## Q40. Why do modern backend systems avoid excessive inheritance?

Because deep inheritance creates tightly coupled and difficult-to-maintain systems.

---

## Q41. What is super keyword in Java?

`super` refers to immediate parent class object.

---

## Q42. Why is super keyword used?

It is used to:
- access parent variables
- access parent methods
- call parent constructors

---

## Q43. How do you call parent constructor?

Using:

```java
super();
```

---

## Q44. What is difference between this and super?

this:
Current class object.

super:
Parent class object.

---

## Q45. Can super access private parent members?

No.

Private members are not directly accessible.

---

## Q46. When does parent constructor execute?

Parent constructor executes before child constructor.

---

## Q47. Why must super() be first statement?

Because parent initialization must happen before child initialization.

---

## Q48. Can super call parent methods?

Yes.

Example:

```java
super.display();
```

---

## Q49. Is super keyword important in backend engineering?

Yes.

It is heavily used in:
- framework extension
- enterprise inheritance
- reusable architectures

---

## Q50. Can super and this be used together?

Yes.

But constructor rules must be followed properly.

---

## Q51. What is method overloading?

Method overloading means multiple methods with same name but different parameters.

---

## Q52. What is method overriding?

Method overriding means child class provides its own implementation of parent method.

---

## Q53. What is compile-time polymorphism?

Method overloading is compile-time polymorphism.

Method resolution happens during compilation.

---

## Q54. What is runtime polymorphism?

Method overriding is runtime polymorphism.

Method resolution happens during runtime.

---

## Q55. Does overloading require inheritance?

No.

Overloading can happen inside same class.

---

## Q56. Does overriding require inheritance?

Yes.

Overriding requires parent-child relationship.

---

## Q57. Can methods be overloaded by changing only return type?

No.

Return type alone is insufficient.

---

## Q58. What is dynamic method dispatch?

JVM deciding actual overridden method execution during runtime.

---

## Q59. Why is @Override annotation important?

It improves:
- readability
- safety
- maintainability

---

## Q60. Why is overriding important in backend engineering?

Overriding enables:
- framework extensibility
- runtime flexibility
- custom implementations

---

## Q61. What is polymorphism in Java?

Polymorphism means one thing taking multiple forms.

---

## Q62. What are types of polymorphism?

- compile-time polymorphism
- runtime polymorphism

---

## Q63. Which concept provides compile-time polymorphism?

Method overloading.

---

## Q64. Which concept provides runtime polymorphism?

Method overriding.

---

## Q65. What is dynamic method dispatch?

JVM deciding actual overridden method execution during runtime.

---

## Q66. What is parent reference child object?

Example:

```java
Animal animal = new Dog();
```

Parent reference points to child object.

---

## Q67. Why is polymorphism important?

Polymorphism improves:
- flexibility
- scalability
- maintainability
- loose coupling

---

## Q68. What is loose coupling?

Reducing dependency between components.

---

## Q69. Why do frameworks heavily use polymorphism?

Polymorphism enables:
- extensibility
- runtime flexibility
- interchangeable implementations

---

## Q70. Why is polymorphism important in backend engineering?

Modern backend frameworks depend heavily on:
- interfaces
- runtime dispatch
- dependency injection
- flexible architectures

---

## Q71. What is abstraction in Java?

Abstraction means hiding implementation complexity and exposing essential behavior only.

---

## Q72. What is abstract class?

Abstract class is incomplete class that cannot be instantiated directly.

---

## Q73. What is abstract method?

Method without implementation.

Child classes must implement it.

---

## Q74. Can abstract class contain normal methods?

Yes.

Abstract classes may contain:
- abstract methods
- implemented methods

---

## Q75. Can we create object of abstract class?

No.

Abstract classes cannot be instantiated directly.

---

## Q76. What is partial abstraction?

Abstract classes providing both:
- implemented methods
- abstract methods

---

## Q77. Difference between abstraction and encapsulation?

Encapsulation:
protects data.

Abstraction:
hides complexity.

---

## Q78. Why is abstraction important?

Abstraction improves:
- maintainability
- scalability
- loose coupling
- flexibility

---

## Q79. Why do frameworks heavily use abstraction?

Abstraction enables:
- extensibility
- interchangeable implementations
- hidden complexity

---

## Q80. Why is abstraction important in backend engineering?

Modern backend systems use abstraction heavily for:
- service layers
- repositories
- scalable architectures
- framework design

---

## Q81. What is interface in Java?

Interface is blueprint of behavior.

It defines what should happen, not implementation details.

---

## Q82. Which keyword is used to implement interface?

`implements`

---

## Q83. Why are interfaces important?

Interfaces improve:
- loose coupling
- flexibility
- maintainability
- scalability

---

## Q84. Can Java support multiple inheritance using interfaces?

Yes.

Java supports multiple inheritance using interfaces.

---

## Q85. Difference between interface and abstract class?

Interface:
behavior contract.

Abstract class:
partial implementation.

---

## Q86. Why do modern backend systems heavily use interfaces?

Interfaces enable:
- dependency injection
- interchangeable implementations
- scalability
- testability

---

## Q87. What is loose coupling?

Reducing dependency between components using abstractions.

---

## Q88. Why is programming to interfaces important?

It improves:
- flexibility
- maintainability
- scalability

---

## Q89. Can interface contain implemented methods?

Modern Java supports:
- default methods
- static methods

---

## Q90. Why are interfaces important in Spring Framework?

Spring heavily depends on interfaces for:
- dependency injection
- service abstraction
- repository abstraction

---

## Q91. What is Object class in Java?

Object class is parent class of all Java classes.

---

## Q92. Why is Object class important?

It provides common methods like:
- toString()
- equals()
- hashCode()

---

## Q93. What is toString() method?

Used to provide string representation of object.

---

## Q94. What is equals() method?

Used for logical equality comparison.

---

## Q95. Difference between == and equals()?

==:
reference comparison.

equals():
logical/content comparison.

---

## Q96. What is hashCode() method?

Used for hashing-based collections.

Examples:
- HashMap
- HashSet

---

## Q97. What is equals() and hashCode() contract?

If equals() returns true,
hashCode() must be same.

---

## Q98. Why are equals() and hashCode() important in backend engineering?

They are heavily used in:
- collections
- caching
- JPA entities
- Hibernate

---

## Q99. What happens if hashCode() contract is broken?

It may create:
- collection bugs
- caching issues
- inconsistent behavior

---

## Q100. Why is toString() useful?

Useful for:
- logging
- debugging
- monitoring

---

## Q101. What is association in Java?

Association represents relationship between independent objects.

---

## Q102. What is aggregation?

Aggregation is weak HAS-A relationship.

Child can exist independently.

---

## Q103. What is composition?

Composition is strong HAS-A relationship.

Child lifecycle depends on parent.

---

## Q104. Difference between aggregation and composition?

Aggregation:
weak ownership.

Composition:
strong ownership.

---

## Q105. What is HAS-A relationship?

Relationship where object contains another object.

---

## Q106. Difference between inheritance and composition?

Inheritance:
IS-A relationship.

Composition:
HAS-A relationship.

---

## Q107. Why do modern backend systems prefer composition?

Composition improves:
- flexibility
- maintainability
- loose coupling

---

## Q108. Where are association and composition used in backend systems?

Used in:
- JPA entities
- domain models
- service architectures
- microservices

---

## Q109. Why is composition important in scalable systems?

Composition reduces tight coupling and improves extensibility.

---

## Q110. Which is preferred in modern architecture: inheritance or composition?

Modern systems usually prefer composition over inheritance.

---

## Q111. What is final keyword in Java?

The `final` keyword represents restriction/non-modifiable behavior.

---

## Q112. What is final variable?

A variable whose value cannot change after initialization.

---

## Q113. What is final method?

Method that cannot be overridden.

---

## Q114. What is final class?

Class that cannot be inherited.

---

## Q115. Why is String class final?

For:
- security
- immutability
- thread safety
- performance

---

## Q116. What is final reference variable?

Reference cannot point to another object.

Object contents may still change.

---

## Q117. Difference between final and immutable object?

final:
reference/value restriction.

Immutable:
entire object state cannot change.

---

## Q118. Difference between final, finally, and finalize()?

final:
keyword.

finally:
exception block.

finalize():
garbage collection related method.

---

## Q119. Why is final important in backend engineering?

final helps:
- immutability
- thread safety
- stable architecture
- safer backend systems

---

## Q120. Why are immutable objects important?

Immutable objects are:
- thread-safe
- predictable
- safer for scalable systems

---

## Q121. What is immutability in Java?

Immutability means object state cannot change after creation.

---

## Q122. Why are immutable objects important?

Immutable objects are:
- thread-safe
- predictable
- safer
- easier to debug

---

## Q123. How do you create immutable class?

Common rules:
- make class final
- make fields private final
- initialize via constructor
- provide only getters
- avoid setters

---

## Q124. Why is String immutable?

For:
- security
- thread safety
- caching
- performance

---

## Q125. What is defensive copying?

Creating copies of mutable objects to prevent external modification.

---

## Q126. Why is immutability important in backend engineering?

Immutability improves:
- thread safety
- scalability
- predictable behavior

---

## Q127. Are immutable objects thread-safe?

Yes.

Because state never changes.

---

## Q128. Difference between mutable and immutable objects?

Mutable:
state can change.

Immutable:
state cannot change after creation.

---

## Q129. Does final reference guarantee immutability?

No.

Object contents may still change.

---

## Q130. Where is immutability heavily used?

Used in:
- DTOs
- configuration objects
- caching systems
- distributed systems

---

## Q131. What are SOLID principles?

SOLID is a set of object-oriented design principles for scalable and maintainable systems.

---

## Q132. What is Single Responsibility Principle (SRP)?

One class should have only one reason to change.

---

## Q133. What is Open/Closed Principle (OCP)?

Systems should be open for extension but closed for modification.

---

## Q134. What is Liskov Substitution Principle (LSP)?

Child classes should safely replace parent classes.

---

## Q135. What is Interface Segregation Principle (ISP)?

Clients should not depend on methods they do not use.

---

## Q136. What is Dependency Inversion Principle (DIP)?

Depend on abstractions instead of concrete implementations.

---

## Q137. Why are SOLID principles important?

SOLID improves:
- scalability
- maintainability
- flexibility
- testability

---

## Q138. Which SOLID principle powers dependency injection?

Dependency Inversion Principle (DIP).

---

## Q139. Why do modern backend systems heavily follow SOLID?

SOLID helps build:
- scalable systems
- loosely coupled architectures
- maintainable codebases

---

## Q140. Why are SOLID principles important in Spring Framework?

Spring heavily depends on:
- abstractions
- dependency injection
- loose coupling
- extensible architecture

---