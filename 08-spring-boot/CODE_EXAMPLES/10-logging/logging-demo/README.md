# logging-demo

Default Logback levels, raised via `logging.level.com.example=DEBUG`, asserted by capturing console output in tests.

## Run

```bash
mvn -q test                                                                     # both tests pass
mvn -q spring-boot:run                                                          # default levels (INFO + WARN appear)
mvn -q spring-boot:run -Dspring-boot.run.arguments=--logging.level.com.example=DEBUG  # DEBUG also appears
mvn -q spring-boot:run -Dspring-boot.run.arguments=--logging.level.com.example=TRACE  # everything
```

## What to notice

- The tests use Boot's `OutputCaptureExtension` to assert what was (and was not) printed at each level.
- `LoggingService` uses standard SLF4J — there is no Boot-specific logger API. The "Boot bit" is just the default Logback config and the property-to-level wiring.
- The logger is `static final`. One per class.
