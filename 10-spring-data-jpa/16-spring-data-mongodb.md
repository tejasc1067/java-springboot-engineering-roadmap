# Spring Data MongoDB

Topic 15 explained when a document database is the right tool. This topic shows what using one from Spring looks like. **Spring Data MongoDB** is the same family as Spring Data JPA: you declare a repository interface, and Spring writes the queries. The difference is underneath. There are no tables, no Hibernate, no persistence context, and no lazy loading. Each object is saved as one JSON-like document.

If you know `JpaRepository`, you already know 80% of this. The other 20% — embedded documents, indexes that aren't created for you, and `save()` replacing the whole document — is where the bugs are, and each one has a test below.

---

## The problem this solves

You've decided (topic 15) that orders are self-contained documents: the order, its items, and its shipping address are always read and written together. You want to:

- store each order as one document, with no mapping layer to fight;
- query by fields *inside* the document ("orders containing BOOK-1");
- keep the repository style you learned in topics 03–05.

---

## How it works

### Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

In a real app you point it at a server:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/shop
```

The demo's tests use **flapdoodle embedded Mongo** (`de.flapdoodle.embed.mongo.spring3x`, test scope). It downloads a real MongoDB 7.0 server binary once, then starts and stops it for the tests. Nothing to install and no Docker. Module 14 shows the production-like alternative, Testcontainers.

### Mapping: `@Document`, and embedding by default

```java
@Document("orders")
public class Order {
    @Id private String id;              // MongoDB generates an ObjectId if null
    private String customerId;          // reference to a document in "customers"
    private Address shippingAddress;    // embedded
    private List<OrderItem> items;      // embedded
    private String status = "NEW";
}

public record OrderItem(String sku, int quantity, long unitPriceCents) { }
public record Address(String city, String postcode) { }
```

Compare this with JPA (topic 06). There is no `@OneToMany`, `@JoinColumn`, `mappedBy`, or `cascade`. **Any field whose type is another class is embedded** in the parent document. `OrderItem` and `Address` have no `@Document` and no `@Id`, because they only exist inside an order.

`OrderRepositoryTest.savedOrder_isOneDocumentWithItemsInside` reads the raw stored document, bypassing the mapping, to show what MongoDB actually holds:

```json
{
  "_id": ObjectId("6651..."),
  "customerId": "c1",
  "shippingAddress": { "city": "Leeds", "postcode": "LS1 4AP" },
  "items": [
    { "sku": "BOOK-1", "quantity": 2, "unitPriceCents": 1500 },
    { "sku": "PEN-3",  "quantity": 1, "unitPriceCents": 200 }
  ],
  "status": "NEW",
  "_class": "com.example.Order"
}
```

One insert writes all of it, and one `findById` reads all of it. Spring adds `_class` so it knows which Java class to create when reading the document back.

Embed or reference is the decision from topic 15. Here the address is embedded on purpose: an order should keep the address it shipped to, even after the customer moves. The customer is *referenced* by `customerId`, because the name and email change and are shared across orders. (Spring Data also has `@DocumentReference` for loading a referenced document automatically. Start with a plain id field: it's explicit and has no hidden queries.)

### The repository

```java
public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByCustomerId(String customerId);

    List<Order> findByItemsSku(String sku);                              // into the embedded array

    List<Order> findByShippingAddressCityAndStatus(String city, String status);

    @Query("{ 'items': { $elemMatch: { 'quantity': { $gte: ?0 } } } }")
    List<Order> findWithAnyItemQuantityAtLeast(int quantity);
}
```

`MongoRepository` gives you `save`, `findById`, `findAll`, `deleteById`, paging, and sorting, the same as `JpaRepository` (topic 03). Derived query names follow the same rules as topic 04, with one addition: **property paths walk into embedded objects and arrays.** `findByItemsSku("BOOK-1")` becomes the MongoDB filter `{ "items.sku": "BOOK-1" }`, which matches any order where *any* item has that sku. `findByShippingAddressCityAndStatus` becomes `{ "shippingAddress.city": ?, "status": ? }`.

When the name gets unreadable, `@Query` takes a raw MongoDB filter in JSON, the equivalent of JPQL in topic 05. `?0` is the first method parameter. `derivedQueries_reachIntoEmbeddedFields` runs all four.

For updates that a repository can't express, inject `MongoTemplate`, Spring's lower-level API. You'll need it in the lost-update section below.

---

## Broken: the schema lives in your code

MongoDB doesn't know what an `Order` should look like. Your Java class is the only schema. `SchemaInCodeTest` inserts a document the way last month's version of the app wrote it, when the field was called `clientId`:

```java
mongo.insert(new Document("_id", "old-1").append("clientId", "c1"), "orders");

Order old = orders.findById("old-1").orElseThrow();
old.getCustomerId();                 // null
orders.findByCustomerId("c1");       // [] - the old order is invisible to the query
```

There's no error at startup, read, or query. Renaming a field in a JPA entity breaks loudly: Hibernate's schema validation or the SQL fails. Renaming it in a `@Document` silently orphans every existing document.

**Fix:** treat field renames as data migrations, the same as Flyway (topic 12). Run an update that renames the field in every stored document (`$rename`), or keep reading the old name with `@Field("clientId")` until the data is migrated. Tools such as Mongock run these migrations at startup, the way Flyway does for SQL.

---

## Broken: the unique index that doesn't exist

```java
@Document("customers")
public class Customer {
    @Id private String id;
    @Indexed(unique = true) private String email;
    private String name;
}
```

That annotation reads like a guarantee. With default settings, **Spring Data MongoDB does not create the index.** `UniqueIndexTest` saves two customers with the same email, and both are stored:

```java
customers.save(new Customer("ann@example.com", "Ann"));
customers.save(new Customer("ann@example.com", "Ann again"));
customers.findByEmail("ann@example.com");     // 2 results
```

Automatic index creation has been off by default since Spring Data MongoDB 3.0, because building an index on a big production collection at application startup can lock things up for minutes.

**Fix for the demo:** turn it on. `UniqueIndexEnabledTest` sets `spring.data.mongodb.auto-index-creation=true`, and the second save throws `DuplicateKeyException`.

**Fix for production:** create indexes deliberately, in a migration (Mongock, or a script run at deploy time), like any other schema change. The annotation then documents the index, and the migration creates it.

Indexes are also what make queries fast, as in a relational database. Without one, `findByCustomerId` reads every document in the collection (topic 15's full scan). Add an index for each field you query by often.

---

## Broken: `save()` replaces the whole document

`repository.save(order)` sends the **entire** order and replaces what's stored. If two requests load the same order, change it, and save, the second save overwrites the first change. `LostUpdateTest.twoReadModifyWriteSaves_lastOneWins` makes that happen deterministically:

```java
Order seenByA = orders.findById(orderId).orElseThrow();
Order seenByB = orders.findById(orderId).orElseThrow();

seenByA.addItem("BOOK-1", 1, 1500);  orders.save(seenByA);
seenByB.addItem("PEN-3", 1, 200);    orders.save(seenByB);   // BOOK-1 is gone

// stored items: [PEN-3]
```

In JPA each item would be its own row, so two requests each inserting an item would both succeed. In a document, the whole list is one field, so replacing the document replaces the list.

**Fix 1: a targeted, atomic update.** Instead of "here is the whole new order", tell MongoDB "append this item". A single-document update is atomic in MongoDB, so concurrent pushes can't overwrite each other:

```java
mongo.updateFirst(
        Query.query(Criteria.where("_id").is(orderId)),
        new Update().push("items", item),
        Order.class);
```

`atomicPush_keepsBothItems` adds both items this way and both survive. `Update` also has `set`, `inc` (atomic counters, such as stock levels), `pull` (remove from an array), and more.

**Fix 2: optimistic locking.** Add a `@Version Long version;` field to `Order`, as in JPA. A save with an outdated version then throws `OptimisticLockingFailureException` instead of silently overwriting. Use this when you really do need to replace the whole document.

---

## Transactions

A single-document write is always atomic, which is why you embed things that change together. MongoDB also supports multi-document transactions, and Spring's `@Transactional` works with them once you define a `MongoTransactionManager` bean. They need MongoDB running as a *replica set* (a production setup), not the single standalone server the demo starts. If you find yourself needing them on every request, revisit topic 15. The data is probably relational.

---

## When to use it / when not to

| Use Spring Data MongoDB when | Don't, when |
|---|---|
| Topic 15's checklist says documents fit: self-contained aggregates, read and written whole, with fields that vary | The data is relational: shared entities, many-to-many, cross-record invariants. Use JPA (topics 02–14). |
| You want the repository style and derived queries you already know | Your queries are mostly ad-hoc reports across collections. SQL is far better at that. |
| You'll own the indexes and migrations yourself | The team expects the database to enforce the schema for them |

---

## Common pitfalls

- **Trusting `@Indexed(unique = true)` without creating the index.** Duplicates are saved. Enable `auto-index-creation` for dev and tests, and create indexes in a migration for production.
- **Renaming a field in the `@Document` class.** Existing documents silently load `null`. Migrate the data with `$rename`, or map the old name with `@Field`.
- **Read-modify-write with `save()` under concurrent requests.** The last save wins and earlier changes vanish. Use `MongoTemplate` updates (`push`, `inc`, `set`) or `@Version`.
- **Embedding a growing list.** Comments or events in an array that grows forever approach the 16 MB document limit and slow every read (topic 15). Give them their own collection.

---

## Code examples

`CODE_EXAMPLES/16-spring-data-mongodb/mongo-demo/`. Run with `mvn -q test`. The first run downloads MongoDB (about 100 MB), and later runs take seconds. Read in this order:

1. `src/main/java/com/example/OrderItem.java`, `Address.java` — embedded records: no `@Document`, no `@Id`.
2. `src/main/java/com/example/Order.java` — `@Document("orders")`, with embedded items and address and a referenced `customerId`.
3. `src/main/java/com/example/OrderRepository.java` — derived queries into embedded fields, plus one `@Query` filter.
4. `src/test/java/com/example/OrderRepositoryTest.java` — the raw stored document, and every repository query.
5. `src/test/java/com/example/SchemaInCodeTest.java` — an old field name loads as `null`.
6. `src/main/java/com/example/Customer.java` + `CustomerRepository.java` — `@Indexed(unique = true)` on email.
7. `src/test/java/com/example/UniqueIndexTest.java` — the duplicate is accepted (index never created).
8. `src/test/java/com/example/UniqueIndexEnabledTest.java` — with `auto-index-creation=true`, the duplicate throws.
9. `src/main/java/com/example/OrderService.java` — `save()`-based vs `$push`-based item adding.
10. `src/test/java/com/example/LostUpdateTest.java` — the lost update, then the atomic fix.

---

## Try this yourself

1. Add `@Version private Long version;` to `Order` and re-run `twoReadModifyWriteSaves_lastOneWins`. What does the second `save` do now? Update the test to expect it.
2. In `SchemaInCodeTest`, annotate `customerId` with `@Field("clientId")`. What happens to the old document? What happens to every order saved by the current code?
3. Add `List<Order> findByItemsUnitPriceCentsGreaterThan(long cents)` and write a test for it. Then write the same filter by hand with `@Query`.

## Self-check

1. An `Order` has a `List<OrderItem>` field. In JPA that needs `@OneToMany` and a second table. What happens to it in Spring Data MongoDB, and how many queries does `findById` run?
2. `Customer.email` has `@Indexed(unique = true)`, yet two customers share an email in production. Give two reasons this could have happened, and the fix for each.
3. Two requests add an item to the same order at the same moment and one item disappears. Explain why `save()` caused it, and give two different fixes.
4. You rename `customerId` to `buyerId` in the `@Document` class and deploy. Nothing errors. What broke, and how should the rename have been done?
