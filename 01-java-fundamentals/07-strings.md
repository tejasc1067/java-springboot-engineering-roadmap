# Strings in Java

Strings are one of the most commonly used data types in Java.

Backend applications constantly process strings for:
- usernames
- passwords
- API requests
- JSON data
- database records
- logs
- validations

Understanding strings properly is extremely important for backend engineering.

---

# 1. What is a String?

A String is a sequence of characters.

Example:

```java
String name = "Tejas";
```

Here:
- `String` is data type
- `"Tejas"` is string value

---

# 2. Why Strings are Important?

Strings are heavily used in:
- user input
- API communication
- database queries
- authentication systems
- file handling

Almost every backend application depends on strings.

---

# 3. Creating Strings

Example:

```java
String city = "Pune";
```

Another way:

```java
String country = new String("India");
```

---

# 4. String Immutability

Strings in Java are immutable.

Meaning:
- string values cannot be changed after creation

Example:

```java
String name = "Java";

name.concat(" Backend");
```

Original string remains unchanged.

This improves:
- security
- memory optimization
- thread safety

---

# 5. Common String Methods

Java provides many useful string methods.

---

## length()

Returns string length.

Example:

```java
String language = "Java";

System.out.println(language.length());
```

---

## toUpperCase()

Converts string to uppercase.

Example:

```java
System.out.println(language.toUpperCase());
```

---

## toLowerCase()

Converts string to lowercase.

---

## charAt()

Accesses character using index.

Example:

```java
System.out.println(language.charAt(0));
```

---

## contains()

Checks if string contains value.

Example:

```java
System.out.println(language.contains("av"));
```

---

## equals()

Compares actual string values.

Example:

```java
String a = "Java";
String b = "Java";

System.out.println(a.equals(b));
```

---

# 6. == vs equals()

VERY IMPORTANT interview concept.

`==`
- compares references

`equals()`
- compares actual values

Example:

```java
String a = new String("Java");
String b = new String("Java");

System.out.println(a == b);

System.out.println(a.equals(b));
```

---

# 7. String Concatenation

Strings can be combined using `+`.

Example:

```java
String firstName = "Tejas";
String lastName = "Chumbalkar";

System.out.println(firstName + " " + lastName);
```

---

# 8. StringBuilder Introduction

Frequent string modification creates unnecessary objects.

Java provides:
- StringBuilder
- StringBuffer

for efficient string manipulation.

Example:

```java
StringBuilder builder = new StringBuilder("Java");

builder.append(" Backend");
```

---

# 9. Real Backend Engineering Importance

Strings are used in:
- REST APIs
- JSON processing
- request validation
- authentication
- logging
- database queries
- file handling

Strong string knowledge is essential for backend development.

---

# 10. Industry Relevance

Large backend systems process millions of strings daily.

Understanding:
- immutability
- string comparison
- string performance
- StringBuilder

helps developers build efficient and scalable applications.
