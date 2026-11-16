# Configuration and Secrets

A container image should be the **same** in every environment: the image you tested in staging is the image you run in production. What differs between environments is configuration (which database, which profile, how many threads) and secrets (passwords, API keys). Both should reach the container **when it starts**, never be built into the image. This topic shows why. An image is a file anyone with pull access can take apart, and secrets put into it can be read back out, even ones you "deleted".

---

## The problem this solves

The first time someone containerises an app, they need it to talk to a database, so they write:

```dockerfile
ENV SPRING_DATASOURCE_PASSWORD=Sup3rS3cret
```

It works. It also means:

- **Everyone who can pull the image can read the password.** That includes CI systems, every developer with registry access, and anyone the image leaks to. Images get pushed to public registries by mistake more often than you'd hope.
- **You need a different image per environment.** Staging and production have different passwords, so you build twice and no longer test what you ship.
- **Rotating the password means rebuilding and redeploying.**

## Broken: three ways secrets get baked in

`Dockerfile.baked` makes all three common mistakes:

```dockerfile
# 1. ENV
ENV SPRING_DATASOURCE_PASSWORD=Sup3rS3cret-from-env

# 2. A build ARG used in a RUN
ARG NPM_TOKEN
RUN echo "pretend this downloads something with $NPM_TOKEN" > /dev/null

# 3. A secrets file copied in, then "deleted"
COPY baked-secrets/application-secrets.yml /app/application-secrets.yml
RUN rm /app/application-secrets.yml
```

`demo.sh` builds it with `--build-arg NPM_TOKEN=npm_t0ken-leaked`, then reads every secret back out:

```text
== BROKEN: secrets baked into the image
  docker image inspect:  SPRING_DATASOURCE_PASSWORD=Sup3rS3cret-from-env
PASS: ENV secret is readable with docker image inspect
  docker history:        NPM_TOKEN=npm_t0ken-leaked
PASS: build ARG value is recorded in the image history
PASS: the 'deleted' secrets file is not visible inside a running container...
  docker save + tar:         password: Sup3rS3cret-from-file
PASS: ...but it is still inside an earlier layer of the image
```

1. **`ENV`** is stored in the image's configuration. `docker image inspect` prints it in plain text.
2. **`ARG`** looks temporary, but when a `RUN` uses it, BuildKit records the value in the image history: `RUN |1 NPM_TOKEN=npm_t0ken-leaked /bin/sh -c ...`.
3. **`RUN rm`** doesn't remove anything from the image. Topic 02 explained that layers are stacked and read-only: the `rm` adds a *new* layer that hides the file, but the layer underneath still contains it. `docker save` exports every layer as a tar file, and the password is right there.

## How it works: configure at `docker run`

The clean `Dockerfile` contains only the jar and safe defaults:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Configuration arrives when the container starts, in two different ways for two different kinds of value.

### Non-secret settings: environment variables

Spring Boot reads environment variables as configuration, and they override `application.yml` inside the jar (property precedence, module 08 topic 05). Spring's **relaxed binding** (module 08 topic 05) maps environment variable names onto property names: upper case, with `.` and `-` replaced by `_`:

| Environment variable | Property it sets |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | `spring.profiles.active` (module 08 topic 07) |
| `APP_GREETING=hello` | `app.greeting` |
| `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/shop` | `spring.datasource.url` |
| `SERVER_PORT=9090` | `server.port` |

Pass them one at a time with `-e NAME=value`, or put an environment's settings in a file and pass `--env-file`:

```bash
# runtime.env
SPRING_PROFILES_ACTIVE=prod
APP_GREETING=hello from the environment
```

```bash
docker run --env-file runtime.env -p 14005:8080 roadmap14-05-clean
```

Activating `prod` also loads `application-prod.yml` from inside the jar. Non-secret production defaults (like `app.mode: production`) can live there safely.

### Secrets: a mounted file, read with `configtree`

Environment variables are visible to anyone who can run `docker inspect` on the host, they're inherited by every child process, and they tend to end up in logs and crash dumps. For secrets, mount a **file** instead, read-only, and let Spring read it:

```bash
docker run --env-file runtime.env \
    -e SPRING_CONFIG_IMPORT=configtree:/run/secrets/ \
    -v "$PWD/runtime-secrets:/run/secrets:ro" \
    -p 14005:8080 roadmap14-05-clean
```

`configtree:` tells Spring Boot to treat a directory as configuration: **each file's name is a property name, and its contents are the value**. The file `runtime-secrets/spring.datasource.password` becomes `spring.datasource.password`. This is exactly the shape Docker Compose secrets (topic 08) and Kubernetes Secrets (topic 12) use. They mount secrets as files under a directory, so the same Spring setting works everywhere.

The demo's `GET /config` reports what the app received, without printing the secret:

```text
== FIXED (run time): one clean image, configured at docker run
PASS: clean image: no secret in inspect or history
PASS: clean image: no secrets file in any layer
  GET /config: {"greeting":"hello from the environment","profiles":"[prod]","mode":"production","dbPasswordSet":true}
PASS: profile came from SPRING_PROFILES_ACTIVE
PASS: application-prod.yml in the jar was applied
PASS: APP_GREETING env var overrode app.greeting
PASS: password loaded from the mounted file
PASS: env vars show up in docker inspect (fine for non-secrets)
PASS: mounted secret value does not appear in docker inspect
```

### Secrets needed during the build

Sometimes the *build* needs a secret, such as a token for a private package registry. Use a BuildKit **secret mount** instead of an `ARG`:

```dockerfile
RUN --mount=type=secret,id=npm_token \
    test -s /run/secrets/npm_token && echo "pretend this downloads something with the token" > /dev/null
```

```bash
docker build --secret id=npm_token,src=./npm_token -f Dockerfile.buildsecret -t roadmap14-05-buildsecret .
```

The file exists at `/run/secrets/npm_token` only while that one `RUN` executes. It's never written to a layer, the history, or the image config:

```text
== FIXED (build time): a secret mount instead of an ARG
PASS: build used the token, but it is in no layer, history, or config
```

---

## When to use what

| Value | Where it goes |
|---|---|
| Safe defaults that are the same everywhere | `application.yml` in the jar |
| Non-secret defaults for one profile | `application-prod.yml` in the jar |
| Per-environment, non-secret settings (URLs, feature flags, pool sizes) | Environment variables (`-e`, `--env-file`, Compose `environment:`, a Kubernetes ConfigMap) |
| Passwords, API keys, private keys | Mounted secret files + `configtree:` (Compose secrets, Kubernetes Secrets), or a secrets manager |
| Tokens needed only during the build | `RUN --mount=type=secret` |

Plain environment variables for secrets are common and often accepted in small setups. Know that the value is visible through `docker inspect` and to every process in the container, and prefer files once you can.

---

## Common pitfalls

- **"I'll delete it in the next step."** `RUN rm secret.txt` adds a layer that hides the file; the earlier layer still has it. Never `COPY` a secret into an image, even temporarily.
- **Secrets in `ARG`.** Build arguments used by a `RUN` end up in `docker history`. Use `--mount=type=secret`.
- **Copying the whole project into the image.** `COPY . .` picks up `.env` files, `application-local.yml` with real passwords, and `.git`. Use a `.dockerignore` that allows only what the image needs.
- **Logging the configuration at startup.** Code that prints every property "for debugging" prints the password too. Log whether a secret is set, never its value, like the demo's `dbPasswordSet`.

---

## Code examples

`CODE_EXAMPLES/05-configuration-and-secrets/config-secrets-demo/`. Run with `./demo.sh`, which needs Docker running, Maven, and JDK 21. Read in this order:

1. `src/main/resources/application.yml`, `application-prod.yml` — safe defaults and a non-secret profile file.
2. `src/main/java/com/example/config/ConfigApplication.java` — `GET /config` reports profile, settings, and *whether* the password is set.
3. `Dockerfile.baked` — the three ways to leak (`ENV`, `ARG`, copy-then-`rm`).
4. `Dockerfile.buildsecret` — the build-time fix.
5. `Dockerfile` — the clean image.
6. `runtime.env` and `runtime-secrets/spring.datasource.password` — runtime configuration and a mounted secret (fake values).
7. `demo.sh` — extracts each leaked secret, then runs the clean image with env vars and a mounted secret.

---

## Try this yourself

1. Run `docker build -f Dockerfile.baked --build-arg NPM_TOKEN=abc -t leaky .` yourself, then `docker history --no-trunc leaky`. Find the token. Then try `docker image inspect leaky`.
2. Start the clean image with `-e APP_GREETING=one` *and* an `--env-file` that sets `APP_GREETING=two`. Which wins? Then add `--app.greeting=three` after the image name (a command-line argument). Which wins now? Compare with module 08 topic 05's precedence list.
3. Add a second secret file, `runtime-secrets/app.api-key`, read it with `@Value("${app.api-key}")`, and add `"apiKeySet"` to `/config`. Did you need to change the `docker run` command?

## Self-check

1. A teammate writes `COPY prod.env /app/` and then `RUN rm /app/prod.env` "so the secrets aren't in the final image". Explain exactly where the secrets still are, and how someone gets them out.
2. Why should staging and production run the *same* image? What breaks if each environment has its own build?
3. `SPRING_DATASOURCE_PASSWORD` as an environment variable vs a mounted file read with `configtree:`. Name one place each can leak, and say which you'd choose for production.
