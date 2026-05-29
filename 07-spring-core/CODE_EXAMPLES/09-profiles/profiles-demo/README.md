# profiles-demo

Two `EmailNotifier` implementations, one tagged `@Profile("dev")`, the other `@Profile("prod")`. A consumer `Mailer` injects whichever variant the active profile registered.

Run:

```bash
mvn -q test                                                # programmatic profile activation
mvn -q compile exec:java "-Dspring.profiles.active=dev"   # picks ConsoleEmailNotifier
mvn -q compile exec:java "-Dspring.profiles.active=prod"  # picks SmtpEmailNotifier (mock)
mvn -q compile exec:java                                   # no profile -> startup failure
```
