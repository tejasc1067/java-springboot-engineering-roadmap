# async-scheduling-demo

`@Async` and `@Scheduled` on Spring Boot's auto-configured executors. Every behaviour is a passing test.

Async:
- `AsyncTest` — `@Async` runs on Boot's executor (`report-*` threads); a self-call runs on the caller's thread; a call from another bean goes through the proxy; a `void` method's exception never reaches the caller; a `CompletableFuture` carries it back; `@Async @EventListener` runs off the publisher's thread.
- `NoEnableAsyncTest` — without `@EnableAsync`, `@Async` is silently ignored.

Scheduling (each job class is behind its own profile):
- `SchedulerStarvationTest` — with Boot's default single scheduler thread, a slow job stops a 50 ms heartbeat.
- `SchedulerPoolTest` — `spring.task.scheduling.pool.size=2` and the heartbeat keeps running.
- `RateVsDelayTest` — `fixedDelay` waits after each run ends; `fixedRate` doesn't.
- `CronExpressionTest` — what a cron actually fires at; a 5-field Unix cron is rejected.
- `DigestJobTest` — two instances of the app both send the daily digest; claiming the run lets only one send.

Run:

```
mvn -q test
```

The scheduling tests wait for real time (about 5 seconds in total).
