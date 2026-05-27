# testability-demo

The same behavior written twice: once so it *can't* be unit-tested, once so it can. The difference is one idea — constructor injection.

Run the tests:

```
mvn test
```

`TestableRefactoredTest` passes (2 tests). `HardToTestBrokenTest` has one `@Disabled` test that documents why the broken version can't be tested — it shows up as skipped.

Read the files in this order: `MessageGateway`, `SmtpMessageGateway`, `HardToTestBroken`, `TestableRefactored`, then the two test files.
