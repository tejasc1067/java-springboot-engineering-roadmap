# 05 — SQL Introduction

SQL ("Structured Query Language") is how you talk to a relational database. Every operation — create a table, insert a row, query, update, delete — is a SQL statement.

This topic introduces the *language*. You'll write your first real SQL, run it against H2, and see output. The next few topics cover individual operations in depth.

> **Setup needed.** Before running any code in this topic, make sure you've completed `SETUP.md` at the repo root (download H2 jar to `04-jdbc-and-database/CODE_EXAMPLES/lib/`).

---

## SQL is declarative

Quick refresher from topic 01. In Java:

```java
List<User> adults = new ArrayList<>();
for (User u : users) {
    if (u.age >= 18) adults.add(u);
}
```

You're describing *how* to find adults: iterate, check each one, append.

In SQL:

```sql
SELECT * FROM users WHERE age >= 18;
```

You're describing *what* you want: "users where age is at least 18." The database decides how to find them.

This matters because the database can pick the best strategy for *your* data — use an index if there's one, scan the table if there isn't, parallelize across cores, whatever. You don't have to write that logic.

---

## The four sub-languages of SQL

SQL is actually a bundle of four sublanguages, distinguished by what kind of operation they perform. People often confuse them.

| Acronym | Stands for | What it does | Keywords |
|---------|-----------|--------------|----------|
| **DDL** | Data Definition Language | Defines/changes the *structure* (schema) | `CREATE`, `ALTER`, `DROP` |
| **DML** | Data Manipulation Language | Changes the *data* in tables | `INSERT`, `UPDATE`, `DELETE` |
| **DQL** | Data Query Language | Reads data | `SELECT` |
| **TCL** | Transaction Control Language | Manages transactions | `COMMIT`, `ROLLBACK`, `SAVEPOINT` |

Some people lump DQL into DML. Names vary. The categories matter; the names don't.

**Why this distinction matters:** in production, who's allowed to run what is often controlled by category. Application code typically runs DML and DQL constantly. DDL runs rarely (migrations only). TCL controls *when* DML writes become permanent.

---

## Your first SQL statements

```sql
-- DDL: create the structure
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    age INT
);

-- DML: add data
INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30);
INSERT INTO users (id, name, age) VALUES (2, 'Bob', 17);
INSERT INTO users (id, name, age) VALUES (3, 'Carol', 25);

-- DQL: read data
SELECT * FROM users WHERE age >= 18;

-- DML: change data
UPDATE users SET age = 31 WHERE id = 1;

-- DML: remove data
DELETE FROM users WHERE id = 2;

-- DDL: remove the structure
DROP TABLE users;
```

Run-through:

1. `CREATE TABLE` defined three columns. The database now knows the table exists.
2. Three `INSERT`s added three rows.
3. `SELECT * FROM users WHERE age >= 18` returns the two adults (Alice and Carol).
4. `UPDATE` modified Alice's age.
5. `DELETE` removed Bob.
6. `DROP TABLE` wiped the table entirely.

That's a complete CRUD cycle plus schema management in 8 statements.

---

## SQL syntax notes that catch beginners

- **SQL keywords are case-insensitive.** `SELECT`, `select`, `Select` are all the same. Convention: uppercase for keywords (`SELECT`), lowercase for identifiers (`users`, `age`).
- **Statements end with `;`.** Some tools require it, others don't. Always add it; harmless when not needed.
- **Strings use single quotes, not double quotes.** `'Alice'` is a string. `"Alice"` may be parsed as a column name. Different from Java.
- **Equality is `=`, not `==`.** Same as math, different from most programming languages.
- **NULL doesn't equal NULL.** `WHERE x = NULL` does not work. Use `WHERE x IS NULL`. This is the most common SQL footgun for newcomers.
- **`SELECT *`** means "all columns." Convenient for exploration; bad for production code (more on this later).

---

## What is H2 and why are we using it?

H2 is a relational database written in pure Java. It can run **in-memory** — meaning the entire database lives in your JVM's heap, no separate process, no installation, no config file.

In-memory means:

- Zero setup. Just add the jar to your classpath.
- Each program run starts with an empty database (unless you persist it to a file).
- Perfect for learning and for tests. Real production apps would use PostgreSQL or MySQL.

H2's SQL is mostly standard, so what you learn here transfers directly to PostgreSQL/MySQL. The few differences are noted as we go.

**Connection URL pattern we'll use:**

```
jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1
```

- `jdbc:h2:` — protocol
- `mem:demo` — in-memory database named "demo"
- `DB_CLOSE_DELAY=-1` — keep the database alive for the JVM's lifetime (otherwise H2 closes it as soon as the last connection closes, which can cause confusion across operations within a single `main`)

---

## Code examples

Run these in order. Each adds one new idea.

1. `FirstQuery.java` — open a connection to H2, run `SELECT 1`. Smoke test.
2. `CreateAndDescribe.java` — DDL: create a table, then read back its column metadata.
3. `InsertAndSelect.java` — DML + DQL: insert rows, read them back.
4. `FullCycle.java` — everything: CREATE, INSERT, SELECT, UPDATE, DELETE, DROP in one program.
5. `NullGotcha.java` — demonstrates the `= NULL` vs `IS NULL` trap.

You don't need to understand the JDBC ceremony (`DriverManager`, `Connection`, `Statement`, etc.) yet. That's topic 12. For now, focus on the SQL strings and the printed output.

---

## Try this yourself

1. In `FirstQuery.java`, change `SELECT 1` to `SELECT 1 + 1` and rerun. What changes?
2. In `InsertAndSelect.java`, add a fourth user with an age of `null`, then add a `WHERE age IS NULL` query.
3. Run `NullGotcha.java` and predict the output before reading it. Were you right?

---

## Self-check

1. What's the difference between DDL and DML? Give an example keyword from each.
2. In SQL, what's wrong with `WHERE email = NULL`? What should it be?
3. Why is SQL called "declarative"?
