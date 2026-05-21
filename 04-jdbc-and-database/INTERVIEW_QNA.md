# Interview Questions — JDBC and Databases

Real questions you'll get asked. Each answer is what an interviewer is hoping to hear — not a one-liner restatement of the question.

If you can answer these in your own words without looking, you're prepared for the database section of a typical backend interview.

---

## Database fundamentals

### Q1. Why do backend systems use databases instead of just files?

Files don't handle concurrent writes safely (two writers can clobber each other), don't enforce structure or types, don't provide fast lookups (every query scans the file), don't support transactions, and don't survive partial-write crashes cleanly. Databases solve all five — that's their whole purpose. Once you have those guarantees, you can build reliable systems on top.

### Q2. What's the difference between a database, a DBMS, and a schema?

A DBMS is the program (PostgreSQL, MySQL). It hosts one or more databases — collections of tables. A schema is the structural definition: which tables exist, what columns each has, what constraints apply. In PostgreSQL a schema is also a namespace within a database (you can have multiple). In MySQL "schema" and "database" are synonyms. Confusion arises because everyone uses the term loosely.

### Q3. What are the three kinds of table relationships?

One-to-one (rare; usually combinable), one-to-many (most common — the many side holds the foreign key), and many-to-many (requires a junction/bridge table). Recognizing which pattern fits the data is half of schema design.

### Q4. What does "normalization" mean and why bother?

Normalization splits data across tables so each piece of information lives in exactly one place. The motivation isn't theoretical — it eliminates *update anomalies* (change one fact in many places, miss one, your data is inconsistent), *insert anomalies* (can't add a customer until they place an order), and *delete anomalies* (cancel the order, lose the customer). 3NF is "good enough" for most schemas.

### Q5. When would you deliberately denormalize?

When measurement shows a normalized query is too slow and the read-vs-consistency tradeoff is acceptable. Common case: storing `author_name` directly on `posts` to avoid joining `users` on every post list. Cost: every author rename now has to update many posts. Denormalize from data, not from anxiety.

---

## SQL

### Q6. What's the difference between `WHERE` and `HAVING`?

`WHERE` filters individual rows before grouping. `HAVING` filters groups after `GROUP BY` has aggregated them. `HAVING COUNT(*) > 1` is valid; `WHERE COUNT(*) > 1` is not, because `COUNT(*)` doesn't exist yet at the `WHERE` step. If a condition could go in `WHERE`, put it there — fewer rows reach the grouping step, which is faster.

### Q7. What is the execution order of a SELECT statement?

`FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT`. The text you read top-to-bottom and the order of execution don't match. Knowing the real order explains many "why doesn't this work" mysteries — like why `WHERE` can't see column aliases (they're defined at `SELECT`, step 5).

### Q8. Difference between INNER JOIN and LEFT JOIN?

INNER returns only rows where the join condition matches on both sides. LEFT returns all rows from the left table, with NULLs for any unmatched right-side columns. Use LEFT when you want "all X plus their Y if any" — like "all customers, with their order count, including zero."

### Q9. What's a Cartesian product and how do you accidentally make one?

Every row from one table paired with every row from the other (N × M result). You make one by writing `FROM customers, orders` without a join condition, or `FROM customers CROSS JOIN orders`. Almost always a bug if accidental. Use explicit `JOIN ... ON ...` so a missing `ON` is a parse error.

### Q10. What's the difference between IN and EXISTS in subqueries?

`IN` materializes the inner query's results into a value list. `EXISTS` short-circuits as soon as one matching row is found. For large inner result sets, `EXISTS` is usually faster. `EXISTS` also handles NULLs cleanly — `IN` with a NULL in the list can produce surprising results.

### Q11. What's a correlated subquery and why is it usually a performance trap?

A subquery that references the outer query's columns, so it re-evaluates per outer row. For N outer rows, the inner query runs N times. Almost always rewritable as a JOIN + GROUP BY, which the database can execute in one pass.

### Q12. What's the difference between DELETE, TRUNCATE, and DROP?

`DELETE` removes specific rows (with WHERE), is logged row-by-row, can be rolled back inside a transaction. `TRUNCATE` empties the table fast with minimal logging — usually not rollbackable. `DROP` removes the table itself, structure and all. Use DELETE for normal app code, TRUNCATE for "wipe a table fast," DROP for "this table is gone."

### Q13. Why is `WHERE LOWER(email) = ?` slow even if email is indexed?

Wrapping the indexed column in a function prevents index use — the database would need to apply `LOWER()` to every row to find matches. Fix: either store the value already-lowercased, or create a functional index `CREATE INDEX ... ON users(LOWER(email))`.

---

## Indexes and performance

### Q14. What is an index, conceptually, and what does it cost?

A data structure (typically B-tree) that lets the database find rows by an indexed column in O(log n) time instead of O(n) for a full table scan. The cost: every write to the table must also update every index, so writes are slower; indexes also take disk space. The tradeoff is "speed up reads, slow down writes."

### Q15. Which columns should you index, and which shouldn't you?

Index: primary key (automatic), every foreign key, columns frequently in WHERE clauses. Don't index: columns with very few distinct values (e.g. `gender`), columns rarely filtered, write-heavy tables with no read pressure. Start without extra indexes and add when you measure a slow query.

### Q16. What's an N+1 query problem?

You fetch N parent rows in one query, then for each one issue another query for its children. Total: 1 + N queries. For 100 parents, 101 queries. The fix is one query with a JOIN, fetching everything at once. Classic mistake especially in ORMs that fetch related collections lazily.

### Q17. How do you find slow queries in production?

Database-side slow query logging (`log_min_duration_statement` in PostgreSQL, `slow_query_log` in MySQL) captures queries above a threshold. Then `EXPLAIN` (or `EXPLAIN ANALYZE`) reveals the actual plan — index usage, row estimates, join strategies. Application-side, log query timings and identify the slowest endpoints.

---

## Transactions and ACID

### Q18. What are the ACID properties? Define each in one sentence.

- **Atomicity:** All statements in a transaction succeed together or none do.
- **Consistency:** The database moves from one valid state to another (all constraints satisfied).
- **Isolation:** Concurrent transactions don't see each other's uncommitted changes.
- **Durability:** Once committed, changes survive crashes.

### Q19. What are isolation levels and what tradeoffs do they make?

From weakest/fastest to strongest/slowest: `READ UNCOMMITTED`, `READ COMMITTED`, `REPEATABLE READ`, `SERIALIZABLE`. Stronger levels prevent more anomalies (dirty reads, non-repeatable reads, phantom reads) but slow concurrent throughput. Most production apps use `READ COMMITTED` (the default in PostgreSQL and Oracle) — strong enough for most logic, fast enough for most loads.

### Q20. What's the difference between optimistic and pessimistic locking?

Pessimistic: lock the row when you read it; nobody else can update until you release (often via `SELECT ... FOR UPDATE`). Optimistic: read without locking; before writing, check that the row hasn't changed (often via a `version` column). Pessimistic prevents conflicts but reduces throughput. Optimistic allows conflicts but detects them; you retry or fail. Optimistic is usually preferred for low-conflict workloads.

### Q21. Why should transactions be short?

A transaction holds locks on the rows it touches until commit or rollback. Other transactions trying to modify those rows have to wait. Long transactions = long lock holds = throughput collapse under concurrency. Slow operations (external API calls, file I/O) should happen outside transactions.

### Q22. What's a deadlock and how is it usually resolved?

Two transactions each hold a lock the other needs. Neither can proceed. The database detects this, picks a victim, and rolls it back, returning a serialization error. Application code should catch this specific error and retry. Usually caused by accessing rows in inconsistent orders across transactions — touching tables in the same order on every code path reduces deadlocks.

---

## JDBC

### Q23. What are the four core JDBC types?

`DriverManager` (factory for connections), `Connection` (a database session), `Statement` / `PreparedStatement` (a query to execute), and `ResultSet` (rows returned by a query). Almost all JDBC code uses these four. Other types like `CallableStatement` exist for specific cases.

### Q24. When would you use `Statement` instead of `PreparedStatement`?

Almost never. Use `Statement` only for DDL (`CREATE TABLE`) or fixed queries with no parameters. For anything with values — even values you "know are safe" — use `PreparedStatement`. It's safer (no injection) and the database can cache the plan.

### Q25. Why is `PreparedStatement` immune to SQL injection?

The SQL template (with `?` placeholders) is sent to the database and parsed first — the plan is fixed before any parameter values are seen. Parameters are sent separately as typed data and only get bound to the placeholders. The parser never sees the user's input, so there's nothing to inject into.

### Q26. What's the difference between `executeQuery`, `executeUpdate`, and `execute`?

- `executeQuery(sql)` for SELECT — returns `ResultSet`.
- `executeUpdate(sql)` for INSERT/UPDATE/DELETE/DDL — returns `int` (rows affected).
- `execute(sql)` is generic — returns `boolean` indicating whether there's a result set. Use only when the statement type isn't known at compile time.

### Q27. What does `executeUpdate` return and why should you check it?

The number of rows the statement affected. Zero means the `WHERE` matched nothing — often a bug indicating the row didn't exist or the condition was wrong. Greater than expected indicates the `WHERE` was too broad. Silently ignoring this value hides whole classes of bugs.

### Q28. Why must JDBC resources be closed?

Each open `Connection`, `Statement`, and `ResultSet` holds database-side resources (cursors, locks, connection slots). Leaking them eventually exhausts the database. The standard fix is `try-with-resources`, which closes everything automatically — even on exceptions — in reverse declaration order.

### Q29. Are column indexes in JDBC 0-based or 1-based?

1-based. `rs.getString(1)` reads the first column. Catches every newcomer.

### Q30. What's the difference between accessing a ResultSet column by name vs by index?

By name (`rs.getString("name")`) is clearer and survives column-order changes. By index (`rs.getString(1)`) is marginally faster but brittle if the SELECT order changes. Prefer by name in normal code; consider by index in hot loops over millions of rows.

### Q31. What's `Connection.RETURN_GENERATED_KEYS` for?

When inserting into a table with an `AUTO_INCREMENT` primary key, you usually want the generated ID back. You pass `Statement.RETURN_GENERATED_KEYS` when preparing the statement, then call `ps.getGeneratedKeys()` after `executeUpdate()` to read the new ID.

### Q32. Walk through the canonical multi-step transaction pattern.

```java
conn.setAutoCommit(false);
try {
    // ... statements ...
    conn.commit();
} catch (Exception e) {
    conn.rollback();
    throw e;
} finally {
    conn.setAutoCommit(true);
}
```

Disable autocommit to begin the transaction; commit on success; rollback on any exception; restore autocommit in finally so the next code using this connection isn't surprised.

### Q33. What happens if you forget to call `commit()` after disabling autocommit?

When the connection closes (or is returned to the pool), the driver implicitly rolls back the uncommitted work. Your changes silently vanish. Always explicitly commit on success or rollback on failure.

---

## Batching and bulk operations

### Q34. When is batch processing the right choice, and what speedup do you expect?

For bulk writes — imports, migrations, periodic syncs. Typical speedup: 10–100× depending on network latency and row count. The savings come from fewer round trips, not from the database doing less work. For per-row inserts in a normal API endpoint, batching adds complexity for no gain.

### Q35. Why combine batching with `setAutoCommit(false)`?

So the entire batch is atomic. If any row fails partway through, you roll back the whole batch instead of leaving the table half-updated. Also: without an explicit transaction, the driver may commit each statement separately, defeating part of the speed benefit.

### Q36. What's the right batch size?

Typically 500–5000 rows per `executeBatch()`. Smaller defeats the purpose; larger risks memory issues since the driver buffers the queued statements. For very large imports, chunk: queue up to chunk size, `executeBatch()`, repeat.

---

## Connection pooling

### Q37. Why is `DriverManager.getConnection` per request bad?

Each call does a TCP handshake, possibly TLS, authentication, and session setup — typically 20–100ms. At any meaningful request rate, this dominates request latency and exhausts the database's connection cap. A connection pool keeps connections warm so each request reuses one.

### Q38. What does `conn.close()` do when `conn` came from a HikariCP `DataSource`?

It returns the connection to the pool instead of physically closing it. Subsequent `dataSource.getConnection()` calls reuse the same underlying connection. Your application code looks identical whether or not a pool is in use.

### Q39. What's a reasonable max pool size for a typical web app?

10. Sometimes 5–20 depending on workload. The pool only needs to be as large as the *concurrent in-flight* queries, which is usually much smaller than total request rate. Oversized pools hurt the database (each connection costs memory and CPU there). Start with 10, measure, raise only if `getConnection()` is waiting.

### Q40. What two things commonly cause "Cannot get connection, request timed out"?

Either (a) the pool is too small for the workload — raise the pool size, or scale horizontally; or (b) some code path is holding a connection too long — usually a long-running transaction, an external API call inside a JDBC try block, or an outright connection leak. Diagnose with HikariCP's `leakDetectionThreshold`.

---

## Security

### Q41. How would you prevent SQL injection?

Use `PreparedStatement` for every query that includes a value, with `?` placeholders and `setXxx(...)` to bind values. Never concatenate user input into SQL. For dynamic identifiers (column names that can't be `?`-bound), validate against an explicit allow-list.

### Q42. Your teammate hand-rolled an "escape function" for apostrophes. Is that safe?

No. Hand-rolling SQL escaping is almost always wrong — different databases escape differently, different SQL contexts (string vs. identifier vs. comment) escape differently, and Unicode/encoding tricks bypass naïve escapers. The only correct fix is parameterized queries via `PreparedStatement`.

### Q43. The user picks a column to sort by. `?` can't bind a column name. What do you do?

Validate the user's input against an explicit allow-list of permitted column names. Only known-safe strings reach the SQL.

```java
Set<String> ALLOWED = Set.of("id", "name", "created_at");
if (!ALLOWED.contains(sortCol)) throw new IllegalArgumentException();
String sql = "SELECT ... ORDER BY " + sortCol;
```

### Q44. Where should database credentials live?

In the environment — environment variables, container secrets, or a secrets manager (AWS Secrets Manager, HashiCorp Vault). Never in source code, config files committed to git, or shared docs. Code reads them via `System.getenv(...)` or a config-binding library.

---

## Exception handling

### Q45. What information can you extract from a `SQLException`?

- `getMessage()` — human-readable description.
- `getSQLState()` — five-character standard code (`23000`-class = constraint violation, `08*` = connection, `40001` = serialization).
- `getErrorCode()` — vendor-specific code.
- `getNextException()` — chained exceptions, especially for batches.

### Q46. Should you wrap `SQLException` in your own exception?

Yes, almost always. Callers shouldn't have to import `java.sql.SQLException` to use your DAO. Convert to a domain exception (`UserRepositoryException`, `DataAccessException`) that wraps the underlying cause. Spring's `JdbcTemplate` does this for you.

### Q47. Which SQLExceptions are worth retrying?

Transient failures only: connection errors (`08*`), deadlocks (`40001` or vendor-specific). Never retry deterministic errors like syntax errors or constraint violations — they'll fail the same way every time. Use exponential backoff (e.g. 100ms, 500ms, 2s), a small retry limit (3), and log each attempt.

---

## Production patterns

### Q48. What's an N+1 problem in an ORM context?

You load N parents (one query), then accessing each parent's child collection issues another query — total 1+N. Avoided by eager fetching or an explicit JOIN. JPA, Hibernate, and Spring Data are notorious for hiding the second-N queries behind innocent-looking property access.

### Q49. Why should you use migrations (Flyway/Liquibase) instead of `CREATE TABLE IF NOT EXISTS` from app code?

Schema changes need: review (it's risky code), versioning (you need to know what version of the schema is deployed where), and predictable ordering (multiple app instances starting simultaneously shouldn't fight). Migration tools provide all three. App-code DDL provides none.

### Q50. What's the role of `setQueryTimeout`?

Cap how long an individual statement is allowed to run before throwing `SQLException`. A runaway query (missing index, accidental cross join) would otherwise tie up the request thread until the database itself decides to give up — often many minutes. For user-facing queries, set 5–30 seconds.

---

## Rapid-fire (one-line answers)

51. **Default autocommit on a fresh Connection?** True.
52. **What does `conn.rollback()` need to be preceded by?** `setAutoCommit(false)` and at least one statement.
53. **`UPDATE` without `WHERE` — what happens?** Every row updated.
54. **How do you get the auto-generated ID after an insert?** `prepareStatement(sql, RETURN_GENERATED_KEYS)`, then `ps.getGeneratedKeys()`.
55. **Which side of a 1:N relationship holds the foreign key?** The many side.
56. **What does `ON DELETE CASCADE` do?** Deleting the parent automatically deletes all referencing children.
57. **In SQL, how do you check for NULL?** `IS NULL` (not `= NULL`).
58. **What's a junction (or bridge) table for?** Implementing many-to-many relationships.
59. **What's the standard Java connection pool?** HikariCP.
60. **Why is `SELECT *` bad in code?** Wasteful transfer, brittle to schema changes, unclear intent.
