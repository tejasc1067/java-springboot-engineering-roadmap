# dockerfile-demo

Topic 14-03: the same Spring Boot app (`GET /hello`) packaged two ways.

- `Dockerfile.naive` — one stage on the Maven image: copy everything, build, run.
- `Dockerfile` — multi-stage: build in a Maven stage (with a BuildKit cache mount for `~/.m2`), copy only `app.jar` onto `eclipse-temurin:21-jre`.
- `.dockerignore` — keeps `target/`, `.git/`, IDE files, and `.env` out of the build context.

Run:

```bash
./demo.sh
```

What each check proves:

1. Both images build and both answer `GET /hello` (on ports 14031 and 14032).
2. The multi-stage image is roughly half the size (982 MB vs 516 MB on the machine this was written on).
3. The naive image ships your source code, the whole `~/.m2` repository, Maven, and `javac`; the multi-stage image ships none of them.
4. After a one-line Java change, the naive build re-downloads every dependency; the multi-stage build reuses them from its cache mount and is several times faster. The rebuilt image serves the new code.

You don't need Maven or a JDK installed: both builds run inside Docker. First run downloads
`maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre`, the `docker/dockerfile:1` build frontend, and the Spring Boot
dependencies (about a minute). Later runs take under a minute. The script removes its containers and images.
