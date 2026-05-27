# exceptions-demo

Testing the unhappy path: asserting that bad input throws the right exception, plus the edge values (0%, 100%) that are valid but easy to get wrong.

Run the tests:

```
mvn test
```

Compare the two ways of asserting an exception: JUnit's `assertThrows` (returns the exception) and AssertJ's `assertThatThrownBy` (fluent type + message in one chain).
