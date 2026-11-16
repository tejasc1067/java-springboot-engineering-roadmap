# Async and Scheduling

Two ways to run code that isn't directly answering the current request. `@Async` runs a method **in the background**, on another thread, so the caller doesn't wait for it: sending an email, generating a report. `@Scheduled` runs a method **on a timer**: every five minutes, or every weekday at 2am. Both are one annotation each, and Spring Boot sets up the thread pools behind them.

Both also fail quietly when misused. Each bug in this topic produces no error. The annotation simply doesn't do what it says, or does it twice. The demo shows each one as a passing test.

---

## The problem this solves

- **Slow side effects slow the request.** Placing an order takes 50 ms. Sending the confirmation email takes 2 seconds because the mail server is slow. If the email is sent inline, every order takes 2 seconds, and a mail-server outage breaks ordering.
- **Some work has to happen on a clock.** Clean up expired sessions every 15 minutes, send a daily digest at 7am, retry failed payments every hour. Nobody sends a request to trigger it.

---

## `@Async`: run a method in the background

### How it works

Two pieces:

```java
@EnableAsync              // once, on a @Configuration class
@Configuration
public class AsyncConfig { }

@Service
public class ReportService {

    @Async
    public CompletableFuture<String> whoRunsMe() {
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }
}
```

When another bean calls `reportService.whoRunsMe()`, it isn't calling your method directly. It calls a **proxy** that Spring put in front of the bean (the proxy pattern, module 02 topic 16; AOP, module 07 topic 10). The proxy hands the real call to a thread pool and returns at once. `AsyncTest.asyncMethod_runsOnBootsExecutor` shows the method running on a thread named `report-1`, not the test's thread.

**What it can return:**

- `void`: fire and forget. The caller gets nothing back, not even errors (see below).
- `CompletableFuture<T>`: the caller gets a future, and can `join()` it later, chain it (module 03c, topic 05), or ignore it. Inside the method, wrap the result with `CompletableFuture.completedFuture(value)`.

### The executor Spring Boot gives you

Spring Boot auto-configures a `ThreadPoolTaskExecutor` for `@Async`, and you tune it in `application.yml`:

```yaml
spring:
  task:
    execution:
      thread-name-prefix: report-    # default "task-"; name threads so logs and thread dumps make sense
      pool:
        core-size: 4                 # default 8
        max-size: 4
        queue-capacity: 100          # default: unbounded
```

Bound the queue. With the default unbounded queue, a burst of 100,000 tasks doesn't fail. It waits in memory until the app runs out. With a bounded queue, the executor grows to `max-size`, and after that the submission is rejected with an exception you can see and handle. (Spring Boot can also run `@Async` on virtual threads with `spring.threads.virtual.enabled=true` (Java 21's lightweight threads). Bound the work some other way then, because virtual threads remove the pool limit.)

### `@Async` + events

Module 07 topic 11 showed that `@EventListener` methods run on the publisher's thread, so a slow listener slows the publisher. Add `@Async` and the listener moves to the executor:

```java
@Async
@EventListener
public void onOrderPlaced(OrderPlaced event) { /* send the confirmation email */ }
```

`asyncEventListener_runsOffThePublishersThread` publishes `OrderPlaced` and the listener runs on a `report-` thread. A listener that throws no longer fails `placeOrder`, but its exception now goes where `void` async exceptions go, which is the next problem.

---

## Broken: four ways `@Async` silently does nothing (or loses things)

### 1. Forgetting `@EnableAsync`

`NoEnableAsyncTest` builds the same `ReportService` in a context without `@EnableAsync`. The method runs on the **caller's** thread. There's no error and no warning, just synchronous code with a misleading annotation.

**Fix:** `@EnableAsync` on a configuration class. Test for it: assert the thread name, as the demo does.

### 2. Calling the method on `this`

```java
public String whoRunsMeViaSelfCall() {
    return whoRunsMe().join();      // this.whoRunsMe() - the real object, not the proxy
}
```

`selfInvocation_skipsTheProxy_andRunsOnTheCallersThread`: the "async" method runs on the test thread. This is the same self-invocation bug as `@Transactional` (module 02 topic 16, module 07 topic 10) and `@Cacheable` (module 15 topic 02). Only calls that come *through the proxy* are intercepted.

**Fix:** call it from another bean. `ReportCaller` injects `ReportService` and calls `reports.whoRunsMe()`, which goes through the proxy and onto a `report-` thread (`callingFromAnotherBean_goesThroughTheProxy`). The same applies to `private` methods: the proxy can't intercept them, so `@Async` on a private method never works. Also note that the `report-` thread doesn't inherit the caller's MDC or trace: log lines from it have no request id unless the executor has a `TaskDecorator`, such as `ContextPropagatingTaskDecorator` ([module 17 topic 06](../17-observability/06-opentelemetry-tracing.md)).

### 3. Exceptions from `void` methods vanish

```java
@Async
public void failInTheBackground() {
    throw new IllegalStateException("report generation failed");
}
```

The caller returned before the method even started, so there is nobody to throw to. The exception goes to an `AsyncUncaughtExceptionHandler`, which by default only writes a log line. `exceptionFromVoidAsync_neverReachesTheCaller` shows the caller returning normally while the exception turns up only in the handler.

**Fixes:**

- Return `CompletableFuture<Void>` instead of `void`. The exception is stored in the future, and whoever calls `join()` gets it (`exceptionFromFutureAsync_reachesTheCallerThroughTheFuture`):

  ```java
  reports.failVisibly().join();     // throws CompletionException caused by IllegalStateException
  ```

- For true fire-and-forget methods, register your own handler that logs with context and counts the failure, so it shows up on a dashboard (module 17):

  ```java
  @EnableAsync
  @Configuration
  public class AsyncConfig implements AsyncConfigurer {
      @Override
      public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
          return (ex, method, params) -> log.error("async {} failed", method.getName(), ex);
      }
  }
  ```

### 4. Reading a field through the proxy

Writing this demo hit a fourth trap. `ConfirmationEmailListener` first had a `public final` field, and the test read it from the injected bean. It was `null`. Because of `@Async`, the injected object is the proxy, a generated subclass whose own copy of the field was never initialised. Method calls are forwarded to the real object, but field reads aren't. **Fix:** keep fields private and expose them through methods, which beans should do anyway.

---

## `@Scheduled`: run a method on a timer

### How it works

```java
@EnableScheduling         // once, on a @Configuration class
@Configuration
public class SchedulingConfig { }

@Component
public class CleanupJobs {

    @Scheduled(fixedRate = 60_000)                  // every 60 s, start to start
    public void refreshCache() { ... }

    @Scheduled(fixedDelay = 60_000)                 // 60 s after the previous run ENDS
    public void retryFailedPayments() { ... }

    @Scheduled(cron = "0 0 2 * * MON-FRI")          // 02:00 on weekdays
    public void nightlyReport() { ... }
}
```

The method must return `void` and take no arguments. `initialDelay` delays the first run, and every value is in milliseconds unless you set `timeUnit`.

### `fixedRate` vs `fixedDelay`

`RateVsDelayTest` runs two jobs that each take 100 ms, both set to 200:

- **`fixedRate = 200`** measures **start to start**: a run every 200 ms, however long each takes. If a run takes *longer* than the rate, the next one starts as soon as it finishes. Runs of the same method never overlap, so they queue up behind each other.
- **`fixedDelay = 200`** measures **end to start**: the job finishes, waits 200 ms, then starts again. The test confirms every gap is at least 300 ms (100 ms of work plus 200 ms of delay).

Use `fixedDelay` for jobs whose duration varies (polling, retrying, batch processing), so they never pile up. Use `fixedRate` when the *cadence* matters and runs are short.

### Cron expressions

Spring cron has **six** fields: `second minute hour day-of-month month day-of-week`. Unix crontab has five, without seconds. Copy one from a crontab and it fails. `CronExpressionTest.unixStyleFiveFieldCron_isRejected` shows `"0 2 * * *"` being refused:

| Spring cron | Fires |
|---|---|
| `0 0 2 * * *` | 02:00:00 every day |
| `0 0 2 * * MON-FRI` | 02:00 on weekdays |
| `0 */15 * * * *` | every 15 minutes, on the quarter hour |
| `0 0 7 1 * *` | 07:00 on the 1st of every month |

Don't guess, test. `CronExpression.parse(...).next(dateTime)` tells you exactly when a schedule fires next. The demo checks that a weekday cron evaluated on a Saturday lands on Monday 02:00.

Crons run in the **server's time zone** unless you pass `zone = "Europe/London"`. A server in UTC runs your "2am" job at 2am UTC, and daylight-saving changes move it relative to local time.

---

## Broken: one slow job freezes all the others

**Spring Boot gives `@Scheduled` a single thread by default.** Every scheduled method in the application shares it. `SchedulerStarvationTest` has a heartbeat every 50 ms and a report job that takes 1 second:

```text
heartbeat runs in 800 ms: expected ~16, actual ≤ 2
```

While the report runs, the heartbeat can't, even though they're unrelated. In production, a stuck job (waiting on a slow API with no timeout, module 13 topic 09) silently stops every other scheduled job in the app.

**Fix:** give the scheduler more threads:

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 4
```

`SchedulerPoolTest` sets it to 2 and the heartbeat keeps running. Also, put a timeout on anything a job waits for, and for long-running work have the scheduled method hand it to the `@Async` executor instead of holding the scheduler thread.

---

## Broken: every instance runs the job

`@Scheduled` runs inside **each running copy** of your app. Run 3 instances behind a load balancer (module 18) and your 7am "send the daily digest" job sends 3 digests to every customer.

`DigestJobTest.scheduledJob_runsOnEveryInstance` starts the same app twice in one JVM, as two instances. Both send:

```text
digests sent: 2
```

**Fix:** only one instance may run each scheduled run. The usual way is a **lock in a shared database**. Before doing the work, the job inserts a row such as `daily-digest:2026-11-16` into a table with a unique key. Exactly one instance's insert succeeds, and the others skip. `ClaimedDigestJob` does this with an in-memory stand-in (`JobClaims`), and `claimingTheRun_letsOnlyOneInstanceSend` shows 1 digest sent. In real projects use **ShedLock**: annotate the method with `@SchedulerLock(name = "dailyDigest")`, and it keeps the lock row in your existing database for you. A lock you build yourself in Redis is harder to get right than it looks; module 15 topic 09 shows why.

Other options: run scheduled jobs in a separate single-instance worker application, or move the schedule outside the app (Kubernetes, introduced in module 14, has a CronJob resource for this). Whichever you choose, make the job **idempotent** (module 13 topic 19), so that if it does run twice after a crash or a retry, nothing is sent twice.

---

## When to use them / when not to

| | Use it for | Don't use it for |
|---|---|---|
| `@Async` | Work the caller doesn't need to wait for: notifications, audit, cache warming, independent calls you want to run in parallel | Anything that must not be lost. If the app restarts, tasks still in the queue are gone. Use a message broker for that (module 16). |
| `@Scheduled` | Periodic housekeeping inside one service: cleanup, refresh, polling | Jobs that need history, retries, or monitoring (use a job framework or broker), or that must run exactly once across many instances without a lock |

`@Async` is **in memory**. It's not a queue you can rely on. If the process stops, pending tasks disappear. When "the email must eventually be sent" matters, write the work to a database or a broker first (the outbox, module 13 topic 19).

---

## Common pitfalls

- **No `@EnableAsync` / `@EnableScheduling`.** The annotations are silently ignored. Add the enable annotation, and assert thread names in a test.
- **Self-invocation.** `this.asyncMethod()` runs synchronously. Call it from another bean.
- **`void @Async` methods that throw.** The exception only reaches a log line. Return `CompletableFuture`, or register an `AsyncUncaughtExceptionHandler` that alerts.
- **The default single scheduler thread.** One slow job blocks all of them. Set `spring.task.scheduling.pool.size`.
- **Scheduled jobs on several instances.** Every instance runs them. Use ShedLock (a database lock) and make the job idempotent.
- **A five-field Unix cron.** Spring needs six fields, starting with seconds. Check with `CronExpression.next()`.

---

## Code examples

`CODE_EXAMPLES/13-async-and-scheduling/async-scheduling-demo/`. Run with `mvn -q test` (the scheduling tests wait about 5 seconds of real time). Read in this order:

1. `src/main/resources/application.yml` — the executor and scheduler settings, with Boot's defaults noted.
2. `src/main/java/com/example/async/AsyncConfig.java` — `@EnableAsync` and a recording `AsyncUncaughtExceptionHandler`.
3. `src/main/java/com/example/async/ReportService.java` + `ReportCaller.java` — `@Async` methods, the self-call, and the call from another bean.
4. `src/test/java/com/example/async/AsyncTest.java` — executor threads, self-invocation, lost vs carried exceptions, `@Async @EventListener`.
5. `src/test/java/com/example/async/NoEnableAsyncTest.java` — `@Async` ignored without `@EnableAsync`.
6. `src/main/java/com/example/async/StarvationJobs.java` + `SchedulerStarvationTest.java` → `SchedulerPoolTest.java` — one slow job blocks the heartbeat, then a second thread fixes it.
7. `src/main/java/com/example/async/RateVsDelayJobs.java` + `RateVsDelayTest.java` — start-to-start vs end-to-start.
8. `src/test/java/com/example/async/CronExpressionTest.java` — what a cron fires at, and a rejected 5-field cron.
9. `src/main/java/com/example/async/UnguardedDigestJob.java` → `ClaimedDigestJob.java` + `JobClaims.java` + `DigestJobTest.java` — two instances double-send, then the claimed run sends once.

---

## Try this yourself

1. In `application.yml`, set `queue-capacity: 2` and `max-size: 2`. Write a test that submits 10 slow `@Async` tasks at once. What exception do you get, and on which call?
2. Change `RateVsDelayJobs.everyTwoHundredFromStart` to sleep 300 ms (longer than its rate). Record the start gaps. Do runs overlap, or queue up?
3. Write a cron for "every Monday at 09:30 in the Asia/Kolkata time zone" and a `CronExpressionTest` that checks its next firing time from a Sunday evening.

## Self-check

1. A teammate adds `@Async` to `sendWelcomeEmail()` and calls it from `register()` in the same class. Registration is still slow. Explain why, and give the fix.
2. A `void @Async` method throws. What does the caller see? Where does the exception go, and what are two ways to make sure someone notices?
3. Your nightly report job got stuck on a slow API at 02:00, and the 15-minute cache refresh hasn't run since. Why are the two connected, and what two changes fix it?
4. The app runs on 3 instances, and customers get the daily digest three times. Why, and how do you make exactly one instance send it?
