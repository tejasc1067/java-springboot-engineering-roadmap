# Layered Jars and Buildpacks

A Spring Boot fat jar is about 21 MB. 20 MB of it is libraries that change a few times a year, and about 80 kB is *your* code, which changes every commit. The Dockerfile from topic 03 copies the whole jar as one image layer, so every one-line change produces a new 21 MB layer that has to be built, pushed to a registry, and pulled by every server. This topic splits the jar so a code change ships only the part that changed. It then shows **buildpacks**, which build a layered, production-ready image with no Dockerfile at all.

---

## The problem this solves

Topic 02 explained that an image is a stack of layers and that Docker reuses a layer when its content hasn't changed. Topic 03's Dockerfile ends with:

```dockerfile
COPY target/app.jar app.jar
```

That's one layer holding everything. Change one string in a controller and the jar's bytes change, so the layer's digest changes, so the whole 21 MB is new. On a team pushing twenty times a day to a registry, and deploying to ten servers, that's a lot of identical library bytes moving around for nothing, and slower deployments.

## How it works

### Spring Boot's layers

Spring Boot builds the fat jar with an index that sorts its contents into four **layers**, from least to most likely to change:

| Layer | Contains | Size in the demo | Changes when |
|---|---|---|---|
| `dependencies` | Released library jars (Spring, Tomcat, Jackson...) | 20 MB | You upgrade a library |
| `spring-boot-loader` | The code that launches a fat jar | 692 kB | You upgrade Spring Boot |
| `snapshot-dependencies` | `-SNAPSHOT` library jars | empty here | Often (snapshots are unreleased builds) |
| `application` | Your classes and `application.yml` | 78 kB | Every commit |

The jar can list and extract them itself, using the `tools` jar mode (Spring Boot 3.3 and later):

```bash
$ java -Djarmode=tools -jar target/app.jar list-layers
dependencies
spring-boot-loader
snapshot-dependencies
application
```

### A layered Dockerfile

`Dockerfile.layered` extracts the layers in a first stage, then copies each one as its own image layer, least-changing first:

```dockerfile
FROM eclipse-temurin:21-jre AS extract
WORKDIR /builder
COPY target/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=extract /builder/extracted/dependencies/ ./
COPY --from=extract /builder/extracted/spring-boot-loader/ ./
COPY --from=extract /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract /builder/extracted/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

- `--layers` extracts into one directory per layer.
- `--launcher` keeps the Spring Boot launcher, so the app starts with `JarLauncher` instead of `java -jar`. The extracted folders together recreate the jar's layout.
- The order of the `COPY` lines matters. Docker reuses layers from the bottom up and stops at the first one that changed, so the layer that changes most often (`application`) goes last.

### What the demo measures

`demo.sh` builds both images, changes one line of code (`"hello v1"` to `"hello v2"`), rebuilds both, and compares the layer digests of v1 and v2:

```text
-- fat jar image, v2 layers (newest first):
0B      ENTRYPOINT ["java" "-jar" "app.jar"]
21.1MB  COPY target/app.jar app.jar # buildkit
-- layered image, v2 layers (newest first):
0B      ENTRYPOINT ["java" "org.springframework.boot…
77.8kB  COPY /builder/extracted/application/ ./ # bu…
4.1kB   COPY /builder/extracted/snapshot-dependencie…
692kB   COPY /builder/extracted/spring-boot-loader/ …
21MB    COPY /builder/extracted/dependencies/ ./ # b…

new layers after a one-line change: fat jar = 1, layered = 1
PASS: fat jar image: the whole jar layer changed
PASS: layered image: only one layer changed
PASS: layered image: dependencies layer reused (18bf41a90b2a)
bytes to ship for the change: fat jar = 21.1MB, layered = 77.8kB
PASS: layered image starts and serves the new code: hello v2
```

Both images had exactly one new layer, but the sizes are very different: the fat-jar image must push and pull **21.1 MB** for a one-line change, and the layered image **77.8 kB**. The dependencies layer has the same digest in v1 and v2, so a registry that already has it (and every server that already pulled it) skips it.

---

## Buildpacks: an image without a Dockerfile

Writing a good Dockerfile takes care: layering, a non-root user (topic 10), JVM memory settings for containers, keeping the base image patched. **Cloud Native Buildpacks** do all of that from your project, with no Dockerfile. Spring Boot's Maven plugin has it built in:

```bash
mvn spring-boot:build-image -Dspring-boot.build-image.imageName=roadmap14-04-buildpacks
docker run --rm -p 14041:8080 roadmap14-04-buildpacks
```

The plugin runs Paketo's builder image in Docker. It detects a Spring Boot jar, adds a JRE, and writes the same four application layers, plus launcher and configuration layers of its own. Running it once on this demo:

```text
$ docker history roadmap14-04-buildpacks --format '{{.Size}}\t{{.CreatedBy}}'
...
77.8kB  Application Slice: 4
692kB   Application Slice: 2
21MB    Application Slice: 1
...

$ docker logs <container>
Calculated JVM Memory Configuration: -XX:MaxDirectMemorySize=10M -Xmx6142679K -XX:MaxMetaspaceSize=78672K ...
```

The same 21 MB / 692 kB / 78 kB split appears as "Application Slices". Three things it does for you that a hand-written Dockerfile often forgets:

- **Runs as a non-root user.** `docker image inspect` shows `user=1002:1001`. Topic 10 explains why this matters.
- **Sizes the JVM to the container's memory.** The memory calculator sets `-Xmx` and the other memory pools from the memory the container is allowed, at startup.
- **Reproducible builds.** Every layer has a fixed timestamp (`docker images` shows the image as created "46 years ago", meaning 1 January 1980), so the same source produces the same image digest.

The costs: the first build downloads the builder image (about 1.3 GB) and takes a minute or more; you control less of what's inside; and when something goes wrong you're debugging someone else's build system. That's why `demo.sh` doesn't run it by default.

---

## When to use it / when not to

| | Use it when | Don't, when |
|---|---|---|
| Layered Dockerfile | You write your own Dockerfile and push images often. This is the default for any real Spring Boot service. | Never really. The extra stage costs a few seconds. |
| Buildpacks (`spring-boot:build-image`) | You want a solid image without maintaining a Dockerfile, especially across many services | You need precise control of the base image, OS packages, or the build runs where Docker-in-Docker isn't available |
| Plain fat-jar `COPY` | Learning, or a one-off image you'll never update | Anything you deploy repeatedly |

---

## Common pitfalls

- **Copying the layers in the wrong order.** If `application` is copied before `dependencies`, every code change rebuilds every layer after it. Tried on this demo, a one-line change produced 3 new layers, including a new 21 MB dependencies layer: no better than the fat jar. Copy least-changing first.
- **Using old extraction syntax.** Older guides use `java -Djarmode=layertools -jar app.jar extract` and `JarLauncher` in `org.springframework.boot.loader`. Spring Boot 3.2 moved the launcher to `org.springframework.boot.loader.launch.JarLauncher`, and 3.3 replaced `layertools` with `tools`. Match the commands to your Boot version.
- **Copying the whole project into the build context.** Without a `.dockerignore`, `docker build .` sends `target/`, `.git`, and IDE folders to the builder. The demo's `.dockerignore` allows only `target/app.jar`.
- **Assuming buildpacks are always smaller.** They aren't automatically. The win is in correctness (non-root, memory settings, patched base) and maintenance, not size.

---

## Code examples

`CODE_EXAMPLES/04-layered-jars-and-buildpacks/layers-demo/`. Run with `./demo.sh`, which needs Docker running, Maven, and JDK 21. Read in this order:

1. `src/main/java/com/example/layers/LayersApplication.java` — one endpoint; `demo.sh` changes its `GREETING` line.
2. `Dockerfile.fatjar` — the single-layer version from topic 03.
3. `Dockerfile.layered` — extract stage plus one `COPY` per layer.
4. `.dockerignore` — keeps the build context to the one jar.
5. `demo.sh` — builds both, changes the code, compares layer digests and sizes, and runs the layered image.

---

## Try this yourself

1. In `Dockerfile.layered`, move the `application` `COPY` line above the `dependencies` line. Run `./demo.sh` again. How many layers change now, and how many bytes would you ship?
2. Add a new dependency to `pom.xml` (for example `spring-boot-starter-validation`) instead of changing code. Which layers change?
3. Run the buildpacks command above, then `docker image inspect roadmap14-04-buildpacks --format '{{.Config.User}}'`. Compare with `docker image inspect eclipse-temurin:21-jre --format '{{.Config.User}}'`.

## Self-check

1. You change one line in a controller and rebuild an image made from a single `COPY app.jar`. Why does the whole jar layer change, and what does that cost on every push and deploy?
2. Name Spring Boot's four jar layers in order, and explain why the order of the `COPY` lines in the Dockerfile matters.
3. What does `spring-boot:build-image` give you that a hand-written `FROM eclipse-temurin` Dockerfile doesn't, and what does it cost?
