# Common mistakes — Containers and Docker

Real container bugs, each one reproduced by a demo in this module. Most of them don't fail loudly. The image builds and the container runs, and the problem shows up later as a leaked password, a slow deploy, lost data, or a 2am restart loop.

---

## 1. Fixing things inside a running container

```bash
docker exec -it my-app bash
# edit a config file, install a package, restart the process...
```

**What goes wrong:** the change lives only in that container's writable layer (topic 02). The next `docker run`, a redeploy, or a second instance starts from the unchanged image, and the fix is gone. Nobody can tell what was changed, either.

**Fix:** change the Dockerfile or the configuration (topic 05), rebuild the image, and replace the container. Use `docker exec` for looking, not fixing.

---

## 2. Shipping the build image to production

```dockerfile
FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY . .
RUN mvn -q package -DskipTests
CMD ["java", "-jar", "target/app.jar"]
```

**What goes wrong:** the image that runs in production contains Maven, a full JDK with `javac`, your source code, and the whole `~/.m2` repository. In topic 03's demo it's 982 MB against 516 MB for the multi-stage version, and anyone who can pull the image can read your code.

**Fix:** a multi-stage build. Build in a Maven stage, then `COPY --from=build /build/target/app.jar app.jar` onto `eclipse-temurin:21-jre`.

---

## 3. `COPY . .` before the build step

```dockerfile
COPY . .
RUN mvn -q package -DskipTests
```

**What goes wrong:** any change to any file invalidates the `COPY` layer, so the build re-runs with an empty Maven repository and downloads every dependency again, on every build, for every developer.

**Fix:** copy the files that rarely change (`pom.xml`) before the ones that change often (`src/`), and keep Maven's repository in a BuildKit cache mount:

```dockerfile
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q package -DskipTests
```

---

## 4. Leaving the JVM at its default 25% heap in a container

```bash
docker run -m 1g my-app        # JVM picks a 256 MB max heap
```

**What goes wrong:** inside a container the JVM sizes its maximum heap at 25% of the memory limit. The app throws `OutOfMemoryError` while three quarters of the container's memory sits unused (topic 03 measures 128 MB under `-m 512m`).

**Fix:** set `-XX:MaxRAMPercentage`, typically 70–80: `-e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`, or in the `ENTRYPOINT`.

---

## 5. Copying the fat jar as one layer, or copying layers in the wrong order

```dockerfile
COPY target/app.jar app.jar
```

**What goes wrong:** every one-line code change produces a new 21 MB layer to push and pull (topic 04 measures 21.1 MB against 77.8 kB). Copying the extracted `application` layer *before* `dependencies` is just as bad: 3 new layers, including the 21 MB dependencies one.

**Fix:** extract with `java -Djarmode=tools -jar app.jar extract --layers --launcher` and `COPY` `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application` in that order. Or use `mvn spring-boot:build-image`.

---

## 6. "Deleting" a secret in a later Dockerfile step

```dockerfile
COPY application-secrets.yml /app/application-secrets.yml
RUN rm /app/application-secrets.yml
```

**What goes wrong:** layers are read-only and stacked. The `rm` adds a layer that hides the file, but the earlier layer still contains it. `docker save` the image, untar it, list the layer tarballs, and the password is there (topic 05's `demo.sh` finds it). Anyone who can pull the image can read it. A secret in `ENV` is even easier to find: `docker image inspect` prints it.

**Fix:** never `COPY` a secret into an image. Mount it at run time and read it with `SPRING_CONFIG_IMPORT=configtree:/run/secrets/`. For secrets needed during the build, use `RUN --mount=type=secret,id=...`.

---

## 7. Passing a build secret with `ARG`

```dockerfile
ARG NPM_TOKEN
RUN npm install --token=$NPM_TOKEN
```

**What goes wrong:** BuildKit records the value in the image history. `docker history --no-trunc` shows `RUN |1 NPM_TOKEN=npm_t0ken-leaked /bin/sh -c ...` (topic 05).

**Fix:** `RUN --mount=type=secret,id=npm_token ...` with `docker build --secret id=npm_token,src=./npm_token`. The file exists only during that `RUN`.

---

## 8. `localhost` in a connection URL between containers

```yaml
app:
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/notes
```

**What goes wrong:** inside a container, `localhost` is that container. The app looks for a database inside itself and exits with `Connection to localhost:5432 refused`, even though Postgres is running next to it and the same URL works when you run the jar on your laptop (topic 06 with plain `docker run`, topic 08 with Compose).

**Fix:** put both containers on a user-defined network, or use Compose, and use the database's container or service name and *container* port: `jdbc:postgresql://db:5432/notes`.

---

## 9. Binding the app to `127.0.0.1` inside the container

```yaml
server:
  address: 127.0.0.1
```

**What goes wrong:** the app answers `curl` from inside its own container, but traffic arriving through the published port gets "Connection reset by peer" (topic 06). The port mapping delivers traffic to the container's network interface, and nothing is listening there.

**Fix:** don't set `server.address` in a container. Spring Boot's default listens on all interfaces (`0.0.0.0`). Control who can reach it with `-p` and networks instead.

---

## 10. Running a database container without a named volume

```bash
docker run -d -e POSTGRES_PASSWORD=secret postgres:17
```

**What goes wrong:** the data goes into an anonymous volume. The next container (a redeploy, an upgrade) gets a new, empty one, and the old data sits orphaned until someone runs `docker volume prune` (topic 07).

**Fix:** always name it: `-v pgdata:/var/lib/postgresql/data`, or a `volumes:` entry in `compose.yaml`. And remember that a volume isn't a backup: use `pg_dump`.

---

## 11. `depends_on` without a healthcheck

```yaml
app:
  depends_on:
    - db
```

**What goes wrong:** Compose waits for the db *container* to start, not for Postgres to accept connections. On a fast laptop it usually works, which is why it ships. On a slow start the app fails with `Connection to db:5432 refused` and exits (topic 08 reproduces it with a 10-second init script).

**Fix:** a `healthcheck` on the database (`pg_isready -h 127.0.0.1 -U notes -d notes`) and `depends_on: db: condition: service_healthy`.

---

## 12. Shell-form `ENTRYPOINT`

```dockerfile
ENTRYPOINT java -jar app.jar
```

**What goes wrong:** Docker runs it as `/bin/sh -c "..."`, so `sh` is PID 1, and it doesn't forward SIGTERM to Java. Every `docker stop` waits the full timeout and then SIGKILLs the app (exit code 137): no graceful shutdown, no shutdown hooks, slow deploys. Topic 09 measures 10.2 s and exit 137 against 2.2 s and exit 143 for the exec form.

**Fix:** exec form, `ENTRYPOINT ["java", "-jar", "app.jar"]`, or end a wrapper script with `exec java ...`.

---

## 13. No `USER` in the Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre
COPY target/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**What goes wrong:** the app runs as root (uid 0). If it's compromised, the attacker can overwrite `/app/app.jar` and change any file in the container, and is one container-runtime bug away from root on the host. Topic 10's `demo.sh` overwrites the jar.

**Fix:** create a system user, copy the app files *before* switching to it (so they stay root-owned and read-only), then `USER app`. Or use a non-root base such as `gcr.io/distroless/java21-debian12:nonroot`.

```dockerfile
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
COPY target/app.jar /app/app.jar
USER app
```

---

## 14. Trusting H2 for queries that will run on PostgreSQL

```java
@Query(value = "select * from product where ifnull(stock, 0) > 0", nativeQuery = true)
```

**What goes wrong:** every `@DataJpaTest` on H2 passes, even with `MODE=PostgreSQL`. In production: `ERROR: function ifnull(integer, integer) does not exist`. H2 has functions and syntax PostgreSQL lacks (`IFNULL`, `DATEADD`, `TOP`) and lacks some PostgreSQL has (`ON CONFLICT`, `jsonb`). Topic 11 shows the whole sequence.

**Fix:** run repository tests that use native SQL or database-specific features against the real engine with Testcontainers and `@ServiceConnection`, and prefer standard SQL (`COALESCE`).

---

## 15. A Kubernetes Service whose selector doesn't match the pod labels

```yaml
# Deployment pod template          # Service
labels:                             selector:
  app: orders                         app: order-service
```

**What goes wrong:** both objects are valid and Kubernetes accepts them. The Service has zero endpoints, so every call to `http://orders` fails while `kubectl get pods` shows the pods `Running`. A `targetPort` that doesn't match `containerPort` gives the same symptom with "connection refused".

**Fix:** copy the selector from `spec.template.metadata.labels`, keep `targetPort` equal to `containerPort` (or use a named port), and check with `kubectl get endpoints orders`. Topic 12's `ManifestChecks` catches both before deploying.

---

## 16. A liveness probe that depends on the database

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health      # includes the db health indicator
```

**What goes wrong:** when the database blips, every pod fails liveness at once and Kubernetes restarts all of them. A database hiccup becomes a full outage, and restarting doesn't fix the database anyway.

**Fix:** liveness on `/actuator/health/liveness`, which reflects only the app's own state. Dependency checks belong in readiness, which only takes the pod out of the Service until it recovers.
