## 1. Treating Databases as Only Storage Layer

Databases deeply affect backend architecture.

---

## 2. Ignoring Persistence Thinking

Backend systems fundamentally depend on persistent data.

---

## 3. Ignoring Data Consistency Importance

Inconsistent data may break transactions.

---

## 4. Overusing File-Based Storage

Files do not scale well for backend systems.

---

## 5. Ignoring Relational Modeling

Poor relationships create backend complexity.

---

## 6. Ignoring Transactional System Awareness

Critical workflows require consistency guarantees.

---

## 7. Thinking Backend Engineering is Only APIs

Persistence is foundational for backend systems.

---

## 8. Ignoring Database Scalability Early

Growth eventually exposes persistence bottlenecks.

---

## 9. Confusing Rows and Columns

Rows represent records while columns represent attributes.

---

## 10. Ignoring Schema Design Importance

Poor schema design creates backend complexity.

---

## 11. Treating CRUD as Only SQL Topic

CRUD is foundational for backend APIs.

---

## 12. Ignoring Relational Thinking

Relationships are critical for backend systems.

---

## 13. Overcomplicating Simple Database Structures

Good schema design should remain maintainable.

---

## 14. Ignoring Database Consistency Requirements

Incorrect data handling breaks workflows.

---

## 15. Thinking Databases Are Optional in Backend Systems

Persistence is foundational for reliable systems.

---

## 16. Ignoring Backend Workflow Perspective

Databases are deeply connected to API workflows.

---

## 17. Ignoring Entity Relationships

Backend systems fundamentally depend on relationships.

---

## 18. Incorrect Relationship Modeling

Poor relationships create complex persistence logic.

---

## 19. Overusing Many-to-Many Relationships

May create maintenance and query complexity.

---

## 20. Ignoring Relational Integrity

Invalid references break consistency.

---

## 21. Designing Tables Without Backend Perspective

Database design should support backend workflows.

---

## 22. Ignoring Transactional Relationship Consistency

Related data must remain synchronized.

---

## 23. Treating Relational Modeling as Only Database Topic

It deeply impacts APIs and backend architecture.

---

## 24. Poor Naming of Database Entities

Confusing schemas reduce maintainability.

---

## 25. Missing Primary Keys

Tables without primary keys create unreliable data.

---

## 26. Incorrect Foreign Key Relationships

Invalid relationships break backend consistency.

---

## 27. Ignoring Unique Constraints

Duplicates create data corruption issues.

---

## 28. Overusing Nullable Columns

Too many null values reduce reliability.

---

## 29. Ignoring Relational Integrity

Broken relationships create orphan records.

---

## 30. Missing Validation Constraints

Invalid data may enter production systems.

---

## 31. Treating Constraints as Optional

Constraints are critical for reliable persistence.

---

## 32. Weak Database Integrity Design

Poor integrity design causes backend failures.

---

## 33. Excessive Duplicate Data

Redundancy creates inconsistency problems.

---

## 34. Ignoring Normalization

Poor normalization increases maintenance complexity.

---

## 35. Storing Multiple Values in One Column

Violates First Normal Form.

---

## 36. Poor Relationship Separation

Bad schema structure hurts scalability.

---

## 37. Over-Normalizing Everything

Too much normalization may hurt performance.

---

## 38. Ignoring Backend Query Patterns

Schema should support backend workflows.

---

## 39. Designing Schema Without Scalability Thinking

Growth eventually exposes bad design.

---

## 40. Treating Database Design as Only DBA Responsibility

Backend engineers must understand schema design deeply.

---

## 41. Treating SQL as Optional

Backend systems fundamentally depend on SQL querying.

---

## 42. Ignoring Transaction Awareness

Poor transaction handling breaks consistency.

---

## 43. Learning ORM Without SQL Understanding

Creates weak backend debugging skills.

---

## 44. Misunderstanding SQL Categories

DDL, DML, DQL and TCL have different responsibilities.

---

## 45. Ignoring Query Performance

Poor SQL affects backend scalability.

---

## 46. Thinking SQL is Only Database Engineer Responsibility

Backend engineers must deeply understand SQL.

---

## 47. Weak Understanding of SELECT Queries

Data retrieval is core backend workflow.

---

## 48. Ignoring Persistence Workflow Thinking

Backend APIs fundamentally depend on SQL workflows.

---

## 49. Using SELECT *

Fetching unnecessary data hurts performance.

---

## 50. Missing WHERE Clause in UPDATE

May accidentally update all rows.

---

## 51. Missing WHERE Clause in DELETE

May accidentally delete all records.

---

## 52. Retrieving Excessive Data

Large unnecessary result sets hurt scalability.

---

## 53. Ignoring Query Performance

Slow queries eventually bottleneck APIs.

---

## 54. Treating CRUD as Only Database Logic

CRUD directly impacts backend APIs.

---

## 55. Weak Understanding of Query Flow

Backend persistence workflows depend on query execution.

---

## 56. Ignoring Scalability Impact of Queries

Bad queries become production bottlenecks.

---

## 57. Fetching Entire Tables Unnecessarily

Hurts scalability and performance.

---

## 58. Missing WHERE Conditions

May retrieve excessive unnecessary data.

---

## 59. Using HAVING Instead of WHERE Incorrectly

Creates inefficient grouped queries.

---

## 60. Ignoring Sorting Performance

ORDER BY on large datasets may become expensive.

---

## 61. Poor Aggregation Query Design

Heavy grouping queries may overload databases.

---

## 62. Ignoring Backend Query Patterns

Queries should align with API workflows.

---

## 63. Retrieving Unsorted Data Blindly

Backend systems often require predictable ordering.

---

## 64. Weak Understanding of Aggregation Logic

Analytics workflows heavily depend on aggregation.

---

## 65. Blindly Joining Large Tables

May create severe performance issues.

---

## 66. Missing Join Conditions

Incorrect joins may create cartesian products.

---

## 67. Poor Relationship Modeling

Bad schema relationships hurt query efficiency.

---

## 68. Overfetching Data Through Joins

Unnecessary joined data increases load.

---

## 69. Ignoring Join Performance

Complex joins eventually bottleneck APIs.

---

## 70. Using Wrong Join Types

Incorrect join choice creates inaccurate results.

---

## 71. Ignoring Backend Query Patterns

Join design should align with API workflows.

---

## 72. Weak Understanding of Relational Querying

Backend systems fundamentally depend on relational joins.

---

## 73. Blindly Nesting Queries

Deep nesting may create performance bottlenecks.

---

## 74. Ignoring Subquery Execution Cost

Nested queries can become expensive on large datasets.

---

## 75. Misusing Correlated Subqueries

Repeated execution may severely impact performance.

---

## 76. Using IN Instead of EXISTS Inefficiently

Large datasets may require optimized existence checks.

---

## 77. Ignoring Backend Query Patterns

Advanced queries should support business workflows.

---

## 78. Writing Complex Queries Without Readability

Unreadable SQL becomes difficult to maintain.

---

## 79. Overfetching Data in Nested Queries

Unnecessary retrieval increases database load.

---

## 80. Weak Understanding of Advanced Querying

Backend systems fundamentally depend on advanced filtering logic.

---

## 81. Ignoring Query Performance

Slow queries eventually break scalability.

---

## 82. Excessive Full Table Scans

Large scans overload production databases.

---

## 83. Blindly Adding Indexes Everywhere

Too many indexes hurt write performance.

---

## 84. Missing Indexes on Frequently Queried Columns

Important queries become unnecessarily slow.

---

## 85. Ignoring Backend Query Patterns

Indexes should support API workflows.

---

## 86. Weak Understanding of Scalability

Database optimization is critical for backend growth.

---

## 87. Retrieving Unnecessary Data

Poor filtering increases database load.

---

## 88. Treating Optimization as Optional

Performance engineering is core backend responsibility.

---