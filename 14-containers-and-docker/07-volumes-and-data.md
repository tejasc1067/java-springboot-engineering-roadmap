# Volumes and Data

A container is meant to be thrown away. You deploy a new version by removing the old container and starting a new one. That's fine for your app, which keeps nothing it can't rebuild, and a disaster for a database, unless its data lives somewhere that outlives the container. This topic shows where data goes by default, how to lose it, and the two ways to keep it: **volumes** and **bind mounts**.

---

## The problem this solves

You run Postgres in a container for local development, create tables, load test data, and work for a week. Then you upgrade the image, or run a cleanup command, or your compose file recreates the container. The database is empty.

Nothing crashed and nothing warned you. The data was tied to the container, and the container is gone.

---

## How it works

### The writable layer: data that dies with the container

An image is read-only (topic 02). When you start a container, Docker adds a thin **writable layer** on top, and every file the container creates or changes goes there. Step 0 of the demo shows the rule:

```text
PASS: stop/start keeps the file: it's the same container
PASS: remove + new container: the file is gone
```

- `docker stop` then `docker start` resumes the **same** container, writable layer included.
- `docker rm` deletes the container **and its writable layer**. A new container from the same image starts from the clean image again.

Every deploy, every `docker compose down`, and every Kubernetes pod restart (topic 12) creates a new container. So "data in the writable layer" means "data that's lost on the next deploy".

### A volume: storage that lives outside any container

A **volume** is a directory Docker manages on its own, separate from every container. You attach it at a path inside the container, and anything written to that path goes into the volume:

```bash
docker run -d --name db \
  -e POSTGRES_PASSWORD=secret \
  -v roadmap14-07-data:/var/lib/postgresql/data \
  postgres:17
```

`-v name:/path` means "mount the volume called `name` at `/path`". It's created the first time it's used. `/var/lib/postgresql/data` is where Postgres keeps its files.

Demo step 2 creates a table with one row, removes the container, and starts a **new** container with the same volume:

```text
PASS: named volume: new container still sees 1 row
Where Docker keeps it: /var/lib/docker/volumes/roadmap14-07-data/_data (inside Docker's VM on Mac/Windows)
```

The container was disposable, and the data wasn't. That is the whole idea.

Useful commands:

```bash
docker volume ls                       # list volumes
docker volume inspect roadmap14-07-data
docker volume rm roadmap14-07-data     # deletes the data for good
docker volume prune                    # deletes every volume no container is using - careful
```

### Broken: forgetting the volume (and the trap inside it)

Demo step 1 runs Postgres with **no** `-v`, adds a row, and replaces the container:

```text
PASS: no volume: new container has no 'customer' table
PASS: the old data still exists in orphaned volume 8478606fada2... - nothing will ever mount it again
```

The second line is the surprise. The `postgres` image's Dockerfile declares `VOLUME /var/lib/postgresql/data`, so when you don't give a volume, Docker quietly creates an **anonymous volume** with a random name. Your data was never in the writable layer. It's sitting in a volume called something like `8478606fada2…`. The new container gets a *new* anonymous volume, so it looks empty.

Two consequences:

- You can sometimes recover "lost" data by finding the old anonymous volume (`docker volume ls`, then mount it by its long id), as long as nobody ran `docker volume prune`.
- Over time these orphans pile up and fill your disk. `docker rm -v` removes a container's anonymous volumes along with it.

Don't rely on any of that. **Always name the volume** for anything you want to keep.

### Bind mounts: a folder from your machine

A **bind mount** maps a folder on your computer into the container:

```bash
docker run --rm -v "$PWD/host-folder:/export" postgres:17 \
  bash -c 'echo "written inside the container" > /export/report.txt'
```

If the part before the `:` is a path (it starts with `/` or `./`), Docker mounts that host folder instead of a volume. Demo step 3:

```text
PASS: bind mount: file written in the container is on the host: written inside the container
```

Both sides see the same files, live. Change a file on your machine and the container sees the change at once.

### Volume or bind mount?

| | Named volume | Bind mount |
|---|---|---|
| Where the data is | Managed by Docker (`/var/lib/docker/volumes/...`, inside Docker's VM on Mac and Windows) | A folder you choose on your machine |
| Use it for | Database files and anything the *container* owns | Files *you* edit or provide: config, init scripts, source code in development |
| Speed on Mac/Windows | Fast | Slower, because files cross from your machine into Docker's VM |
| Portable | Yes, the same `-v name:/path` works anywhere | Depends on that path existing on the host |
| Gotchas | Easy to forget it exists, until `prune` deletes it | File ownership and permissions between host and container; you can overwrite the image's own files |

Topic 08 uses both: a named volume for Postgres's data, and a read-only bind mount (`:ro` at the end) for an SQL script.

---

## When to use it / when not to

- **Databases, message brokers, anything stateful:** a named volume, always.
- **Your Spring Boot app:** no volume. Keep the app **stateless** (module 18). Uploaded files go to object storage or a database, logs go to stdout (module 17), and sessions go to Redis (module 15). Then any container can be replaced, and you can run several.
- **Production databases:** often not in a container you manage at all, but a managed database service. Volumes are perfect for development and testing. Running a database in production also means backups, upgrades, and replication, which a volume alone doesn't give you.

**A volume is not a backup.** `docker compose down -v`, `docker volume rm`, or `prune` deletes it as quickly as the container. Back up with the database's own tool (`pg_dump`).

---

## Common pitfalls

- **No volume for the database.** The data is gone on the next deploy, or stranded in an anonymous volume. Always `-v some-name:/var/lib/postgresql/data`.
- **`docker volume prune` or `docker compose down -v` "to clean up".** They delete your data volumes too. Read what a cleanup command removes before running it.
- **Mounting at the wrong path.** `-v data:/var/lib/postgres` (missing `ql/data`) is accepted happily, and the real data still goes to an anonymous volume. Check the image's documentation for its data directory. For `postgres:17` it's `/var/lib/postgresql/data`.
- **A bind mount hiding the image's files.** Mounting an empty host folder over `/app` makes the image's `/app/app.jar` invisible to the container. Mount only the specific path you mean to replace.

---

## Code examples

`CODE_EXAMPLES/07-volumes-and-data/volumes-demo/`. Run with `./demo.sh` (Docker must be running).

1. `demo.sh` step 0 — a file in the writable layer: kept by stop/start, lost on removal.
2. `demo.sh` step 1 — Postgres without a volume: a new container is empty, and the old data sits in an orphaned anonymous volume.
3. `demo.sh` step 2 — Postgres with a named volume: the row survives a new container.
4. `demo.sh` step 3 — a bind mount: a file written in the container appears on your machine.

---

## Try this yourself

1. Run step 2 by hand, then `docker volume inspect roadmap14-07-data`. Start a container with the volume mounted at `/data` (`docker run --rm -it -v roadmap14-07-data:/data postgres:17 ls /data`) and look at what Postgres stored.
2. Run Postgres without `-v`, insert a row, remove the container with plain `docker rm -f`, then find the orphaned volume with `docker volume ls`. Mount it into a new Postgres container by its id and check the row is back. Clean up with `docker volume rm <id>`.
3. Bind-mount a folder containing an `init.sql` file to `/docker-entrypoint-initdb.d` and start a fresh Postgres. What happens to the script on the second start, with the same named data volume?

## Self-check

1. You `docker stop` and `docker start` a container, and its files are still there. You `docker rm` it and start a new one from the same image, and they're gone. Why the difference?
2. You started Postgres without `-v`, removed the container, and the new one is empty. Where did the data actually go, and why isn't it really deleted yet?
3. You need a database's data to persist, and you want to edit an `application.yml` on your laptop and have a container see it. Which needs a named volume and which a bind mount, and why?
