# lifecycle-demo

Shows when `@BeforeAll`, `@BeforeEach`, `@AfterEach`, and `@AfterAll` run, and proves that each test gets a fresh instance of the test class.

Run the tests:

```
mvn test
```

Watch the printed `[BeforeAll]/[AfterEach]/...` lines to see the order. The assertions on `beforeEachRuns` and `cart.size()` are what prove test isolation.
