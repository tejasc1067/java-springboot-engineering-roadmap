# Database per Service and Data Ownership

The single most important structural rule in microservices: **each service owns its own database, and no other service may touch it.** Topic 03 said the acid test of a real boundary is whether a service can own its data outright — this topic is that rule made concrete, with the failure you get when you break it. Two services sharing a database are not two services; they're one database with two front doors, and the "microservices" you built will deploy, break, and scale as one unit while costing you a network in the middle.

This is the first code topic. Its broken example is a shared database you can watch fail; its fixed example is the `order-inventory-system` scaffold, where the two services each own a separate H2 database.

---

## The problem this solves

`order-service` needs to know stock levels to accept an order. That data lives in `inventory-service`. The lazy answer — the one that feels efficient — is: *they're both on the same database anyway, so let `order-service` just `SELECT` from the `product` table directly.* No network call, no serialization, a plain SQL join. Fast, simple, done.

It's a trap, and it's the number-one way a microservices migration produces a distributed monolith. The moment `order-service` reads inventory's `product` table, the two services are welded together at the schema:

- `inventory-service` can no longer change its own tables without breaking `order-service` — a rename, a split, a column type change now ripples into code another team owns.
- Neither can be deployed independently: a schema migration has to be coordinated across both.
- There's no encapsulation: inventory's *internal* storage decisions are now inventory's *public API*, whether they meant that or not.

You've paid for two services (two processes, two deploys, network latency between them) and gotten one tangled system that's harder to change than the monolith you started with.

## Vulnerable version: the shared database

`CODE_EXAMPLES/04-database-per-service-and-data-ownership/shared-database-antipattern/` makes this observable with plain JDBC and one H2 database. Two "services" share one `product` table. The orders side reads inventory's table directly:

```java
// orders-service reading inventory's table directly — the anti-pattern
try (var ps = conn.prepareStatement("SELECT stock FROM product WHERE sku = ?")) {
    ps.setString(1, sku);
    ...
}
```

This works — until the inventory team does something completely routine to *their own* table:

```java
// inventory-service evolves its own schema. Entirely their right to do.
ALTER TABLE product ALTER COLUMN stock RENAME TO available_qty;
```

Now run the exact same orders-side query and it throws:

```
Column "STOCK" not found
```

Nothing in orders-service changed. It broke because it reached into a schema it doesn't own. The test asserts exactly this failure — `mvn test` prints:

```
orders-service broke on 'SELECT stock ...' after inventory renamed its own column.
Sharing a database couples their schemas: neither can be deployed independently.
```

That runtime break is the whole lesson. In production it wouldn't surface in a test — it would surface as a 500 in orders-service the day inventory shipped a migration, discovered by a team that didn't make the change and can't easily fix it.

## The fix: one database per service, reached through an API

The `order-inventory-system` scaffold (elsewhere in this module's `CODE_EXAMPLES/`) is the corrected version. Look at the two `application.yml` files:

```yaml
# inventory-service            # order-service
url: jdbc:h2:mem:inventorydb   url: jdbc:h2:mem:orderdb
```

Two separate databases. `order-service` has no JDBC connection to `inventorydb` at all — it *cannot* read inventory's tables even if it wanted to. When it needs stock, it asks over HTTP (topic 05):

```java
StockResponse stock = inventory.getStock(request.sku()); // an API call, not a SQL query
```

Now inventory can rename `stock` to `available_qty`, split its table, switch from H2 to Postgres, add caching — anything — and `order-service` neither knows nor cares, as long as the *API contract* (`GET /api/inventory/{sku}` returns `{sku, available}`) holds. The database is inventory's private implementation detail; the API is its public promise. That separation is the entire point.

Notice too that the two services don't even share the DTO class: inventory has `com.example.inventory.StockResponse` and order has its own `com.example.order.StockResponse`. Sharing a database is the deep coupling; sharing a DTO jar is the same mistake one level up — it re-welds the two build-times together. They agree on JSON, not on Java.

## "But then how does order-service get inventory's data?"

This is the real question, and it has three legitimate answers — none of them "read the other service's tables":

1. **Ask synchronously** (topic 05). `order-service` calls `inventory-service` when it needs an answer *right now*, like at checkout. Simple, always fresh, but couples availability (if inventory is down, you can't check stock — hence topics 09–11).
2. **Be told asynchronously and keep a local copy** (topic 07). `inventory-service` publishes "stock changed" events; `order-service` keeps its own small, read-only replica of just the fields it cares about. No call at request time, survives inventory being down, but the copy is *eventually* consistent (it can be briefly stale).
3. **Store a reference, not a copy.** `order-service` stores the `sku` (a string id) and nothing else about the product. When it needs details, it resolves them via option 1 or 2. This is why the scaffold's `OrderRecord` holds a `sku` string, not a foreign key to a product row.

Which you choose is a real trade-off (freshness vs. availability vs. complexity), and the rest of the module is largely about making these three options safe.

## When to use it / when not to

- **Database-per-service is non-negotiable *if* you've decided to split.** If two services genuinely can't stop sharing tables, that's topic 03 telling you they aren't two services — merge them back.
- **The cost is real and permanent:** you lose cross-service SQL joins, cross-service foreign keys, and cross-service ACID transactions. A query that needs data from three services becomes API composition (topic 08); an operation that must update two services' data becomes a saga (topic 18), because there is no transaction that spans two databases.
- **If those losses are unacceptable for a given pair of tables** — they're always queried together, always updated together, always consistent — that's a strong signal the tables belong to *one* service. Let the data boundaries decide the service boundaries.

## Common pitfalls

- **The "shared read-only" exception.** "order-service only *reads* inventory's table, it never writes — that's safe, right?" No. A read still couples you to inventory's schema (as the demo shows, a rename breaks a pure read) and to its availability and load. Read-coupling is still coupling.
- **The shared "common"/"core" database.** A single database everyone points at "for convenience." It's the anti-pattern wearing a respectable name — every service is now undeployable without considering every other.
- **Sharing an entity/DTO library across services.** Feels DRY; actually re-couples build and deploy. When you change the shared class, every service must recompile and redeploy together — a distributed monolith at the code level. Duplicate the small DTOs instead (as the scaffold does).
- **Cross-service foreign keys.** A foreign key from `orders.sku` to `inventory.product.sku` at the database level assumes one database. Across services you keep the id as a plain value and accept that referential integrity is now your application's job, not the database's.

---

## Code examples

Read in this order:

1. `04-database-per-service-and-data-ownership/shared-database-antipattern/src/test/java/com/example/shareddb/SharedDatabaseAntipatternTest.java` — the broken version. Run `mvn test`; watch the orders-side query break the moment inventory renames its own column.
2. `order-inventory-system/inventory-service/src/main/resources/application.yml` and `order-inventory-system/order-service/src/main/resources/application.yml` — the fix: two separate databases (`inventorydb`, `orderdb`).
3. `order-inventory-system/order-service/src/main/java/com/example/order/OrderController.java` — how order-service gets inventory data *without* the shared table: it calls the API (`inventory.getStock(...)`).
4. `order-inventory-system/order-service/src/main/java/com/example/order/OrderRecord.java` — order-service stores only a `sku` reference, never a copy of inventory's schema.

## Try this yourself

1. Run the anti-pattern test, then edit the migration line to a *different* schema change — change the column's type (`ALTER TABLE product ALTER COLUMN stock SET DATA TYPE VARCHAR`) instead of renaming it. Does the orders-side read still break, and how (silently wrong value vs. exception)? This is why even "compatible-looking" schema changes are dangerous across a shared database.
2. In the scaffold, try to make `order-service` read `inventorydb` directly — add a second datasource pointing at `jdbc:h2:mem:inventorydb`. Notice it doesn't work: the two in-memory databases are separate JVMs, so there's literally no shared table to read. The architecture physically prevents the anti-pattern. That's the point.

## Self-check

1. Two services share one database and one team renames a column in a table they own. Why does that break the other service, and what does that tell you about whether they can be deployed independently?
2. `order-service` needs product data that `inventory-service` owns. List the three legitimate ways it can get that data without reading inventory's tables, and give one trade-off of each.
3. Why is sharing a library of DTO/entity classes across two services a version of the same mistake as sharing a database? What do the two services share instead, in the scaffold?
