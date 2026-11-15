# Setup

One-time setup to run the code examples in this repository.

## Required

| Tool | Version | Why |
|------|---------|-----|
| JDK | 21 (LTS) | Java version targeted by all examples. Java 17 also works for most. |
| IntelliJ IDEA Community | Latest | Recommended editor. Eclipse and VS Code work too. |
| Git | Any recent | To clone the repo. |

Needed later — install when you reach the module, not now:

| Tool | From module | Why | How |
|------|-------------|-----|-----|
| Maven | 05 | Builds every example from module 05 on. | Module 05, topic 02 walks through installing it. |
| Docker | 14 | Runs databases, Redis, Kafka and your own app in containers. | [Installing Docker](#installing-docker-module-14-onwards) below. |

Verify Java:

```bash
java --version
# should print: openjdk 21.x.x  (or similar)
```

If `java` isn't found, install JDK 21 from https://adoptium.net/ (free, open-source).

## Running modules 01, 02, 03

These modules use only the standard library. No setup beyond the JDK.

To run any example:

```bash
# from the repo root
java 01-java-fundamentals/CODE_EXAMPLES/01-java-execution-flow/HelloWorld.java
```

`java <file>.java` directly compiles and runs a single source file (Java 11+ feature, no `javac` step needed).

## Running module 04 (JDBC)

Module 04 needs the H2 in-memory database driver. One-time setup:

1. Create a `lib/` folder inside `04-jdbc-and-database/CODE_EXAMPLES/`:

   ```bash
   mkdir -p 04-jdbc-and-database/CODE_EXAMPLES/lib
   ```

2. Download H2 2.2.224:

   - Direct: https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar
   - Save it to `04-jdbc-and-database/CODE_EXAMPLES/lib/h2-2.2.224.jar`

3. Verify:

   ```bash
   ls 04-jdbc-and-database/CODE_EXAMPLES/lib/
   # should show: h2-2.2.224.jar
   ```

### Additional jars for topic 17 onwards (HikariCP)

Topic 17 (connection pooling) and topic 18 (best practices) use **HikariCP**, the standard JDBC connection pool used by Spring Boot. HikariCP needs the SLF4J logging API at runtime. Download these to the same `lib/` folder:

- HikariCP: https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar
- slf4j-api: https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar
- slf4j-simple (optional, for log output): https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.13/slf4j-simple-2.0.13.jar

After downloading, your `lib/` folder should contain four jars:

```
h2-2.2.224.jar
HikariCP-5.1.0.jar
slf4j-api-2.0.13.jar
slf4j-simple-2.0.13.jar
```

Topics 05–16 work with just `h2-2.2.224.jar`. You can download the others when you reach topic 17.

To run any JDBC example from the repo root:

**macOS / Linux (topics 05-16, single jar):**
```bash
java --class-path "04-jdbc-and-database/CODE_EXAMPLES/lib/h2-2.2.224.jar" \
  04-jdbc-and-database/CODE_EXAMPLES/13-jdbc-connection-and-basic-crud/ConnectAndPing.java
```

**macOS / Linux (topics 17+, all jars):**
```bash
java --class-path "04-jdbc-and-database/CODE_EXAMPLES/lib/*" \
  04-jdbc-and-database/CODE_EXAMPLES/17-jdbc-connection-pooling/HikariCpBasic.java
```

**Windows (PowerShell) — note the semicolon separator instead of colon:**
```powershell
java --class-path "04-jdbc-and-database\CODE_EXAMPLES\lib\*" `
  04-jdbc-and-database\CODE_EXAMPLES\13-jdbc-connection-and-basic-crud\ConnectAndPing.java
```

### Easier: run from IntelliJ

1. **File → Open** → select the repo root folder.
2. IntelliJ will detect it as a plain folder. Mark `04-jdbc-and-database/CODE_EXAMPLES` as a "Sources Root" (right-click → Mark Directory as → Sources Root).
3. **File → Project Structure → Libraries → +** → Java → pick `04-jdbc-and-database/CODE_EXAMPLES/lib/h2-2.2.224.jar`.
4. Open any example, click the green ▶ next to `main`.

### "Why is there a `lib/h2-...jar`? Don't real projects use Maven?"

Yes, they do. Module 05 introduces Maven and Gradle properly. We use a single downloaded jar here so you can focus on **JDBC concepts** without learning a build tool at the same time. By module 05 you'll see exactly what Maven does and never download a jar manually again.

## Verifying your module 04 setup

After installing the H2 jar, try this from the repo root:

```bash
java --class-path "04-jdbc-and-database/CODE_EXAMPLES/lib/h2-2.2.224.jar" \
  04-jdbc-and-database/CODE_EXAMPLES/13-jdbc-connection-and-basic-crud/ConnectAndPing.java
```

Expected output:

```
Connected to H2.
SELECT 1 returned: 1
Connection closed.
```

If you see that, you're ready. If not, see Troubleshooting below.

## Installing Docker (module 14 onwards)

You don't need Docker until module 14. Skip this section until then.

### What Docker is, in one paragraph

A **container** is a program packaged together with everything it needs to run — the right Java version, libraries, config — so it runs the same way on every machine. **Docker** is the tool that builds and runs containers. From module 14 on, you'll use it to start a real PostgreSQL, Redis, or Kafka with one command instead of installing each one on your computer, and to package your own Spring Boot app the way companies ship it to production.

Containers are a Linux feature. On Linux, Docker runs directly. On Windows and macOS, Docker runs a small hidden Linux virtual machine and your containers live inside it — the installers below set that up for you.

### What you'll install

There are two parts, and you need both working:

- **Docker client** — the `docker` command you type.
- **Docker Engine (the "daemon" or "server")** — the background program that actually runs containers.

On Windows and macOS, **Docker Desktop** installs both. On Linux, you install **Docker Engine** directly.

> **Licence check (work laptops).** Docker Desktop is free for personal use, education, open source, and companies with fewer than 250 employees *and* under $10 million annual revenue. Larger companies need a paid subscription. If you're on a work laptop, ask your IT team before installing. Docker Engine on Linux, and Colima on macOS (below), are free for everyone.

### Windows

Requirements: 64-bit Windows 10 or 11 with the latest updates installed, and hardware virtualization turned on (it is on most laptops; if not, it's a BIOS/UEFI setting usually called "Intel VT-x" or "AMD-V").

1. **Install WSL 2** (Windows Subsystem for Linux — the Linux layer Docker uses). Open **PowerShell as Administrator** and run:
   ```powershell
   wsl --install
   ```
   Restart your computer when it asks.
2. **Install Docker Desktop.** Either download the installer from <https://www.docker.com/products/docker-desktop/> and run it, or in PowerShell:
   ```powershell
   winget install Docker.DockerDesktop
   ```
   Keep the option **"Use WSL 2 instead of Hyper-V"** ticked.
3. **Start Docker Desktop** from the Start menu. Accept the terms. Wait until the whale icon in the system tray stops animating and the app says **Engine running**.
4. **Open a new terminal** (PowerShell, or the terminal inside IntelliJ) and go to [Verify Docker works](#verify-docker-works).

### macOS

Pick the installer for your chip: Apple menu → **About This Mac** → "Chip: Apple M…" means **Apple silicon**; "Processor: Intel…" means **Intel**.

**Option A — Docker Desktop (easiest):**

1. Download Docker Desktop for your chip from <https://www.docker.com/products/docker-desktop/>, open the `.dmg`, and drag **Docker** into **Applications**. Or, with Homebrew:
   ```bash
   brew install --cask docker-desktop
   ```
2. Open **Docker** from Applications. Approve the permissions it asks for. Wait until the whale icon in the menu bar stops animating.
3. Open a new terminal and go to [Verify Docker works](#verify-docker-works).

**Option B — Colima (free for everyone, no GUI):**

```bash
brew install colima docker
colima start --cpu 4 --memory 6
```

Colima has to be started again after every reboot (`colima start`). Testcontainers (module 14, topic 11) also needs two environment variables with Colima — add these to `~/.zshrc`, then open a new terminal:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

### Linux (Ubuntu / Debian)

On Linux you install **Docker Engine**, not Docker Desktop.

1. **Install** with Docker's official script:
   ```bash
   curl -fsSL https://get.docker.com -o get-docker.sh
   sudo sh get-docker.sh
   ```
   (Fedora, Arch and others: follow your distribution's page at <https://docs.docker.com/engine/install/>.)
2. **Start Docker now and on every boot:**
   ```bash
   sudo systemctl enable --now docker
   ```
3. **Let your user run `docker` without `sudo`:**
   ```bash
   sudo usermod -aG docker $USER
   ```
   **Log out and log back in** (or reboot) — the change doesn't apply to terminals that are already open.

   Be aware: being in the `docker` group is effectively root access on that machine, because a container can mount any folder on the host. Fine on your own laptop; think twice on a shared server.

### Verify Docker works

In a **new** terminal, on any operating system:

```bash
docker version
```

You must see **both** a `Client:` section and a `Server:` section. If you only see `Client:` followed by an error, the engine isn't running — see Troubleshooting below.

Then run your first container:

```bash
docker run --rm hello-world
```

Expected output (the first time, Docker downloads the image first):

```
Unable to find image 'hello-world:latest' locally
latest: Pulling from library/hello-world
...
Hello from Docker!
This message shows that your installation appears to be working correctly.
```

What just happened: Docker downloaded a tiny image from Docker Hub, started a container from it, the container printed that message and exited, and `--rm` deleted the stopped container. That is the whole lifecycle module 14 builds on.

### Give Docker enough memory

Later modules run several containers at once (an app + PostgreSQL + Kafka). Docker Desktop: **Settings → Resources** → set **Memory to at least 4 GB** (6 GB is comfortable) and **CPUs to at least 2**. Colima: the `--cpu 4 --memory 6` flags above. Linux uses your machine's memory directly — nothing to set.

Check what Docker has:

```bash
docker info --format 'CPUs: {{.NCPU}}  Memory: {{.MemTotal}} bytes'
```

## Troubleshooting

**`Error: Could not find or load main class`**
You're probably using the old `java ClassName` form. With single-file launch you need the `.java` extension: `java HelloWorld.java`.

**`error: cannot find symbol: class Connection`**
The H2 jar isn't on the classpath. Double-check the path in `--class-path` matches where you saved the jar.

**`UnsupportedClassVersionError`**
Your JDK is older than the bytecode in the file. Install JDK 21.

**Windows path issues**
Use double quotes around the `--class-path` value. If your repo lives at a path with spaces (e.g. `C:\Personal Project\...`), the quotes are mandatory.

### Docker

**`Cannot connect to the Docker daemon` / `failed to connect to the docker API`**
The `docker` command is installed but the engine isn't running. Windows/macOS: open Docker Desktop and wait for "Engine running" (or run `colima start`). Linux: `sudo systemctl start docker`.

**`permission denied while trying to connect to the Docker daemon socket`** (Linux)
Your user isn't in the `docker` group yet, or you haven't logged out and back in since adding it. Run `groups` — `docker` should be in the list.

**`docker: command not found`**
Close and reopen the terminal (the installer updates `PATH` only for new terminals). On macOS with Colima, check `brew install docker` ran.

**Windows: `WSL 2 installation is incomplete` or Docker Desktop won't start**
Run `wsl --update` in an Administrator PowerShell, restart, and check virtualization is enabled (Task Manager → Performance → CPU → "Virtualization: Enabled").

**`no matching manifest for linux/arm64`** (Apple silicon)
The image was only built for Intel. Add `--platform linux/amd64` to `docker run`; it runs under emulation, slower but works.

**Containers get killed, or Kafka/PostgreSQL exit on start with no clear error**
Usually Docker is out of memory. Raise it in **Settings → Resources** (see "Give Docker enough memory").
