# NoSQL and When to Use It

"NoSQL" is an umbrella name for databases that don't store data as rows in related tables: document stores like MongoDB, key-value stores like Redis, wide-column stores like Cassandra, and graph databases like Neo4j. Each is very good at one shape of question and worse than a relational database at most others.

This topic gives you the vocabulary, the one design decision that matters most in a document database (embed or reference), and an honest checklist for when *not* to leave PostgreSQL. Topic 16 then puts real MongoDB behind a Spring Data repository.

---

## The problem this solves

Everything in this module so far has been relational: tables, foreign keys, joins, transactions. That model is excellent and should be your default. It also has real costs in some situations:

- **The data is naturally a nested blob.** A product with 40 optional attributes that differ per category (a laptop has RAM, a shirt has a size) becomes either 40 mostly-null columns or an awkward attribute table.
- **The screen reads one thing, always whole.** An order page shows the order, its items, and the shipping address. Relationally that's a join across three or four tables on every page view.
- **Scale beyond one machine's writes.** A relational database scales reads well (replicas, [module 18 topic 06](../18-system-design-basics/06-replication-and-read-replicas.md)), but splitting *writes* across many machines is hard. Some NoSQL databases are built to do that from day one.
- **The question is "what's under this key?" and nothing else.** A session, a cache entry, a rate-limit counter. A full SQL engine is more than the job needs.

NoSQL databases trade away some of the relational guarantees (joins, schemas, multi-row transactions) to be better at one of these.

---

## The four families

| Family | Stores | Great at | Weak at | Examples |
|---|---|---|---|---|
| **Document** | JSON-like documents, grouped in collections | Reading and writing a whole nested object at once; fields that vary per record | Joins across collections; invariants across many documents | MongoDB, Couchbase |
| **Key-value** | A value under a key | "Get/set this key" in microseconds; caches, sessions, counters | Any question that isn't "what's under this key?" | Redis (module 15), DynamoDB in its simplest use |
| **Wide-column** | Rows grouped into partitions, spread across many machines | Huge write volumes (events, sensor data, messages) at predictable speed | Ad-hoc queries; you design one table per query you'll ask | Cassandra, ScyllaDB |
| **Graph** | Nodes and the relationships between them | Following relationships many levels deep ("friends of friends who bought X") | Bulk aggregation and reporting | Neo4j |

The common thread: **NoSQL databases are designed around the queries you'll run.** A relational schema is designed around the data, and SQL can answer questions nobody planned for. A NoSQL schema is designed around the access patterns. Questions you didn't plan for are slow or impossible.

---

## How a document database works

A document is a JSON-like object stored as one unit. Here is an order in MongoDB:

```json
{
  "_id": "o1",
  "customerId": "c1",
  "customerName": "Ann Lee",
  "items": [
    { "sku": "BOOK-1", "qty": 2 },
    { "sku": "PEN-3",  "qty": 1 }
  ],
  "shippingAddress": { "city": "Leeds", "postcode": "LS1 4AP" }
}
```

What would be four tables (`orders`, `order_item`, `address`, and a join to `customer`) is one document. `EmbedVsReferenceTest.embeddedOrder_isReadInOneLookup` reads all of it, items and customer name included, with a single lookup by id. No join, no N+1 (topic 07), and no lazy loading.

The demo uses a tiny in-memory `DocumentStore` (JSON documents by id, no indexes, no transactions) so it runs with no database installed. H2 plays the relational side of every comparison.

### The decision that matters: embed or reference

Every relationship in a document database is either **embedded** (a copy of the data lives inside the document) or **referenced** (the document stores an id, and you look the other thing up separately). Make this decision per relationship, deliberately. It's the document-database version of designing tables.

**Embedding has a cost the relational model doesn't: copies go stale.** The order above embeds `customerName`. `embeddedCopy_goesStaleWhenTheOriginalChanges` renames the customer:

```java
customers.save("c1", ann.put("name", "Ann Park"));

customers.findById("c1").get("name")          // "Ann Park"
orders.findById("o1").get("customerName")     // "Ann Lee"  <- still the old copy
orders.findById("o2").get("customerName")     // "Ann Lee"
```

Nothing is wrong with the database. There are three copies of the name, and only one was updated. To fix it, the application must find and update every order that holds a copy.

The relational version, `relationalRename_isOneUpdateAndVisibleEverywhere`, stores the name **once** and joins to it. One `UPDATE`, and every order shows "Ann Park". This is what normalisation buys you, and what you give up when you embed.

**Referencing avoids staleness and costs a second lookup.** `referencingInsteadOfEmbedding_staysCorrectButCostsASecondLookup` stores only `customerId` in the order. The name is always current, but the *application* now does the join: load the order, then load the customer.

Rules of thumb:

| Embed when | Reference when |
|---|---|
| The data belongs to the parent and is read with it (order items, an address *as it was at order time*) | The data is shared by many parents and changes (a customer's current name, a product's price) |
| It's bounded: an order has tens of items, not millions | It grows without limit: comments on a popular post, log entries for a device |
| A stale copy is correct or harmless (the shipping address *should* stay as it was) | A stale copy is a bug |

Watch for unbounded growth. A MongoDB document has a hard 16 MB limit. Embedding every comment in the post document works until the post goes viral.

---

## What you give up

### A schema the database enforces

A document database accepts whatever fields you send. `SchemaAndTransactionsTest.documentStore_acceptsAMisspelledField` saves `{"emial": "bob@example.com"}` without complaint. The first code to read `email` gets nothing. The same insert into an H2 table with an `email` column fails immediately (`relationalSchema_rejectsTheSameMistake`).

"Schemaless" doesn't mean there is no schema. It means the schema lives in your application code, and nothing stops two versions of your code from writing different shapes. MongoDB can validate documents with a JSON Schema if you turn it on, but it's off by default.

### Transactions across many records

In MongoDB, **a write to one document is atomic**: all of its fields and nested arrays change together, or none do. That's why the document model pushes you to keep things that change together in one document.

Changes that span documents are the hard case. `twoDocumentUpdate_withoutATransaction_canStopHalfway` moves 30 from account A to account B as two separate writes and crashes in between:

```text
A = 70, B = 0  ->  total 70. The 30 has vanished.
```

`relationalTransaction_rollsBackBothRows` does the same in H2 inside a transaction (topic 08). The crash triggers a rollback, and the total is still 100.

MongoDB *does* support multi-document transactions (since version 4.0), but they are slower, have limits, and go against how the database is designed. If your core operations routinely need several records to change together (money, stock levels, bookings), that is a strong sign the data is relational.

### Queries you didn't plan for

`KeyValueLookupTest` stores 10,000 sessions by key. Looking one up by key reads 1 entry. Asking "which sessions belong to user-42?" reads all 10,000, because nothing is organised by user. Real document databases let you add secondary indexes (topic 16), and real key-value stores often have none. In both cases you index for the questions you know about. SQL with a decent schema answers new questions with a new `WHERE` clause, plus an index once the query matters.

---

## When to use it / when not to

**Start with a relational database** (PostgreSQL is the usual choice). Reach for NoSQL when you can name the specific problem it solves:

| Good reason | Not a reason |
|---|---|
| Records are self-contained nested objects, read and written whole, with fields that vary per record (product catalogues, content, user profiles) | "We don't know our schema yet." You do. It's in your code. Migrations (topic 12) handle change. |
| Write volume or data size that one relational primary genuinely can't handle | "NoSQL scales better." A well-indexed PostgreSQL handles far more than most applications ever need. |
| A pure key lookup with expiry: cache, session, rate limit (Redis, module 15) | "Joins are slow." Joins on indexed keys are fast. N+1 is slow (topic 07). |
| Deep relationship traversal (graph) or huge time-ordered event streams (wide-column) | "It's what everyone uses." |

Strong signs to **stay relational**:

- Many-to-many relationships and data that is shared and updated (customers, products, prices).
- Reporting and ad-hoc questions from analysts or the business.
- Invariants that span records: balances, stock counts, "no double booking".
- A small team. One database you understand well beats two you half-understand.

**Polyglot persistence** is common in real systems: PostgreSQL as the source of truth, Redis in front of it as a cache (module 15), and perhaps a search engine for full-text search. Each store does the job it's best at. Every extra database is one more thing to run, back up, and keep consistent, so add them one at a time, for a reason.

One more option before adding a second database: PostgreSQL has a `jsonb` column type that stores and indexes JSON documents inside a relational table. Varying product attributes can live in one `jsonb` column next to normal columns, inside normal transactions.

---

## Common pitfalls

- **Embedding data that changes and is shared.** Customer names, product prices. Every copy goes stale on update. Reference it, or accept that the copy is a snapshot and label it that way (`priceAtPurchase`).
- **Unbounded arrays inside a document.** Comments, events, or log lines embedded in a parent grow toward the 16 MB limit and make every read of the parent slower. Put them in their own collection with a reference to the parent.
- **Modelling documents like tables.** One collection per entity, ids everywhere, and application-side joins on every read. That gives you a relational design without joins or transactions. If every read needs three lookups, either embed or use a relational database.
- **No validation because "it's schemaless".** Validate at the application boundary (module 11's bean validation), and consider the database's own schema validation.

---

## Code examples

`CODE_EXAMPLES/15-nosql-and-when-to-use-it/nosql-tradeoffs-demo/`. Run with `mvn -q test`. Read in this order:

1. `src/main/java/com/example/DocumentStore.java` — the toy document database: JSON documents by id, a lookup counter, and a full-scan `findWhere`.
2. `src/test/java/com/example/EmbedVsReferenceTest.java`:
   - `embeddedOrder_isReadInOneLookup` — the whole order, with items and customer name, in one lookup.
   - `embeddedCopy_goesStaleWhenTheOriginalChanges` — renaming the customer leaves two orders with the old name.
   - `relationalRename_isOneUpdateAndVisibleEverywhere` — the same rename in H2, visible through the join.
   - `referencingInsteadOfEmbedding_staysCorrectButCostsASecondLookup` — no staleness, but the application does the join.
3. `src/test/java/com/example/SchemaAndTransactionsTest.java`:
   - `documentStore_acceptsAMisspelledField` / `relationalSchema_rejectsTheSameMistake`
   - `twoDocumentUpdate_withoutATransaction_canStopHalfway` / `relationalTransaction_rollsBackBothRows`
4. `src/test/java/com/example/KeyValueLookupTest.java` — 1 entry read by key; 10,000 read to find one user's sessions.

---

## Try this yourself

1. In `EmbedVsReferenceTest`, write the "fix" for the stale names: after renaming the customer, use `orders.findWhere(...)` to find and update every order with `customerId = "c1"`. How many documents did it scan? What happens if the program crashes halfway through the updates?
2. In `KeyValueLookupTest`, add a second `DocumentStore` that acts as an index: key `user-42`, value a list of that user's session ids. Answer the user-42 question again and count the documents read. What must now happen on every session save and delete?
3. Sketch, as JSON, a blog post document with its author and comments. For each field, write "embed" or "reference" and one sentence why.

## Self-check

1. An order document embeds the customer's name. The customer changes their name. What does the order page show now, and what are your two ways to fix it?
2. Give one kind of data where embedding is clearly right and one where referencing is clearly right, and say what property of the data decides it.
3. A teammate wants MongoDB for a payments ledger "because it scales". What would you ask about transactions and invariants before agreeing?
4. Name the four NoSQL families and the one question each is best at answering.
