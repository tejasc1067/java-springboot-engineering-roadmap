# Subqueries and Nested Queries

This topic focuses on:
# advanced SQL querying foundations

Real production systems frequently require:
# queries inside queries

Subqueries are heavily used in:
- analytics systems
- reporting systems
- transactional systems
- filtering workflows
- backend business logic

Very important backend engineering topic.

---

# What is a Subquery?

Subquery means:
# query inside another query

Example:

SELECT name
FROM users
WHERE id IN (
SELECT user_id
FROM orders
);

Very important SQL-querying foundation.

---

# Why Subqueries Exist

Sometimes queries require:
# intermediate query results

Examples:
- users with orders
- products with highest sales
- customers with payments
- employees above average salary

Very important backend querying concept.

---

# Subquery Execution Flow

Database executes:
# inner query first

then:
# outer query uses result

Very important SQL-execution mindset.

---

# Subquery in WHERE Clause

Very common backend usage.

Example:

SELECT *
FROM employees
WHERE salary > (
SELECT AVG(salary)
FROM employees
);

Very important analytics-query concept.

---

# Subquery in FROM Clause

Subquery can behave like:
# temporary table

Example:

SELECT *
FROM (
SELECT *
FROM users
) AS temp_users;

Very important SQL-querying awareness.

---

# Correlated Subquery

Correlated subquery depends on:
# outer query row

Query executes repeatedly for outer rows.

Very important advanced SQL concept.

---

# EXISTS Operator

Used for:
# checking record existence

Example:

SELECT name
FROM users u
WHERE EXISTS (
SELECT 1
FROM orders o
WHERE o.user_id = u.id
);

Very important backend-querying concept.

---

# IN Operator

Used for:
# matching values from subquery result

Example:

WHERE id IN (
SELECT user_id
FROM orders
)

Very important filtering-query concept.

---

# Why Subqueries Matter in Backend Systems

Backend systems constantly require:
- advanced filtering
- analytics querying
- conditional querying
- transactional checks
- business-rule querying

Very important persistence-engineering mindset.

---

# Real-World Backend Examples

Examples:
- users with pending orders
- products above average sales
- customers with transactions
- inactive accounts
- fraud-detection workflows

All heavily depend on:
# subqueries

Very important backend engineering awareness.

---

# Production Importance

Poor nested queries may cause:
- slow APIs
- scalability bottlenecks
- heavy database load
- inefficient query execution

Very important production-engineering topic.

---

# Important Engineering Lesson

Good backend engineering requires:
# efficient advanced-query mindset

NOT:
# blindly nesting queries

Very important backend engineering mindset.

---

# Industry Relevance

Modern backend systems fundamentally rely on:
- advanced querying
- nested filtering
- transactional consistency
- scalable persistence
- optimized business querying

Subqueries are foundational for advanced backend engineering.