# Spring Data JDBC

Spring Data JDBC gives you the same repository interfaces as Spring Data JPA (`save`, `findById`, derived queries, `@Query`) without JPA or Hibernate underneath. There is no persistence context, no lazy loading, no dirty checking, and no proxies. A method call runs the SQL it obviously should, and you can read every statement in the log.

Topic 14 was a catalogue of bugs caused by Hibernate's hidden machinery. This topic shows a model where most of that machinery doesn't exist. It also has to be honest about the different rules you follow instead, because Spring Data JDBC has a few surprises of its own.

---

## The problem this solves

JPA does a lot for you, and each convenience can bite:

- **Lazy loading** can throw `LazyInitializationException` after the transaction ends (topic 14, pitfall 1), or cause N+1 queries (topic 07).
- **Dirty checking** writes changes you never asked to save, and ignores changes made to detached entities (topic 14, pitfall 2).
- **Object graphs with cascades** let one `save` or `delete` reach much further than you meant (topic 14, pitfall 8).

Some teams decide they'd rather write a little more code and have none of those surprises. Spring Data JDBC is built for them. It's the middle ground between raw JDBC (module 04) and JPA.

---

## How it works

### Aggregates: the one idea to learn

Spring Data JDBC is built around **aggregates**, a term from Domain-Driven Design. An aggregate is a cluster of objects that are always loaded, saved, and deleted **together**, through one **root**:

- `Order` is a root. Its `OrderItem`s belong to it, and nothing else ever points at an item.
- `Customer` is a *different* aggregate. Many orders refer to it, but saving an order must never save a customer.

The rules that follow from this:

1. **One repository per aggregate root.** There's an `OrderRepository`, and deliberately no `OrderItemRepository`.
2. **Children are saved and loaded with their root.** No cascade settings, because it's always "everything in the aggregate".
3. **Other aggregates are referenced by id, never by object.** An `Order` holds a customer *id*, not a `Customer` you can navigate into.

```java
@Table("PURCHASE_ORDER")          // ORDER is a reserved word in SQL
public class Order {
    @Id private Long id;
    private AggregateReference<Customer, Long> customerId;   // another aggregate: id only
    private String status = "NEW";

    @MappedCollection(idColumn = "ORDER_ID", keyColumn = "LINE_NO")
    private List<OrderItem> items = new ArrayList<>();        // part of this aggregate
}

public class OrderItem {           // no @Id, no repository: it only exists inside an Order
    private String sku;
    private int quantity;
}
```

`@MappedCollection` tells Spring which column in `order_item` points back at the order (`ORDER_ID`) and which stores each item's position in the list (`LINE_NO`). `AggregateReference<Customer, Long>` is a typed wrapper around the customer's id. It says "this is a `Customer` id" without loading the customer.

### You write the schema

Spring Data JDBC never creates tables. The demo puts the DDL in `src/main/resources/schema.sql`, which Spring Boot runs at startup for embedded databases. A real project uses Flyway (topic 12). There's no `ddl-auto` to argue about.

### The repository

```java
public interface OrderRepository extends ListCrudRepository<Order, Long> {

    List<Order> findByStatus(String status);

    @Query("select o.* from purchase_order o join order_item i on i.order_id = o.id where i.sku = :sku")
    List<Order> findContainingSku(String sku);
}
```

Derived queries work as in topic 04, for the root's own columns. `@Query` takes **plain SQL**, because there is no JPQL. `AggregateTest.customQuery_isPlainSql` runs both.

### What the tests show

**Saving the root saves the children.** `savingTheRoot_savesItsItems` saves one `Order` with two items, and two `order_item` rows appear.

**A loaded aggregate is complete.** `loadedAggregate_isComplete_noLazyLoading` loads an order, confirms no transaction is open, and reads its items. In JPA that's the exact setup for `LazyInitializationException`. Here the items were loaded with the order, because there is no lazy loading at all. The trade-off is that you can't load an order *without* its items, so keep aggregates small.

**Other aggregates are referenced by id.** `otherAggregates_areReferencedById_notLoaded` reads `order.getCustomerId().getId()`. To get the customer's name you call `customerRepository.findById(...)` yourself. That's one more line, and you can see the query instead of discovering it in a log.

---

## Broken: expecting dirty checking

In JPA, this persists the change (topic 08). The entity is *managed*, and Hibernate writes any changes at commit:

```java
@Transactional
public void shipWithoutSave(Long orderId) {
    Order order = orders.findById(orderId).orElseThrow();
    order.setStatus("SHIPPED");
}
```

In Spring Data JDBC, `findById` returns a plain Java object. Nothing is watching it. `NoDirtyCheckingTest.changingALoadedObject_withoutSave_writesNothing` calls this method and the status is still `NEW`.

**Fix:** call `orders.save(order)`. This is the rule for every change: nothing is written unless you call `save`. `explicitSave_writesTheChange` shows it working. Developers coming from JPA forget the `save`; developers who learned JDBC first find it natural.

---

## Broken: `save()` rewrites every child

You load an order, change only its status, and save it. The SQL log shows what Spring Data JDBC does:

```text
UPDATE "PURCHASE_ORDER" SET "STATUS" = ?, "CUSTOMER_ID" = ? WHERE "PURCHASE_ORDER"."ID" = ?
DELETE FROM "ORDER_ITEM" WHERE "ORDER_ITEM"."ORDER_ID" = ?
INSERT INTO "ORDER_ITEM" ("LINE_NO", "ORDER_ID", "QUANTITY", "SKU") VALUES (?, ?, ?, ?)
```

Without dirty checking it doesn't know which items changed, so it **deletes all of them and inserts the ones in memory**. The aggregate you hold is treated as the whole truth.

`savingTheRoot_rewritesAllItems_fromWhatIsInMemory` shows the consequence. After the order is loaded, someone else adds a `GIFT-WRAP` item row directly with SQL: another process, a support script, a manual fix. Then the order's status is saved:

```text
items before save: BOOK-1, GIFT-WRAP
items after  save: BOOK-1
```

The gift wrap is gone, without an error.

**What to do:**

- Change an aggregate *only* through its root and repository, never with side-door SQL on child tables. That's the aggregate rule, and this is what breaking it costs.
- Keep aggregates small. An order with 10 items is fine; a "customer" aggregate holding 50,000 orders would delete and re-insert 50,000 rows on every save. If a collection can grow without limit, it belongs in its own aggregate, referenced by id.
- For concurrent edits of the same aggregate, add a `@Version` field. A save with an outdated version then fails instead of overwriting.

---

## Broken: an id you set yourself

`save()` decides between `INSERT` and `UPDATE` by looking at the id. If it's `null`, the entity is new and gets an `INSERT`. Otherwise it's treated as existing and gets an `UPDATE`. That works for database-generated ids. It goes wrong for **natural keys**, ids you choose yourself, such as a product SKU:

```java
products.save(new Product("SKU-1", "Notebook"));
```

`NaturalKeyTest.saveWithAnIdAlreadySet_runsAnUpdate_andSilentlySavesNothing`:

```text
UPDATE "PRODUCT" SET "NAME" = ? WHERE "PRODUCT"."SKU" = ?     <- matches 0 rows
```

No exception, and no product. The insert you meant never happened.

**Fixes**, any one of them:

- Insert explicitly: `jdbcAggregateTemplate.insert(product)` (the `explicitInsert_works` test).
- Add a `@Version Long version;` field. Spring Data then decides "new" by the version being `null`, not the id.
- Implement `Persistable<String>` on the entity, and return `true` from `isNew()` for objects that haven't been saved yet.

JPA has a version of this problem too: `save` on an entity with an id calls `merge`, which runs an extra `SELECT` first. Spring Data JDBC fails more quietly.

---

## Also worth knowing

- **Name quoting.** Names you give in `@Table` or `@MappedCollection` are quoted in the SQL, which makes them case-sensitive. H2 and PostgreSQL fold unquoted names differently (H2 to upper case, PostgreSQL to lower case), so `@Table("purchase_order")` failed against H2 in this demo with `Table "purchase_order" not found (candidates are: "PURCHASE_ORDER")`. Match the case the database actually stores. That's why the demo uses upper case.
- **No `@ManyToMany`.** A many-to-many relationship is two aggregates and a join table that one of them owns, holding ids.

---

## Spring Data JDBC vs JPA

| | Spring Data JPA | Spring Data JDBC |
|---|---|---|
| What runs underneath | Hibernate: persistence context, proxies, flush | Plain SQL through `JdbcTemplate` |
| Loading related data | Lazy or eager per relationship; N+1 and `LazyInitializationException` possible | The whole aggregate, every time; other aggregates by id only |
| Writing changes | Dirty checking at commit; `save` often unnecessary | Only on `save`; nothing else writes |
| Saving a collection | Inserts, updates, or deletes the changed rows | Deletes all child rows and re-inserts them |
| Queries | Derived, JPQL, native SQL, Criteria/Specifications (topic 10) | Derived, plain SQL |
| Schema | Can generate it (`ddl-auto`), should use Flyway | You write it (Flyway) |
| Learning curve | Steep: you need to know the hidden machinery | Shallow, but you must design aggregates |

**Choose JPA when** your domain has rich object graphs that are navigated in many directions, you want features like Specifications and second-level caching, or the team already knows Hibernate well. It's still the most common choice in Spring Boot.

**Choose Spring Data JDBC when** your model splits naturally into small aggregates, you want every SQL statement to be predictable, or the team has been burned by lazy loading and dirty-checking surprises.

Both use the same `spring.datasource` settings and transactions (topic 08), and they can live in one application.

---

## Common pitfalls

- **Expecting a change to persist without `save`.** No dirty checking: call `save` after every change.
- **Big or unbounded aggregates.** Every save deletes and re-inserts all children. Split anything that grows into its own aggregate.
- **Writing to child tables outside the root.** The next save of the root deletes those rows. Change children only through the aggregate.
- **`save()` with a pre-set id on a new entity.** It runs an `UPDATE` that matches nothing. Use `JdbcAggregateTemplate.insert`, `@Version`, or `Persistable`.

---

## Code examples

`CODE_EXAMPLES/17-spring-data-jdbc/jdbc-aggregate-demo/`. Run with `mvn -q test`. SQL logging is on, so read the statements as the tests run. Read in this order:

1. `src/main/resources/schema.sql` — the tables you write yourself. `order_item` has no id of its own.
2. `src/main/java/com/example/Order.java`, `OrderItem.java`, `Customer.java` — one aggregate with children, one aggregate referenced by id.
3. `src/main/java/com/example/OrderRepository.java` — a derived query and a plain-SQL `@Query`.
4. `src/test/java/com/example/AggregateTest.java` — save with children, complete loading, id references, the delete-and-reinsert rewrite, plain SQL.
5. `src/main/java/com/example/OrderService.java` + `src/test/java/com/example/NoDirtyCheckingTest.java` — a change without `save` is lost.
6. `src/main/java/com/example/Product.java` + `src/test/java/com/example/NaturalKeyTest.java` — the silent `UPDATE` for a natural key, and `insert` as the fix.

---

## Try this yourself

1. Add `@Version private Long version;` to `Product` (and a `version bigint` column) and re-run `saveWithAnIdAlreadySet_runsAnUpdate_andSilentlySavesNothing`. What SQL runs now? Update the test.
2. Add a `@Version` field to `Order` (and a column). In `savingTheRoot_rewritesAllItems_fromWhatIsInMemory`, load the order *twice*, save one copy with a changed status, then save the other. What happens to the second save?
3. Change `@Table("PURCHASE_ORDER")` back to `@Table("purchase_order")` and run the tests. Read the error message. Which database rule causes it?

## Self-check

1. What is an aggregate? For an online shop, name two aggregates, and one object that belongs *inside* one of them.
2. A `@Transactional` method loads an order, sets its status, and returns without calling `save`. What happens under JPA, and what happens under Spring Data JDBC? Why?
3. You change only an order's status and call `save`. What SQL does Spring Data JDBC run for the order's items, and what design rule does that force on you?
4. `productRepository.save(new Product("SKU-1", "Notebook"))` returns normally, but the product isn't in the database. Explain why, and give two fixes.
