# 01 — Introduction to Databases

Topic 00 made the case for why databases exist. This topic introduces the actual vocabulary you'll use for the rest of the module.

No code yet. The terms here are foundational — every later topic assumes you know them.

---

## Database vs. DBMS

These are two different things and confusing them will trip you up later.

- **Database** = the data itself plus the structure that organizes it. The thing you query.
- **DBMS** (Database Management System) = the program that runs the database. The thing you talk to.

So when someone says "PostgreSQL is a database," they're being loose. PostgreSQL is technically a DBMS. The actual database is the collection of tables you create inside it (e.g., the `appdb` database that holds your `users` and `orders` tables).

In practice everyone says "database" for both. Just know the distinction exists.

**Common DBMSes you'll see in backend jobs:**

- **PostgreSQL** — modern default for new projects. Free, open source, very capable.
- **MySQL** — extremely common, especially in older systems and WordPress-adjacent stacks.
- **SQLite** — embedded, runs as part of your app, not a separate server. Used in mobile apps and small tools.
- **H2** — in-memory Java database. Used heavily in development and tests. **This is what we'll use in this module.**
- **Oracle, SQL Server** — enterprise. You'll meet them in banks, governments, big corps.

---

## The vocabulary of a relational database

A relational database organizes data into **tables**. A table looks like a spreadsheet:

```
users table

+----+------------+----------------------+
| id | name       | email                |
+----+------------+----------------------+
|  1 | Alice      | alice@example.com    |
|  2 | Bob        | bob@example.com      |
|  3 | Carol      | carol@example.com    |
+----+------------+----------------------+
```

- **Table** = the whole grid (here: `users`).
- **Row** (also called a **record**) = one horizontal line. One row = one user.
- **Column** (also called a **field** or **attribute**) = one vertical line. Here: `id`, `name`, `email`.
- **Schema** = the structure: which tables exist, what columns each has, what types those columns are, what the rules are.

Schema is the **definition**. Rows are the **data**. You define the schema once (with `CREATE TABLE`), then insert/update/delete rows over the life of the app.

---

## CRUD: the four operations

Every database operation you'll do for the rest of your career falls into one of four buckets:

| Op | SQL keyword | What it does |
|----|-------------|--------------|
| **C**reate | `INSERT` | Adds a new row |
| **R**ead | `SELECT` | Fetches existing rows |
| **U**pdate | `UPDATE` | Modifies existing rows |
| **D**elete | `DELETE` | Removes rows |

That's it. Almost every REST API endpoint in your future maps to one of these:

```
POST   /users         → INSERT
GET    /users/42      → SELECT WHERE id = 42
PUT    /users/42      → UPDATE WHERE id = 42
DELETE /users/42      → DELETE WHERE id = 42
```

When people say "build a CRUD app," they mean an app whose backend mostly translates HTTP requests to one of these four operations. **Most backend code is, fundamentally, CRUD plus some business rules.**

---

## What makes it "relational"

The word "relational" doesn't mean "tables are related to each other" (though they often are). It comes from *relational algebra*, a 1970 mathematical model. Practically, it means:

1. Data lives in tables (called **relations** in the math).
2. Tables can reference each other by ID (foreign keys — covered in topic 03).
3. You query the data with **declarative** SQL: *what* you want, not *how* to get it. The database figures out the *how*.

That last point matters. In Java you write:

```java
List<User> result = new ArrayList<>();
for (User u : users) {
    if (u.age >= 18) result.add(u);
}
```

You're telling the program *how* to filter, step by step.

In SQL you write:

```sql
SELECT * FROM users WHERE age >= 18;
```

You're declaring *what* you want. The database decides how to find it efficiently — using an index, scanning the table, whatever's fastest. **That's the relational model's superpower.**

---

## Common confusions

- **"Database = MySQL"** — no, MySQL is a DBMS. A MySQL installation can host many databases.
- **"Schema and database are the same thing"** — in PostgreSQL they're different (a database contains schemas which contain tables); in MySQL they're synonyms. Just know it varies.
- **"Row and record are different"** — they're synonyms.
- **"NoSQL means no SQL"** — historically yes, now most NoSQL systems have SQL-like query languages too. It really means "not relational."

---

## Self-check

1. What's the difference between a database and a DBMS? Give an example of each.
2. A REST endpoint `GET /products/15` is most likely doing which CRUD operation in SQL?
3. In what sense is SQL "declarative"? Give one example contrasting it with imperative code.
