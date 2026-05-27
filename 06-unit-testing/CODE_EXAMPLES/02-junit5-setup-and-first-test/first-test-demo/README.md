# first-test-demo

The smallest possible JUnit 5 project: one production class, one test class, one test dependency.

Run the tests:

```
mvn test
```

Expect `Tests run: 2, Failures: 0`. Then break `PriceCalculator.lineTotal` (change `*` to `+`) and run again to see a failure message.
