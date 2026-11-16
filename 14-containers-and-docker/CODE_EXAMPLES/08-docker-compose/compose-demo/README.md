# compose-demo

A Spring Boot app (JPA, one `Note` entity, `GET`/`POST /notes`) and Postgres, run together with Docker Compose.

```
./demo.sh                                   # the whole lesson, with PASS/FAIL checks
docker compose up -d --wait                 # or just run the working setup yourself
curl -X POST -H 'Content-Type: text/plain' -d 'hello' http://localhost:14008/notes
curl http://localhost:14008/notes
docker compose down -v                      # stop and delete everything, including the data
```

Needs Docker running. The first run pulls `postgres:17` and `maven:3.9-eclipse-temurin-21` (about 1.5 GB on disk together, as `docker images` reports it) and downloads Maven dependencies while building the image (about 1 minute). After that, `./demo.sh` takes about 50 seconds.

Files:

- `compose.yaml` — the working setup: service name as host, Postgres healthcheck, `depends_on: condition: service_healthy`, named volume, password from `DB_PASSWORD`.
- `compose.broken-localhost.yaml` — JDBC URL with `localhost`: the app exits with `Connection to localhost:5432 refused`.
- `compose.broken-no-healthcheck.yaml` — short `depends_on` plus a database that takes 10 s to become ready: the app exits with `Connection to db:5432 refused`.
- `compose.slow-db.yaml` + `slow-init.sql` — makes the database slow to start, so you can see the working setup wait correctly.
- `Dockerfile` — multi-stage build (topic 03).

What each check proves: the two broken setups fail with the exact error; the working setup waits for the slow database and serves a note; data survives `docker compose down`; `docker compose down -v` deletes it.
