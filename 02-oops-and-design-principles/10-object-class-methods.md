# Object Class Methods in Java

Every class in Java implicitly extends:
# Object class

The Object class provides common methods used across Java applications.

These methods are extremely important for:
- collections
- hashing
- caching
- equality comparison
- logging
- debugging

Modern backend systems heavily depend on proper implementation of:
- toString()
- equals()
- hashCode()

---

# 1. What is Object Class?

In Java:
every class implicitly extends Object class.

Example:

```java
class Student {

}
```

Internally behaves like:

```java
class Student extends Object {

}
```

Very important Java concept.

---

# 2. Why Object Class is Important?

Object class provides common methods such as:
- toString()
- equals()
- hashCode()
- getClass()

All Java objects inherit these methods.

---

# 3. toString() Method

Used for:
# object representation as string

Example:

```java
System.out.println(object);
```

Internally calls:
- toString()

Useful for:
- logging
- debugging
- monitoring

---

# 4. equals() Method

Used for:
# logical equality comparison

Example:

```java
user1.equals(user2)
```

Very important for:
- collections
- entity comparison
- business logic

---

# 5. == vs equals()

## ==

Checks:
- reference equality

Checks whether references point to same object.

---

## equals()

Checks:
- logical/content equality

Very important distinction.

---

# 6. hashCode() Method

Used for:
# hashing-based collections

Examples:
- HashMap
- HashSet
- caching systems

Very important backend engineering concept.

---

# 7. equals() and hashCode() Contract

Important rule:

If:
```text
equals() == true
```

Then:
```text
hashCode() must be same
```

Breaking this rule may create:
- collection bugs
- caching issues
- inconsistent behavior

Very important enterprise Java concept.

---

# 8. Object Identity vs Logical Equality

## Object Identity

Memory/reference equality.

Example:

```java
obj1 == obj2
```

---

## Logical Equality

Content equality.

Example:

```java
obj1.equals(obj2)
```

Very important distinction.

---

# 9. Real-World Backend Examples

These methods are heavily used in:
- JPA entities
- Hibernate
- Redis caching
- DTO comparison
- collections
- logging systems

Improper implementation may create severe backend bugs.

---

# 10. Common Beginner Confusions

Beginners often confuse:
- ==
- equals()

Remember:

==:
reference comparison.

equals():
logical comparison.

---

# 11. Industry Relevance

Object class methods are foundational for:
- enterprise Java
- collections framework
- Spring applications
- Hibernate/JPA
- scalable backend systems

Strong understanding is essential for backend engineering.