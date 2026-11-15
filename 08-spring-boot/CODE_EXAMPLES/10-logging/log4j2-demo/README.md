# log4j2-demo

The `logging-demo` app with Logback swapped for Log4j2. `LoggingService` and `App`
are identical copies; only `pom.xml` differs:

1. exclude `spring-boot-starter-logging` from `spring-boot-starter`,
2. add `spring-boot-starter-log4j2`.

## Run

```bash
mvn -q test   # 2 tests pass: SLF4J is bound to Log4j2; INFO shows, DEBUG doesn't
```

## Break it

Delete the `<exclusions>` block and run `mvn test`. Startup still succeeds, but SLF4J
prints `Class path contains multiple SLF4J providers` and picks Logback, so
`slf4jIsNowBackedByLog4j2` fails. In a real app nothing would fail at all — your Log4j2
configuration would just be ignored.
