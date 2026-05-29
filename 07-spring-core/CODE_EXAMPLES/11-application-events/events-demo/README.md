# events-demo

One publisher (`OrderService`), one event (`OrderPlacedEvent`), two listeners (`AuditLogger`, `NotificationSender`). Notice that neither listener is referenced from the publisher's code or its constructor.

Run:

```bash
mvn -q test
mvn -q compile exec:java
```

The test publishes one event and asserts both listeners observed it.
