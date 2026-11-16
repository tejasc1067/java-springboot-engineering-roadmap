# Docker Compose

A real backend is never one container. Even the smallest Spring Boot app needs a database next to it. Starting each piece with its own long `docker run` command (network, ports, environment variables, volumes, the right order) gets old by the second day and goes wrong by the third. **Docker Compose** describes the whole set in one file, `compose.yaml`, and starts it with one command.

This topic runs a Spring Boot + Postgres app with Compose, and shows the two mistakes almost everyone makes on the first try.

---

## The problem this solves

Without Compose, starting the app and its database by hand looks like this:

```bash
docker network create notes-net
docker volume create notes-data
docker run -d --name db --network notes-net -v notes-data:/var/lib/postgresql/data \
  -e POSTGRES_DB=notes -e POSTGRES_USER=notes -e POSTGRES_PASSWORD=... postgres:17
# ...wait until Postgres is actually ready...
docker build -t notes-app .
docker run -d --name app --network notes-net -p 14008:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/notes -e ... notes-app
```

Every teammate has to type it right, in the right order, with the same names. Stopping and cleaning up is another set of commands. Compose turns this into a file that lives in the repository, next to the code it runs.

---

## How it works

### The file

`compose.yaml` from the demo:

```yaml
name: roadmap14-08

services:
  db:
    image: postgres:17
    environment:
      POSTGRES_DB: notes
      POSTGRES_USER: notes
      POSTGRES_PASSWORD: ${DB_PASSWORD:-dev-only-password}
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -h 127.0.0.1 -U notes -d notes"]
      interval: 2s
      timeout: 3s
      retries: 30

  app:
    build: .
    image: roadmap14-08-app
    ports:
      - "14008:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/notes
      SPRING_DATASOURCE_USERNAME: notes
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-dev-only-password}
    depends_on:
      db:
        condition: service_healthy

volumes:
  db-data:
```

Reading it top to bottom:

- **`services`**: each entry becomes one container. `db` runs the `postgres:17` image. `app` is **built** from the `Dockerfile` in this folder (the multi-stage build from topic 03) and tagged `roadmap14-08-app`.
- **One network, automatically.** Compose puts every service on a private network where each service's **name is its hostname** (topic 06). The app reaches Postgres at `db:5432`. Only `app` publishes a port to your machine (`14008:8080`). The database isn't reachable from outside at all, and doesn't need to be.
- **`environment`**: Spring Boot reads `SPRING_DATASOURCE_URL` as `spring.datasource.url`, the relaxed binding from module 08 topic 05. The app's own `application.yml` contains no database address, because that changes per environment (topic 05).
- **`${DB_PASSWORD:-dev-only-password}`**: Compose takes the value from your shell or a `.env` file next to `compose.yaml`, falling back to the default. Real passwords don't belong in this file (topic 05).
- **`volumes: db-data`**: a named volume for Postgres's data (topic 07), declared at the bottom and created on first use.
- **`healthcheck` + `depends_on: condition: service_healthy`**: start `app` only once Postgres actually accepts connections. More on this below.

### The commands

```bash
docker compose up -d --wait     # build if needed, start everything, return once all are healthy
docker compose ps               # what's running, and its health
docker compose logs -f app      # follow one service's logs
docker compose down             # stop and remove the containers and network; the volume stays
docker compose down -v          # ...and delete the volumes too (the data is gone)
docker compose up -d --build    # rebuild the app image after a code change
```

`-d` runs in the background. `--wait` blocks until every service with a healthcheck is healthy, so a script can call the API on the next line.

---

## Broken 1: `localhost` in the JDBC URL

On your laptop, with Postgres in Docker and the app run from your IDE, this URL works:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/notes
```

Put the app in a container and it fails (`compose.broken-localhost.yaml`):

```text
PASS: app exited (1): 'Connection to localhost:5432 refused' - localhost is the app's own container
```

Inside a container, `localhost` means **that container**, not your machine and not the other services. The app container has no Postgres in it. Use the **service name**: `jdbc:postgresql://db:5432/notes`. (Topic 06 explains container networking in full.)

## Broken 2: `depends_on` without a healthcheck

```yaml
app:
  depends_on:
    - db
```

This looks like "start the app after the database". It means "start the app after the database **container has started**". Postgres inside it may still be initialising. Then Spring Boot tries to connect during startup, fails, and the app exits.

An honest note: on a fast laptop this race **rarely shows**. Run 4 times in a row without a healthcheck, the app started fine every time, because Postgres initialises in about a second and the JVM takes longer. That's exactly why it gets shipped. It fails later on a slow CI machine, on a cold start, or when the database first imports seed data.

To make it reproducible, `compose.broken-no-healthcheck.yaml` mounts `slow-init.sql` (`SELECT pg_sleep(10);`) into Postgres's init folder, standing in for a database that takes a while:

```text
PASS: app exited (1): 'Connection to db:5432 refused' - it started before Postgres was ready
```

### Fixed: wait for *healthy*, not *started*

Give `db` a **healthcheck**, a command Compose runs inside the container every 2 seconds. Then make `app` wait for it to pass:

```yaml
db:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -h 127.0.0.1 -U notes -d notes"]
app:
  depends_on:
    db:
      condition: service_healthy
```

`pg_isready` ships in the Postgres image and succeeds when the server accepts connections. The `-h 127.0.0.1` matters. While a new database is initialising, the image runs a temporary server that listens only on a local socket, and plain `pg_isready` would report it ready too early. Checking over TCP waits for the real one.

The demo starts the working file with the same slow database:

```text
Both services healthy after 16 s
PASS: app waited for the database, then saved and read a note through http://localhost:14008/notes
```

`depends_on` with a healthcheck only orders **startup**. If the database restarts later, the app has to cope on its own. Spring Boot's connection pool (HikariCP) reconnects automatically for later requests.

### Data across `down` and `down -v`

```text
PASS: after down + up, the note is still there
PASS: after down -v + up, the database starts empty
```

`down` removes the containers and the network but keeps the named volume. `down -v` deletes the volume as well (topic 07). Use `down -v` when you *want* a clean database, never by habit.

---

## When to use it / when not to

**Use Compose for:**

- **Local development:** start the app's dependencies (Postgres, Redis in module 15, Kafka in module 16) with one command, identical for every developer.
- **Demos and tests on one machine:** this module's demos, a manual end-to-end check.
- **Small single-server deployments**, if you accept that one machine is a single point of failure (module 18).

**Not for:** running across several machines, restarting failed containers on another host, or rolling updates without downtime. That's an orchestrator's job (Kubernetes, topic 12). For tests that need a real database, prefer Testcontainers (topic 11), which starts containers from inside the test, so `mvn test` needs nothing started beforehand.

Spring Boot also has `spring-boot-docker-compose`, a dependency that runs `docker compose up` for you when the app starts in development and wires the connection details automatically. It's convenient, but learn the plain file first. It's what that support reads.

---

## Common pitfalls

- **`localhost` in a URL between containers.** It points at the container itself. Use the service name (`db`).
- **Short-form `depends_on`.** Only waits for the container to start. Add a `healthcheck` and `condition: service_healthy`.
- **`docker compose down -v` out of habit.** It deletes your database volume. Use plain `down` unless you want the data gone.
- **Forgetting to rebuild.** `docker compose up` reuses the existing `app` image, so your code change doesn't appear. Use `docker compose up -d --build`.
- **Real secrets in `compose.yaml`.** The file is committed to Git. Use `${VARIABLE}` with a git-ignored `.env` file (topic 05).

---

## Code examples

`CODE_EXAMPLES/08-docker-compose/compose-demo/`. Run with `./demo.sh` (Docker must be running; the first run builds the image, about 1 minute). Read in this order:

1. `src/main/java/com/example/compose/` — a tiny app: `Note` entity, `NoteRepository`, `NoteController` (`GET`/`POST /notes`).
2. `src/main/resources/application.yml` — no database address; it comes from the environment.
3. `Dockerfile` — the multi-stage build from topic 03.
4. `compose.yaml` — the working setup.
5. `compose.broken-localhost.yaml` — `localhost` in the JDBC URL.
6. `compose.broken-no-healthcheck.yaml` + `slow-init.sql` — short `depends_on` with a slow database.
7. `compose.slow-db.yaml` — the same slow database added to the working setup, which still waits correctly.
8. `demo.sh` — runs all of it and checks each outcome.

---

## Try this yourself

1. Run `docker compose up -d --wait`, then `docker compose exec db psql -U notes -d notes -c 'select * from note;'` after adding a note with `curl`. You're running `psql` inside the database container.
2. Add a third service, `adminer` (image `adminer`, port `14082:8080`), to `compose.yaml`. Open it in your browser and log in with server `db`. Why does `db` work as the server name here but not from your browser's address bar?
3. Create a `.env` file containing `DB_PASSWORD=something-else`, run `docker compose down -v` and `up` again, and check with `docker compose exec app env` which password the app received. Then add `.env` to `.gitignore`.

## Self-check

1. The app's JDBC URL is `jdbc:postgresql://localhost:5432/notes` and it works from your IDE but fails in Compose. Why, and what's the fix?
2. What exactly does short-form `depends_on: [db]` wait for, and why did this demo need a deliberately slow database to make the problem show?
3. What's the difference between `docker compose down` and `docker compose down -v`, and when would you deliberately use each?
