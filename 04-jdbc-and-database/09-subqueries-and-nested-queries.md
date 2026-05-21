# 09 — Subqueries and Nested Queries

A **subquery** is a `SELECT` inside another `SELECT` (or `INSERT`, `UPDATE`, `DELETE`). They let you express "use the result of this query as input to that query" without round-tripping to your application code.

Subqueries are powerful but can be slow if used carelessly. Knowing when to use them — and when a JOIN does the same job faster — is part of writing maintainable SQL.

---

## The simplest case

"Find users older than the average age."

You could do it in two steps:

```sql
SELECT AVG(age) FROM users;          -- step 1, returns e.g. 28.5
SELECT * FROM users WHERE age > 28.5;  -- step 2, hardcoded
```

With a subquery, one statement:

```sql
SELECT * FROM users WHERE age > (SELECT AVG(age) FROM users);
```

The inner query runs first, returns a single value, and the outer query uses it.

---

## Three places subqueries appear

### 1. In the WHERE clause (most common)

```sql
SELECT * FROM orders
WHERE customer_id IN (SELECT id FROM customers WHERE country = 'USA');
```

"Orders placed by USA customers." The subquery returns a *set* of IDs; the outer `IN` checks membership.

### 2. In the SELECT list (computed columns)

```sql
SELECT
    name,
    (SELECT COUNT(*) FROM orders WHERE customer_id = customers.id) AS order_count
FROM customers;
```

For each customer, a subquery counts their orders. This works but is often replaced by a JOIN + GROUP BY for performance (see below).

### 3. In the FROM clause (derived table)

```sql
SELECT category, max_price
FROM (SELECT category, MAX(price) AS max_price FROM products GROUP BY category) sub
WHERE max_price > 100;
```

The subquery acts as a temporary table named `sub`. Useful when you want to filter or further-aggregate the output of an aggregation.

---

## IN vs EXISTS

Two ways to ask "does a related row exist."

```sql
-- IN: pass a list of values
SELECT * FROM customers
WHERE id IN (SELECT customer_id FROM orders);

-- EXISTS: pass a query that returns at least one row
SELECT * FROM customers c
WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);
```

Both return customers who have at least one order. Differences:

- `IN` materializes the inner query into a list, then checks each outer row against the list.
- `EXISTS` evaluates the inner query per outer row and short-circuits as soon as one match is found.

**Performance rule of thumb:**

- For small inner result sets: `IN` is usually fine.
- For very large inner result sets: `EXISTS` is often faster.
- Modern databases optimize both pretty well — measure with `EXPLAIN` before worrying about it (topic 10).

**Behavior differences with NULL:**

- `WHERE x IN (1, 2, NULL)` returns nothing for rows where `x = 3` (one of the comparisons is NULL, which propagates). Subtle, footgun.
- `EXISTS` doesn't have this issue.

When in doubt, prefer `EXISTS`. It's safer with NULLs and tends to perform consistently.

---

## Correlated vs non-correlated subqueries

A **non-correlated** subquery runs once and produces a value (or set) the outer query uses:

```sql
SELECT * FROM users WHERE age > (SELECT AVG(age) FROM users);
```

The inner `SELECT AVG(age)` doesn't reference the outer query. It runs once. Cheap.

A **correlated** subquery references the outer query, so it runs *once per outer row*:

```sql
SELECT name,
       (SELECT COUNT(*) FROM orders WHERE customer_id = customers.id) AS order_count
FROM customers;
```

The inner subquery references `customers.id` from the outer row. The database has to evaluate it once for *every* customer. For 4 customers it's fine. For 4 million it's catastrophic.

**This is the #1 subquery performance trap.** Correlated subqueries on large tables are usually rewritten as JOINs:

```sql
-- Same result, dramatically faster
SELECT customers.name, COUNT(orders.id) AS order_count
FROM customers
LEFT JOIN orders ON orders.customer_id = customers.id
GROUP BY customers.name;
```

When you see a correlated subquery on a large table, mentally flag it for rewriting as a JOIN.

---

## When to use a subquery vs a JOIN

You can often write the same query both ways:

```sql
-- Subquery
SELECT * FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE country = 'USA');

-- JOIN
SELECT orders.*
FROM orders
INNER JOIN customers ON customers.id = orders.customer_id
WHERE customers.country = 'USA';
```

The JOIN version is usually:

- Faster (databases optimize JOINs more aggressively)
- More flexible (you can SELECT columns from both tables)

The subquery version is usually:

- Clearer when you're filtering, not joining (e.g. "find X where Y exists")
- Better with `EXISTS` for "does any matching row exist" checks

**Rule of thumb:** if you need columns from both tables, use a JOIN. If you're only filtering, use a subquery or `EXISTS`.

---

## Subqueries in DML

Subqueries aren't just for SELECT.

```sql
-- Delete orders from customers in a specific country
DELETE FROM orders
WHERE customer_id IN (SELECT id FROM customers WHERE country = 'XYZ');

-- Insert from another table
INSERT INTO archived_orders SELECT * FROM orders WHERE created_at < '2020-01-01';

-- Update based on another table
UPDATE products
SET price = price * 1.1
WHERE category IN (SELECT id FROM categories WHERE name = 'electronics');
```

Subqueries in `DELETE` and `UPDATE` are powerful — and dangerous. Always test with `SELECT` first to confirm the subquery returns what you expect.

---

## Code examples

1. `SimpleSubquery.java` — value from an aggregate used in the outer WHERE.
2. `InOperator.java` — `WHERE x IN (subquery)`.
3. `ExistsOperator.java` — `EXISTS (subquery)` and why it's often safer than `IN`.
4. `CorrelatedSubquery.java` — runs once per outer row. Same result, slower.
5. `SubqueryVsJoin.java` — same query written both ways, side by side.

---

## Try this yourself

1. In `SubqueryVsJoin.java`, add a `System.nanoTime()` around each query and compare runtimes. With small data the difference will be tiny; the *plan* is what differs.
2. Rewrite `CorrelatedSubquery.java` as a JOIN + GROUP BY and compare the output.
3. Modify `ExistsOperator.java` to use `NOT EXISTS` and observe what changes.

---

## Self-check

1. What's the difference between a correlated and a non-correlated subquery? Which one usually performs worse on large data?
2. You're checking "does this customer have any orders." Why might `EXISTS` be safer than `IN`?
3. When is a JOIN preferable to a subquery, and when is a subquery preferable?
