# testcontainers-demo

A native query that passes every H2 test and fails on the real database, caught by running the same
repository test against PostgreSQL 17 in a container.

- `H2ProductRepositoryTest` — `ifnull(stock, 0)` works on H2. Green.
- `H2PostgresModeTest` — H2 in `MODE=PostgreSQL`. Still green: it's still H2.
- `PostgresProductRepositoryTest` — Testcontainers starts `postgres:17`, `@ServiceConnection` points Boot at it:
  - `sameBrokenQuery_failsOnRealPostgres` — `ERROR: function ifnull(integer, integer) does not exist`
  - `portableQuery_passesOnRealPostgres` — `coalesce(stock, 0)` works on both.

Run (Docker running):

```
mvn -q test
```

First run pulls `postgres:17` (about 700 MB on disk) and `testcontainers/ryuk` (the helper that removes test
containers when the JVM exits). After that the whole suite takes about 5 seconds. The test prints one
`ERROR: function ifnull ... does not exist` log line - that's the failure the test expects.
