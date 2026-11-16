# layers-demo

Why a Spring Boot image should be built in layers. `demo.sh` builds the same app two ways, changes one line of
code, rebuilds, and counts which image layers changed.

- `Dockerfile.fatjar` — copies the 21 MB fat jar as one layer (the topic 03 Dockerfile).
- `Dockerfile.layered` — splits the jar with `java -Djarmode=tools -jar app.jar extract --layers --launcher` and copies each of Spring Boot's four layers separately.

Run (Docker must be running; needs Maven and a JDK 21 on the host):

```
./demo.sh
```

What the checks prove:

- `fat jar image: the whole jar layer changed` — one changed line means a new 21.1 MB layer to push and pull.
- `layered image: only one layer changed` and `dependencies layer reused` — the 21 MB of libraries is the same layer as before; only the ~78 kB application layer is new.
- `layered image starts and serves the new code` — the extracted layout runs with `JarLauncher`.

First run downloads `eclipse-temurin:21-jre` (about 480 MB as listed by `docker images`) and Maven dependencies. Takes about 15 seconds after that.

Optional, not in `demo.sh`: build the same app with Cloud Native Buildpacks, no Dockerfile at all:

```
mvn spring-boot:build-image -Dspring-boot.build-image.imageName=roadmap14-04-buildpacks
docker run --rm -p 14041:8080 roadmap14-04-buildpacks
```

The first run downloads the Paketo builder (about 1.3 GB) and takes a minute or two.
