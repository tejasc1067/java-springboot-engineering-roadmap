# layers-demo

Topic 14-02: images, containers, and layers. Run:

```bash
./demo.sh
```

What each check proves:

1. `docker history` lists one layer per Dockerfile instruction, newest on top.
2. Two containers from the same image each get their own writable layer: a file written in one
   (`docker diff` shows `A /app/note.txt`) is invisible in the other.
3. Removing a container throws its writable layer away. A new container from the same image starts clean.
4. Two images built `FROM eclipse-temurin:21-jre` share its base layers byte for byte, so they're stored once.
5. Build cache and instruction order: after editing one small file, `Dockerfile.slow-order` (`COPY . .` first)
   re-runs its 5-second "install dependencies" step; `Dockerfile.fast-order` (copy the dependency list first,
   code last) takes it from cache.

The demo builds from a temporary copy of `context/`, so editing `greeting.txt` in step 5 doesn't change the repo.
Uses `eclipse-temurin:21-jre` (downloaded once). About 20 seconds; the script removes its containers and images.
