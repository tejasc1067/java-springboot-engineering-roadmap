# wiring-by-hand-demo

Five small services plus an `OrderService` that depends on all of them. The lesson is `Main.java`: every collaborator is constructed by hand, in the right order. It works; it just does not scale.

There is no Spring in this project. Topic 02 introduces Spring; this one shows the problem Spring solves.

Run:

```bash
mvn -q compile exec:java     # runs Main, places one order, prints the result
mvn -q test                  # runs the unit test (which re-does the wiring)
```

Read in this order: `InventoryService`, `PriceCalculator`, `EmailNotifier` + `ConsoleEmailNotifier`, `OrderRepository` + `InMemoryOrderRepository`, `Order`, `OrderService`, then `Main`, then `OrderServiceTest`. Notice how `Main` and the test set up the same five objects in the same order.
