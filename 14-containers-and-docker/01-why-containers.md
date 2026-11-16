# Why Containers

Your Spring Boot app is a jar. To run, that jar needs a particular Java version, an operating system, maybe some native libraries and environment variables. All of that lives on whatever machine runs it, and it's never quite the same on two machines. A **container** packages the app *together with* the runtime it needs, so the thing you test is exactly the thing that runs in production.

This topic is the "why" and the vocabulary. It starts from zero: no Docker knowledge assumed.

---

## The problem this solves

You build a jar on your laptop with Java 21. It passes every test. You copy it to the test server and it crashes before printing a single line:

```text
java.lang.UnsupportedClassVersionError: Hello has been compiled by a more recent version of the Java Runtime
(class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 61.0
```

The server has Java 17. Your code is fine. The *environment* is different. That's the famous "works on my machine". Other versions of the same problem:

- The server's time zone, locale, or default character encoding differs, and dates or accented characters come out wrong.
- A colleague has PostgreSQL 15 locally and production runs 17.
- Someone patched the server by hand two years ago, and nobody knows exactly what's installed.
- Setting up a new developer's laptop takes a day of following an out-of-date wiki page.

The root cause in every case: **the app and its environment are shipped separately**, and only the app is under version control.

---

## How it works

### Image and container

Two words carry the whole topic:

- An **image** is a packaged, read-only bundle containing everything needed to run your app: a minimal Linux file system, a Java runtime, your jar, and the command to start it. You build it once, from a recipe file called a **Dockerfile**, and it never changes afterwards. Think of it as the class.
- A **container** is a *running* instance of an image: a normal process on the machine, but one that sees only the image's files and its own network and process space. You can start many containers from one image. Think of them as the objects.

**Docker** is the most common tool for building images and running containers. Others exist (Podman, containerd, and the tools inside Kubernetes), and they all use the same image format.

### The three commands you need first

```bash
docker build -t my-app .        # read ./Dockerfile, produce an image called "my-app"
docker run my-app               # start a container from that image
docker ps                       # list running containers
```

`docker run` does three things: it fetches the image if it isn't on your machine yet (a **pull**, from a **registry** such as Docker Hub), creates a container from it, and starts the image's command inside it.

### The fix, in four lines

The demo's `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jre                # start from an image that already has Linux + a Java 21 runtime
COPY out/ /app/                            # add our compiled classes
CMD ["java", "-cp", "/app", "Hello"]       # what to run when a container starts
```

`eclipse-temurin:21-jre` is the official Eclipse Temurin (OpenJDK) image: name `eclipse-temurin`, **tag** `21-jre`. The tag picks the version. `docker build` turns this into an image that contains Java 21 *and* the program. Wherever that image runs, it runs on Java 21, because the runtime is inside it.

The demo shows the whole story. `demo.sh` compiles `Hello.java` with JDK 21, runs it on a Java 17 "server" (it fails), then builds the image and runs that:

```text
== 2. Run the compiled class on a 'server' that has Java 17
	java.lang.UnsupportedClassVersionError: Hello has been compiled by a more recent version of the Java Runtime (class file version 65.0), ...
PASS: Java 17 refuses it: UnsupportedClassVersionError (works on my machine, not on the server)
== 3. Package program + Java 21 runtime into one image, then run the image
   Hello from Java 21.0.13 on Linux (aarch64)
   Square area: 9.0
PASS: the image runs it: the runtime travels with the program
PASS: inside the container the OS is Linux, whatever the host is
== 4. A container starts in about a second (no OS to boot)
PASS: container started, ran, and exited in under 1s
```

The host was a Mac (`Darwin arm64`), yet the program reports `Linux`. Inside the container, the program sees the image's world, not your laptop's.

---

## Containers vs virtual machines

You may have used a **virtual machine** (VM): VirtualBox, VMware, a cloud server. Both isolate an app, but in very different ways.

```text
          Virtual machines                          Containers
  +-----------+  +-----------+          +-----------+  +-----------+
  |   App A   |  |   App B   |          |   App A   |  |   App B   |
  | libs, JRE |  | libs, JRE |          | libs, JRE |  | libs, JRE |
  | Guest OS  |  | Guest OS  |          +-----------+  +-----------+
  | (kernel!) |  | (kernel!) |          |  container runtime (Docker) |
  +-----------+  +-----------+          +-----------------------------+
  |        hypervisor        |          |   ONE shared Linux kernel   |
  +--------------------------+          +-----------------------------+
  |        hardware          |          |          hardware           |
```

- A **VM** emulates a whole computer and boots a complete operating system, kernel included. It takes tens of seconds to start and uses gigabytes of memory before your app does anything.
- A **container** doesn't boot anything. It's an ordinary process that the Linux kernel *isolates*: it gets its own view of the file system, process list, and network. Every container on a machine shares that machine's one kernel. That's why the demo's container starts, runs, and exits in under a second.

The trade-off: VM isolation is stronger, because a separate kernel is a harder wall. Container isolation is lighter and faster, which is why it's the normal way to run services today. The two are often combined: cloud servers are VMs, and containers run inside them.

**On a Mac or Windows laptop**, there's no Linux kernel to share, so Docker Desktop quietly runs one small Linux VM and puts all your containers inside it. You can see it: on the Mac this topic was written on, every container reports the same kernel, `7.0.14-linuxkit`, which is Docker Desktop's VM. On a Linux server there's no VM; containers use the host's kernel directly.

---

## What containers do *not* solve

- **They don't make the code correct.** A bug in the jar is a bug in the image.
- **They don't keep data.** A container's files disappear when it's removed (topic 07 shows this and the fix).
- **They don't configure themselves.** Database URLs and passwords still have to come from somewhere, and *not* from inside the image (topic 05).
- **The CPU architecture still matters.** An image built only for Intel (`amd64`) can't run natively on an ARM Mac or ARM server (`arm64`), and vice versa. Official images like `eclipse-temurin` publish both, and Docker picks the right one.

---

## When to use it / when not to

| Use containers when | You can skip them when |
|---|---|
| The app runs anywhere other than your laptop: test, staging, production | You're learning Java and running one file with `java Hello.java` (modules 01–04) |
| Several people or machines must run *exactly* the same thing | A one-off script on your own machine |
| You need local copies of real dependencies (PostgreSQL, Redis, Kafka) for development and tests (topics 08 and 11) | |

For a backend service in 2026, containers are the default packaging. The rest of this module is how to do it well.

---

## Common pitfalls

- **"It's in a container, so it's the same everywhere."** Only if you run the *same image*. Rebuilding from a Dockerfile on another day can pull a newer base image (topic 03 covers pinning tags).
- **Thinking a container is a small VM.** It has no init system, no SSH, and no persistent disk by default. You don't log into it to fix things; you change the image and replace the container.
- **Ignoring architecture.** An image built on an ARM Mac without multi-platform settings may fail on an Intel server with `exec format error`.

---

## Code examples

`CODE_EXAMPLES/01-why-containers/hello-demo/`. Run `./demo.sh` (needs Docker running). Read in this order:

1. `Hello.java` — a tiny program compiled with Java 21 that prints the Java version and OS it runs on.
2. `Dockerfile` — four lines: base image with Java 21, copy the classes, set the start command.
3. `demo.sh` — compile, fail on Java 17, build the image, run it, time a container start.

---

## Try this yourself

1. In the `Dockerfile`, change `eclipse-temurin:21-jre` to `eclipse-temurin:17-jre` and run `./demo.sh`. Which check fails now, and why is that a *good* thing to find at build time rather than on the server?
2. Run `docker run --rm eclipse-temurin:21-jre java -version` and `docker run --rm eclipse-temurin:17-jre java -version`. Two Java versions, side by side, and neither is installed on your machine.
3. Run `docker run --rm -it eclipse-temurin:21-jre bash`, then `ls /`, `cat /etc/os-release`, and `ps aux`. What can the container see? Type `exit`. What happened to the container?

## Self-check

1. In one sentence each: what is an image, and what is a container? How are they related?
2. The same jar runs on your laptop and crashes on the server with `UnsupportedClassVersionError`. Explain the cause, and how an image prevents it.
3. Why does a container start in under a second when a VM takes much longer? What does each one share with the host?
4. You run Docker on a Mac. Is there a VM involved? Why?
