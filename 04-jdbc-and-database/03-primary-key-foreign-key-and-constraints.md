# Primary Key, Foreign Key and Constraints

This topic focuses on:
# keys and constraints in relational databases

These concepts are foundational for:
- relational integrity
- consistency
- joins
- transactions
- JPA/Hibernate mappings
- backend persistence engineering

Very important backend engineering topic.

---

# What is a Primary Key?

Primary key:
# uniquely identifies each row

Examples:
- user_id
- order_id
- product_id

A primary key must be:
- unique
- not null

Very important relational-database foundation.

---

# Why Primary Keys Matter

Without primary keys:
- duplicate records occur
- relationships break
- querying becomes unreliable

Very important backend persistence concept.

---

# Primary Key Example

Users Table

user_id | name
1       | Tejas
2       | Rahul

Each row is uniquely identifiable.

---

# What is a Foreign Key?

Foreign key:
# connects tables together

Example:

Orders Table

order_id | user_id
101      | 1

Here:
user_id

references:
Users.user_id

Very important relational-modeling concept.

---

# Why Foreign Keys Matter

Foreign keys maintain:
# relational integrity

They prevent:
- invalid references
- orphan records
- inconsistent relationships

Very important backend engineering foundation.

---

# Relational Integrity

Relational integrity means:
# relationships remain valid and consistent

Very important transactional-system foundation.

---

# Unique Constraint

Unique constraint ensures:
# duplicate values are not allowed

Examples:
- email
- username

Very important backend consistency concept.

---

# NOT NULL Constraint

NOT NULL constraint ensures:
# value must exist

Examples:
- name
- email
- password

Very important data-consistency concept.

---

# Default Constraint

Default constraint provides:
# automatic default value

Example:
status = ACTIVE

Very important backend workflow convenience.

---

# Check Constraint

Check constraint validates:
# allowed values and rules

Example:
age >= 18

Very important data-validation awareness.

---

# Backend Engineering Connection

Backend systems heavily depend on:
- valid relationships
- unique records
- transactional consistency
- data integrity

Very important persistence-engineering mindset.

---

# Real-World Backend Examples

Examples:
- unique email in user system
- valid order-user relationships
- non-null passwords
- valid payment status

All depend on:
# constraints

Very important backend engineering awareness.

---

# Production Importance

Constraints help prevent:
- corrupted data
- invalid relationships
- inconsistent transactions
- broken backend workflows

Very important production-engineering topic.

---

# JPA and Hibernate Connection

Later in:
- Hibernate
- Spring Data JPA

you will map:
- primary keys
- foreign keys
- relationships
- constraints

Very important learning progression.

---

# Important Engineering Lesson

Good backend engineering requires:
# strong data integrity

Backend consistency starts from:
# proper database constraints

Very important engineering mindset.

---

# Industry Relevance

Modern backend systems fundamentally rely on:
- relational integrity
- valid relationships
- transactional consistency
- reliable persistence
- constraint-driven validation

Keys and constraints are foundational for backend engineering.