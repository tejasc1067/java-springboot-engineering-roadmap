# 03 — Primary Keys, Foreign Keys, and Constraints

These are the rules the database enforces about what your data is allowed to look like. They're not optional polish — without them, "the data" becomes "whatever junk anyone managed to write."

The fundamental principle: **enforce invariants at the database, not in the application.** Application code changes, gets bypassed, has bugs. The database is the one place every write must go through. Rules belong there.

---

## Primary key

A **primary key** is a column (or combination of columns) that uniquely identifies each row in a table.

Required properties:

1. **Unique** — no two rows can have the same value.
2. **Not null** — every row must have one.
3. **Stable** — the value should not change over the lifetime of the row.

```sql
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(200)
);
```

`id` is the primary key. Two rows with `id = 1` cannot exist — the database will reject the second insert.

### Why every table needs one

Without a primary key, you can't reliably:

- Update a single row (`UPDATE users SET name = 'X' WHERE ???`)
- Delete a single row
- Reference the row from another table (foreign keys point to primary keys)
- Trust that "your row" is actually unique — you might have duplicates and not know it

Tables without a primary key are almost always a bug. The few exceptions (high-volume log/event tables) are advanced cases.

### Natural vs. surrogate keys

Two schools of thought:

- **Natural key** — use an existing meaningful value, e.g. `email` for users, `isbn` for books.
- **Surrogate key** — use an artificial value with no business meaning, e.g. an auto-incrementing `id`.

Surrogate keys win in practice. Why: business values change. Emails change. ISBNs get corrected. If you keyed everything off `email` and Alice updates her email, every foreign key everywhere has to be updated too. With a surrogate `id`, the email changes and nothing else has to.

**Default to surrogate keys.** Use natural keys only when you have a specific reason.

### Auto-incrementing keys

You don't usually pick primary key values by hand. The database generates them:

```sql
-- H2, PostgreSQL syntax
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100)
);

INSERT INTO users (name) VALUES ('Alice');  -- id becomes 1
INSERT INTO users (name) VALUES ('Bob');    -- id becomes 2
```

The database tracks the next available number. You insert without specifying `id`, and it assigns one.

---

## Foreign key

A **foreign key** is a column that references the primary key of another table. It says "this value must match an existing row in that other table."

```sql
CREATE TABLE customers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100)
);

CREATE TABLE orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT,
    amount DECIMAL(10, 2),
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);
```

The `FOREIGN KEY` line tells the database: every value in `orders.customer_id` must correspond to an actual row in `customers.id`.

### What this prevents

Without the foreign key:

```sql
INSERT INTO orders (customer_id, amount) VALUES (9999, 50.00);
```

This inserts an order for customer 9999. Customer 9999 doesn't exist. The order is now an **orphan** — it refers to a customer that isn't there. Your application code that does `SELECT customer.name FROM customers WHERE id = 9999` returns nothing, and downstream code probably crashes or shows "null" to a user.

With the foreign key, the database rejects the insert. The orphan can never exist.

### Cascading deletes

What happens when you delete a customer who has orders? Three options:

```sql
FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT
-- Refuse to delete the customer if they have any orders. Safe default.

FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
-- Automatically delete all their orders too. Dangerous — easy to lose data.

FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL
-- Set the customer_id to NULL on those orders. Order survives, just orphaned.
```

`RESTRICT` is the safest default. Use `CASCADE` only when you're sure the child rows are *meaningless* without the parent (e.g. a user's session tokens — deleting the user should delete their sessions).

---

## Other constraints

### NOT NULL

A column that cannot be empty.

```sql
email VARCHAR(200) NOT NULL
```

Use it for every column that doesn't have a legitimate "no value" meaning. If a `users.email` is nullable, you'll spend the rest of your career writing `if (email != null)` checks. Make it required at the database level.

### UNIQUE

A column whose values must all be distinct.

```sql
email VARCHAR(200) NOT NULL UNIQUE
```

Now no two users can have the same email. Try to insert a duplicate and the database rejects it.

A primary key is implicitly `NOT NULL UNIQUE`. The `UNIQUE` constraint is for *other* columns that should also be distinct but aren't the primary key.

### CHECK

A custom rule the value must satisfy.

```sql
CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    price DECIMAL(10, 2),
    CHECK (price >= 0)
);
```

Now `INSERT INTO products VALUES (1, 'X', -5.00)` will be rejected. Negative prices were impossible at the database level.

### DEFAULT

A value to use when the insert doesn't specify one.

```sql
status VARCHAR(20) DEFAULT 'PENDING'
```

`INSERT INTO orders (customer_id, amount) VALUES (1, 100)` gives the new order `status = 'PENDING'` without you having to mention it.

---

## Why this matters: the rule belongs at the bottom

Imagine your `users.email` is *not* `UNIQUE` at the database level. Your Java code checks for duplicates before inserting:

```java
if (userRepository.findByEmail(email) == null) {
    userRepository.save(new User(email));
}
```

This works… **most of the time**. Now two users sign up with the same email at the exact same millisecond. Both Java threads run the check at the same time. Both see "no existing user." Both insert. **Two users now exist with the same email.** The application thought it had a uniqueness rule. It didn't.

A `UNIQUE` constraint at the database makes this impossible. Both inserts can't both succeed — one is guaranteed to fail.

**Lesson:** application-level checks are convenience. Database-level constraints are guarantees. You need both, but only one is non-negotiable.

---

## Common pitfalls

- **Table with no primary key.** Almost always a bug. Add one.
- **Using application-generated IDs (e.g. UUIDs) without making them PK.** UUIDs work fine as primary keys; just declare them as such.
- **Foreign key declared but referenced column isn't unique/primary.** Some databases let you do this; don't. Foreign keys should always point to a PK or unique column.
- **No `NOT NULL` on columns that obviously can't be null.** A `users.created_at` that's nullable is a sign nobody thought about it.
- **`ON DELETE CASCADE` everywhere.** One accidental `DELETE FROM customers` and you lose every order in the system. Default to `RESTRICT` and opt in to `CASCADE` only where it's clearly correct.

---

## Self-check

1. You're designing a `payments` table. What columns would you put `NOT NULL` on, and which would be the primary key? Why?
2. A junior dev removes a `UNIQUE` constraint to "fix a bug" where the insert was failing. What's likely actually going wrong in their code, and what's the danger of removing the constraint?
3. Explain in one sentence why a `CHECK (price >= 0)` constraint is better than a Java `if (price < 0) throw ...` check at the API layer. (Hint: you need both, but for different reasons.)
