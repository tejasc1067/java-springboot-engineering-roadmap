# Liveness and Readiness

Kubernetes asks every pod two questions over and over: "are you alive, or should I restart you?" (liveness) and "should I send you traffic right now?" (readiness). Module 14 wired the probes into a Deployment. This topic is about the answers: what Spring Boot puts behind `/actuator/health/liveness` and `/actuator/health/readiness`, how the app changes them itself, and the configuration mistakes that turn a probe into the cause of an outage instead of protection against one.

The demo calls the real Spring Boot endpoints from a small model of the kubelet (the Kubernetes agent that runs the probes), so you can watch restart counts change, second by second, without a cluster.

---

## The problem this solves

A probe is an automated decision with real consequences. A failing **readiness** probe takes the pod out of the Service: no traffic, nothing else happens. A failing **liveness** probe kills the container and starts a new one, dropping every request it was serving and throwing away its warm caches.

So each probe must answer exactly its own question. If liveness reports "the database is unreachable", Kubernetes hears "this JVM is broken, restart it", which can't fix a database. If readiness reports "UP" while the app is shutting down, the load balancer keeps sending requests to a server that is closing. And if the probe settings are too strict, a healthy app gets killed during a garbage-collection pause or a slow start.

## How it works

### Spring Boot's availability model

Spring Boot keeps two pieces of state for the whole application, independent of any health check:

| State | Values | Meaning |
|---|---|---|
| `LivenessState` | `CORRECT`, `BROKEN` | Is the app's internal state OK? `BROKEN` means "only a restart can fix me". |
| `ReadinessState` | `ACCEPTING_TRAFFIC`, `REFUSING_TRAFFIC` | Should this instance get requests now? |

You read them through `ApplicationAvailability` and change them by publishing an event:

```java
AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);   // stop sending me requests
AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);              // restart me
```

Actuator exposes them as two **health groups**. Each group is a list of health indicators; the group's status is the combination of its members' statuses:

| Endpoint | Default members | Fails as |
|---|---|---|
| `/actuator/health/liveness` | `livenessState` | `503 DOWN` when `BROKEN` |
| `/actuator/health/readiness` | `readinessState` | `503 OUT_OF_SERVICE` when `REFUSING_TRAFFIC` |

On Kubernetes they're enabled automatically. Anywhere else, set `management.endpoint.health.probes.enabled: true` (module 14 topic 09 shows the 404 you get without it).

The demo's configuration adds the database to readiness, and only to readiness:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState, database   # keep readinessState in the list (pitfall below)
        liveness:
          include: livenessState
```

`ProbeEndpointsTest` shows each reaction:

- `databaseDown_readinessFails_livenessStillPasses`: readiness `503 DOWN`, liveness `200`. Nothing to restart.
- `refusingTraffic_takesTheInstanceOutOfRotation_withoutTouchingLiveness`: readiness `503 OUT_OF_SERVICE`. Use this while reloading a large cache, or while a critical dependency's circuit breaker is open.
- `livenessBroken_isHowTheAppAsksToBeRestarted`: liveness `503 DOWN`. Use it only for states the app can't recover from on its own.

### Spring Boot changes the state itself, at startup and shutdown

`StartupAndShutdownTest`:

- **Startup.** The web server is already listening while `ApplicationRunner`s and `CommandLineRunner`s run, but Spring Boot publishes `ACCEPTING_TRAFFIC` only after they finish. `CatalogWarmUp`, a runner, sees liveness `CORRECT` and readiness `REFUSING_TRAFFIC`. So warm-up work in a runner automatically keeps the pod out of the Service until it's done (`duringWarmUp_theAppIsLiveButNotReady_andBecomesReadyOnlyAfterTheRunnersFinish`).
- **Shutdown.** When the context closes (which is what SIGTERM triggers), Spring Boot publishes `REFUSING_TRAFFIC` first, then stops the web server, which drains in-flight requests gracefully (module 14 topic 09). `onShutdown_readinessFlipsToRefusingTraffic` records `[ACCEPTING_TRAFFIC, REFUSING_TRAFFIC]`.

Kubernetes stops routing to a terminating pod on its own, but not instantly, and other load balancers rely only on readiness. The readiness flip tells every caller that polls it to stop sending traffic.

### The kubelet model

`Kubelet` (in the test sources) follows the same rules as Kubernetes, one simulated second at a time:

- a probe runs at `initialDelaySeconds`, then every `periodSeconds`;
- it **fails** if the endpoint answers unhealthy **or takes longer than `timeoutSeconds`**;
- `failureThreshold` failures in a row: liveness restarts the container (its clock starts again), readiness takes it out of the Service;
- while a startup probe hasn't passed yet, liveness and readiness don't run.

The one thing it leaves out is CrashLoopBackOff, the growing wait Kubernetes adds between repeated restarts, which makes real recovery slower than shown here.

## Broken: the database in liveness

```yaml
management.endpoint.health.group.liveness.include: livenessState, database
```

`DatabaseOutage` gives one pod ten simulated minutes with a three-minute database outage (01:00 to 04:00). After each restart the app needs 20 seconds before it answers. The probes hit the real Spring Boot endpoints.

`DatabaseInLivenessTest.aDatabaseOutage_restartsThePodAgainAndAgain`:

```text
restarts at: 80 s, 121 s, 162 s, 203 s
```

Three liveness failures in a row (01:00, 01:10, 01:20) kill the container. The new one starts during the outage, fails liveness again, and is killed again. Four restarts, each dropping in-flight requests, and every pod shares that database, so every pod does this at once. On a real cluster each restart also reconnects to a database that is struggling already, and CrashLoopBackOff stretches the delays.

### Fixed: the database only in readiness

`DatabaseInReadinessTest.aDatabaseOutage_takesThePodOutOfTheService_withoutRestartingIt`: zero restarts. The pod leaves the Service at 01:20 and is back at 04:00, the second the database is.

Be honest about what readiness buys here: every pod shares the database, so every pod goes unready together, and the Service has nobody to send traffic to. Callers get errors either way. What you gain is no restart storm, no lost in-flight work, and instant recovery. Some teams therefore keep shared dependencies out of *both* probes, and let requests fail fast with a clear `503` instead. Dependencies that only *this* instance can lose (its own connection pool, a local disk) are the strongest case for readiness.

## Broken: changing a group's settings without its member list

Two configuration traps produce no error and no warning.

**Listing only the dependency in readiness** (`ReadinessGroupOverrideTest`):

```yaml
management.endpoint.health.group.readiness.include: database
```

`include` replaces the group's members instead of adding to them, so `readinessState` is gone. The app publishes `REFUSING_TRAFFIC` (as it does at every shutdown), and `/actuator/health/readiness` still answers `200 UP`. Always write `readinessState` (and `livenessState` for liveness) into the list.

**Touching the liveness group without `include`** (`LivenessGroupOverrideTest`):

```yaml
management.endpoint.health.group.liveness.show-components: always   # only meant to show more detail
```

Configuring a group at all replaces Spring Boot's built-in probe group, and a group with no `include` contains **every** health indicator. Liveness now includes the database, and `touchingTheLivenessGroupWithoutInclude_putsTheDatabaseIntoLiveness` shows it going `DOWN` when the database does. That's the restart storm above, caused by a change to how much detail the endpoint shows.

## Broken: probe settings that kill a healthy app

`ProbeTimingTest` is plain Java: only the kubelet's rules matter.

**A garbage-collection pause with `failureThreshold: 1`.** A 3-second stop-the-world pause at 01:40 makes one probe time out (`timeoutSeconds: 1`). With a threshold of 1 the container is restarted at 01:40; with 3 the single slow answer is tolerated (`oneGcPause_withFailureThresholdOne_restartsTheContainer`).

**A liveness endpoint that slows down under load.** From 01:00 every answer is `UP` but takes 1.5 s, because the probe request waits for a busy thread pool. Every answer counts as a failure, and the pod is restarted 11 times in four minutes, at exactly the moment the service is busiest, pushing its load onto the other pods (`aLivenessEndpointThatSlowsUnderLoad_restartsThePodExactlyWhenItIsBusiest`). Keep liveness cheap and give the timeout room. Running Actuator on its own port (`management.server.port`) gives probes their own connector and threads, separate from application traffic.

**A slow start with no startup probe.** The app needs 90 s before it answers at all (migrations, a big cache). Liveness starts after 10 s, fails three times, and restarts it at 31 s, then again 31 s later, forever: 19 restarts in ten minutes and not one second ready (`aSlowStartingApp_withoutAStartupProbe_isKilledBeforeItEverStarts`).

### Fixed: a startup probe

```yaml
startupProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  periodSeconds: 10
  failureThreshold: 30        # up to 300 s to start
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  periodSeconds: 10
  failureThreshold: 3
```

Until the startup probe passes, Kubernetes doesn't run liveness or readiness. Once it passes, liveness takes over with its normal, strict settings. `aStartupProbe_givesTheAppTimeToStart_thenLivenessTakesOver`: zero restarts, ready shortly after 90 s. A large `initialDelaySeconds` on liveness also avoids the kill, but it delays every restart, including ones where the app would have started quickly.

## When to use it / when not to

| Put it in | When |
|---|---|
| **liveness** | Only the app's own state: `livenessState`, which you set to `BROKEN` when a component detects it's wedged (a deadlocked worker, a corrupted in-memory state). |
| **readiness** | `readinessState`, plus things this instance needs before it can serve: warm-up, its own connection pool. Shared dependencies are a judgement call (see above). |
| **startup probe** | Any app that can take longer to start than `failureThreshold × periodSeconds` of liveness. For Spring Boot apps, that's most of them on a busy node. |
| **neither** | Downstream services you call over HTTP. If inventory-service is down, restarting or unreadying order-service fixes nothing. Handle it with timeouts and circuit breakers (module 13 topics 09–11). |

## Common pitfalls

- **Dependencies in liveness.** A database blip becomes a restart storm (`DatabaseInLivenessTest`). Liveness is `livenessState` only.
- **`include` without the state indicator.** `readiness.include: db` drops `readinessState`, so shutdown and warm-up are invisible to the probe (`ReadinessGroupOverrideTest`). List `readinessState` explicitly.
- **Configuring a probe group without `include`.** The group silently becomes "all indicators" (`LivenessGroupOverrideTest`). Write `include` whenever you touch a group.
- **`failureThreshold: 1` or a tight `timeoutSeconds` on liveness.** One GC pause or one busy minute restarts a healthy pod (`ProbeTimingTest`). Use 3 or more, and a timeout with headroom.
- **No startup probe for a slow-starting app.** It's killed before it ever starts. Add a startup probe rather than a huge `initialDelaySeconds`.

---

## Code examples

`CODE_EXAMPLES/08-liveness-and-readiness/probes-demo/`. Run with `mvn -q test` (no Docker). Read in this order:

1. `src/main/resources/application.yml` — the probe groups, with the member lists written out.
2. `src/main/java/com/example/probes/DatabaseHealthIndicator.java` — a database check the tests can switch off.
3. `src/test/java/com/example/probes/ProbeEndpointsTest.java` — what each endpoint contains and how it reacts to the database, `REFUSING_TRAFFIC`, and `BROKEN`.
4. `src/main/java/com/example/probes/CatalogWarmUp.java` and `src/test/java/com/example/probes/StartupAndShutdownTest.java` — readiness during warm-up and at shutdown.
5. `src/test/java/com/example/probes/Kubelet.java` — the probe rules Kubernetes follows.
6. `src/main/resources/application-broken-liveness.yml`, `src/test/java/com/example/probes/DatabaseOutage.java`, `DatabaseInLivenessTest.java` — the restart storm.
7. `src/test/java/com/example/probes/DatabaseInReadinessTest.java` — the same outage, no restarts.
8. `src/main/resources/application-readiness-without-state.yml`, `ReadinessGroupOverrideTest.java`, `LivenessGroupOverrideTest.java` — the two group-configuration traps.
9. `src/test/java/com/example/probes/ProbeTimingTest.java` — GC pauses, slow endpoints, slow starts, and the startup probe.

---

## Try this yourself

1. In `DatabaseOutage`, change `APP_STARTUP_SECONDS` to 60. Run `DatabaseInLivenessTest`: how many restarts now, and when is the pod first ready again after the database comes back? What does that tell you about restarts for apps that start slowly?
2. Add a `ReadinessState.REFUSING_TRAFFIC` publish at the start of `CatalogWarmUp.run()` and an `ACCEPTING_TRAFFIC` publish at the end. Does `StartupAndShutdownTest` still pass? Why is the second publish a bad idea in a runner? (Hint: who publishes `ACCEPTING_TRAFFIC` after the runners anyway, and what if your runner throws?)
3. In `ProbeTimingTest`, find the smallest `failureThreshold × periodSeconds` for liveness that survives the 90-second start without a startup probe. Then make the app take 200 s to start and compare that setting with a startup probe.

## Self-check

1. The database goes down for three minutes. Walk through what happens to a pod when the database is in its liveness group, and when it's only in readiness. Which one loses in-flight requests?
2. Your teammate sets `management.endpoint.health.group.readiness.include: db, redis`. What can the readiness probe no longer see, and when does that matter?
3. A Spring Boot app takes 2 minutes to start on a busy node and keeps getting restarted before it's ready. Which probe settings would you change, and why is a startup probe better than a long `initialDelaySeconds`?
