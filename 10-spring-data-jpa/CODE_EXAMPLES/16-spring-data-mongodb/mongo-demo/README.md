# mongo-demo

Spring Data MongoDB against a real MongoDB 7.0 server, started by
[flapdoodle embedded Mongo](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo.spring) for the tests.
No Docker and no install. The first run downloads the MongoDB binary (about 100 MB) into `~/.embedmongo`;
later runs start in a few seconds.

- `OrderRepositoryTest` — an order with items and address is stored as ONE document; derived queries reach into embedded fields (`findByItemsSku`) and `@Query` takes a raw MongoDB filter.
- `SchemaInCodeTest` — documents written under an old field name load with `null`, silently.
- `UniqueIndexTest` — `@Indexed(unique = true)` alone does not stop duplicates (index creation is off by default).
- `UniqueIndexEnabledTest` — with `spring.data.mongodb.auto-index-creation=true`, the duplicate throws `DuplicateKeyException`.
- `LostUpdateTest` — two read-modify-write `save()` calls lose an item; an atomic `$push` keeps both.

Run:

```
mvn -q test
```

At shutdown you may see `ERROR ... Mongod : Got some response, should be an error message`.
That's flapdoodle stopping the server; the tests have already passed.
