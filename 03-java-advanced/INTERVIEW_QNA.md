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

## Q171. What is heap memory in Java?

Heap stores objects and shared data.

---

## Q172. What is stack memory in Java?

Stack stores method calls and local variables.

---

## Q173. Difference between heap and stack memory?

Heap:
stores objects.

Stack:
stores execution frames and local variables.

---

## Q174. What is object lifecycle in Java?

Objects are created, used, become unreachable and garbage collected.

---

## Q175. What are strong references?

Default Java references preventing immediate garbage collection.

---

## Q176. What are weak references?

References allowing earlier garbage collection.

---

## Q177. Can Java have memory leaks?

Yes.

Unused objects may remain referenced.

---

## Q178. What causes OutOfMemoryError?

JVM cannot allocate required memory.

---

## Q179. What causes StackOverflowError?

Deep or uncontrolled recursion.

---

## Q180. Why is memory management important in backend systems?

Impacts scalability, latency and production stability.

---

## Q181. What is garbage collection in Java?

Automatic removal of unreachable objects from memory.

---

## Q182. What are GC roots?

Starting points used by GC for reachability analysis.

---

## Q183. What is Young Generation?

Memory area storing newly created objects.

---

## Q184. What is Old Generation?

Memory area storing long-lived objects.

---

## Q185. What is Minor GC?

GC process cleaning Young Generation.

---

## Q186. What is Major/Full GC?

GC process cleaning Old Generation.

---

## Q187. What are Stop-The-World pauses?

Application thread pauses during GC operations.

---

## Q188. What is G1 GC?

Garbage collector designed for lower pause times.

---

## Q189. Can excessive object creation affect performance?

Yes.

It increases GC pressure and latency.

---

## Q190. Why is GC understanding important for backend systems?

GC heavily impacts scalability and latency.

---

## Q191. What is JVM?

Java Virtual Machine responsible for executing Java bytecode.

---

## Q192. Why is JVM important?

Provides platform independence and runtime management.

---

## Q193. What is bytecode?

Intermediate compiled Java code executed by JVM.

---

## Q194. What are class loading phases?

- Loading
- Linking
- Initialization

---

## Q195. What is JIT compiler?

Compiles frequently executed bytecode into native machine code.

---

## Q196. Difference between interpreter and JIT?

Interpreter:
executes line-by-line.

JIT:
optimizes hot code into native code.

---

## Q197. What are stack frames?

Runtime method execution structures stored in stack memory.

---

## Q198. What are important JVM runtime areas?

- Heap
- Stack
- Method Area
- PC Register
- Native Method Stack

---

## Q199. What is HotSpot JVM?

JVM optimization mechanism for frequently executed code.

---

## Q200. Why are JVM internals important in backend systems?

Impact scalability, latency, memory usage and performance.

---

## Q201. Why is performance optimization important in backend systems?

Impacts scalability, latency and infrastructure efficiency.

---

## Q202. Difference between CPU-bound and IO-bound tasks?

CPU-bound:
heavy computation.

IO-bound:
waiting for external resources.

---

## Q203. Why is excessive object creation harmful?

Increases GC pressure and memory overhead.

---

## Q204. Why is StringBuilder preferred in loops?

Avoids unnecessary temporary String objects.

---

## Q205. What is boxing and unboxing?

Conversion between primitives and wrapper objects.

---

## Q206. Why does collection choice matter?

Different collections provide different performance characteristics.

---

## Q207. What is caching?

Storing reusable data to avoid repeated expensive operations.

---

## Q208. Why is batching important?

Improves throughput and reduces overhead.

---

## Q209. Why should optimization be measurement-driven?

Premature optimization may increase complexity unnecessarily.

---

## Q210. Why is profiling important in backend engineering?

Helps identify real bottlenecks and performance issues.

---

## Q211. Why was Java 8 revolutionary?

Introduced functional programming support and modern APIs.

---

## Q212. What is lambda expression?

Compact anonymous function representation.

---

## Q213. Why are lambdas important?

Improve readability and simplify functional-style processing.

---

## Q214. What is method reference?

Shorter lambda syntax referencing existing methods.

---

## Q215. What is Stream API?

Declarative pipeline-based collection processing API.

---

## Q216. What is lazy evaluation in streams?

Operations execute only during terminal operation.

---

## Q217. What is Optional?

Container object representing optional value presence.

---

## Q218. Why is Optional important?

Reduces NullPointerException risks and improves API clarity.

---

## Q219. What are default methods?

Methods with implementation inside interfaces.

---

## Q220. Why are Java 8 features important in backend engineering?

Modern frameworks heavily depend on functional-style programming.

---

## Q221. What is functional interface?

Interface containing exactly one abstract method.

---

## Q222. Why are functional interfaces important?

Enable lambda expressions and functional programming.

---

## Q223. What is @FunctionalInterface annotation?

Compiler validation annotation for functional interfaces.

---

## Q224. What is Predicate in Java?

Functional interface representing boolean condition logic.

---

## Q225. What is Function in Java?

Functional interface representing transformation operation.

---

## Q226. What is Consumer in Java?

Functional interface consuming data without returning result.

---

## Q227. What is Supplier in Java?

Functional interface generating or supplying values.

---

## Q228. What is function composition?

Combining functions into transformation pipelines.

---

## Q229. Why are functional interfaces important in backend systems?

Heavily used in streams, async systems and modern frameworks.

---

## Q230. What are benefits of functional programming style?

Improves readability, composability and declarative processing.

---

## Q231. What is Stream API?

Declarative collection-processing API introduced in Java 8.

---

## Q232. What are intermediate operations in streams?

Lazy operations like filter, map and sorted.

---

## Q233. What are terminal operations in streams?

Operations triggering execution like collect and reduce.

---

## Q234. What is lazy evaluation in streams?

Intermediate operations execute only during terminal operation.

---

## Q235. What is filter() in Stream API?

Used for conditional selection of elements.

---

## Q236. What is map() in Stream API?

Used for transforming stream elements.

---

## Q237. What is reduce() in Stream API?

Used for aggregation and accumulation logic.

---

## Q238. What is flatMap() in Stream API?

Used for flattening nested structures.

---

## Q239. What are advantages of Stream API?

Improves readability, composability and declarative processing.

---

## Q240. Why should parallel streams be used carefully?

Improper usage may reduce performance and increase contention.

---

## Q241. Why was Optional introduced in Java?

To improve null-safety and API clarity.

---

## Q242. What does Optional represent?

Value may or may not exist.

---

## Q243. Difference between Optional.of() and Optional.ofNullable()?

of():
does not allow null.

ofNullable():
allows null.

---

## Q244. What is ifPresent() in Optional?

Executes logic only when value exists.

---

## Q245. What is orElse() in Optional?

Provides fallback value when Optional is empty.

---

## Q246. Difference between orElse() and orElseGet()?

orElseGet() performs lazy fallback generation.

---

## Q247. What is orElseThrow()?

Throws exception when value absent.

---

## Q248. Why is Optional important in backend systems?

Improves API contracts and null safety.

---

## Q249. Where should Optional ideally be used?

Mostly for return types.

---

## Q250. Why should Optional not be overused?

May increase complexity and object overhead.

---

## Q251. What is reflection in Java?

Runtime inspection and manipulation of classes and objects.

---

## Q252. Why is reflection important?

Used heavily by frameworks for dynamic behavior and automation.

---

## Q253. What is Class class in Java?

Entry point for reflection operations.

---

## Q254. How can Class objects be obtained?

- ClassName.class
- object.getClass()
- Class.forName()

---

## Q255. What can reflection inspect?

Fields, methods, constructors and annotations.

---

## Q256. What is dynamic object creation?

Creating objects at runtime using reflection.

---

## Q257. Why do frameworks use reflection?

For dependency injection, annotation scanning and runtime processing.

---

## Q258. Why is reflection slower than direct calls?

Runtime metadata inspection introduces overhead.

---

## Q259. What are risks of excessive reflection usage?

Reduced performance, readability and maintainability.

---

## Q260. Which major frameworks heavily use reflection?

Spring, Hibernate, Jackson and JUnit.

---

## Q261. What are annotations in Java?

Metadata providing information about program elements.

---

## Q262. Why are annotations important?

Enable framework automation and declarative programming.

---

## Q263. What is @Override annotation?

Indicates method overrides parent method.

---

## Q264. What is @Deprecated annotation?

Marks outdated APIs.

---

## Q265. What is @SuppressWarnings annotation?

Suppresses compiler warnings.

---

## Q266. What are meta-annotations?

Annotations defining behavior of annotations.

---

## Q267. What are retention policies?

Define lifecycle of annotations.

---

## Q268. Why are runtime annotations important?

Frameworks inspect them using reflection.

---

## Q269. Why do frameworks heavily use annotations?

For dependency injection, API mapping and automation.

---

## Q270. Which major frameworks heavily depend on annotations?

Spring, Hibernate, JUnit and REST frameworks.

---

## Q271. What is serialization in Java?

Converting object into byte stream.

---

## Q272. What is deserialization?

Converting byte stream back into object.

---

## Q273. Why is serialization important?

Used for distributed communication, caching and persistence.

---

## Q274. What is Serializable interface?

Marker interface enabling Java serialization.

---

## Q275. What is ObjectOutputStream?

Used for writing serialized objects.

---

## Q276. What is ObjectInputStream?

Used for reading serialized objects.

---

## Q277. What is transient keyword?

Prevents field serialization.

---

## Q278. Why is serialVersionUID important?

Controls serialization compatibility between versions.

---

## Q279. Why is serialization important in distributed systems?

Enables inter-service object transfer.

---

## Q280. Why is native Java serialization used carefully today?

Due to security, performance and compatibility concerns.

---

## Q281. What is socket in Java?

Communication endpoint for network interaction.

---

## Q282. What is ServerSocket?

Server-side socket listening for client connections.

---

## Q283. What is client-server architecture?

Client requests services, server provides services.

---

## Q284. Difference between TCP and UDP?

TCP:
reliable and connection-oriented.

UDP:
faster but unreliable.

---

## Q285. Why is TCP heavily used in backend systems?

Provides reliable ordered communication.

---

## Q286. What is blocking IO?

Thread waits until IO operation completes.

---

## Q287. Why is blocking IO important in backend engineering?

Affects scalability and thread utilization.

---

## Q288. What are ports in networking?

Identifiers for services/applications.

---

## Q289. Why is networking important in distributed systems?

Distributed systems fundamentally communicate through networks.

---

## Q290. Which backend systems heavily depend on networking?

REST APIs, microservices, databases and message brokers.

---

## Q291. What is Java NIO?

Non-blocking IO framework introduced for scalable IO handling.

---

## Q292. Why was NIO introduced?

To improve scalability and concurrency handling.

---

## Q293. Difference between blocking and non-blocking IO?

Blocking IO waits for completion.
Non-blocking IO handles multiple operations efficiently.

---

## Q294. What are buffers in NIO?

Temporary storage containers for IO operations.

---

## Q295. What are channels in NIO?

Communication paths for data transfer.

---

## Q296. What is Selector in NIO?

Allows monitoring multiple channels using single thread.

---

## Q297. Why is NIO important for scalable backend systems?

Reduces thread overhead and improves concurrency scalability.

---

## Q298. Which major frameworks heavily use NIO concepts?

Netty, Kafka and WebFlux.

---

## Q299. Why is NIO important in reactive systems?

Reactive systems heavily depend on non-blocking IO.

---

## Q300. What are challenges of NIO?

Higher complexity and event-driven debugging difficulty.

---