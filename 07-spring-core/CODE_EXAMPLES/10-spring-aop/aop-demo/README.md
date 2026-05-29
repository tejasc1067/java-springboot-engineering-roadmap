# aop-demo

One `@Around` aspect that times every call to any `*Service` method. Two services match the pointcut, so both get advised; the test asserts the aspect's recorded event list contains the expected entries.

Run:

```bash
mvn -q test
mvn -q compile exec:java
```

Read `TimingAspect` first (the pointcut expression is the lesson), then `OrderService` / `UserService` (unchanged, unaware of the aspect), then `AppConfig` (which adds `@EnableAspectJAutoProxy`).
