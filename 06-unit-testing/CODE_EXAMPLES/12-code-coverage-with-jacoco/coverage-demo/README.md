# coverage-demo

Generates a JaCoCo coverage report and deliberately leaves one branch (the SILVER tier) untested, so you can see what an uncovered branch looks like.

Run the tests and build the report:

```
mvn test
```

Then open the generated report in a browser:

```
target/site/jacoco/index.html
```

Drill into `LoyaltyDiscount`. Covered lines are green, the missed SILVER branch is red/yellow. Add a SILVER test (see the topic's "Try this yourself") and re-run to watch it turn green.
