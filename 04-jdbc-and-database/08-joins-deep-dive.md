# 08 — Joins Deep Dive

Normalization (topic 04) split your data across multiple tables. Joins put them back together at query time.

Almost every non-trivial production query has at least one join. Get comfortable here.

---

## The setup

For all examples in this topic, picture two tables:

```
customers                   orders
+----+---------+            +----+-------------+--------+
| id | name    |            | id | customer_id | amount |
+----+---------+            +----+-------------+--------+
|  1 | Alice   |            |  1 |           1 | 100.00 |
|  2 | Bob     |            |  2 |           1 |  50.00 |
|  3 | Carol   |            |  3 |           2 |  75.00 |
|  4 | David   |            |  4 |           5 | 200.00 |    ← orphan (customer 5 doesn't exist)
+----+---------+            +----+-------------+--------+
```

Note: Carol (id 3) has no orders. David (id 4) has no orders. The order with `customer_id = 5` has no customer (an orphan — wouldn't exist if a foreign key constraint were enforced, but useful to demonstrate joins).

---

## INNER JOIN — only matches

```sql
SELECT customers.name, orders.amount
FROM customers
INNER JOIN orders ON customers.id = orders.customer_id;
```

Result:

```
+-------+--------+
| name  | amount |
+-------+--------+
| Alice | 100.00 |
| Alice |  50.00 |
| Bob   |  75.00 |
+-------+--------+
```

Only rows where the join condition matches both sides. **Carol and David disappear** (no orders). **The orphan order disappears** (no customer).

INNER JOIN is the default and the most common. When someone says "JOIN" with no qualifier, they mean INNER JOIN.

---

## LEFT JOIN — everything from the left, even unmatched

```sql
SELECT customers.name, orders.amount
FROM customers
LEFT JOIN orders ON customers.id = orders.customer_id;
```

Result:

```
+-------+--------+
| name  | amount |
+-------+--------+
| Alice | 100.00 |
| Alice |  50.00 |
| Bob   |  75.00 |
| Carol |   NULL |     ← Carol kept, even though she has no orders
| David |   NULL |     ← David too
+-------+--------+
```

All rows from the **left** table (customers) appear. Where there's no match in the right table, the right-side columns are filled with `NULL`.

Use LEFT JOIN when you want "all X, plus any Y they have." E.g., "all customers, plus their order count (which might be zero)."

```sql
SELECT customers.name, COUNT(orders.id) AS order_count
FROM customers
LEFT JOIN orders ON customers.id = orders.customer_id
GROUP BY customers.name;
```

This gives Carol and David `order_count = 0` instead of dropping them.

---

## RIGHT JOIN — everything from the right

Mirror image of LEFT JOIN. Returns all rows from the right table, plus matches from the left.

```sql
SELECT customers.name, orders.amount
FROM customers
RIGHT JOIN orders ON customers.id = orders.customer_id;
```

Result:

```
+-------+--------+
| name  | amount |
+-------+--------+
| Alice | 100.00 |
| Alice |  50.00 |
| Bob   |  75.00 |
| NULL  | 200.00 |     ← the orphan order, no customer to match
+-------+--------+
```

In practice, RIGHT JOIN is **rare**. You can always rewrite it as a LEFT JOIN by swapping the table order:

```sql
-- Same result as the RIGHT JOIN above:
SELECT customers.name, orders.amount
FROM orders
LEFT JOIN customers ON customers.id = orders.customer_id;
```

Most teams use LEFT JOIN exclusively for consistency. RIGHT JOIN exists; you'll see it; you'll basically never write it.

---

## FULL OUTER JOIN — everything from both sides

```sql
SELECT customers.name, orders.amount
FROM customers
FULL OUTER JOIN orders ON customers.id = orders.customer_id;
```

Result:

```
+-------+--------+
| name  | amount |
+-------+--------+
| Alice | 100.00 |
| Alice |  50.00 |
| Bob   |  75.00 |
| Carol |   NULL |
| David |   NULL |
| NULL  | 200.00 |
+-------+--------+
```

All rows from both tables. Unmatched sides get `NULL`. Useful for "find every customer with no orders AND every orphan order" kinds of audits.

(H2, PostgreSQL support it. MySQL doesn't natively — you have to union a LEFT and a RIGHT join.)

---

## CROSS JOIN — every row × every row

```sql
SELECT customers.name, products.name
FROM customers
CROSS JOIN products;
```

Result is the **Cartesian product**: every customer paired with every product. 4 customers × 10 products = 40 rows. Almost always a bug if you didn't intend it.

You usually meet CROSS JOIN by accident — when you forget the `ON` clause:

```sql
-- BUG: no join condition. This is a CROSS JOIN in disguise.
SELECT customers.name, orders.amount
FROM customers, orders;
```

This older comma syntax silently produces a Cartesian product. With 1000 customers and 10000 orders, you get 10 million rows. Then your query "is slow." Always use explicit `JOIN ... ON ...` and never the comma form.

---

## Visualizing joins

```
INNER JOIN        LEFT JOIN         RIGHT JOIN        FULL OUTER JOIN

    ___               ___              ___                  ___
   /   \             /   \            /   \                /   \
  ( A∩B )           (A∪(A∩B))        ((A∩B)∪B)            (A∪B)
   \___/             \___/            \___/                \___/
```

(A = left table rows. B = right table rows. A∩B = rows that match. The shaded area is what's returned.)

---

## Joining more than two tables

```sql
SELECT
    customers.name,
    orders.id        AS order_id,
    products.name    AS product_name,
    order_items.quantity
FROM customers
INNER JOIN orders        ON customers.id  = orders.customer_id
INNER JOIN order_items   ON orders.id     = order_items.order_id
INNER JOIN products      ON order_items.product_id = products.id;
```

Four tables, three joins. The pattern scales — at each step, you're chaining one more "this table connects to that one by this column" rule.

---

## The orphan-row problem (and a hard-won lesson)

In our example, the order with `customer_id = 5` referred to a non-existent customer. With INNER JOIN it silently disappeared from results. Imagine that's an order for $50,000. Your revenue report just under-counts by $50,000 and nobody notices.

**This is why foreign key constraints (topic 03) matter.** A `FOREIGN KEY` would have prevented the orphan from being inserted in the first place.

When you join and get fewer rows than expected, suspect either:

1. Bad join condition (e.g. `ON customers.id = orders.id` — wrong column).
2. Orphan rows being silently filtered out by INNER JOIN.

A quick diagnostic:

```sql
SELECT COUNT(*) FROM orders;
-- vs
SELECT COUNT(*) FROM orders INNER JOIN customers ON orders.customer_id = customers.id;
```

If the second is smaller, you have orphans.

---

## Code examples

1. `InnerJoin.java` — basic inner join. Carol/David/orphan vanish.
2. `LeftJoin.java` — Carol/David appear with NULL amount; orphan still vanishes.
3. `RightJoin.java` — Carol/David vanish; orphan appears with NULL name.
4. `FullOuterJoin.java` — everyone appears.
5. `AccidentalCartesian.java` — what happens when you forget the `ON`. **Watch the row count.**
6. `ThreeTableJoin.java` — joining customers + orders + products via a junction.

---

## Try this yourself

1. In `LeftJoin.java`, change `LEFT JOIN` to `INNER JOIN`. Which rows disappear?
2. Modify `InnerJoin.java` to also include the customer's id. Notice how to disambiguate when both tables have an `id` column.
3. Rewrite `RightJoin.java` as an equivalent LEFT JOIN by swapping the table order.

---

## Self-check

1. You want a report listing every product and its sales count, including products with zero sales. INNER, LEFT, or RIGHT? Why?
2. You wrote `FROM customers, orders` without an `ON` clause. What kind of result do you get and how many rows?
3. Why is RIGHT JOIN considered unnecessary by most teams?
