# disambiguation-demo

Two `EmailNotifier` implementations, three consumers that pick between them differently:

- `NotificationSender` -- takes whichever notifier is `@Primary` (the console one).
- `LoggingNotificationSender` -- uses `@Qualifier("loggingNotifier")` to ignore the primary and grab the named one.
- `BroadcastSender` -- injects `List<EmailNotifier>` and uses every implementation.

`DisambiguationTest` asserts each path. It also includes a nested broken configuration that shows what happens *without* `@Primary` or `@Qualifier`: `NoUniqueBeanDefinitionException` at context startup.

Run:

```bash
mvn -q test
mvn -q compile exec:java
```
