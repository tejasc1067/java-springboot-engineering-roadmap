# 06 — Basic SQL Queries: CRUD in Detail

Topic 05 introduced SQL. This topic drills into each of the four CRUD operations — `INSERT`, `SELECT`, `UPDATE`, `DELETE` — and the variations of each.

These four operations are 95% of what you'll do against a database for the rest of your career. Be fluent with them.

---

## INSERT

The most basic form:

```sql
INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30);
```

This says: insert one row into `users` with these column values.

### Inserting multiple rows at once

```sql
INSERT INTO users (id, name, age) VALUES
    (1, 'Alice', 30),
    (2, 'Bob', 25),
    (3, 'Carol', 35);
```

One statement, three rows. Much faster than three separate `INSERT`s — the database parses and plans only once.

### Omitting columns

If a column has a `DEFAULT` value, an auto-increment, or is nullable, you can leave it out:

```sql
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Only specify name; id and created_at are auto-filled.
INSERT INTO users (name) VALUES ('Dave');
```

### Inserting without column names

```sql
INSERT INTO users VALUES (1, 'Alice', 30);
```

This works if you provide values for *all* columns in *table-definition order*. Don't do this in production code — if someone adds a column later, every implicit insert breaks. **Always name the columns explicitly.**

---

## SELECT

You'll write more `SELECT`s than every other statement combined. Worth getting comfortable.

### Basic SELECT

```sql
SELECT * FROM users;
SELECT name, age FROM users;
SELECT name AS user_name FROM users;
```

- `*` = all columns
- Specific columns by name (preferred)
- `AS` renames the output column

### Why `SELECT *` is bad in production

It looks convenient, but:

1. **Performance.** If the table has 30 columns and you need 2, you're transferring 15× more data over the network.
2. **Brittleness.** Schema change adds a new column. Suddenly your code receives an extra value and your manual `ResultSet` reading breaks.
3. **Intent.** Reading code with `SELECT *` doesn't tell the next person what data is actually used. `SELECT name, email` does.

**Rule:** `SELECT *` is fine in the SQL console for exploration. In application code, always list the columns you need.

### LIMIT (returning only N rows)

```sql
SELECT * FROM users LIMIT 10;
```

Get the first 10 rows. Critical for pagination — never load 1M rows into a UI.

(H2, PostgreSQL, MySQL support `LIMIT`. SQL Server uses `TOP`. Oracle uses `FETCH FIRST n ROWS ONLY`. Different conventions exist.)

### DISTINCT

```sql
SELECT DISTINCT country FROM users;
```

Remove duplicate rows from the result. Returns each unique country once.

---

## UPDATE

```sql
UPDATE users SET age = 31 WHERE id = 1;
```

Update one row. The `WHERE` clause restricts which rows are changed.

### The terrifying mistake: UPDATE without WHERE

```sql
UPDATE users SET age = 31;     -- ← every user is now 31
```

No `WHERE` clause = update *every row*. This has destroyed many a Friday afternoon.

**Defensive habits:**

1. Write the `WHERE` first, then go back and add `SET`.
2. Run a `SELECT ... WHERE ...` first to see what rows match. If the count looks right, swap `SELECT` for `UPDATE`.
3. In production tools, wrap in `BEGIN; ... ROLLBACK;` first to preview the change (more on transactions in topic 11).

### Multi-column update

```sql
UPDATE users SET age = 31, name = 'Alicia' WHERE id = 1;
```

Update several columns in one statement.

### Update with computed value

```sql
UPDATE products SET price = price * 1.1 WHERE category = 'electronics';
```

Increase all electronics prices by 10%. The new value is computed from the existing one.

---

## DELETE

```sql
DELETE FROM users WHERE id = 1;
```

Same warning as `UPDATE`:

```sql
DELETE FROM users;             -- ← every row gone
```

`DELETE` without `WHERE` empties the table. (Notice: the table itself still exists with its schema; only the data is gone. To remove the table itself, use `DROP TABLE`.)

### DELETE vs TRUNCATE vs DROP

| Statement | What it removes | Reversible (inside a transaction)? |
|-----------|----------------|------|
| `DELETE FROM users WHERE ...` | Specific rows | Yes |
| `DELETE FROM users` | All rows | Yes |
| `TRUNCATE TABLE users` | All rows (fast, no logging per row) | Varies by DB. Usually no. |
| `DROP TABLE users` | All rows AND the table structure | No |

Use `DELETE` for normal application work. `TRUNCATE` is for "wipe this table fast." `DROP` is for "I never want this table again."

---

## Verifying your changes

Every DML statement (`INSERT`, `UPDATE`, `DELETE`) returns the **number of rows affected**. Use it.

```java
int rowsUpdated = stmt.executeUpdate("UPDATE users SET age = 31 WHERE id = 1");
if (rowsUpdated == 0) {
    // No row had id = 1. Maybe a bug. Maybe legitimate. Don't silently ignore.
}
if (rowsUpdated > 1) {
    // You expected to update one row but updated more. Probably a bug.
}
```

Checking the returned count catches a lot of bugs — wrong WHERE clauses, race conditions, missing rows — that would otherwise pass silently.

---

## Code examples

1. `InsertVariations.java` — single-row, multi-row, with/without column names.
2. `SelectVariations.java` — `SELECT *`, specific columns, `DISTINCT`, `LIMIT`, aliases.
3. `UpdateWithVerification.java` — uses the returned row count to confirm the change.
4. `DangerousUpdateAndDelete.java` — demonstrates the missing-WHERE catastrophe.
5. `FullCrudCycle.java` — end-to-end CRUD with row-count assertions.

---

## Try this yourself

1. In `SelectVariations.java`, replace `SELECT *` with `SELECT name, age`. Run it and compare the output.
2. In `DangerousUpdateAndDelete.java`, modify the program to add the missing `WHERE` clause and observe how the row count changes.
3. Add a `LIMIT 2` to a `SELECT` and see how many rows come back.

---

## Self-check

1. What's the difference between `DELETE FROM users` and `DROP TABLE users`?
2. Why is `SELECT *` discouraged in application code but fine in the SQL console?
3. You ran `UPDATE products SET price = 0` by mistake. The statement succeeded and the row count returned was 50,000. What's your next move (assuming no transaction was open)?
