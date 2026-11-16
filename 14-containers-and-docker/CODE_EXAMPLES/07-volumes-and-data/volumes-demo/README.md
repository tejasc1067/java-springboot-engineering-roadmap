# volumes-demo

Where a container's data lives, and what removing the container does to it. No app, just `postgres:17`.

```
./demo.sh
```

Needs Docker running. First run pulls `postgres:17` (about 700 MB on disk, as `docker images` reports it). Takes about 10 seconds after that.

What each check proves:

0. A file written inside a container survives `stop`/`start` (same container) but not `rm` (new container, fresh writable layer).
1. Postgres with no volume given: a new container has no table. The old data isn't deleted. It's stranded in an **anonymous volume** (the image declares `VOLUME /var/lib/postgresql/data`) that nothing will mount again.
2. Postgres with a **named volume** (`-v roadmap14-07-data:/var/lib/postgresql/data`): the row survives removing the container.
3. A **bind mount** (`-v "$PWD/host-folder:/export"`): a file written inside the container appears in a folder on your machine.

Everything the script creates is removed when it exits.
