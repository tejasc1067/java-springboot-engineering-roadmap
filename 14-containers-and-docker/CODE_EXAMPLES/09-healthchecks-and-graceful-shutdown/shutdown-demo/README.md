# shutdown-demo

A Spring Boot app with a 3-second endpoint (`GET /slow`) and Actuator. The demo stops the container in the middle of a request in three different setups.

```
./demo.sh
```

Needs Docker running. The first run pulls `maven:3.9-eclipse-temurin-21` and `eclipse-temurin:21-jre` (about 1.3 GB on disk together, as `docker images` reports it) and downloads Maven dependencies (about 1 minute). After that, about 25 seconds.

Files:

- `Dockerfile` — exec-form `ENTRYPOINT ["java", "-jar", "app.jar"]` and a `HEALTHCHECK` that curls `/actuator/health`.
- `Dockerfile.shell-form` — the same, but `ENTRYPOINT java -jar app.jar` (shell form).
- `src/main/resources/application.yml` — `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase`, and Kubernetes probe endpoints enabled.

What each check proves:

1. The container's health goes `starting` → `healthy` while its state is `running` the whole time; `/actuator/health/readiness` answers.
2. Exec form + graceful shutdown: the in-flight request finishes with 200, then the app exits on SIGTERM (exit code 143) in about 2 s.
3. `SERVER_SHUTDOWN=immediate`: the in-flight request gets no response (curl exit 52).
4. Shell form: PID 1 is `sh`, SIGTERM never reaches Java, and Docker kills it after the full 10 s timeout (exit code 137).
