# Healthchecks and Graceful Shutdown

Two moments in a container's life go wrong quietly: **starting** and **stopping**. While a Spring Boot app starts, its container is "running" for several seconds before the app can answer a request. When it stops, at every deploy, requests that are half-finished can be cut off. A **healthcheck** tells Docker (and later Kubernetes, topic 12) when the app is really ready. **Graceful shutdown** lets in-flight requests finish before the app exits.

Both depend on details beginners don't see: what `docker stop` actually sends, which process receives it, and one line of a Dockerfile that silently breaks it.

---

## The problem this solves

- **Starting:** something sends traffic to your app the moment its container is "running": a load balancer, Compose's `depends_on` (topic 08), a test script. The JVM is still starting and Spring is still connecting to the database, so the first requests fail.
- **Stopping:** you deploy a new version several times a day. Each deploy stops the old container. A customer who clicked "Pay" one second earlier is in the middle of a request. If the app dies mid-request, the customer sees an error, and you don't know whether the payment went through.

---

## How it works

### Healthchecks: "running" is not "ready"

A container's **state** (`running`, `exited`) only says the process exists. A Docker `HEALTHCHECK` adds a **health status** by running a command inside the container every few seconds:

```dockerfile
HEALTHCHECK --interval=2s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1
```

- The command asks Spring Boot Actuator's `/actuator/health` (module 08 topic 12). `curl -f` fails on any non-2xx status, so a `DOWN` app (which Actuator reports as 503) fails the check.
- `--start-period=30s`: failures during the first 30 s don't count, giving the app time to boot.
- `--retries=3`: three failures in a row turn the status `unhealthy`.
- `curl` has to exist in the image. `eclipse-temurin:21-jre` includes it. Minimal images (distroless, topic 10) don't, which is one reason Kubernetes probes (topic 12), which call HTTP from outside the container, are the usual answer in production.

Demo step 1 reads the status right after `docker run`, then until it changes:

```text
PASS: health went 'starting' -> 'healthy' while the container was 'running' the whole time
```

Check it yourself with `docker ps` (the STATUS column shows `(healthy)`) or `docker inspect -f '{{.State.Health.Status}}' <container>`. Compose's `depends_on: condition: service_healthy` and `docker compose up --wait` (topic 08) both use this status.

**Liveness and readiness.** Kubernetes asks two different questions: "is the process alive, or should it be restarted?" (liveness) and "should it get traffic right now?" (readiness). Actuator answers them at `/actuator/health/liveness` and `/actuator/health/readiness`. They're enabled automatically on Kubernetes. Elsewhere you turn them on with `management.endpoint.health.probes.enabled: true`, as the demo does:

```text
PASS: /actuator/health/readiness answers 200 (Kubernetes probes, topic 12)
```

Without that setting, outside Kubernetes, those two paths return 404. Topic 12 wires them into probes.

### What `docker stop` does

`docker stop` sends the main process the signal **SIGTERM**, meaning "please shut down". It then waits, by default 10 seconds, or `-t <seconds>`. If the process is still alive after that, it sends **SIGKILL**, which can't be caught: the process dies instantly, mid-request.

On the Docker Desktop 29.9.3 used to build this demo, `docker stop` without `-t` waited only about 3 seconds before killing. With `-t 10` it waited the full 10. Pass `-t` (or Compose's `stop_grace_period`) explicitly rather than relying on the default. The demo always uses `docker stop -t 10`.

The **exit code** tells you how the process ended: **143** = it exited in response to SIGTERM (128 + 15), and **137** = it was SIGKILLed (128 + 9). A 137 on every deploy means your app never shut down cleanly.

### Graceful shutdown in Spring Boot

When the JVM receives SIGTERM, Spring Boot begins closing the application context. With **graceful shutdown**, the embedded web server first stops accepting **new** requests, then waits for requests already in progress to finish, up to a time limit, before shutting down:

```yaml
server:
  shutdown: graceful                     # the default since Spring Boot 3.4
spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s      # how long to wait for in-flight requests (default 30s)
```

Graceful is the default in Spring Boot 3.4 and later. The demo confirmed it on 3.5.14 by running the image with its `application.yml` ignored, and the app still logged `Commencing graceful shutdown`. The demo's yml writes it out so you can see it.

Demo step 2 starts a request to `GET /slow` (which takes 3 s), stops the container 1 s in, and watches:

```text
   stop took 2.2s, exit code 143, request: finished HTTP 200 (curl exit 0)
PASS: in-flight request finished (200), then the app shut down on SIGTERM (exit 143) after 2.2s
```

The request completed, and the app exited about 2 s later, as soon as it had nothing left to do. The log shows:

```text
o.s.b.w.e.tomcat.GracefulShutdown : Commencing graceful shutdown. Waiting for active requests to complete
o.s.b.w.e.tomcat.GracefulShutdown : Graceful shutdown complete
```

Keep `timeout-per-shutdown-phase` **shorter** than the time the platform waits before SIGKILL (`docker stop -t`, Compose's `stop_grace_period`, Kubernetes' `terminationGracePeriodSeconds`, 30 s by default). Otherwise the platform kills the app while Spring is still politely waiting.

---

## Broken 1: `server.shutdown: immediate`

Some older projects, and some copied configs, set `server.shutdown: immediate`. Demo step 3 runs the same image with `SERVER_SHUTDOWN=immediate`:

```text
   stop took 0.2s, exit code 143, request:  HTTP 000 (curl exit 52)
PASS: in-flight request was dropped - the caller got no response
```

The app shut down in 0.2 s, and the request in progress got **no response at all** (`curl` exit 52, "empty reply"). Multiply that by every request in flight at every deploy.

**Fix:** remove the setting (graceful is the default) or set `graceful` explicitly.

## Broken 2: shell-form `ENTRYPOINT` (Java never hears SIGTERM)

These two lines look like the same thing:

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]     # exec form
ENTRYPOINT java -jar app.jar               # shell form
```

They aren't. Docker runs the shell form as `/bin/sh -c "java -jar app.jar"`, so the **shell** becomes the container's main process, PID 1, and Java runs as its child. `docker stop` sends SIGTERM to PID 1. The shell doesn't pass it on, and the kernel ignores signals sent to a PID 1 that hasn't set up a handler for them. Java keeps running, unaware, until the SIGKILL. Demo step 4 (`Dockerfile.shell-form`):

```text
PASS: PID 1 is 'sh', not java
   stop took 10.2s, exit code 137, request: finished HTTP 200 (curl exit 0)
PASS: SIGTERM never reached java: Docker waited the full timeout (10.2s) and SIGKILLed it (exit 137)
```

Look closely. The request still finished, because 3 s is shorter than the 10 s timeout and Java never knew it was being stopped. But:

- Every stop takes the **full timeout**, so deploys and `docker compose down` are slow.
- The app is **SIGKILLed**, so no graceful shutdown happens. Any request still running at that moment is cut off, connections aren't closed cleanly, and Spring's shutdown hooks don't run.
- No log line says any of this happened. You see exit code 137, if you look.

**Fix:** use the **exec form**, a JSON array. Java is then PID 1 and receives SIGTERM directly (step 2). If you really need a shell, for example to expand a variable, end the script with `exec java ...` so Java *replaces* the shell as PID 1.

---

## When to use it / when not to

- **Healthcheck:** on every long-running service image, unless the platform probes from outside instead (Kubernetes uses its own probes, topic 12, and ignores the Dockerfile `HEALTHCHECK`). Point it at Actuator, not at a business endpoint that might be slow or need authentication.
- **Graceful shutdown:** always. It's the default, so the job is to *not break it*: exec-form `ENTRYPOINT`, a timeout shorter than the platform's grace period, and no `immediate`.
- Graceful shutdown covers **HTTP requests**. Background work has its own settings: `@Async` tasks and `@Scheduled` jobs (module 08 topic 13), for example `spring.task.execution.shutdown.await-termination`, and message consumers (module 16). Make long jobs idempotent (module 13 topic 19), so being killed and re-run is safe.

---

## Common pitfalls

- **Shell-form `ENTRYPOINT`/`CMD`.** Java isn't PID 1, never gets SIGTERM, and is SIGKILLed after the timeout. Use `["java", "-jar", "app.jar"]`.
- **`server.shutdown: immediate`.** In-flight requests are dropped. Leave the default (graceful).
- **Shutdown timeout longer than the platform's grace period.** Spring waits 30 s, Docker kills at 10 s, and the request is cut off anyway. Keep `timeout-per-shutdown-phase` below `stop -t` / `stop_grace_period` / `terminationGracePeriodSeconds`.
- **A healthcheck that uses a tool the image doesn't have.** `curl` in a distroless image means the check always fails and the container is marked `unhealthy`. Check that the tool exists, or probe from outside.
- **Healthcheck on an endpoint that calls other services.** One slow downstream service makes every instance "unhealthy" at once. Use Actuator's health endpoint, and keep external dependencies out of liveness (topic 12).

---

## Code examples

`CODE_EXAMPLES/09-healthchecks-and-graceful-shutdown/shutdown-demo/`. Run with `./demo.sh` (Docker must be running; the first run builds the image, about 1 minute). Read in this order:

1. `src/main/java/com/example/shutdown/SlowController.java` — `GET /slow`, a 3-second request to interrupt.
2. `src/main/resources/application.yml` — graceful shutdown, the shutdown timeout, and probe endpoints enabled.
3. `Dockerfile` — exec-form `ENTRYPOINT` and a `HEALTHCHECK` on `/actuator/health`.
4. `Dockerfile.shell-form` — identical except `ENTRYPOINT java -jar app.jar`.
5. `demo.sh` — health status `starting` → `healthy`; then a stop during `/slow` with graceful shutdown, with `immediate`, and with the shell-form image.

---

## Try this yourself

1. Set `timeout-per-shutdown-phase: 1s` in `application.yml`, rebuild, and run step 2 by hand (`docker run`, `curl /slow` in a second terminal, `docker stop -t 10`). What happens to the 3-second request now?
2. Change `Dockerfile.shell-form`'s last line to `ENTRYPOINT exec java -jar app.jar` and run step 4 again. What is PID 1 now, and how long does `docker stop` take?
3. Make the healthcheck fail: change its URL to `/actuator/nope`. How long until `docker ps` shows `unhealthy`? Does Docker restart the container on its own? (Compare with Kubernetes in topic 12.)

## Self-check

1. A container shows `Up 5 seconds (health: starting)`. What does that tell you that `running` alone doesn't, and what makes it change to `healthy`?
2. Every deploy takes exactly 10 seconds longer than it should, and the old container always exits with code 137. What's the likely cause in the Dockerfile, and why does it also break graceful shutdown?
3. A request takes 3 s and arrives 1 s before `docker stop`. Describe what happens with graceful shutdown, and with `server.shutdown: immediate`.
