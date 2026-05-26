# wrapped-project

Demonstrates the Maven wrapper. You can build and run this project without having Maven installed — the wrapper downloads it for you on first run.

## Files

```text
wrapped-project/
├── mvnw                                  <- POSIX shell launcher (bash/zsh, also Git Bash on Windows)
├── mvnw.cmd                              <- Windows polyglot (batch + PowerShell) launcher
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties      <- pins Maven 3.9.9
├── pom.xml
└── src/main/java/com/example/Hello.java
```

No `maven-wrapper.jar` is committed; the wrapper downloads it lazily on first run (matches Spring Initializr 2026 output).

## Run it

POSIX (macOS / Linux / Git Bash on Windows):

```bash
./mvnw -q package
java -cp target/wrapped-project-1.0.0.jar com.example.Hello
```

Windows CMD or PowerShell:

```cmd
mvnw.cmd -q package
java -cp target/wrapped-project-1.0.0.jar com.example.Hello
```

Expected output:

```text
hello from a wrapped project
```

First run downloads Apache Maven 3.9.9 to `~/.m2/wrapper/dists/`. Subsequent runs use the cached install.

## Where Maven actually lives after the download

```bash
./mvnw -v
```

The `Maven home` line will point inside your home directory's `.m2/wrapper/dists/`, not a system-wide path. Even if you have Maven installed elsewhere on PATH, the wrapper uses the version pinned in `.mvn/wrapper/maven-wrapper.properties`.

## Change the pinned Maven version

Edit `.mvn/wrapper/maven-wrapper.properties`, change `distributionUrl`, run `./mvnw -v` again. New version downloads and is cached. Commit the change so teammates pick it up.

## The point

The wrapper makes "what Maven version" a per-project decision, committed alongside the code. New machines need only a JDK — no separate Maven install.
