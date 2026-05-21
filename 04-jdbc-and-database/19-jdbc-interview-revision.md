# 19 — JDBC and Databases: Revision and Interview Prep

You've worked through 19 topics. This page is a single-screen summary of what should now be muscle memory, plus the questions most likely to come up in a backend interview.

If you can answer each section's "quick recap" question in your own words without looking, you're solid. If not, the topic number is in parentheses.

---

## The mental model

### What's a database, really?

A separate program that stores your data on disk, lets multiple clients talk to it concurrently, enforces structure, and gives you ACID guarantees you couldn't get from raw files. (00, 01)

### What's a relational database specifically?

Data lives in tables with typed columns. Tables reference each other via foreign keys. You query with declarative SQL — the database figures out *how* to execute it. (01, 02)

### What's normalization for?

Eliminate duplicated data. Each fact lives in one place; consistency follows from structure rather than discipline. (04)

### What's JDBC?

Java's standard API for talking to relational databases. An interface, not an implementation — the actual database driver (h2.jar, postgresql.jar) provides the implementation behind the interface. (12)

---

## SQL fluency

### Quick recap: write the SQL for...

(Try each in your head, then check the linked topic if stuck.)

1. Insert one user. (06)
2. Find the user with email 'x@y.com'. (06)
3. List the 5 most recent orders, newest first. (07)
4. Count orders per customer. (07)
5. List all customers, including those with no orders, with their order count. (08)
6. Find users older than the average age. (09)
7. Begin a transaction, do two updates, commit. (11)

### The execution order of a SELECT

`FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT`

This explains why `WHERE` can't use aggregate functions (they don't exist yet) but `HAVING` can. (07)

### Joins in one paragraph

INNER = only matched. LEFT = all from left + matches from right (NULL otherwise). RIGHT = mirror image (rarely used). FULL OUTER = all from both. CROSS = every combination (almost always a bug if accidental). (08)

---

## JDBC essentials

### The 4-type API

`DriverManager → Connection → PreparedStatement → ResultSet`. Each one is `AutoCloseable`; each one must be in a try-with-resources. (12, 13)

### The canonical transaction pattern

```java
conn.setAutoCommit(false);
try {
    // work
    conn.commit();
} catch (Exception e) {
    conn.rollback();
    throw e;
} finally {
    conn.setAutoCommit(true);
}
```

Memorize. (15)

### The canonical SELECT pattern

```java
try (Connection conn = ds.getConnection();
     PreparedStatement ps = conn.prepareStatement(SQL)) {
    ps.setString(1, value);
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            // build domain object
        }
    }
}
```

Memorize. (13)

---

## Security

### SQL injection in one sentence

If user input becomes part of the SQL string (concatenation), an attacker can break out of the intended structure and run arbitrary SQL. (14)

### The fix in one sentence

`PreparedStatement` with `?` placeholders sends SQL and parameters separately; the parameters never get parsed as SQL, so injection is structurally impossible. (14)

### When can't `?` help?

When the dynamic part is an identifier (column/table name, ORDER BY direction). Then validate against an allow-list of known safe values. (14)

---

## Production reality

### Why DriverManager isn't enough

Opening a connection takes 20-100ms. Pooling reuses pre-opened connections. HikariCP is the standard. (17)

### Why transactions should be short

A long-running transaction holds locks the whole time, blocking other writers. Do slow work (external APIs, file I/O) outside the transaction. (15, 17)

### Why indexes aren't free

Indexes speed up reads but slow down writes (every insert updates every index). Index primary keys, foreign keys, frequently-queried columns. Don't index everything. (10)

---

## Common interview questions

### Q: What's the difference between Statement and PreparedStatement?

`Statement` runs a literal SQL string. `PreparedStatement` runs a SQL template with `?` placeholders, with parameters bound separately. PreparedStatement is safer (no SQL injection) and often faster (the database caches the plan). Use `Statement` only for fixed DDL or queries with zero parameters.

### Q: What are ACID properties?

- **Atomicity:** all statements in a transaction succeed or none do.
- **Consistency:** the database moves from one valid state to another (constraints satisfied).
- **Isolation:** concurrent transactions don't see each other's uncommitted changes.
- **Durability:** committed changes survive crashes.

### Q: What's the difference between a JOIN and a subquery? When would you use each?

Both can express the same questions. JOINs are usually faster and let you SELECT columns from both tables. Subqueries are clearer when you're filtering ("WHERE customer_id IN (SELECT...)") rather than combining. For "does any matching row exist," `EXISTS` is safer than `IN` with NULL.

### Q: What does `setAutoCommit(false)` do?

Stops each statement from being committed individually. Subsequent statements are tentative until you call `commit()` (make permanent) or `rollback()` (discard). The transaction starts on the first statement after `setAutoCommit(false)`.

### Q: What's a connection leak and how do you prevent it?

A connection that was opened but never closed. With a pool, leaked connections accumulate until the pool is exhausted; the app errors with "Cannot get connection." Prevent by putting every `Connection` in a `try-with-resources`. Detect via HikariCP's `leakDetectionThreshold`.

### Q: Why use a connection pool?

Opening connections is expensive (TCP, auth, session setup). A pool keeps connections warm so each request reuses one. Critical for any app handling more than a few requests per second.

### Q: How does HikariCP differ from other pools?

Fastest, simplest config, best defaults, excellent leak detection. Spring Boot uses it by default. Earlier pools (DBCP, C3P0) are historical.

### Q: How would you prevent SQL injection?

Use `PreparedStatement` for all queries that include any value (even if you think the source is "trusted"). For dynamic identifiers (column names), validate against an allow-list. Never construct SQL by string concatenation with untrusted input.

### Q: What is `executeQuery` vs `executeUpdate` vs `execute`?

- `executeQuery` for `SELECT` — returns `ResultSet`.
- `executeUpdate` for `INSERT`/`UPDATE`/`DELETE`/DDL — returns `int` (rows affected).
- `execute` is generic — returns `boolean` indicating whether there's a `ResultSet`. Use only when the statement type isn't known at compile time.

### Q: What happens if you forget to close a `ResultSet`?

The driver holds resources (cursor, possibly memory, possibly database-side state) until garbage collection or connection close. With many such leaks, you exhaust the database's per-connection resources or the JVM heap. Always close in `try-with-resources`.

### Q: When would you use batch processing?

When inserting/updating many rows together — bulk imports, migrations, periodic syncs. Each round trip to the database is expensive; batching sends many operations per round trip. Combine with a transaction so the whole batch is atomic.

### Q: What's the difference between `DELETE` and `TRUNCATE`?

`DELETE` removes specific rows (with `WHERE`) and is logged row-by-row, so it can be rolled back inside a transaction. `TRUNCATE` empties the table fast, with minimal logging — typically can't be rolled back. `DROP` removes the table itself.

### Q: What's an N+1 query problem?

You fetch a list of N parents, then for each one, fetch its children with a separate query. Total: 1 + N queries. The fix is one query that joins. (You'll meet this term again in module 10 — JPA — where N+1 is the classic ORM mistake.)

### Q: How do you handle "duplicate key" exceptions cleanly?

Catch `SQLException`, inspect `getSQLState()` — `23000` / `23505` indicate uniqueness violation. Wrap in a domain exception ("UserAlreadyExists") and let the caller decide what to do (return 409 to the API, retry with a different value, etc.).

---

## Self-test: rapid fire

If you can't answer one in 10 seconds, revisit the topic.

1. PRIMARY KEY vs UNIQUE constraint?
2. What's a foreign key's job?
3. `WHERE x = NULL` — what's wrong?
4. WHERE vs HAVING — which comes first?
5. INNER JOIN vs LEFT JOIN — which keeps unmatched rows?
6. What's the JDBC ResultSet's starting position?
7. Are column indexes in JDBC 0-based or 1-based?
8. What does `conn.rollback()` need to be preceded by?
9. Default autocommit value on a fresh `Connection`?
10. What does `executeUpdate()` return?
11. Why is `WHERE LOWER(email) = ...` slow even with an index on email?
12. What's the typical max-pool-size for a Java backend?
13. Name two reasons to prefer `PreparedStatement` over `Statement`.
14. What's the safest way to interpolate a user-chosen sort column into SQL?
15. Why is `BatchUpdateException.getUpdateCounts()` useful?

---

## You're done

You've worked through the persistence layer of a Java backend honestly — not "I've watched videos about JDBC" but "I've written it, broken it, fixed it." That's the bar.

What's next in the roadmap:

- **Module 05** — Maven and Gradle. You'll stop downloading jars by hand.
- **Module 06** — Unit testing with JUnit. You'll test the JDBC code you just wrote.
- **Modules 07–09** — Spring Core, Spring Boot, REST APIs. The connection pool will become a Spring bean; the transaction pattern will become `@Transactional`.
- **Module 10** — Spring Data JPA. Hibernate generates the SQL you've been writing.

Every one of those modules sits on top of what you just learned. The vocabulary, the patterns, the failure modes — all transfer. Spring isn't magic; it's well-organized JDBC.

Onwards.
