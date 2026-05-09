# Operators and Conditional Statements

Operators and conditional statements are used to perform calculations, comparisons, and decision-making in Java programs.

These concepts are heavily used in backend applications for:
- validations
- authentication
- authorization
- business logic
- filtering conditions

---

# 1. What are Operators?

Operators are special symbols used to perform operations on variables and values.

Example:

```java
int sum = 10 + 20;
```

Here:
- `+` is an arithmetic operator
- it adds two numbers

---

# 2. Arithmetic Operators

Arithmetic operators are used for mathematical calculations.

| Operator | Description | Example |
|----------|-------------|----------|
| + | Addition | a + b |
| - | Subtraction | a - b |
| * | Multiplication | a * b |
| / | Division | a / b |
| % | Modulus | a % b |

Example:

```java
int a = 10;
int b = 5;

System.out.println(a + b);
System.out.println(a - b);
System.out.println(a * b);
System.out.println(a / b);
```

---

# 3. Comparison Operators

Comparison operators compare values and return boolean results.

| Operator | Description |
|----------|-------------|
| == | Equal to |
| != | Not equal to |
| > | Greater than |
| < | Less than |
| >= | Greater than or equal to |
| <= | Less than or equal to |

Example:

```java
int age = 20;

System.out.println(age >= 18);
```

Output:

```text
true
```

---

# 4. Logical Operators

Logical operators are used to combine multiple conditions.

| Operator | Description |
|----------|-------------|
| && | AND |
| || | OR |
| ! | NOT |

Example:

```java
int age = 25;
boolean hasLicense = true;

if (age >= 18 && hasLicense) {

    System.out.println("Eligible to drive");
}
```

---

# 5. Conditional Statements

Conditional statements help programs make decisions.

Java supports:
- if
- if-else
- else-if
- switch

---

# 6. if Statement

The `if` statement executes code only if condition is true.

Example:

```java
int marks = 80;

if (marks >= 40) {

    System.out.println("Pass");
}
```

---

# 7. if-else Statement

Example:

```java
int marks = 30;

if (marks >= 40) {

    System.out.println("Pass");

} else {

    System.out.println("Fail");
}
```

---

# 8. Nested if Statement

Nested if means placing one if statement inside another.

Example:

```java
int age = 25;
boolean hasId = true;

if (age >= 18) {

    if (hasId) {

        System.out.println("Entry Allowed");
    }
}
```

---

# 9. switch Statement

Switch is useful when checking multiple fixed values.

Example:

```java
int day = 2;

switch (day) {

    case 1:
        System.out.println("Monday");
        break;

    case 2:
        System.out.println("Tuesday");
        break;

    default:
        System.out.println("Invalid Day");
}
```

---

# 10. Ternary Operator

Ternary operator is a short form of if-else.

Example:

```java
int age = 20;

String result = (age >= 18) ? "Adult" : "Minor";

System.out.println(result);
```

---

# 11. Real Backend Engineering Importance

Operators and conditions are heavily used in:
- API validations
- login systems
- role-based access
- filtering data
- authentication checks
- business rules
- request processing

Almost every backend application depends on conditional logic.
