# 10 — Indexes and Query Optimization

An **index** is a data structure the database keeps alongside a table to speed up reads. Without indexes, finding a row means scanning every row in the table — fast on 1000 rows, glacial on 100 million.

This topic introduces the *mental model* of indexes and how to think about query performance. Real-world optimization is a deep topic — what you need now is enough intuition to write reasonable SQL and recognize obvious problems in code review.

---

## The phone book analogy

Imagine a phone book with 10 million entries. To find "Smith, J.":

- **Without an index:** start at page 1, read every name, stop when you find Smith.
  Worst case: 10 million reads. This is a **full table scan**.
- **With the index (alphabetical order):** open near the S section, scan a few pages.
  ~30 reads. This is an **index seek**.

A database index is the equivalent of "the book is already in alphabetical order." For a `users` table indexed on `email`, finding `alice@example.com` doesn't require scanning every user — the database uses the index to jump directly to the right rows.

---

## How indexes are implemented (briefly)

Most relational databases use **B-tree** indexes by default. A B-tree is a balanced tree structure that lets you find any value in O(log n) time. For a billion rows that's ~30 comparisons.

You don't need to know B-tree internals to use indexes well. Just two facts:

1. Looking up a value by an indexed column is *fast* (O(log n)).
2. Scanning every row of a table is *slow on big tables* (O(n)).

Everything else is consequence.

---

## When the database uses an index

A WHERE condition can use an index when the indexed column is compared directly to a value:

```sql
WHERE email = 'alice@example.com'        -- uses email index
WHERE id BETWEEN 100 AND 200             -- uses primary key index (range)
WHERE name LIKE 'A%'                     -- uses name index (prefix match)
```

A WHERE condition **cannot** use an index when:

```sql
WHERE LOWER(email) = 'alice@example.com'   -- function on column → no index
WHERE name LIKE '%son'                     -- leading wildcard → no index
WHERE email != 'x@y.com'                   -- inequality usually skips index
```

Functions on indexed columns are the most common index-killer. If you find yourself writing `WHERE LOWER(email) = ...`, store the value already-lowercased, or create a **functional index** on `LOWER(email)`.

---

## Creating an index

```sql
CREATE INDEX idx_users_email ON users(email);
```

That's it. From now on, queries with `WHERE email = ...` use the index automatically. You don't change your queries.

**Multi-column index:**

```sql
CREATE INDEX idx_orders_customer_date ON orders(customer_id, created_at);
```

This helps queries that filter by `customer_id`, OR by `customer_id AND created_at`, but not queries that filter only by `created_at` alone. Column order matters — the index is sorted "by customer first, then by date within customer."

**Unique index:**

```sql
CREATE UNIQUE INDEX idx_users_email_unique ON users(email);
```

Doubles as a uniqueness constraint AND a lookup index. Common pattern.

---

## Indexes have a cost

Indexes aren't free. For every write to the table, the database must also update every index on it.

- **5 indexes on a table** = each `INSERT` does 1 row write + 5 index updates. Writes are ~5x slower than no-index.
- **Indexes take disk space** — proportional to the table size for each one.
- **Indexes can become out of date** — `ANALYZE` or auto-stats keep them efficient.

**The tradeoff:** indexes speed up reads, slow down writes. A read-heavy table (analytics, lookup tables) benefits from many indexes. A write-heavy table (logs, events) should have few.

**Rules of thumb:**

- Index every primary key (automatic).
- Index every foreign key (the database doesn't do this automatically — you should).
- Index columns frequently used in WHERE.
- Don't index columns rarely filtered on, or columns with very few distinct values (e.g. `gender`, `is_active`).
- Start without extra indexes. Add them when a query is provably slow.

---

## EXPLAIN: ask the database what it's doing

Every relational database has an `EXPLAIN` (or `EXPLAIN PLAN`) command that shows how a query will be executed — index seek, full scan, join algorithm used, estimated row count, etc.

```sql
EXPLAIN SELECT * FROM users WHERE email = 'alice@example.com';
```

Output varies by database. What you're looking for:

- **"Seq Scan"** or **"Full Table Scan"** — the database is reading every row. Slow on big tables.
- **"Index Scan"** or **"Index Seek"** — the database is using an index. Good.
- A huge "rows examined" number for what should be a small result — something's not using the index it should.

**You don't have to read EXPLAIN output fluently.** You just need to know it exists and recognize "full scan = bad on big table." When a query is unexpectedly slow, run `EXPLAIN` on it.

---

## Clustered vs non-clustered indexes (short version)

- **Clustered index** — defines the physical order of the data on disk. A table can have only one. In MySQL InnoDB and SQL Server, the primary key is usually the clustered index.
- **Non-clustered index** — a separate lookup structure that points back to the actual rows. A table can have many.

You'll encounter the terms in interviews. The practical impact: looking up a row by clustered index is one step (the data is right there). Looking up by non-clustered index is two steps (find the pointer, then fetch the row). For most application code this distinction doesn't matter; for very high-performance work it does.

---

## A common performance pattern: the missing-FK index

Foreign keys are not automatically indexed in most databases. So:

```sql
CREATE TABLE orders (
    id INT PRIMARY KEY,
    customer_id INT,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

-- This is FAST (uses primary key index):
SELECT * FROM customers WHERE id = 42;

-- This is SLOW (no index on orders.customer_id):
SELECT * FROM orders WHERE customer_id = 42;
```

**Fix:**

```sql
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
```

This single fix can make joins involving the FK 100× faster.

**Rule:** every foreign key should have an index. Always.

---

## Code examples

1. `FullTableScanVsIndex.java` — same query on a 50,000-row table, with and without an index. Times both.
2. `ExplainPlan.java` — reads H2's EXPLAIN output to show what plan the database chose.
3. `IndexWriteCost.java` — measures insert speed with 0, 1, and 5 indexes. Demonstrates the write penalty.
4. `FunctionKillsIndex.java` — shows that `WHERE LOWER(email) = ...` skips the index, even though `WHERE email = ...` uses it.

---

## Try this yourself

1. In `FullTableScanVsIndex.java`, change the table size from 50,000 to 500,000. How does the ratio change?
2. In `FunctionKillsIndex.java`, drop the existing email index and create a functional index `CREATE INDEX ... ON users(LOWER(email))`. Re-run. Does the function-call query speed up?
3. Create a composite index `(country, age)`. Compare `WHERE country = 'USA' AND age > 30` vs `WHERE age > 30`. Which uses the index?

---

## Self-check

1. You're reviewing a new table that has a primary key but no other indexes, and 3 foreign keys. What's the most likely performance problem?
2. Why is `WHERE LOWER(email) = 'x@y.com'` slow even if there's an index on `email`?
3. Indexes speed up reads. What do they slow down, and why?
