# nosql-tradeoffs-demo

The trade-offs between a document store and a relational database, each as a passing test.
`DocumentStore` is a toy in-memory document database (JSON documents by id, no indexes, no
transactions) so this runs without installing anything. H2 plays the relational side.
Topic 16 uses real MongoDB.

- `EmbedVsReferenceTest` — an embedded order reads in one lookup; the embedded customer name goes stale on rename; the relational join doesn't; referencing fixes staleness at the cost of a second lookup.
- `SchemaAndTransactionsTest` — the document store accepts a misspelled field that H2 rejects; a two-document transfer stops halfway, while an H2 transaction rolls back.
- `KeyValueLookupTest` — lookup by key reads 1 entry; lookup by any other field reads all 10,000.

Run:

```
mvn -q test
```
