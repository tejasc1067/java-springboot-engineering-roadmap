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

## 33. Using Wrong Collection Type

Incorrect collection selection causes performance issues.

---

## 34. Confusing List and Set

List allows duplicates, Set does not.

---

## 35. Ignoring Collection Performance

Different collections have different performance characteristics.

---

## 36. Overusing ArrayList Everywhere

Not every use case requires ArrayList.

---

## 37. Misunderstanding Queue Behavior

Queues are heavily used for processing order management.

---

## 38. Using Map Without Understanding Key Uniqueness

Duplicate keys overwrite previous values.

---

## 39. Ignoring Collections API Design

Collections are designed around reusable interfaces.

---

## 40. Treating Collections as Simple Containers Only

Collections are foundational backend engineering structures.

---

## 41. Assuming All ArrayList Operations Are Fast

Middle insertion and deletion are expensive.

---

## 42. Ignoring Resizing Overhead

Frequent resizing impacts performance.

---

## 43. Using ArrayList for Frequent Middle Insertions

LinkedList may be more suitable in some cases.

---

## 44. Confusing Arrays and ArrayList

They have very different behavior and APIs.

---

## 45. Ignoring Internal Shifting Costs

Insertion and deletion may shift many elements.

---

## 46. Overusing ArrayList Without Performance Thinking

Collection selection impacts scalability.

---

## 47. Misusing Iterators

Incorrect iterator usage may cause runtime issues.

---

## 48. Treating ArrayList as Unlimited Free Memory

Large collections impact memory consumption.

---

## 49. Assuming LinkedList is Always Faster

LinkedList is slower for random access.

---

## 50. Ignoring LinkedList Memory Overhead

Each node stores extra references.

---

## 51. Using LinkedList Without Understanding Traversal Cost

Traversal impacts scalability.

---

## 52. Overusing Vector in Modern Applications

Modern systems usually prefer better alternatives.

---

## 53. Ignoring Synchronization Overhead in Vector

Synchronization impacts performance.

---

## 54. Using Wrong Collection for Workload Type

Collection choice should depend on access patterns.

---

## 55. Treating All Lists as Identical

Different list implementations behave differently internally.

---

## 56. Ignoring Backend Scalability Implications

Collection performance affects large-scale systems.

---

## 57. Overriding equals() Without hashCode()

This breaks HashSet and HashMap behavior.

---

## 58. Misunderstanding HashSet Uniqueness

HashSet depends on hashing and equality checks.

---

## 59. Ignoring Collision Impacts

Poor hashing can reduce performance.

---

## 60. Using Mutable Fields in hashCode()

Changing hash-relevant fields creates inconsistent behavior.

---

## 61. Confusing Reference Equality and Logical Equality

equals() and == are different concepts.

---

## 62. Assuming HashSet Maintains Order

HashSet does not guarantee insertion order.

---

## 63. Writing Poor hashCode() Implementations

Bad hashing impacts scalability.

---

## 64. Ignoring Backend Lookup Performance

Hashing is foundational for scalable systems.

---

## 65. Writing Poor HashMap Keys

Bad key design causes collisions and performance degradation.

---

## 66. Ignoring hashCode() and equals() Rules

Incorrect implementations break map behavior.

---

## 67. Assuming HashMap Maintains Order

HashMap does not guarantee insertion order.

---

## 68. Ignoring Collision Impacts

Heavy collisions reduce lookup efficiency.

---

## 69. Misunderstanding Load Factor

Load factor affects resizing and performance.

---

## 70. Overusing TreeMap Without Need

Sorting introduces additional overhead.

---

## 71. Treating All Maps as Identical

Different map implementations behave differently internally.

---

## 72. Ignoring Scalability Implications of Hashing

Hashing architecture impacts backend performance heavily.

---

## 73. Confusing Comparable and Comparator

They solve different sorting problems.

---

## 74. Writing Inconsistent compareTo() Logic

Incorrect comparison logic breaks sorting behavior.

---

## 75. Duplicating Sorting Logic Everywhere

Reusable comparators improve maintainability.

---

## 76. Ignoring Sorting Performance

Large-scale sorting impacts scalability.

---

## 77. Overcomplicating Comparator Implementations

Modern lambda syntax simplifies sorting.

---

## 78. Using Natural Ordering for Every Use Case

Many systems require multiple sorting strategies.

---

## 79. Ignoring Backend Pagination Requirements

Sorting is critical for API pagination systems.

---

## 80. Writing Non-Scalable Ranking Logic

Large ranking systems require optimized sorting strategies.

---

## 81. Assuming HashMap is Thread-Safe

HashMap is not safe for concurrent modifications.

---

## 82. Overusing Synchronized Collections

Coarse synchronization hurts scalability.

---

## 83. Using CopyOnWriteArrayList in Write-Heavy Systems

Frequent copying impacts performance.

---

## 84. Ignoring Concurrent Modification Problems

Unsafe iteration causes runtime failures.

---

## 85. Misunderstanding Fail-Fast Behavior

Concurrent modification exceptions are intentional safety checks.

---

## 86. Ignoring Scalability Tradeoffs in Concurrency

Thread safety and scalability must be balanced.

---

## 87. Misusing BlockingQueue

Producer-consumer systems require proper queue management.

---

## 88. Treating Concurrent Collections as Magic Solutions

Correct architecture still matters heavily.

---

## 89. Calling run() Instead of start()

run() does not create new thread.

---

## 90. Misunderstanding Thread Lifecycle

Incorrect lifecycle understanding causes debugging issues.

---

## 91. Overusing sleep() for Coordination

sleep() is unreliable for synchronization.

---

## 92. Ignoring Thread Coordination Problems

Poor coordination causes inconsistent behavior.

---

## 93. Assuming Concurrency Means Parallelism

They are different concepts.

---

## 94. Creating Too Many Threads Manually

Excessive threads hurt scalability.

---

## 95. Ignoring Backend Concurrency Requirements

Modern backend systems are highly concurrent.

---

## 96. Treating Multithreading as Simple Sequential Programming

Concurrency introduces complex runtime behavior.

---

## 97. Ignoring Race Conditions

Shared mutable data requires proper synchronization.

---

## 98. Over-Synchronizing Entire Methods

Large synchronized sections reduce scalability.

---

## 99. Using Wrong Lock Objects

Incorrect lock selection breaks synchronization guarantees.

---

## 100. Ignoring Visibility Problems

Threads may not immediately see shared updates.

---

## 101. Synchronizing Unnecessary Code

Unnecessary locking reduces performance.

---

## 102. Assuming synchronized Solves All Concurrency Problems

Concurrency requires careful architecture design.

---

## 103. Ignoring Backend Scalability Tradeoffs

Synchronization impacts throughput and latency.

---

## 104. Treating Concurrency Bugs as Easily Reproducible

Concurrency bugs are often unpredictable and difficult to debug.

---

## 105. Assuming volatile Solves All Concurrency Problems

volatile does not guarantee atomicity.

---

## 106. Ignoring Deadlock Risks

Improper lock ordering causes deadlocks.

---

## 107. Using Mutable Shared Objects Everywhere

Mutable shared state increases concurrency complexity.

---

## 108. Ignoring Visibility Problems

Threads may cache stale values.

---

## 109. Assuming count++ is Atomic

Increment operations are not atomic.

---

## 110. Underestimating Production Concurrency Bugs

Concurrency bugs are extremely difficult to reproduce.

---

## 111. Overusing Shared Mutable State

Reducing shared state simplifies concurrency.

---

## 112. Ignoring Scalability Effects of Synchronization

Concurrency control impacts throughput heavily.

---