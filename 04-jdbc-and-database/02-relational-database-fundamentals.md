# 02 — Relational Database Fundamentals

You know what tables, rows, and columns are (topic 01). Now: how tables connect to each other, and why "relational" thinking is different from "list of objects" thinking.

This is the mental model JPA, Hibernate, and Spring Data JPA all assume you have. Getting it solid here saves months later.

---

## Why one big table doesn't work

Imagine an orders table where each row stores everything:

```
orders (the wrong way)

+----+-------------+-------------------+----------+----------+----------+
| id | customer    | customer_email    | product  | price    | quantity |
+----+-------------+-------------------+----------+----------+----------+
|  1 | Alice       | alice@example.com | Laptop   | 1200.00  | 1        |
|  2 | Alice       | alice@example.com | Mouse    | 25.00    | 2        |
|  3 | Bob         | bob@example.com   | Keyboard | 80.00    | 1        |
|  4 | Alice       | alice@x.com       | Cable    | 10.00    | 3        |
+----+-------------+-------------------+----------+----------+----------+
```

Look at row 4. Alice's email changed (she updated it somewhere), but only on this row. Now `customer = 'Alice'` has two different emails depending on which row you read.

This is **data duplication causing inconsistency**. The same fact ("Alice's email is X") is stored in many places, and they can drift apart. Multiply by millions of rows and your reports start lying to you.

The fix: split into separate tables that *reference* each other.

---

## The same data, modeled relationally

```
customers
+----+-------+-------------------+
| id | name  | email             |
+----+-------+-------------------+
|  1 | Alice | alice@example.com |
|  2 | Bob   | bob@example.com   |
+----+-------+-------------------+

products
+----+-----------+--------+
| id | name      | price  |
+----+-----------+--------+
| 10 | Laptop    | 1200.0 |
| 11 | Mouse     |   25.0 |
| 12 | Keyboard  |   80.0 |
| 13 | Cable     |   10.0 |
+----+-----------+--------+

orders
+----+-------------+------------+----------+
| id | customer_id | product_id | quantity |
+----+-------------+------------+----------+
|  1 |           1 |         10 |        1 |
|  2 |           1 |         11 |        2 |
|  3 |           2 |         12 |        1 |
|  4 |           1 |         13 |        3 |
+----+-------------+------------+----------+
```

Now Alice's email lives in exactly **one place**: `customers.id = 1`. The orders table just references her by ID. Update her email once, every order automatically reflects the new value.

The `customer_id` column in `orders` is a **foreign key** — it points to a row in another table. (Foreign keys get their own topic next.)

---

## The three kinds of relationships

Every relationship between two tables falls into one of three patterns. Knowing them is half of database design.

### One-to-one (1:1)

Each row in table A matches exactly one row in table B, and vice versa.

```
users  ←→  user_profiles
(one user has one profile, one profile belongs to one user)
```

Rare in practice. Usually you'd just make them one table. Common reasons to split:

- One side is huge/optional (e.g. `users` + `user_kyc_documents` where most users haven't uploaded docs).
- Performance — wide rows get split so common reads don't pull in rarely-needed columns.

### One-to-many (1:N)

Each row in table A matches many rows in table B. Each row in table B matches exactly one row in A.

```
customers  ←→  orders
(one customer has many orders, each order has one customer)
```

This is the most common relationship by a mile. ~70% of relationships in a typical schema are 1:N.

**How it's stored:** the "many" side has a foreign key to the "one" side. `orders.customer_id → customers.id`.

### Many-to-many (M:N)

Rows in A can match many in B, and rows in B can match many in A.

```
students  ←→  courses
(one student takes many courses, one course has many students)
```

**You can't store this in two tables.** You need a third — a **junction table** (also called a join table or bridge table):

```
students                courses                 enrollments
+----+-------+          +----+----------+       +------------+-----------+
| id | name  |          | id | title    |       | student_id | course_id |
+----+-------+          +----+----------+       +------------+-----------+
|  1 | Alice |          | 10 | Math     |       |          1 |        10 |
|  2 | Bob   |          | 11 | Physics  |       |          1 |        11 |
|  3 | Carol |          | 12 | Biology  |       |          2 |        10 |
+----+-------+          +----+----------+       |          3 |        12 |
                                                +------------+-----------+
```

The junction table holds the relationship. Each row says "this student is in this course." Add a new enrollment = add a row. Cancel one = delete a row.

---

## Why relational thinking is different from object thinking

In Java you might write:

```java
class Customer {
    String name;
    List<Order> orders;  // a customer owns a list of orders
}
class Order {
    Product product;     // an order has a product reference
}
```

The customer *contains* the orders. The order *contains* the product.

In a relational database, **nothing contains anything**. Every table is flat. Relationships are inferred from foreign keys. To get "Alice's orders," you query the orders table where `customer_id = 1`.

This is why ORMs (like Hibernate) exist: they translate between "object graph" thinking and "flat tables with foreign keys" thinking. When you learn JPA later, you'll see annotations like `@OneToMany` and `@ManyToMany` — they're just declaring which of these three patterns you're using.

---

## Common confusions

- **"Tables 'connected' by relationships"** — they're not physically connected. The 'relationship' is just a foreign key column. The database has no special data structure storing the link.
- **"Many-to-many needs only two tables"** — no. You always need three (the junction table).
- **"The 'many' side has the foreign key"** — yes, always. Memorize: *the many points to the one*.

---

## Self-check

1. A `posts` table and a `comments` table — which side holds the foreign key, and what's the relationship called?
2. Why does many-to-many require a third table? What would go wrong if you tried to do it with two?
3. Storing a customer's email on every order row leads to a specific kind of bug. Name it and explain how splitting into two tables fixes it.
