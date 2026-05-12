## 1. Catching Generic Exception Everywhere

Overusing generic Exception hides real problems.

---

## 2. Swallowing Exceptions Silently

Ignoring exceptions makes debugging difficult.

---

## 3. Using Exceptions for Normal Flow Control

Exceptions should represent abnormal situations.

---

## 4. Confusing Checked and Unchecked Exceptions

These have very different handling behaviors.

---

## 5. Ignoring finally Block for Cleanup

Resources should be cleaned properly.

---

## 6. Throwing Generic Exceptions Excessively

Prefer meaningful exception types.

---

## 7. Losing Root Cause Information

Always preserve useful debugging information.

---

## 8. Printing Stack Traces Carelessly in Production

Production systems should use proper logging instead.

---

## 9. Overusing Generic Exception

Generic exceptions reduce clarity and maintainability.

---

## 10. Creating Meaningless Custom Exceptions

Exception names should represent real business/domain problems.

---

## 11. Hiding Root Cause During Exception Wrapping

Always preserve original exception cause.

---

## 12. Using Checked Exceptions Everywhere

Not all exceptions should be checked exceptions.

---

## 13. Ignoring Domain-Level Exception Design

Business exceptions improve backend readability.

---

## 14. Logging Same Exception Multiple Times

Duplicate logging creates noisy production logs.

---

## 15. Exposing Sensitive Information in Exception Messages

Never leak internal details to API consumers.

---

## 16. Swallowing Runtime Exceptions Silently

Silent failures make debugging extremely difficult.

---

## 17. Using Raw Types Excessively

Raw types reduce type safety.

---

## 18. Misunderstanding Generic Syntax

Generic syntax confusion is very common among beginners.

---

## 19. Ignoring Compile-Time Type Safety

Generics exist to prevent runtime type errors.

---

## 20. Using Object Instead of Proper Generics

This reduces readability and maintainability.

---

## 21. Overcomplicating Generic Design

Unnecessarily complex generics reduce code clarity.

---

## 22. Ignoring Generics in Collections

Collections should use proper type parameters.

---

## 23. Creating Unsafe Cast Operations

Improper casting may cause runtime failures.

---

## 24. Misusing Generic Interfaces

Generic abstractions should remain clean and reusable.

---

## 25. Misusing extends and super

Incorrect wildcard usage causes API design problems.

---

## 26. Ignoring PECS Principle

PECS is very important for collections design.

---

## 27. Misunderstanding Type Erasure

Generics mainly exist at compile time.

---

## 28. Trying to Use Primitive Types in Generics

Generics require wrapper classes.

---

## 29. Creating Unsafe Generic APIs

Poor wildcard design reduces type safety.

---

## 30. Overusing Wildcards Everywhere

Unnecessary wildcard usage reduces readability.

---

## 31. Creating Generic Arrays Directly

Generic arrays have runtime limitations.

---

## 32. Confusing Covariance and Contravariance

These concepts are foundational for advanced generics.

---