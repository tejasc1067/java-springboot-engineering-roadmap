# datajpatest-demo

`@DataJpaTest` — boots only the JPA slice (no web, no service beans), wraps each test in a transaction that rolls back.

Run:

```
mvn test
```

Compare the startup banner to a `@SpringBootTest` from earlier modules — the `@DataJpaTest` slice is noticeably smaller.
