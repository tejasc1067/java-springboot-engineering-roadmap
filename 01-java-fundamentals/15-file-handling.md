# File Handling in Java

File handling allows Java applications to create, read, write, and manage files.

Backend systems constantly work with files for:
- logs
- reports
- configurations
- uploads
- downloads
- CSV processing

Understanding file handling properly is important for backend engineering.

---

# 1. What is File Handling?

File handling means performing operations on files such as:
- create
- read
- write
- update
- delete

Java provides built-in APIs for file operations.

---

# 2. Why File Handling is Important?

Backend systems frequently use files for:
- logging
- report generation
- batch processing
- configuration management

Without proper file handling:
- applications may lose data
- resource leaks may occur
- debugging becomes difficult

---

# 3. File Class

Java provides File class for file and directory operations.

Example:

```java
File file = new File("sample.txt");
```

Used for:
- checking file existence
- creating files
- getting file details

---

# 4. Writing Files

Files can be written using:
- FileWriter
- BufferedWriter

Example:

```java
FileWriter writer = new FileWriter("sample.txt");
```

---

# 5. Reading Files

Files can be read using:
- FileReader
- BufferedReader

Example:

```java
BufferedReader reader =
        new BufferedReader(
                new FileReader("sample.txt")
        );
```

---

# 6. BufferedReader

BufferedReader improves file reading performance.

Benefits:
- efficient reading
- line-by-line processing
- reduced I/O operations

Backend systems heavily use buffering for performance.

---

# 7. BufferedWriter

BufferedWriter improves file writing performance.

Benefits:
- reduced disk operations
- efficient large file writing

Important for:
- logs
- reports
- batch systems

---

# 8. Exception Handling in File Operations

File operations may fail due to:
- missing files
- permission issues
- invalid paths

Therefore:
exception handling is extremely important.

---

# 9. try-with-resources

Introduced in Java 7.

Automatically closes resources.

Example:

```java
try (BufferedReader reader =
        new BufferedReader(
                new FileReader("sample.txt"))) {

}
```

Benefits:
- safer resource management
- cleaner code
- reduced memory leaks

VERY important backend practice.

---

# 10. File Paths

Developers should avoid hardcoded file paths whenever possible.

Incorrect:

```text
C:\Users\Files\data.txt
```

Better:
- relative paths
- configurable paths

Important for scalable backend systems.

---

# 11. Backend Engineering Importance

File handling is heavily used in:
- logging systems
- report generation
- configuration management
- upload/download systems
- batch processing

Spring Boot applications heavily depend on file operations.

---

# 12. Industry Relevance

Strong file handling skills help developers:
- build reliable systems
- process external data
- manage logs efficiently
- avoid resource leaks

File handling is a foundational backend engineering skill.
