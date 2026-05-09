# Variables and Memory Basics

Variables are used to store data in memory.

Every Java application works with data such as:
- numbers
- text
- objects
- user input

Variables allow programs to store and manipulate this data.

---

# 1. What is a Variable?

A variable is a named memory location used to store data.

Example:

```java
int age = 25;
```

Here:
- `age` is variable name
- `25` is stored value
- `int` is data type

---

# 2. Why Variables are Important?

Variables help programs:
- store user data
- perform calculations
- manage application state
- process backend requests

Backend applications constantly store:
- user information
- API responses
- database records

---

# 3. Primitive Data Types

Primitive types store actual values directly.

Java primitive types:

| Type | Example | Size |
|------|----------|------|
| byte | 10 | 1 byte |
| short | 100 | 2 bytes |
| int | 1000 | 4 bytes |
| long | 100000L | 8 bytes |
| float | 10.5f | 4 bytes |
| double | 20.99 | 8 bytes |
| char | 'A' | 2 bytes |
| boolean | true | JVM dependent |

---

# 4. Reference Data Types

Reference types store memory addresses of objects.

Example:

```java
String name = "Tejas";
```

Here:
- actual object exists in heap memory
- variable stores reference/address

Reference types include:
- String
- Arrays
- Objects
- Collections

---

# 5. Primitive vs Reference Types

Primitive:
- stores actual value
- fixed memory size
- faster access

Reference:
- stores object reference
- points to heap memory
- used for complex data structures

---

# 6. Memory Basics

Java mainly uses:
- Stack Memory
- Heap Memory

## Stack Memory

Stores:
- method calls
- local variables
- references

## Heap Memory

Stores:
- objects
- arrays
- application data

---

# 7. Real Backend Engineering Importance

Understanding memory helps in:
- debugging applications
- optimizing performance
- understanding object references
- preventing memory leaks
- improving backend scalability
