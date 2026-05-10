# super Keyword in Java

The `super` keyword is used in inheritance to refer to parent class members.

It is one of the most important concepts in Object-Oriented Programming.

The `super` keyword is heavily used for:
- parent constructor calling
- parent method access
- parent variable access
- inheritance hierarchy management

It is very important in:
- enterprise Java
- framework development
- backend architectures
- Spring-based systems

---

# 1. What is super Keyword?

The `super` keyword refers to:
# immediate parent class object

It is used inside child class.

Example:

```java
super.display();
```

---

# 2. Why super is Important?

Inheritance creates parent-child relationships.

Sometimes child class needs to:
- access parent behavior
- reuse parent logic
- initialize parent state

The `super` keyword enables this.

---

# 3. Accessing Parent Variables

If parent and child contain same variable names,
`super` helps access parent variable.

Example:

```java
super.name
```

---

# 4. Accessing Parent Methods

Child class may call parent methods using:

```java
super.displayInfo();
```

This helps:
- reuse parent logic
- extend behavior
- avoid duplicate code

---

# 5. Calling Parent Constructor

Parent constructor can be called using:

```java
super();
```

or:

```java
super(parameters);
```

Very important constructor behavior.

---

# 6. Constructor Execution Flow

When child object is created:
- parent constructor executes first
- child constructor executes later

This ensures proper object initialization.

---

# 7. Important Rule

`super()` must be first statement inside constructor.

Incorrect:

```java
System.out.println("Hello");
super();
```

Correct:

```java
super();
System.out.println("Hello");
```

---

# 8. super vs this

`super`
refers to parent class.

`this`
refers to current class object.

Very important interview topic.

---

# 9. Real-World Backend Examples

The `super` keyword is heavily used in:
- framework extension
- reusable base classes
- enterprise inheritance hierarchies
- Spring infrastructure

Example:
- RuntimeException
- custom exceptions
- framework adapters

---

# 10. Why super Matters in Backend Engineering?

Backend systems often use:
- reusable parent classes
- abstract base implementations
- inheritance hierarchies

The `super` keyword helps manage parent-child behavior safely.

---

# 11. Common Beginner Confusions

Beginners often confuse:
- this
- super

Remember:

this:
Current object.

super:
Parent object.

---

# 12. Industry Relevance

The `super` keyword is important for:
- enterprise Java
- Spring Framework
- backend frameworks
- scalable inheritance systems

Strong understanding helps build maintainable object hierarchies.