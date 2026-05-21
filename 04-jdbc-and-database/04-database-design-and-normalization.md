# 04 — Database Design and Normalization

Designing a schema means deciding *what tables exist and what columns they have*. Done well, the schema makes queries easy and prevents whole classes of bugs. Done badly, the schema fights you forever.

Normalization is a set of rules for how to split data across tables to eliminate duplication. You don't need to memorize the formal definitions for the rest of your life — but you do need the intuition behind each one, because every backend engineer makes these tradeoffs constantly.

---

## The problem normalization solves

Recall the bad design from topic 02:

```
orders (denormalized)
+----+-------------+-------------------+----------+----------+
| id | customer    | customer_email    | product  | price    |
+----+-------------+-------------------+----------+----------+
|  1 | Alice       | alice@example.com | Laptop   | 1200.00  |
|  2 | Alice       | alice@example.com | Mouse    | 25.00    |
|  3 | Alice       | alice@x.com       | Cable    | 10.00    |  ← email drifted
+----+-------------+-------------------+----------+----------+
```

Three problems live in this table:

1. **Update anomaly** — Alice changes her email. You have to update every row. Forget one, the data is inconsistent.
2. **Insert anomaly** — A new customer signs up but hasn't ordered anything. You can't add them — the row needs a `product` and `price`.
3. **Delete anomaly** — Alice cancels her only order. Deleting that row also deletes her customer info.

Normalization eliminates all three by splitting the table.

---

## First Normal Form (1NF)

**Rule: each cell holds a single, atomic value. No lists, no comma-separated strings.**

Violation:

```
users
+----+-------+------------------------+
| id | name  | phone_numbers          |
+----+-------+------------------------+
|  1 | Alice | 555-1111, 555-2222     |  ← comma-separated list
+----+-------+------------------------+
```

Querying "find all users with phone 555-1111" requires substring matching. Inserting a new phone means string manipulation. Removing one means parsing and rejoining.

**1NF fix:** make phone numbers their own table.

```
users                       phones
+----+-------+              +----+---------+-----------+
| id | name  |              | id | user_id | number    |
+----+-------+              +----+---------+-----------+
|  1 | Alice |              |  1 |       1 | 555-1111  |
+----+-------+              |  2 |       1 | 555-2222  |
                            +----+---------+-----------+
```

Now every cell holds one value. Each phone is its own row. Querying is `SELECT * FROM phones WHERE number = '555-1111'`. Clean.

**Quick test for 1NF:** could you split this cell's contents on a comma or semicolon and get meaningful pieces? If yes, you're violating 1NF.

---

## Second Normal Form (2NF)

**Rule: every non-key column must depend on the *whole* primary key, not part of it.**

This only matters when you have a **composite primary key** (a key made of multiple columns). With a single-column PK, you're already in 2NF.

Violation: order line items with composite key `(order_id, product_id)`:

```
order_items
+----------+------------+----------+--------------+
| order_id | product_id | quantity | product_name |
+----------+------------+----------+--------------+
|        1 |         10 |        2 | Laptop       |
|        2 |         10 |        1 | Laptop       |  ← duplicated
+----------+------------+----------+--------------+
```

`product_name` depends only on `product_id`, not on `(order_id, product_id)`. So it's duplicated every time the same product appears in another order.

**2NF fix:** move `product_name` to a `products` table keyed on `product_id`.

```
order_items                 products
+----------+------------+   +------------+--------------+
| order_id | product_id |   | product_id | product_name |
+----------+------------+   +------------+--------------+
|        1 |         10 |   |         10 | Laptop       |
|        2 |         10 |   +------------+--------------+
+----------+------------+
```

---

## Third Normal Form (3NF)

**Rule: non-key columns must not depend on other non-key columns.**

Violation:

```
employees
+----+-------+---------------+-----------------+
| id | name  | department_id | department_name |
+----+-------+---------------+-----------------+
|  1 | Alice |            10 | Engineering     |
|  2 | Bob   |            10 | Engineering     |  ← duplicated
+----+-------+---------------+-----------------+
```

`department_name` depends on `department_id`, not on `employees.id` directly. Two non-key columns related to each other = transitive dependency = 3NF violation.

**3NF fix:** make departments its own table.

```
employees                   departments
+----+-------+---------------+    +----+-----------------+
| id | name  | department_id |    | id | name            |
+----+-------+---------------+    +----+-----------------+
|  1 | Alice |            10 |    | 10 | Engineering     |
|  2 | Bob   |            10 |    +----+-----------------+
+----+-------+---------------+
```

Now `department_name` lives once. Rename the department by updating one row.

---

## The pragmatic version

You don't need to memorize 1NF/2NF/3NF formally. Most working backend engineers couldn't define them on the spot. What they *can* do is recognize a smell:

- "Is the same piece of information stored in more than one place?" → likely a normalization issue
- "Could I imagine a list growing inside this cell?" → 1NF problem
- "If I changed one value, would I have to update many rows?" → 2NF or 3NF problem

**Default to normalized.** Split related things into separate tables connected by foreign keys. Only un-normalize when you have a measured performance reason.

---

## Denormalization: when to break the rules on purpose

Sometimes normalized design is too slow. If every page load requires joining 5 tables, you might **denormalize**: duplicate some data on purpose to avoid the join.

Examples:

- A `posts` table stores the post's `author_name` directly (in addition to `author_id`) so listing posts doesn't need a join to `users`.
- An analytics warehouse stores fully flattened rows because reads vastly outnumber writes and analysts want simple queries.
- A `users` table stores `total_orders_count` as a column, updated whenever an order is placed, rather than computing `COUNT(*)` over orders every time the user profile is viewed.

**The rule:** denormalize only when you have *measured* that the join is the bottleneck, and you accept the cost — every duplicated value is a new opportunity for data to go out of sync. You're trading consistency for speed.

In a normal CRUD app, **don't denormalize prematurely.** Premature denormalization is one of the most common rookie mistakes in backend design.

---

## A worked example: designing a blog

Requirements: users write posts. Posts have tags. Users can comment on posts.

### First pass (one big table — wrong)

```
posts (denormalized — bad)
+----+--------+---------+--------------+---------+---------------+
| id | title  | content | author_name  | tags    | comments      |
+----+--------+---------+--------------+---------+---------------+
|  1 | Hello  | ...     | Alice        | java,db | "nice"; "lol" |
+----+--------+---------+--------------+---------+---------------+
```

Every problem we just discussed. Tags are a comma-string (1NF), author_name duplicates per post (3NF), comments are in a cell (1NF).

### Second pass (normalized)

```
users          posts                       comments              tags           post_tags
+----+-----+   +----+--------+-----------+ +----+---------+----+ +----+-----+  +---------+--------+
| id | name|   | id | title  | author_id | | id | post_id | text| | id | name|  | post_id | tag_id |
+----+-----+   +----+--------+-----------+ +----+---------+----+ +----+-----+  +---------+--------+
|  1 | Alice|  |  1 | Hello  |         1 | |  1 |       1 | nice| |  1 | java|  |       1 |      1 |
+----+-----+   +----+--------+-----------+ |  2 |       1 | lol | |  2 | db  |  |       1 |      2 |
                                           +----+---------+----+ +----+-----+  +---------+--------+
```

Five tables. Each piece of data lives in one place. Updates, inserts, deletes all clean.

Notice:

- `users` → `posts` is 1:N (one author, many posts).
- `posts` → `comments` is 1:N.
- `posts` ↔ `tags` is M:N — hence the `post_tags` junction table.

This is what a healthy schema looks like.

---

## Common pitfalls

- **One God table for everything.** "Just one big `data` table." This is a structural disaster. Split early.
- **Storing JSON blobs to avoid designing a schema.** Sometimes legitimate (configs, audit logs). Often a sign someone gave up on modeling. JSON in a relational DB means you lose constraints, joins, and indexes on the inner fields.
- **Denormalizing day one "for performance."** Measure first. Premature denormalization adds bugs without proof of benefit.
- **Many-to-many modeled as a comma-separated column.** `tags VARCHAR(500) = 'java,spring,db'`. Always use a junction table. Always.
- **Naming inconsistency.** `users.userId` here, `orders.user_id` there, `payments.uid` elsewhere. Pick one convention, stick to it.

---

## Self-check

1. A `users` table has columns `id, name, country, country_population`. Which normal form is being violated, and why?
2. You're building a movie database. Movies have multiple genres, genres apply to multiple movies. Sketch the tables needed.
3. Give an example of a legitimate reason to denormalize, and one cost you pay for that choice.
