# Garbage Collection in Java

Garbage Collection heavily impacts backend performance and scalability.

Modern backend systems constantly create:
- requests
- responses
- temporary objects
- collections
- async tasks

Understanding garbage collection is extremely important for backend engineering.

---

# 1. What is Garbage Collection?

Garbage Collection (GC):
# automatically frees unused memory

Very important JVM feature.

---

# 2. Why GC Exists?

Without GC:
developers would manually manage memory.

Manual memory management may cause:
- crashes
- memory corruption
- leaks

Very important runtime engineering concept.

---

# 3. Unreachable Objects

Objects become GC candidates when:
# no reachable references remain

Very important memory lifecycle concept.

---

# 4. GC Roots

GC starts traversal from:
# GC Roots

Examples:
- stack references
- static references
- active thread references

Very important JVM internals topic.

---

# 5. JVM Generations

Modern JVM memory divided into:
- Young Generation
- Old Generation

Very important GC architecture concept.

---

# 6. Young Generation

Stores:
# newly created objects

Most objects die quickly.

Very important backend allocation behavior.

---

# 7. Old Generation

Stores:
# long-lived objects

Very important memory optimization topic.

---

# 8. Minor GC

Minor GC cleans:
# Young Generation

Usually:
- faster
- more frequent

Very important runtime behavior.

---

# 9. Major / Full GC

Major GC cleans:
# Old Generation

Usually:
- slower
- more expensive

Very important production engineering concern.

---

# 10. Stop-The-World Pauses

During some GC operations:
# application threads pause

This may cause:
- latency spikes
- request slowdown

Very important backend scalability topic.

---

# 11. Common GC Algorithms

Modern JVM supports:
- Serial GC
- Parallel GC
- G1 GC
- ZGC

Very important JVM engineering awareness.

---

# 12. Why G1 GC Became Important?

Designed for:
# lower pause times

Very important backend latency optimization.

---

# 13. GC Tuning Basics

Backend engineers may tune:
- heap sizes
- GC algorithms
- pause-time goals

Very important production engineering topic.

---

# 14. Real-World Backend Relevance

GC heavily impacts:
- APIs
- microservices
- distributed systems
- high-throughput applications

Very important backend engineering topic.

---

# 15. Common Beginner Confusions

Beginners often:
- assume GC solves all memory problems
- ignore object allocation pressure
- misunderstand heap generations
- ignore GC pauses

Understanding GC behavior is very important.

---

# 16. Industry Relevance

Modern backend systems heavily depend on:
- efficient memory allocation
- low-latency GC
- scalable heap management
- GC-aware backend design

Backend engineering strongly relies on garbage collection understanding.