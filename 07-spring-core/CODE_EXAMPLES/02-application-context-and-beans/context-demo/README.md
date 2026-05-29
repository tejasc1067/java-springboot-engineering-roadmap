# context-demo

The same order-service graph from topic 01, now wired by Spring instead of by hand.

The lesson is the diff against `01-why-spring-and-the-ioc-container/wiring-by-hand-demo/`:

- `pom.xml` gains one dependency: `spring-context`.
- A new file `AppConfig.java` replaces the wiring block from `Main`.
- `Main` is now five lines instead of ten — it bootstraps the container and asks for `OrderService`.

Run:

```bash
mvn -q compile exec:java     # boots the context, places one order
mvn -q test                  # asserts the container built what we expected
```

Read in this order: `AppConfig` (the recipe), `Main` (the bootstrap), `AppConfigTest` (the assertions). The domain classes are unchanged from topic 01 and you can skim them.
