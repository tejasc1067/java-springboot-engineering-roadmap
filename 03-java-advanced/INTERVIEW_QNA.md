## Q1. What is exception in Java?

Exception is runtime abnormal condition that interrupts normal program flow.

---

## Q2. Why is exception handling important?

It improves:
- reliability
- debugging
- resilience
- maintainability

---

## Q3. Difference between Error and Exception?

Error:
serious system-level failure.

Exception:
application/runtime problem.

---

## Q4. What are checked exceptions?

Checked exceptions are checked at compile time.

Example:
IOException

---

## Q5. What are unchecked exceptions?

Unchecked exceptions occur at runtime.

Example:
NullPointerException

---

## Q6. Difference between throw and throws?

throw:
used to explicitly throw exception.

throws:
used to declare possible exceptions.

---

## Q7. What is finally block?

finally block executes whether exception occurs or not.

---

## Q8. What is exception propagation?

Unhandled exception moves up call stack.

---

## Q9. Why are stack traces important?

Stack traces help debug runtime failures.

---

## Q10. Why is exception handling important in backend engineering?

Backend systems constantly face:
- database failures
- API failures
- network issues
- invalid requests

---

## Q11. What is custom exception in Java?

Custom exception is user-defined exception class.

---

## Q12. Why are custom exceptions important?

They improve:
- readability
- debugging
- maintainability
- business clarity

---

## Q13. Difference between checked and unchecked custom exceptions?

Checked:
extends Exception.

Unchecked:
extends RuntimeException.

---

## Q14. What is exception chaining?

Wrapping one exception inside another exception.

---

## Q15. Why is exception chaining important?

It preserves root cause for debugging.

---

## Q16. What are business exceptions?

Exceptions representing domain/business failures.

Example:
UserNotFoundException

---

## Q17. What is fail-fast principle?

Applications should fail early and clearly.

---

## Q18. Why should generic Exception be avoided excessively?

It reduces debugging clarity.

---

## Q19. Why are custom exceptions important in backend systems?

Backend systems require meaningful domain-level error handling.

---

## Q20. What is exception wrapping?

Converting low-level exceptions into business/domain exceptions.

---