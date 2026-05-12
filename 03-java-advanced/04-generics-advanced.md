# Advanced Generics in Java

Advanced generics are heavily used in:
- Spring Framework
- collections framework
- repositories
- reusable libraries
- enterprise backend systems

Modern Java architecture depends heavily on advanced generics.

Very important enterprise Java topic.

---

# 1. Bounded Types

Bounded types restrict:
# allowed generic types

Example:

```java
<T extends Number>
```

Only numeric types are allowed.

Very important compile-time safety feature.

---

# 2. Why Bounded Types are Important?

Bounded types improve:
- type safety
- reusable APIs
- compile-time validation

Very important architecture principle.

---

# 3. Wildcards

Wildcard:
# ?

represents unknown type.

Used heavily in:
- collections
- APIs
- reusable abstractions

Very important Java feature.

---

# 4. Upper Bounded Wildcards

Example:

```java
<? extends Number>
```

Means:
- Number
- subclasses of Number

Usually used for:
# reading data safely

Very important collections concept.

---

# 5. Lower Bounded Wildcards

Example:

```java
<? super Integer>
```

Means:
- Integer
- parent classes of Integer

Usually used for:
# safely inserting data

Very important collections design principle.

---

# 6. PECS Principle

Very important interview topic.

PECS means:

# Producer Extends
# Consumer Super

Rules:
- producer → extends
- consumer → super

Very important collections API concept.

---

# 7. Covariance Basics

Covariance allows:
# reading safely

Commonly associated with:
```java
<? extends Type>
```

Very important generic behavior concept.

---

# 8. Contravariance Basics

Contravariance allows:
# safe insertion

Commonly associated with:
```java
<? super Type>
```

Very important collections behavior concept.

---

# 9. Type Erasure

Java generics mainly exist at:
# compile time

At runtime:
generic type information is mostly removed.

This behavior is called:
# type erasure

Very important advanced Java topic.

---

# 10. Generic Restrictions

Generics have restrictions.

Examples:
- cannot directly use primitive types
- cannot create generic arrays directly
- static members cannot use generic type parameter directly

Very important interview topic.

---

# 11. Real-World Backend Examples

Advanced generics are heavily used in:
- Spring Data JPA
- repositories
- API wrappers
- collections framework
- reusable backend libraries

Very important enterprise Java topic.

---

# 12. Common Beginner Confusions

Beginners often:
- misuse wildcards
- confuse extends vs super
- misunderstand PECS
- misunderstand type erasure

Advanced generics require careful understanding.

---

# 13. Industry Relevance

Modern enterprise Java heavily depends on:
- advanced generics
- type-safe abstractions
- reusable frameworks
- collections architecture

Framework-level backend engineering strongly relies on advanced generics.