# Images, Containers, and Layers

Topic 01 said an image is a read-only package and a container is a running instance of it. This topic opens both up. An image is a **stack of layers**, one per Dockerfile instruction. A container is that stack plus **one thin writable layer** of its own. Those two facts explain why a container's files vanish when you remove it, why images that share a base cost almost nothing extra on disk, and why the *order* of lines in a Dockerfile decides whether a rebuild takes one second or ten minutes.

---

## The problem this solves

Without this model, three things about Docker look like random behaviour:

- You fix something *inside* a running container, restart a fresh one, and the fix is gone.
- You change one line of code, rebuild, and Docker re-downloads every dependency again, every time.
- Ten images on your machine are each "500 MB", yet they use nowhere near 5 GB of disk.

All three follow from how layers work.

---

## How it works

### A Dockerfile is a list of steps; each step makes a layer

```dockerfile
FROM eclipse-temurin:21-jre          # start from someone else's image (its layers come with it)
WORKDIR /app                         # set the working directory
COPY deps.list .                     # add a file              -> new layer
RUN sleep 5 && echo "..." > deps-installed.txt   # run a command -> new layer (whatever it changed on disk)
COPY greeting.txt .                  # add another file        -> new layer
CMD ["cat", "greeting.txt"]          # metadata only: what a container runs on start
```

Each instruction that changes the file system (`COPY`, `ADD`, `RUN`) adds a **layer**: a record of the files that step added, changed, or deleted. The final image is the layers stacked on top of each other, and **every layer is read-only**. `docker history` shows the stack, newest on top:

```text
CREATED BY                                      SIZE
CMD ["cat" "greeting.txt"]                      0B
COPY greeting.txt . # buildkit                  12.3kB
RUN /bin/sh -c sleep 5 && echo "dependencies…   12.3kB
COPY deps.list . # buildkit                     12.3kB
```

(Below these come the base image's own layers: Ubuntu, then the Java runtime. On the Docker version this was written with, even a tiny layer shows as 12.3 kB; that's storage overhead, not the files' real size.)

### A container adds one writable layer on top

When you `docker run` an image, Docker puts a thin **writable layer** on top of the read-only stack. Every file the container creates or changes goes there. The image underneath is never modified.

```text
  container c1           container c2
  [ writable: note.txt ] [ writable: (empty) ]
            \               /
        [ COPY greeting.txt ]   <- read-only, shared
        [ RUN sleep 5 ...   ]
        [ COPY deps.list    ]
        [ eclipse-temurin:21-jre layers ]
```

The demo writes a file in container `c1`, then looks around:

```text
   docker diff c1: C /app
   docker diff c1: A /app/note.txt
PASS: c1's change is recorded in c1's own layer
PASS: c2 cannot see the file c1 wrote
PASS: a new container from the same image has no note.txt (data in a container is temporary - topic 07)
```

`docker diff` lists what the container changed compared with its image: `A` added, `C` changed, `D` deleted. Then:

- **Containers are isolated from each other.** `c2` started from the same image and can't see `c1`'s file.
- **Removing a container deletes its writable layer.** A new container from the same image starts clean.

So **never fix things inside a running container.** Change the Dockerfile, rebuild the image, and replace the container. Anything that must survive (a database's files, uploads) goes in a **volume** (topic 07), not the container's layer.

### Shared layers are stored once

Layers are identified by a hash of their content. Two images built `FROM eclipse-temurin:21-jre` start with the *same* six base layers, and Docker stores them once:

```text
PASS: both images start with the same 6 base layers, stored once
```

That's why ten images built on one base don't take ten times the disk space, and why pulling a new version of your app downloads only the layers that changed.

### The build cache, and why instruction order matters

When you rebuild, Docker walks the Dockerfile top to bottom. For each step it asks: *is this step and everything it depends on unchanged since last time?* If yes, it reuses the layer from cache (`CACHED` in the build output). **The first step that changed, and every step after it, runs again.** For `COPY`, "changed" means the copied files' *content* changed.

**Broken: copy everything first.**

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY . .                                        # any edit to any file invalidates this...
RUN sleep 5 && echo "dependencies installed" > deps-installed.txt   # ...so this slow step re-runs every time
CMD ["cat", "greeting.txt"]
```

**Fixed: copy what rarely changes first, and your code last.**

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY deps.list .                                # the dependency list: rarely changes
RUN sleep 5 && echo "dependencies installed" > deps-installed.txt   # cached unless deps.list changes
COPY greeting.txt .                             # your code: changes all the time
CMD ["cat", "greeting.txt"]
```

The `RUN sleep 5` stands in for "download all the dependencies" (`mvn dependency:go-offline`, `npm install`). The demo edits `greeting.txt` and rebuilds both:

```text
   slow-order rebuild: 5s   fast-order rebuild: 0s
PASS: the rebuild times match: only slow-order paid for the 5s step again
PASS: slow-order (COPY . . first): editing greeting.txt re-ran the slow dependency step
PASS: fast-order (dependency list first): the slow step came from cache
```

With real dependencies, that difference is minutes per build, for every developer, on every change. Topic 03 applies this to a Maven project, and topic 04 splits the Spring Boot jar itself into layers.

Writing the demo turned up a detail worth knowing: the cache is keyed on content, not on the time you saved a file. Changing a file to text you already built once before can be served from cache. That's why the demo writes a timestamp into its edit.

---

## Commands for looking inside

| Command | Shows |
|---|---|
| `docker images` | images on your machine, with tags and sizes |
| `docker history <image>` | the layers of an image and the instruction that made each |
| `docker ps` / `docker ps -a` | running containers / all containers, including stopped |
| `docker diff <container>` | files the container added (`A`), changed (`C`), deleted (`D`) |
| `docker exec -it <container> sh` | a shell inside a running container, for looking, not fixing |
| `docker rm -f <container>` | stop and remove a container (and its writable layer) |
| `docker image rm <image>` | remove an image (layers still used by other images stay) |

---

## Common pitfalls

- **Fixing a problem inside a running container.** The fix lives in the writable layer and is lost when the container is replaced. Fix the Dockerfile instead.
- **`COPY . .` before the slow steps.** Every code change re-runs dependency installation. Copy the dependency manifest first, install, then copy the code.
- **Storing data in the container's file system.** It disappears with the container. Use a volume (topic 07).
- **Deleting files in a later layer to "make the image smaller".** `RUN rm big-file` adds a layer that *hides* the file; the earlier layer still contains it and still ships. Don't add it in the first place, or use a multi-stage build (topic 03).

---

## Code examples

`CODE_EXAMPLES/02-images-containers-and-layers/layers-demo/`. Run `./demo.sh` (about 20 seconds). Read in this order:

1. `context/Dockerfile.fast-order` — one layer per instruction, with the slow step placed before the code.
2. `context/Dockerfile.slow-order` — the same steps, with `COPY . .` first.
3. `demo.sh` — layers with `docker history`; the writable layer with `docker diff`; isolation between containers; shared base layers; the rebuild-time comparison.

---

## Try this yourself

1. Start a container with `docker run -d --name play eclipse-temurin:21-jre sleep infinity`, then `docker exec play sh -c 'rm /usr/bin/whoami'` and `docker diff play`. What does `D` mean? Start a second container from the same image: is `whoami` there?
2. In `Dockerfile.fast-order`, edit `deps.list` instead of `greeting.txt` and rebuild. Which steps re-run, and why is that correct?
3. In `Dockerfile.slow-order`, add `RUN rm deps-installed.txt` at the end and compare `docker history` before and after. Did the image get smaller?

## Self-check

1. You create a file inside a running container, remove the container, and start a new one from the same image. Is the file there? Explain using the word *layer*.
2. Your Dockerfile has `COPY . .` on line 3 and `RUN mvn dependency:go-offline` on line 4. Why does every code change re-download dependencies, and how do you reorder it?
3. Two images are each listed at 500 MB and share a base image. Why don't they use 1 GB of disk?
4. What does `docker diff` show, and why is "fix it inside the container" the wrong habit?
