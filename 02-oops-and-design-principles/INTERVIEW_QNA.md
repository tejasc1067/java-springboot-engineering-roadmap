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