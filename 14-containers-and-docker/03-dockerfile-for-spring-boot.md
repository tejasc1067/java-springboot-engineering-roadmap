# A Dockerfile for Spring Boot

Topics 01 and 02 used toy programs. This topic packages a real Spring Boot app, two ways. The first is the Dockerfile most people write first: it works, and it ships nearly a gigabyte of build tools, your source code, and every downloaded dependency to production. The second is a **multi-stage build**: build in one image, run in another, and copy across only the jar. Same app, half the size, nothing in it that production doesn't need, and rebuilds several times faster.

---

## The problem this solves

To run, a Spring Boot app needs a Java *runtime* and `app.jar`. To *build* it, you need much more: a full JDK with the compiler, Maven, your source code, and a few hundred megabytes of downloaded dependencies. If one image does both jobs, production gets the build tools too:

- **Bigger images:** slower to push, pull, and start on a new server, and more disk everywhere.
- **More attack surface:** a compiler and Maven inside a production container are tools an attacker can use, and more installed software means more security updates (topic 10).
- **Your source code ships:** anyone who can pull the image can read it.
- **Slow rebuilds:** a one-line change re-downloads every dependency.

---

## How it works

### The app

`dockerfile-demo` is a minimal Spring Boot web app with one endpoint, `GET /hello`. One line in its `pom.xml` matters for Docker:

```xml
<build>
    <finalName>app</finalName>      <!-- the jar is target/app.jar, not target/dockerfile-demo-1.0.0.jar -->
    ...
</build>
```

A fixed jar name means the Dockerfile doesn't change every time the version number does.

### Broken: build and run in one image

```dockerfile
FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY . .
RUN mvn -q package -DskipTests
EXPOSE 8080
CMD ["java", "-jar", "target/app.jar"]
```

`maven:3.9-eclipse-temurin-21` is an image with a full JDK 21 and Maven installed. The Dockerfile copies the project in, builds the jar, and runs it, all in the same image. It works:

```text
PASS: naive image answers on :14031
```

But look at what it ships:

```text
   naive: 982 MB   multi-stage: 516 MB
PASS: naive image contains your source code
PASS: naive image contains the whole Maven repository (54 MB)
PASS: naive image contains Maven and a full JDK (compiler included)
```

And because `COPY . .` comes before `mvn package` (topic 02), changing one line of Java invalidates the build step, and Maven starts again with an empty `~/.m2` and downloads everything.

### Fixed: a multi-stage build

```dockerfile
# syntax=docker/dockerfile:1

# ---- Stage 1: build (Maven + full JDK; thrown away afterwards) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q package -DskipTests

# ---- Stage 2: run (just a JRE + the jar) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Line by line, what's new:

- **Two `FROM` lines = two stages.** The first is named `build` (`AS build`). Only the **last** stage becomes the image. Everything else exists only during the build.
- **`COPY --from=build ...`** copies one file, the jar, out of the build stage into the runtime stage. The source, the JDK, Maven, and `~/.m2` are left behind.
- **`eclipse-temurin:21-jre`** is a Java *Runtime* Environment: enough to run Java, with no compiler. A running app never needs `javac`.
- **`RUN --mount=type=cache,target=/root/.m2`** is a **BuildKit cache mount**. Maven's download folder is kept between builds on your machine, but it's never part of any image layer. Dependencies download once.
- **`# syntax=docker/dockerfile:1`** on the first line tells Docker to use the current Dockerfile syntax, which provides `--mount`. It's downloaded once, like a base image.
- **`ENTRYPOINT ["java", "-jar", "app.jar"]`**, the *exec form* (a JSON array), runs `java` directly as the container's main process, so it receives the stop signal when the container is stopped. Topic 09 shows why that matters for not dropping requests.
- **`EXPOSE 8080`** documents which port the app listens on. It doesn't publish anything by itself; `docker run -p 14032:8080` does (topic 06).

The result, from `demo.sh`:

```text
PASS: multi-stage image answers on :14032
PASS: multi-stage image is 466 MB smaller
PASS: multi-stage image has no source, no ~/.m2, no Maven, no javac - just a JRE and app.jar
== 5. Change one line of Java and rebuild both
   naive rebuild: 16s   multi-stage rebuild: 3s
PASS: naive re-downloaded every dependency; multi-stage reused them from its cache mount
PASS: the rebuilt multi-stage image serves the changed code
```

Most of the remaining 516 MB is the JRE base image itself. Topic 10 goes further with smaller and safer base images, and topic 04 splits the jar into layers so a code change doesn't re-ship the 20 MB of Spring libraries inside it.

### `.dockerignore`

`docker build .` sends the whole directory, the **build context**, to the builder before anything else happens. A `.dockerignore` file keeps things out of it, the same way `.gitignore` works for Git:

```text
target/
.git/
.idea/
*.iml
.vscode/
.env
*.log
```

This matters most for the naive `COPY . .`: without it, your local `target/`, your Git history, and any `.env` file with real passwords would be copied into the image. Even with a multi-stage build, a smaller context makes every build faster. Topic 05 covers secrets properly; the rule here is that the context should never contain them.

### Building and running it yourself

```bash
cd CODE_EXAMPLES/03-dockerfile-for-spring-boot/dockerfile-demo
docker build -t hello-app .                 # uses ./Dockerfile (the multi-stage one)
docker run --rm -p 8080:8080 hello-app      # Ctrl+C to stop
curl localhost:8080/hello                   # in another terminal
```

You don't need Maven or a JDK installed. The build stage brings its own.

---

## Two more things every Java Dockerfile should get right

### Pin the base image

`eclipse-temurin:21-jre` is a *moving* tag: it points at the newest Java 21 update and gets re-pointed every few months. Two builds of the same Dockerfile, weeks apart, can produce different images. That's normally what you want, because you get security fixes. But it means "the same Dockerfile" isn't always "the same image". Options, from loosest to strictest:

| Tag | Moves when |
|---|---|
| `eclipse-temurin:21-jre` | every Java 21 update and every OS patch |
| `eclipse-temurin:21.0.13_9-jre` | only OS patches (Java stays at 21.0.13+9) |
| `eclipse-temurin:21-jre@sha256:18bb4a91…` | never: a digest identifies one exact image |

A common setup: a version tag in the Dockerfile, plus a tool such as Dependabot or Renovate that opens a pull request when a newer base image is published. And never use `:latest` for a base image, because you don't know what Java version you'll get.

### Let the JVM use the container's memory

Inside a container, the JVM reads the container's memory limit and, by default, sizes its maximum heap at **25%** of it. With `docker run -m 512m`, that's a 128 MB heap, and 384 MB sit unused while the app runs out of memory. Checked on `eclipse-temurin:21-jre`:

```text
docker run -m 512m ...                                   MaxHeapSize = 134217728   (128 MB)
docker run -m 512m -e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 ...   MaxHeapSize = 402653184   (384 MB)
```

Set `-XX:MaxRAMPercentage` (typically 70–80) through `JAVA_TOOL_OPTIONS` or in the `ENTRYPOINT`. Leave the rest for the JVM's own non-heap memory: thread stacks, metaspace, and buffers.

---

## When to use it / when not to

| Approach | Use it when |
|---|---|
| Multi-stage Dockerfile (this topic) | The default for a Spring Boot service. You control every line, and the build needs nothing but Docker. |
| Building the jar outside Docker (`mvn package`, then a Dockerfile that only does `COPY target/app.jar`) | Your build pipeline already produces and tests the jar. Then the Dockerfile is just the second stage. |
| `mvn spring-boot:build-image` (Buildpacks) | You'd rather not write a Dockerfile at all. Topic 04. |
| A single-stage build image (the naive version) | Never for production. It's fine for a throwaway experiment. |

`-DskipTests` in the Dockerfile is deliberate: tests should run *before* the image is built (`mvn test`, or your build pipeline), and integration tests may need Docker themselves (topic 11). Running them inside `docker build` makes every image build slow and hides test output.

---

## Common pitfalls

- **Shipping the build image.** Maven, a JDK, the source, and `~/.m2` all reach production. Use a multi-stage build with a JRE runtime stage.
- **`COPY . .` with no `.dockerignore`.** `target/`, `.git/`, and `.env` files end up in the image or slow down the build. Add a `.dockerignore`.
- **A version number in the jar name.** `COPY target/demo-1.0.3.jar` breaks on the next release. Set `<finalName>app</finalName>`, or copy `target/*.jar` into a fixed name.
- **The default 25% heap.** A container with 1 GB gets a 256 MB heap. Set `-XX:MaxRAMPercentage`.
- **Shell-form `CMD java -jar app.jar`.** `java` may not be the container's main process, so it may not get the stop signal. Use the JSON (exec) form.

---

## Code examples

`CODE_EXAMPLES/03-dockerfile-for-spring-boot/dockerfile-demo/`. Run `./demo.sh` (about a minute; the first run longer while images and dependencies download). Read in this order:

1. `pom.xml` and `src/main/java/com/example/dockerfile/DockerfileDemoApplication.java` — the app, with `<finalName>app</finalName>`.
2. `Dockerfile.naive` — build and run in the Maven image.
3. `Dockerfile` — the multi-stage version with a cache mount.
4. `.dockerignore` — what never enters the build context.
5. `demo.sh` — builds both, checks both serve `/hello`, compares sizes and contents, then times a rebuild after a one-line change.

---

## Try this yourself

1. Remove `--mount=type=cache,target=/root/.m2` from the `Dockerfile` and run `./demo.sh` twice. How do the multi-stage rebuild times compare now?
2. Change the runtime stage to `FROM eclipse-temurin:21-jdk` and compare the image size. What did the extra size buy you at runtime?
3. Build the multi-stage image and run `docker run --rm -m 512m --entrypoint java <image> -XX:+PrintFlagsFinal -version | grep MaxHeapSize`. Then add `-e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` and run it again. Finally try `docker run --rm --entrypoint sh <image> -c 'command -v jcmd'`: the JRE has no `jcmd`. What does that mean for debugging a production container?

## Self-check

1. What ends up in the final image of a multi-stage build, and what's discarded? How does `COPY --from=build` decide?
2. Name three things the naive single-stage image ships to production that the app never uses at runtime, and one risk each creates.
3. Why does the naive Dockerfile re-download every Maven dependency after a one-line code change, and what two things in the multi-stage version stop that?
4. A container limited to 1 GB of memory gets `OutOfMemoryError` with a heap of only 256 MB. Why, and what's the fix?
