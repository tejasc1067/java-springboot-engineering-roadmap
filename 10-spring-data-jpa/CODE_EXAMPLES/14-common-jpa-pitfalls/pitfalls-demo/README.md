# pitfalls-demo

Three of the most common JPA bugs, each in a passing test that demonstrates the failure mode:

1. `LazyInitializationException` — accessing a lazy collection after the transaction ended.
2. Modifying a detached entity has no effect — no implicit save.
3. The fix: call `repository.save(detached)` which performs a merge.

Run:

```
mvn test
```
