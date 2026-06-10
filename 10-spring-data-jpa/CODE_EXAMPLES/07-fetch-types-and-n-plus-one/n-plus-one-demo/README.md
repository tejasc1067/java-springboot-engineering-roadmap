# n-plus-one-demo

Demonstrates the N+1 query problem and two fixes (`JOIN FETCH` and `@EntityGraph`). Uses Hibernate's `Statistics` API to count queries.

Run:

```
mvn test
```

The first test asserts that the naive query triggers 1 + N SELECTs. The other two assert that the fixes collapse them to 1.
