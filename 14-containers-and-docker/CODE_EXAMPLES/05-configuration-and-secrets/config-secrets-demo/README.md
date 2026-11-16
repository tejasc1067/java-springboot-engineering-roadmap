# config-secrets-demo

Secrets baked into an image versus configuration supplied when the container starts. The app's
`GET /config` reports which profile, settings, and secrets it received, without ever printing a secret.

- `Dockerfile.baked` — BROKEN: a password in `ENV`, a token in a build `ARG`, and a secrets file that is copied in and then `rm`-ed.
- `Dockerfile.buildsecret` — FIXED for build time: the token comes in through `RUN --mount=type=secret` and is never stored.
- `Dockerfile` — FIXED for run time: code and safe defaults only. `demo.sh` runs it with `--env-file runtime.env` (non-secret settings) and a read-only mount of `runtime-secrets/` loaded with `SPRING_CONFIG_IMPORT=configtree:/run/secrets/`.

Run (Docker running; Maven and JDK 21 on the host):

```
./demo.sh
```

What the checks prove:

- The `ENV` password shows in `docker image inspect`, the `ARG` token in `docker history`, and the "deleted" file is still in an earlier layer (found with `docker save` + `tar`), even though `ls` inside the container can't see it.
- The secret-mount build used the token but stored it nowhere.
- The clean image contains no secret; at runtime the app got the `prod` profile, the env-var override, and the password from the mounted file. Env vars appear in `docker inspect`; the mounted file's value doesn't.

All passwords and tokens in this folder are fake. Takes about 10 seconds after the first `eclipse-temurin:21-jre` pull.
