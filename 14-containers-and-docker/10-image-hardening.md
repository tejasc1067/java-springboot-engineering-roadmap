# Image Hardening

The Dockerfile from topic 03 works. This topic is about what it does *by default* that you'd never choose on purpose: it runs your app as **root**, ships tools the app never uses, and lets the app write over its own code. "Hardening" means removing those defaults one at a time, so that if an attacker does get into your app (through a library bug like Log4Shell, module 08 topic 10), they find as little as possible to work with.

None of this changes what your app does. Every variant in the demo answers `GET /hello` the same way.

---

## The problem this solves

A container is not a security boundary you can lean on. It's a process on a shared machine with some walls around it (topic 01). If the app inside is compromised, what the attacker can do next depends on what you put in the image:

- **Running as root.** Inside the container, uid 0 can modify any file: replace your jar, change configuration, install tools. If a container-escape bug exists in the runtime, root inside is far closer to root on the host.
- **Shells and tools.** `sh`, `curl`, and a package manager are exactly what an attacker uses to download and run their next step.
- **Size.** Every extra package is more code that might contain a known vulnerability. It's also more to pull on every deploy.

---

## How it works

`hardening-demo` builds the same app four ways and checks each one. `./demo.sh`:

```text
== 1. Image size
   JDK base: 786 MB   JRE base: 516 MB   distroless: 310 MB
PASS: JRE image is smaller than JDK image
PASS: distroless image is smaller than JRE image

== 2. Default image: runs as root
   hello from user root
PASS: app runs as uid 0 (root)
PASS: as root, the app's own jar can be overwritten (an attacker inside the app could replace its code)

== 3. USER app: runs as an unprivileged user
   hello from user app
PASS: app runs as uid 999, not root
PASS: overwriting /app/app.jar is refused: Permission denied

== 4. Distroless :nonroot: no shell, uid 65532
   hello from user nonroot
PASS: app runs as uid 65532
PASS: no shell in the image: 'docker exec ... sh' fails (nothing for an attacker to run either)

== 5. Read-only root filesystem
PASS: --read-only alone: app crashes ('Unable to start web server' - Tomcat needs a writable /tmp)
PASS: --read-only --tmpfs /tmp: app runs, and nothing outside /tmp is writable
```

(Sizes are what `docker image inspect` reports on the machine that ran it, including the 21 MB jar. Your numbers will differ slightly. The order won't.)

### Step 1: run on a JRE, not a JDK

```dockerfile
FROM eclipse-temurin:21-jdk     # compiler, jshell, jlink... to RUN an already-compiled jar
```

A JDK is for *building* Java. At runtime you only need the JRE: 786 MB down to 516 MB. Topic 03's multi-stage build already gets this right by building with a JDK image in the first stage and copying the jar into a JRE image.

### Step 2: stop running as root

`Dockerfile.root` has no `USER` line, so the process runs as root:

```text
$ docker exec roadmap14-10-root-c id
uid=0(root) gid=0(root) groups=0(root)
$ docker exec roadmap14-10-root-c sh -c 'echo tampered >> /app/app.jar'     # succeeds
```

The fix is two lines:

```dockerfile
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
COPY target/app.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- `useradd --system ... --shell /usr/sbin/nologin` creates a user that exists only to run the app.
- `COPY` runs **before** `USER`, so the jar stays owned by root and is read-only for the `app` user. The app can run its jar but not change it:

```text
$ docker exec roadmap14-10-nonroot-c id
uid=999(app) gid=999(app) groups=999(app)
$ docker exec roadmap14-10-nonroot-c sh -c 'echo tampered >> /app/app.jar'
sh: 1: cannot create /app/app.jar: Permission denied
```

Don't "fix" a permission error by adding `COPY --chown=app` to the jar or by running `chmod 777`. That hands write access straight back.

### Step 3: distroless, an image with nothing but the runtime

```dockerfile
FROM gcr.io/distroless/java21-debian12:nonroot
COPY target/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Google's **distroless** images contain the JVM and the libraries it needs, and nothing else: no shell, no package manager, no `curl`. The `:nonroot` tag already runs as uid 65532, so you don't write a `USER` line. The result is 310 MB instead of 516 MB, and:

```text
$ docker exec roadmap14-10-distroless-c sh
OCI runtime exec failed: exec failed: unable to start container process: exec: "sh": executable file not found in $PATH
```

That's the point. It's also the cost: you can't `docker exec` in to look around while debugging. You debug from outside, with logs (module 17), Actuator (module 08 topic 12), and `docker cp`, or you temporarily use the `:debug-nonroot` tag, which adds a busybox shell.

Because there's no shell, every `ENTRYPOINT` and `CMD` must use the JSON (exec) form, `["java", "-jar", ...]`. The shell form `ENTRYPOINT java -jar /app/app.jar` is run through `/bin/sh`, so the container never starts: `exec: "/bin/sh": stat /bin/sh: no such file or directory`. (The exec form is also what lets `docker stop` reach the JVM directly, topic 09.)

### Step 4: a read-only filesystem

At run time you can make the whole container filesystem read-only:

```bash
docker run --read-only roadmap14-10-nonroot                 # crashes
docker run --read-only --tmpfs /tmp roadmap14-10-nonroot    # works
```

The first one fails:

```text
ERROR ... o.s.boot.SpringApplication : Application run failed
org.springframework.context.ApplicationContextException: Unable to start web server
```

Embedded Tomcat writes temporary files under `/tmp`. Giving it an in-memory `/tmp` (`--tmpfs /tmp`) is enough, and nothing else in the container can be written. In Kubernetes the same setting is `readOnlyRootFilesystem: true` plus an `emptyDir` volume mounted at `/tmp` (topic 12).

### Step 5: know what's in the image, and pin it

- **Pin versions.** `FROM eclipse-temurin:21-jre` moves whenever a new 21.x is published, so the same Dockerfile builds a different image next month. For reproducible builds pin the exact version (`eclipse-temurin:21.0.x_y-jre`) or the digest (`eclipse-temurin@sha256:...`). Then update on purpose, often, because pinning also pins old vulnerabilities.
- **Scan.** Image scanners list the known vulnerabilities (CVEs) in every package in an image. Docker Desktop includes `docker scout` (`docker scout quickview <image>`, which requires signing in to a Docker account). Trivy and Grype are popular free alternatives that run in CI. Fewer packages, as with distroless, means fewer findings to triage.
- **Keep secrets out of the image.** Anything in a layer can be read by anyone who can pull the image (topic 05).

---

## When to use it / when not to

| Measure | Cost | Do it? |
|---|---|---|
| JRE base instead of JDK | None | Always |
| Non-root `USER` | Two lines; files the app writes must go in a directory it owns | Always |
| Distroless | No shell for debugging; exec-form `ENTRYPOINT` only | Production images, once your team is comfortable debugging from outside |
| `--read-only` + `/tmp` tmpfs | Find every path the app writes to | Production, especially internet-facing services |
| Pinned versions + scanning | Someone has to bump versions regularly | Always, with the bumping automated |

Buildpacks (`spring-boot:build-image`, topic 04) already produce a non-root image with a JRE. If you use them, you get steps 1 and 2 for free.

---

## Common pitfalls

- **No `USER` line.** The default user is root. Add a system user and `USER` it, or use a `:nonroot` base image.
- **`COPY --chown=app` (or `chmod 777`) on the application files.** It makes the code writable by the process that runs it. Leave app files owned by root, and give the app a separate writable directory only if it really needs one.
- **Shell-form `ENTRYPOINT` on distroless.** `ENTRYPOINT java -jar app.jar` needs `/bin/sh`, which isn't there. Use `["java", "-jar", "/app/app.jar"]`.
- **`--read-only` without a writable `/tmp`.** Spring Boot crashes at startup with `Unable to start web server`. Mount a tmpfs at `/tmp`.

---

## Code examples

`CODE_EXAMPLES/10-image-hardening/hardening-demo/`. Run `./demo.sh` with Docker running. Read in this order:

1. `src/main/java/com/example/hardening/HardeningApplication.java` — `GET /hello` reports which user the JVM runs as.
2. `Dockerfile.jdk` — the naive version: a full JDK, running as root.
3. `Dockerfile.root` — a JRE, still running as root.
4. `Dockerfile.nonroot` — a system user plus `USER app`, with the jar left root-owned.
5. `Dockerfile.distroless` — distroless `:nonroot`: no shell, uid 65532.
6. `demo.sh` — builds all four images and runs checks 1–5.

---

## Try this yourself

1. Change `Dockerfile.nonroot` to `COPY --chown=app:app target/app.jar /app/app.jar` and rerun `./demo.sh`. Which check fails, and why is that a real risk?
2. Change `Dockerfile.distroless` to the shell form `ENTRYPOINT java -jar /app/app.jar`, build it, and run it. Read the error.
3. Start the non-root image with `--read-only --tmpfs /tmp`, then `docker exec` in and try `touch /app/x` and `touch /tmp/x`. Which works?

## Self-check

1. Your container runs as root, but "it's inside a container, so it's isolated". Give two concrete things root inside the container can do that a non-root user can't.
2. Why does the `COPY` of the jar come *before* `USER app` in `Dockerfile.nonroot`, and what would change if it came after with `--chown=app`?
3. What does a distroless image leave out, what does that cost you when debugging, and what do you do instead of `docker exec ... sh`?
4. A Spring Boot container started with `--read-only` exits immediately. What's the error, why does it happen, and what's the fix?
