# jdbc-aggregate-demo

Spring Data JDBC on H2: repositories without Hibernate. Every behaviour that differs from JPA is a passing test.
SQL logging is on, so `mvn test` prints every statement that runs.

- `AggregateTest` — saving an `Order` saves its items; a loaded order is complete (no lazy loading); the customer is referenced by id only; saving the order deletes and re-inserts all its items; `@Query` is plain SQL.
- `NoDirtyCheckingTest` — changing a loaded object inside `@Transactional` writes nothing without `save()`.
- `NaturalKeyTest` — `save()` on a new entity with a pre-set id runs an `UPDATE`, matches nothing, and silently saves nothing; `JdbcAggregateTemplate.insert` fixes it.

Run:

```
mvn -q test
```
