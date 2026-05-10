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