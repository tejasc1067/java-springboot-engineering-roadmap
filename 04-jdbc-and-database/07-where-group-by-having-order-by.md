# 07 — WHERE, GROUP BY, HAVING, ORDER BY

These four clauses transform `SELECT * FROM users` from "give me everything" into "give me exactly the slice I need, organized the way I need it." Most real queries you'll write are some combination of these.

The execution order matters and is non-obvious — that's where most beginners trip up. Get it right and the rest is mechanical.

---

## The conceptual execution order

When you read a SELECT, you read it like English:

```sql
SELECT name, COUNT(*)
FROM users
WHERE country = 'USA'
GROUP BY name
HAVING COUNT(*) > 1
ORDER BY name
LIMIT 10;
```

But the database **executes** it in a different order:

```
1. FROM users                         ← pick the table
2. WHERE country = 'USA'              ← filter individual rows
3. GROUP BY name                      ← collapse into groups
4. HAVING COUNT(*) > 1                ← filter groups
5. SELECT name, COUNT(*)              ← pick output columns
6. ORDER BY name                      ← sort
7. LIMIT 10                           ← take the first N
```

This explains why some things work and others don't:

- `WHERE` can't reference aggregates (like `COUNT(*)`) because aggregates don't exist yet at step 2.
- `HAVING` *can* reference aggregates because by step 4 they've been computed.
- `ORDER BY` *can* reference column aliases from `SELECT` because step 5 has already happened.
- `WHERE` *can't* reference column aliases from `SELECT` because step 2 hasn't seen them yet.

Memorize the order. It saves hours of "why doesn't this work."

---

## WHERE — filtering rows

```sql
SELECT * FROM users WHERE age >= 18;
SELECT * FROM users WHERE country = 'USA' AND age >= 18;
SELECT * FROM users WHERE country IN ('USA', 'Canada');
SELECT * FROM users WHERE name LIKE 'A%';        -- starts with A
SELECT * FROM users WHERE age BETWEEN 18 AND 65;
SELECT * FROM users WHERE email IS NULL;         -- not = NULL!
```

Operators you'll use constantly:

| Operator | Meaning |
|----------|---------|
| `=`, `!=`, `<`, `>`, `<=`, `>=` | comparisons |
| `AND`, `OR`, `NOT` | combine conditions |
| `IN (a, b, c)` | matches any value in the list |
| `BETWEEN a AND b` | inclusive range |
| `LIKE 'pattern'` | string matching with `%` (any) and `_` (one char) |
| `IS NULL`, `IS NOT NULL` | null checks |

---

## ORDER BY — sorting the result

```sql
SELECT * FROM users ORDER BY age;             -- ascending (default)
SELECT * FROM users ORDER BY age DESC;        -- descending
SELECT * FROM users ORDER BY country, age;    -- by country, then age within each country
```

Without `ORDER BY`, the database returns rows in **whatever order is convenient** — usually insertion order or storage order, but you cannot rely on it. If your code assumes a specific order, add `ORDER BY` or you'll have non-reproducible bugs.

---

## GROUP BY — collapsing rows

`GROUP BY` is where SQL stops feeling like "filter a list" and starts feeling powerful.

```sql
SELECT country, COUNT(*) AS user_count
FROM users
GROUP BY country;
```

This says: "group the rows by `country`, then for each group produce one output row with the country name and a count of how many users were in that group."

Before `GROUP BY`:
```
+----+-------+---------+
| id | name  | country |
+----+-------+---------+
|  1 | Alice | USA     |
|  2 | Bob   | USA     |
|  3 | Carol | India   |
|  4 | Eve   | India   |
|  5 | Dan   | UK      |
+----+-------+---------+
```

After `GROUP BY country`:
```
+---------+------------+
| country | user_count |
+---------+------------+
| USA     |          2 |
| India   |          2 |
| UK      |          1 |
+---------+------------+
```

The 5 rows collapsed into 3 groups.

### Aggregate functions

`GROUP BY` is almost always paired with an aggregate function:

| Function | What it does |
|----------|---------|
| `COUNT(*)` | number of rows in the group |
| `COUNT(column)` | number of non-null values |
| `SUM(column)` | total |
| `AVG(column)` | average |
| `MIN(column)`, `MAX(column)` | smallest, largest |

```sql
SELECT category, SUM(price) AS total_revenue, AVG(price) AS avg_price
FROM products
GROUP BY category;
```

### The GROUP BY rule

If you use `GROUP BY`, every column in the `SELECT` list must either:

- be in the `GROUP BY` clause, or
- be wrapped in an aggregate function

```sql
-- WRONG (`name` is neither grouped nor aggregated)
SELECT country, name, COUNT(*) FROM users GROUP BY country;
```

What would `name` even mean for the row representing all USA users? There are two names (Alice, Bob). The database doesn't pick arbitrarily — it errors. Some databases (older MySQL) silently picked one, which is worse.

---

## HAVING — filtering groups

`WHERE` filters individual rows. `HAVING` filters **groups** after they've been formed.

```sql
SELECT country, COUNT(*) AS user_count
FROM users
GROUP BY country
HAVING COUNT(*) > 1;             -- only groups with more than 1 user
```

This shows USA and India (2 each) but excludes UK (1).

### WHERE vs HAVING — the classic confusion

```sql
-- Filter rows BEFORE grouping: only consider adults
SELECT country, COUNT(*) FROM users WHERE age >= 18 GROUP BY country;

-- Filter groups AFTER grouping: only show countries with > 1 user
SELECT country, COUNT(*) FROM users GROUP BY country HAVING COUNT(*) > 1;

-- Both: only count adults, only show countries with > 1 adult
SELECT country, COUNT(*) FROM users WHERE age >= 18 GROUP BY country HAVING COUNT(*) > 1;
```

**Rule of thumb:** if the condition is about a single row, use `WHERE`. If it's about an aggregate of the group, use `HAVING`.

`WHERE COUNT(*) > 1` doesn't work — `COUNT(*)` doesn't exist yet at the `WHERE` step.

---

## Performance note: filter early

`WHERE` filters before grouping. `HAVING` filters after. So if a condition *can* go in `WHERE`, put it there — you reduce the number of rows the database has to group, which is much faster.

```sql
-- Slow: groups all rows, then discards most groups
SELECT country, COUNT(*) FROM users GROUP BY country HAVING country = 'USA';

-- Fast: filters first, only groups USA rows
SELECT country, COUNT(*) FROM users WHERE country = 'USA' GROUP BY country;
```

The result is the same. The plan is very different.

---

## Code examples

1. `WhereClause.java` — comparison, AND/OR, IN, LIKE, BETWEEN, NULL.
2. `OrderBy.java` — ascending, descending, multi-column.
3. `GroupByAndAggregates.java` — COUNT, SUM, AVG, MIN, MAX with GROUP BY.
4. `HavingClause.java` — filtering groups.
5. `WhereVsHaving.java` — direct comparison: same data, three queries showing where each clause belongs.

---

## Try this yourself

1. Modify `GroupByAndAggregates.java` to find the total price of all products per category, but only for products with `price > 10`.
2. In `WhereVsHaving.java`, swap a `HAVING` for a `WHERE` (or vice versa) and see what error you get.
3. Add `ORDER BY count DESC LIMIT 3` to find the top 3 countries by user count.

---

## Self-check

1. Why can't you use `WHERE COUNT(*) > 5`? Where would you write that instead?
2. You're listing users sorted by age, oldest first. What clause and keyword?
3. What's the difference in execution between `WHERE category = 'electronics'` and `HAVING category = 'electronics'`? Which is faster and why?
