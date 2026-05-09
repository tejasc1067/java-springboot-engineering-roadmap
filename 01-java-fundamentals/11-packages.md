# Packages in Java

Packages are used to organize Java classes and interfaces into structured groups.

Packages help developers:
- organize code
- avoid naming conflicts
- improve maintainability
- build modular applications

Backend applications heavily depend on packages for clean architecture and scalable project structure.

Understanding packages properly is extremely important for backend engineering.

---

# 1. What is a Package?

A package is a collection of related classes and interfaces.

Example:

```java
package com.company.project;
```

Here:
- `com.company.project` is package name

---

# 2. Why Packages are Important?

Without packages:
- large applications become messy
- class naming conflicts occur
- maintainability becomes difficult

Packages improve:
- organization
- modularity
- scalability

---

# 3. Package Declaration

Package declaration must be first statement in Java file.

Example:

```java
package fundamentals;
```

---

# 4. Import Statement

Import statements allow using classes from other packages.

Example:

```java
import java.util.Scanner;
```

---

# 5. Built-in Packages

Java provides many built-in packages.

Examples:
- java.util
- java.io
- java.lang
- java.time

These packages provide:
- collections
- file handling
- utilities
- date/time APIs

---

# 6. User-Defined Packages

Developers can create custom packages.

Example structure:

```text
com.company.project
```

This improves project organization.

---

# 7. Package Naming Convention

Recommended conventions:
- lowercase letters
- meaningful names
- reverse domain naming

Example:

```text
com.tejas.backend
```

---

# 8. Package Folder Structure

Package structure matches folder structure.

Example:

```text
src/
└── com/
    └── company/
        └── project/
```

---

# 9. Real Backend Engineering Importance

Packages are heavily used in backend systems for separating:
- controllers
- services
- repositories
- entities
- configurations
- exceptions

Example Spring Boot structure:

```text
controller/
service/
repository/
entity/
dto/
config/
```

---

# 10. Industry Relevance

Large enterprise applications may contain:
- hundreds of packages
- thousands of classes

Proper package structure improves:
- maintainability
- scalability
- team collaboration

Strong package understanding is foundational for enterprise backend engineering.
