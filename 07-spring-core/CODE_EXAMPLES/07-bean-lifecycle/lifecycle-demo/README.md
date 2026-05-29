# lifecycle-demo

`@PostConstruct` and `@PreDestroy` on two beans: one populates a cache at startup, the other simulates an opened/closed resource.

Run:

```bash
mvn -q test                 # asserts cache is warm and resource is open during context life
mvn -q compile exec:java    # prints the lifecycle messages in order
```

Read the markdown alongside `WarmedCache` and `LifecycleResource`. The "opened" / "warmed" lines printing before the application work, and the "closed" / "cleared" lines printing on context shutdown, are the whole lesson.
