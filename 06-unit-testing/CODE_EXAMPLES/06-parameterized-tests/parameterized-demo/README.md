# parameterized-demo

One test method, many inputs. Shows `@ValueSource`, `@CsvSource`, `@MethodSource`, and `@EnumSource`.

Run the tests:

```
mvn test
```

Notice the test *count* in the Surefire output is much higher than the number of methods — each input is reported as its own test, so a failure tells you exactly which input broke.
