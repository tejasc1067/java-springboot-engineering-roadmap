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

## Q21. What are generics in Java?

Generics allow parameterized types for type-safe programming.

---

## Q22. Why were generics introduced?

To improve:
- type safety
- readability
- maintainability

---

## Q23. What is type safety?

Compile-time validation of data types.

---

## Q24. What is generic class?

Class that accepts type parameter.

Example:
class Box<T>

---

## Q25. What is generic method?

Method that works with generic type parameters.

---

## Q26. Why are generics important in collections?

Generics prevent invalid object insertion and explicit casting.

---

## Q27. What are raw types?

Using generic classes without specifying type parameter.

Example:
List list

---

## Q28. Why should raw types be avoided?

They reduce type safety.

---

## Q29. Where are generics heavily used in backend systems?

Used in:
- collections
- repositories
- DTO wrappers
- APIs
- service layers

---

## Q30. Why are generics important in enterprise Java?

They improve:
- reusable architecture
- compile-time safety
- maintainability

---

## Q31. What are bounded types in generics?

Bounded types restrict allowed generic types.

Example:
<T extends Number>

---

## Q32. What is wildcard in Java generics?

Wildcard (?) represents unknown type.

---

## Q33. What is upper bounded wildcard?

```
<? extends Type>
```

Used for safe reading operations.

---

## Q34. What is lower bounded wildcard?

```<? super Type>```

Used for safe insertion operations.

---

## Q35. What is PECS principle?

PECS means:
Producer Extends Consumer Super.

---

## Q36. What is covariance in Java generics?

Covariance allows safe reading using:
```<? extends Type>```

---

## Q37. What is contravariance in Java generics?

Contravariance allows safe insertion using:
```<? super Type>```

---

## Q38. What is type erasure?

Generic type information is mostly removed at runtime.

---

## Q39. Why is type erasure important?

It explains runtime generic limitations.

---

## Q40. Where are advanced generics heavily used?

Used in:
- Spring Framework
- collections
- repositories
- reusable libraries

---

## Q41. What is Collections Framework?

Collections Framework is unified architecture for storing and manipulating data.

---

## Q42. Why was Collections Framework introduced?

To improve:
- dynamic data handling
- reusable APIs
- scalability
- maintainability

---

## Q43. What are main collection interfaces?

Main interfaces:
- List
- Set
- Queue
- Map

---

## Q44. Difference between List and Set?

List:
- ordered
- duplicates allowed

Set:
- unique elements only

---

## Q45. What is Queue in Java?

Queue manages processing order.

Usually follows FIFO behavior.

---

## Q46. What is Map in Java?

Map stores key-value pairs.

---

## Q47. Difference between Collections and Arrays?

Collections:
- dynamic size
- reusable APIs

Arrays:
- fixed size

---

## Q48. What is Iterable interface?

Iterable supports iteration behavior.

Used internally by for-each loop.

---

## Q49. Why are collections important in backend systems?

Backend systems constantly process large amounts of data.

---

## Q50. Where are collections heavily used?

Used in:
- APIs
- repositories
- caching
- request processing
- microservices

---

## Q51. What is List in Java?

List is ordered collection that allows duplicates.

---

## Q52. What is ArrayList?

ArrayList is resizable array implementation of List.

---

## Q53. Why is ArrayList fast for access operations?

Because internally it uses array indexing.

---

## Q54. What is internal structure of ArrayList?

ArrayList internally uses dynamic array.

---

## Q55. What happens during ArrayList resizing?

Larger array is created and elements are copied.

---

## Q56. Why is insertion in middle expensive in ArrayList?

Because internal shifting occurs.

---

## Q57. Difference between Array and ArrayList?

Array:
fixed size.

ArrayList:
dynamic size.

---

## Q58. What are common iteration methods in ArrayList?

- for loop
- enhanced for loop
- iterator

---

## Q59. What is time complexity of ArrayList access?

Approximate O(1).

---

## Q60. Where is ArrayList heavily used in backend systems?

Used in:
- API responses
- repositories
- DTO processing
- service layers

---

## Q61. What is LinkedList in Java?

LinkedList is doubly linked list implementation of List.

---

## Q62. What is internal structure of LinkedList?

Each node stores:
- data
- previous reference
- next reference

---

## Q63. Why is LinkedList good for insertions?

Insertion does not require large shifting operations.

---

## Q64. Why is LinkedList slower for access operations?

Traversal occurs node-by-node.

---

## Q65. What is Vector in Java?

Vector is synchronized dynamic array.

---

## Q66. Difference between Vector and ArrayList?

Vector:
synchronized.

ArrayList:
not synchronized.

---

## Q67. Difference between ArrayList and LinkedList?

ArrayList:
fast access.

LinkedList:
better insertion/deletion.

---

## Q68. Why does LinkedList consume more memory?

Each node stores extra references.

---

## Q69. Why is Vector less commonly used today?

Synchronization creates performance overhead.

---

## Q70. Where is LinkedList used in backend systems?

Used in:
- queues
- buffering
- task processing

---

## Q71. What is Set in Java?

Set is collection that stores unique elements.

---

## Q72. What is HashSet?

HashSet is hash table based implementation of Set.

---

## Q73. What is hashing?

Hashing converts object into numeric hash value.

---

## Q74. Why is hashing important?

Hashing enables fast lookup and insertion.

---

## Q75. What is hashCode()?

hashCode() generates numeric hash representation of object.

---

## Q76. What is equals() in Java?

equals() determines logical equality.

---

## Q77. Why are hashCode() and equals() important together?

Collections depend on both for correct uniqueness handling.

---

## Q78. What is collision in hashing?

Collision occurs when multiple objects generate same hash.

---

## Q79. What is performance complexity of HashSet?

Approximate O(1) for:
- add
- remove
- contains

---

## Q80. Where is hashing heavily used in backend systems?

Used in:
- caching
- indexing
- authentication
- duplicate detection
- scalable lookups

---

## Q81. What is Map in Java?

Map stores key-value pairs.

---

## Q82. What is HashMap?

HashMap is hash table based implementation of Map.

---

## Q83. How does HashMap work internally?

Uses:
- hashCode()
- buckets
- equals()
- collision handling

---

## Q84. What are buckets in HashMap?

Buckets are internal storage locations.

---

## Q85. What is load factor in HashMap?

Load factor controls resizing threshold.

Default approximately 0.75.

---

## Q86. What is rehashing?

Entries are redistributed into larger structure during resizing.

---

## Q87. What is collision in HashMap?

Multiple keys map to same bucket.

---

## Q88. Difference between HashMap and LinkedHashMap?

LinkedHashMap maintains insertion order.

---

## Q89. Difference between HashMap and TreeMap?

TreeMap stores entries in sorted order.

---

## Q90. Where is HashMap heavily used in backend systems?

Used in:
- caching
- session management
- indexing
- distributed systems
- request processing

---

## Q91. What is Comparable in Java?

Comparable defines natural ordering of objects.

---

## Q92. What is Comparator in Java?

Comparator defines external/custom sorting logic.

---

## Q93. What is compareTo() method?

compareTo() defines natural ordering.

---

## Q94. What is compare() method?

compare() defines custom comparison logic.

---

## Q95. Difference between Comparable and Comparator?

Comparable:
natural ordering.

Comparator:
custom ordering.

---

## Q96. Why is Comparator important?

Supports multiple sorting strategies.

---

## Q97. Why is sorting important in backend systems?

Used in:
- APIs
- reports
- dashboards
- ranking systems
- pagination

---

## Q98. What is lambda-based sorting?

Sorting using lambda expressions.

---

## Q99. What is typical sorting complexity?

Approximate O(n log n).

---

## Q100. Where are sorting systems heavily used?

Used in:
- leaderboards
- analytics
- pagination
- backend reporting

---

## Q101. Why are normal collections unsafe in concurrency?

Multiple threads may corrupt shared collection state.

---

## Q102. What are synchronized collections?

Thread-safe wrappers around normal collections.

---

## Q103. What is ConcurrentHashMap?

Highly optimized thread-safe map implementation.

---

## Q104. Why is ConcurrentHashMap preferred over Hashtable?

Better scalability and concurrency performance.

---

## Q105. What is CopyOnWriteArrayList?

Collection that creates copy during modification.

---

## Q106. When is CopyOnWriteArrayList useful?

Useful for read-heavy systems.

---

## Q107. What is BlockingQueue?

Thread-safe queue used in producer-consumer systems.

---

## Q108. Difference between fail-fast and fail-safe iteration?

Fail-fast:
throws exception.

Fail-safe:
works on copied structure.

---

## Q109. Where are concurrent collections heavily used?

Used in:
- caching
- microservices
- async processing
- thread pools
- distributed systems

---

## Q110. Why are concurrent collections important in backend systems?

Backend systems process multiple concurrent requests simultaneously.

---

## Q111. What is thread in Java?

Thread is lightweight execution unit inside process.

---

## Q112. What is multithreading?

Executing multiple threads concurrently.

---

## Q113. Difference between process and thread?

Process:
independent running program.

Thread:
execution unit inside process.

---

## Q114. What is Runnable interface?

Runnable represents task executed by thread.

---

## Q115. Difference between start() and run()?

start():
creates new thread.

run():
normal method call.

---

## Q116. What is thread lifecycle?

Thread states:
- NEW
- RUNNABLE
- RUNNING
- WAITING
- TERMINATED

---

## Q117. What is sleep() in Java?

Temporarily pauses thread execution.

---

## Q118. What is join() method?

Allows one thread to wait for another thread.

---

## Q119. What are daemon threads?

Background service threads.

---

## Q120. Why is multithreading important in backend systems?

Improves scalability and concurrent request handling.

---

## Q121. What is race condition?

Race condition occurs when multiple threads modify shared data simultaneously.

---

## Q122. What is synchronization in Java?

Synchronization controls concurrent access to shared resources.

---

## Q123. What is critical section?

Code accessing shared mutable resource.

---

## Q124. What does synchronized keyword do?

Allows only one thread to access protected section at a time.

---

## Q125. What is object-level locking?

Locking based on object monitor.

---

## Q126. What is class-level locking?

Locking based on Class object using static synchronized methods.

---

## Q127. Why are synchronized blocks useful?

Provide granular locking and better scalability.

---

## Q128. What is visibility problem in concurrency?

Threads may not immediately see shared updates.

---

## Q129. What are disadvantages of synchronization?

- blocking
- scalability overhead
- reduced parallelism

---

## Q130. Where is synchronization heavily used in backend systems?

Used in:
- banking systems
- inventory systems
- payment systems
- counters
- caches

---

## Q131. What is thread safety?

Code behaves correctly under concurrent access.

---

## Q132. Why are immutable objects thread-safe?

Their state cannot change after creation.

---

## Q133. What is deadlock?

Threads wait forever for each other’s locks.

---

## Q134. What is starvation?

Some threads never receive CPU/resources.

---

## Q135. What is livelock?

Threads remain active but make no useful progress.

---

## Q136. What is visibility problem in concurrency?

Threads may not immediately see shared updates.

---

## Q137. What does volatile keyword do?

Provides visibility guarantees between threads.

---

## Q138. Does volatile guarantee atomicity?

No.

volatile guarantees visibility only.

---

## Q139. What is atomicity?

Operation executes completely or not at all.

---

## Q140. Why are concurrency bugs difficult to debug?

They occur unpredictably under concurrent execution.

---

## Q141. What is Executor Framework?

Framework for scalable thread and task management.

---

## Q142. Why are thread pools important?

Thread reuse improves scalability and resource efficiency.

---

## Q143. What is ExecutorService?

Interface for task execution and thread-pool management.

---

## Q144. What is fixed thread pool?

Thread pool with fixed number of reusable threads.

---

## Q145. What is cached thread pool?

Thread pool that dynamically creates threads.

---

## Q146. What is ScheduledExecutorService?

Executor for delayed and periodic task execution.

---

## Q147. Difference between submit() and execute()?

submit():
returns Future.

execute():
does not return result.

---

## Q148. Why is shutdown() important?

Prevents thread and resource leaks.

---

## Q149. What is Future in Java?

Represents asynchronous computation result.

---

## Q150. Where is Executor Framework heavily used?

Used in:
- async APIs
- microservices
- messaging systems
- web servers
- distributed systems

---

## Q151. What is Callable in Java?

Callable represents async task that returns result.

---

## Q152. Difference between Runnable and Callable?

Runnable:
does not return result.

Callable:
returns result and throws exceptions.

---

## Q153. What is Future?

Future represents pending async computation result.

---

## Q154. What is limitation of Future?

Future.get() blocks thread.

---

## Q155. What is CompletableFuture?

Modern API for asynchronous and non-blocking programming.

---

## Q156. Why is CompletableFuture important?

Supports async chaining and composition.

---

## Q157. What is async chaining?

Multiple async steps connected together.

---

## Q158. What is blocking vs non-blocking?

Blocking:
thread waits idly.

Non-blocking:
thread continues useful work.

---

## Q159. Where is CompletableFuture heavily used?

Used in:
- microservices
- async APIs
- distributed systems
- event-driven systems

---

## Q160. Why is async programming important in backend systems?

Improves scalability and responsiveness.

---

## Q161. What are atomic classes in Java?

Thread-safe lock-free utility classes.

---

## Q162. What is CAS in concurrency?

Compare-And-Set operation used in lock-free concurrency.

---

## Q163. Why are atomic classes scalable?

Avoid heavy locking overhead.

---

## Q164. What is ReentrantLock?

Flexible alternative to synchronized keyword.

---

## Q165. What is ReadWriteLock?

Separates read and write locking.

---

## Q166. When is ReadWriteLock useful?

Useful in read-heavy systems.

---

## Q167. What is Semaphore?

Controls limited concurrent resource access.

---

## Q168. What is ThreadLocal?

Provides thread-specific storage.

---

## Q169. Where is ThreadLocal heavily used?

Used in:
- request context
- transaction context
- security context

---

## Q170. Why is lock contention dangerous?

Reduces throughput and scalability.

---