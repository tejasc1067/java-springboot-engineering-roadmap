# Common Mistakes — JDBC and Databases

Real bugs that have shipped to real production, with the specific code that causes them and the specific fix.

These are not typos. They're the things that look fine in a code review, pass tests in dev, and break months later under real load or attack.

---

## Schema and modeling

### 1. Table with no primary key

```sql
-- BAD
CREATE TABLE events (event_type VARCHAR(50), payload TEXT);
```

You can't update or delete a single row reliably, can't reference it from another table, can't trust uniqueness. Almost always a bug.

**Fix:** add a primary key. Auto-incrementing surrogate is fine in the vast majority of cases.

```sql
CREATE TABLE events (id BIGINT AUTO_INCREMENT PRIMARY KEY, event_type VARCHAR(50), payload TEXT);
```

---

### 2. Storing multiple values in one column

```sql
-- BAD
CREATE TABLE users (id INT PRIMARY KEY, phone_numbers VARCHAR(500));
-- inserted as: '555-1111, 555-2222'
```

Violates 1NF. Querying for "users with phone 555-1111" requires substring matching. Adding/removing one phone is string manipulation.

**Fix:** separate table.

```sql
CREATE TABLE phones (id INT PRIMARY KEY, user_id INT, number VARCHAR(20));
```

---

### 3. Many-to-many as a comma-separated column

Same as #2 but for relationships. `tags VARCHAR(500) = 'java,db,spring'` is never the right answer. Always use a junction table.

---

### 4. Application-level uniqueness without a database constraint

```java
// BAD
if (userRepository.findByEmail(email) == null) {
    userRepository.save(new User(email));
}
```

Looks fine in single-threaded tests. Two simultaneous signups with the same email both pass the check and both insert. You now have two users with the same email.

**Fix:** add a `UNIQUE` constraint at the database level. Let the database enforce uniqueness; the application check is just convenience.

---

### 5. Nullable columns that shouldn't be

A `users.created_at` declared without `NOT NULL` is almost always a bug — someone forgot. Every nullable column is one more `if (x != null)` branch in your code, forever.

**Fix:** be deliberate. Default to `NOT NULL` unless "no value" is genuinely meaningful for that column.

---

### 6. `ON DELETE CASCADE` everywhere

```sql
-- DANGEROUS
FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
```

One `DELETE FROM customers WHERE last_login < '2020'` becomes "delete every order, every comment, every payment for those users." Often irreversible.

**Fix:** default to `ON DELETE RESTRICT`. Opt into `CASCADE` only where it's clearly correct (e.g. deleting a user should delete their session tokens).

---

## SQL

### 7. `WHERE x = NULL`

```sql
-- BAD: returns nothing, no error
SELECT * FROM users WHERE email = NULL;
```

NULL means "unknown." `NULL = NULL` is itself NULL (not true). The comparison never matches.

**Fix:** `WHERE email IS NULL` (and `IS NOT NULL` for the inverse).

---

### 8. `SELECT *` in application code

```java
ResultSet rs = stmt.executeQuery("SELECT * FROM users");
String name = rs.getString("name");
```

Performance: hauls back every column even if you need two. Brittleness: schema changes can break the row-reading code in subtle ways.

**Fix:** name the columns you need: `SELECT id, name, email FROM users`.

---

### 9. `UPDATE`/`DELETE` without `WHERE`

```sql
UPDATE products SET price = 0;        -- ← every product is now free
DELETE FROM users;                    -- ← every user is now gone
```

This has destroyed many a Friday. No warning, no confirmation.

**Fix:** habit — write the `WHERE` first, then add `SET`. In a SQL console, run `SELECT COUNT(*) FROM ... WHERE ...` first to preview. In production, wrap in a transaction with `ROLLBACK` ready.

---

### 10. `HAVING` when you meant `WHERE`

```sql
-- Functionally works, but inefficient:
SELECT category, COUNT(*) FROM products
GROUP BY category HAVING category = 'electronics';

-- Better — filter before grouping:
SELECT category, COUNT(*) FROM products
WHERE category = 'electronics' GROUP BY category;
```

`WHERE` filters rows before grouping (less work). `HAVING` filters groups after.

**Rule:** if the condition is about a single row, use `WHERE`. If it's about an aggregate, use `HAVING`.

---

### 11. Forgetting the `ON` clause and getting a Cartesian product

```sql
-- BAD: legacy comma syntax with no ON
SELECT * FROM customers, orders;
```

4 customers × 1000 orders = 4000 rows. Then "the query is slow." Then "the database is broken."

**Fix:** always use explicit `JOIN ... ON ...`. Never the comma syntax. The verbose form makes a missing `ON` a parse error.

---

### 12. INNER JOIN losing rows you didn't realize were missing

You report total revenue with `SELECT SUM(amount) FROM orders INNER JOIN customers ON ...`. The number is off because orphan orders (orders whose customer was deleted without `ON DELETE CASCADE`) silently disappear.

**Fix:** if you want "all orders even without a matching customer," use `LEFT JOIN`. Audit periodically with `SELECT COUNT(*) FROM orders` vs the joined count.

---

### 13. Function on indexed column kills the index

```java
// BAD: even with index on email, this scans the whole table
"WHERE LOWER(email) = ?"
```

The database can't use the index because it would have to apply `LOWER()` to every row first.

**Fix:** either store the value already-lowercased (cleaner), or create a functional index `CREATE INDEX ... ON users(LOWER(email))`.

---

## JDBC

### 14. Building SQL with string concatenation

```java
// CATASTROPHIC: SQL injection
String sql = "SELECT * FROM users WHERE email = '" + email + "'";
```

The textbook security failure. Attacker submits `anything' OR '1'='1` and bypasses your auth.

**Fix:** always use `PreparedStatement` with `?` placeholders and bind values with `setString`/`setInt`/etc.

```java
PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE email = ?");
ps.setString(1, email);
```

---

### 15. Connections, statements, ResultSets not closed

```java
// LEAK
Connection conn = DriverManager.getConnection(URL, USER, PASS);
Statement s = conn.createStatement();
ResultSet rs = s.executeQuery("SELECT ...");
// no close anywhere; exception path doubly so
```

Production symptom: app runs fine for an hour, then "Cannot get connection — pool exhausted." Restart fixes it for an hour. Repeat.

**Fix:** every JDBC resource in try-with-resources.

```java
try (Connection conn = ds.getConnection();
     PreparedStatement ps = conn.prepareStatement(SQL);
     ResultSet rs = ps.executeQuery()) {
    while (rs.next()) { /* ... */ }
}
```

---

### 16. Forgetting `conn.commit()`

```java
conn.setAutoCommit(false);
ps.executeUpdate("INSERT ...");
// method returns; connection closes; insert silently vanishes
```

When a connection closes without an explicit `commit()`, most drivers roll back. Your work disappears.

**Fix:** commit explicitly on success, rollback explicitly on failure, both inside try/catch.

---

### 17. Catching SQLException and swallowing it

```java
try {
    ps.executeUpdate();
} catch (SQLException e) {
    log.error("oops");   // no stack trace, no rollback, no rethrow
}
```

Future-you debugging a production incident has nothing to go on.

**Fix:** include the full exception (`log.error("inserting user", e)`), rollback in transactions, and rethrow as a domain exception so callers know.

---

### 18. `DriverManager.getConnection()` per request

```java
@GetMapping("/user/{id}")
public User getUser(@PathVariable long id) {
    try (Connection conn = DriverManager.getConnection(URL, USER, PASS); ...) {
        // 50ms of TCP handshake on every single request
    }
}
```

Connection setup dominates request latency. Under load, you exhaust the database's connection limit.

**Fix:** use a `DataSource` backed by HikariCP. Open the pool once at startup; borrow per request.

---

### 19. Long-running transactions

```java
conn.setAutoCommit(false);
stmt.executeUpdate("UPDATE inventory SET ...");
externalApi.callSlowService();          // 5 seconds, holding the lock
stmt.executeUpdate("UPDATE orders SET ...");
conn.commit();
```

The row(s) updated by the first statement are locked for those 5 seconds. Other transactions trying to touch them queue up. Throughput collapses.

**Fix:** don't put slow non-database work inside a transaction. Either do the API call first and use the result, or split into separate transactions.

---

### 20. Holding a `ResultSet` open while doing other work

```java
ResultSet rs = stmt.executeQuery("SELECT ...");
while (rs.next()) {
    callOtherService(rs.getString(1));   // ResultSet is still open
}
```

The connection (and database cursor) is held for the entire iteration. Slow callees → long-held resources → pool exhaustion.

**Fix:** drain the ResultSet into a `List` first, then do the slow work outside the JDBC try block.

---

### 21. Ignoring `executeUpdate` return value

```java
ps.executeUpdate("UPDATE users SET ... WHERE id = ?");
// returned int discarded
```

A WHERE that matches nothing returns 0. Silent failure — the user thought their update happened.

**Fix:** check the return value. If it's 0 and you expected 1, throw or log meaningfully.

---

### 22. Single-row inserts in a tight loop

```java
for (User u : users) {
    try (Connection conn = ds.getConnection();
         PreparedStatement ps = conn.prepareStatement(SQL)) {
        ps.setString(1, u.name());
        ps.executeUpdate();
    }
}
```

For 10k rows: 10k connection borrowings, 10k prepares, 10k network round trips. Slow.

**Fix:** one connection, one PreparedStatement, `addBatch` per row, `executeBatch` at the end.

---

### 23. JDBC batch without a transaction

```java
// Batch with autocommit on
for (Row r : rows) { ps.addBatch(); ... }
ps.executeBatch();
// each statement may have committed independently
```

A partial failure halfway leaves the table in an inconsistent state. You can't roll back.

**Fix:** `setAutoCommit(false)` before the batch, `commit()` after, `rollback()` on `BatchUpdateException`.

---

### 24. Index everything (write performance death)

```sql
CREATE INDEX idx1 ON users(name);
CREATE INDEX idx2 ON users(email);
CREATE INDEX idx3 ON users(created_at);
CREATE INDEX idx4 ON users(country);
CREATE INDEX idx5 ON users(status);
-- 5 indexes = every insert updates 5 indexes
```

Reads got fast. Inserts got 5x slower. Total throughput dropped.

**Fix:** index PK, FKs, frequently-filtered columns. Stop. Add others only when a measured query is slow.

---

### 25. No index on a foreign key

```sql
CREATE TABLE orders (
    id INT PRIMARY KEY,
    customer_id INT,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);
-- Many databases don't index FKs automatically.
```

Now every JOIN involving `orders.customer_id` is a full table scan.

**Fix:** index every foreign key. Always.

```sql
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
```

---

## Transactions

### 26. Catching the exception but not rolling back

```java
try {
    stmt.executeUpdate("UPDATE A ...");
    stmt.executeUpdate("UPDATE B ...");
    conn.commit();
} catch (Exception e) {
    log.error("failed", e);   // forgot rollback
}
```

The first UPDATE's tentative state hangs around until the connection closes (where it'll be rolled back implicitly — but the connection might be returned to the pool first, with surprising downstream effects).

**Fix:** always `conn.rollback()` in the catch.

---

### 27. Long transactions across user interactions

A "shopping cart" transaction that stays open while the user takes 10 minutes to fill out a form. The locked rows block other carts.

**Fix:** transactions are for single coherent operations, not for "the whole user session." For multi-step business logic across user interactions, use a draft/state machine in the database (e.g. `cart_status = 'pending'`), not a held transaction.

---

### 28. Mixing autocommit modes on the same connection

```java
conn.setAutoCommit(false);
// do work, commit
// forget to set autocommit back to true
// next caller of this connection gets surprising behavior
```

With a pool, the same connection serves the next request still in non-autocommit mode.

**Fix:** restore autocommit in `finally`. Pools like HikariCP also reset on return — but don't rely on it; be explicit.

---

## Configuration / Production

### 29. Hardcoded credentials

```java
private static final String PASS = "Pa$$w0rd";   // committed to git forever
```

**Fix:** environment variables, secrets manager, config file outside the repo. Code should never see the literal password.

---

### 30. No query timeout

A runaway query (missing index, accidental cross join, unexpected data shape) ties up the request thread until the database decides to give up — often many minutes.

**Fix:** `ps.setQueryTimeout(5)` on user-facing queries. In Spring, `@Transactional(timeout = 5)`.

---

### 31. Running DDL from application code

```java
@PostConstruct
void init() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS ...");
}
```

Multiple instances at startup fight. Rolling back a schema change is hard. Reviews are inadequate.

**Fix:** use Flyway or Liquibase. Migrations are SQL files committed to git and run by a tool, not the app.

---

### 32. Retrying every SQLException

```java
catch (SQLException e) {
    retry();   // even for syntax errors, constraint violations, etc.
}
```

You retry a "duplicate key" error 3 times and get 3 failures. Or you retry a syntax error forever.

**Fix:** only retry transient failures (connection blip, deadlock — SQLState classes `08*` and `40001`). Non-transient failures throw immediately.
