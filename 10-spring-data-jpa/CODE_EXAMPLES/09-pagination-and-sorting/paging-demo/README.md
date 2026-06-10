# paging-demo

`Pageable`, `Page<T>`, `Slice<T>`, `Sort` — the Spring Data way to paginate.

Run:

```
mvn test
```

Watch the console for the extra `count(b1_0.id)` query Hibernate issues for `Page<T>` but not for `Slice<T>`.
