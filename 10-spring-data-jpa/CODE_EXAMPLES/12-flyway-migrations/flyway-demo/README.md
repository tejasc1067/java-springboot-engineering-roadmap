# flyway-demo

Three SQL migrations (V1 creates the table, V2 adds a column + index, V3 seeds two rows). JPA runs in `ddl-auto: validate` mode — it does not modify the schema.

Run:

```
mvn test
```

Look for `Flyway Community Edition ... by Redgate` in the startup log, followed by lines for each migration applied.
