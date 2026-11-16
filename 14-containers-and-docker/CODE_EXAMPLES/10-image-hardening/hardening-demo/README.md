# hardening-demo

One Spring Boot app (`GET /hello` answers with the OS user it runs as), packaged four ways:

| Dockerfile | Base | Runs as |
|---|---|---|
| `Dockerfile.jdk` | `eclipse-temurin:21-jdk` | root |
| `Dockerfile.root` | `eclipse-temurin:21-jre` | root |
| `Dockerfile.nonroot` | `eclipse-temurin:21-jre` + `USER app` | `app` (uid 999) |
| `Dockerfile.distroless` | `gcr.io/distroless/java21-debian12:nonroot` | `nonroot` (uid 65532) |

Run (Docker running, Maven on PATH):

```
./demo.sh
```

What the checks prove:

1. **Size:** JRE image < JDK image; distroless < JRE.
2. **Default image:** the app runs as uid 0 and can overwrite its own jar.
3. **`USER app`:** the app runs as uid 999; writing to `/app/app.jar` gets `Permission denied`.
4. **Distroless:** runs as uid 65532 and has no shell (`docker exec ... sh` fails).
5. **Read-only filesystem:** `--read-only` alone crashes Spring Boot (`Unable to start web server`, Tomcat needs `/tmp`); `--read-only --tmpfs /tmp` works.

First run downloads the three base images (JDK, JRE, distroless: about 1.5 GB on disk together). The script removes its containers on exit; the four `roadmap14-10-*` images stay (`docker rmi roadmap14-10-jdk roadmap14-10-root roadmap14-10-nonroot roadmap14-10-distroless` to remove them).
