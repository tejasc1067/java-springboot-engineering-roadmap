# 02 — Maven setup and project layout

This topic gets Maven installed on your machine, then walks through the **standard Maven project layout** so the rest of the module has something concrete to refer to. We also build the smallest possible Maven project — `hello-maven` — and run it.

---

## The problem this solves

Maven (and most build tools) work by **convention over configuration**: if your code lives where Maven expects, you write almost nothing in `pom.xml`. If your code lives somewhere else, you write a lot of `pom.xml`. So step one is learning where Maven expects things.

---

## Installing Maven

You only need to do this once per machine.

### Check first

You may already have Maven through IntelliJ or another tool. Run:

```bash
mvn -v
```

If you see something like `Apache Maven 3.9.x`, skip to "Standard Maven project layout" below.

### Install (Windows)

Easiest path: **download the binary zip** from <https://maven.apache.org/download.cgi> (pick `apache-maven-3.9.x-bin.zip`). Unzip it somewhere like `C:\Program Files\apache-maven-3.9.9\`. Add `C:\Program Files\apache-maven-3.9.9\bin` to your `PATH` environment variable. Open a new terminal. Run `mvn -v`.

Alternative: `winget install Apache.Maven` or `choco install maven`. Both work; both also set the PATH for you.

### Install (macOS)

```bash
brew install maven
```

### Install (Linux)

```bash
sudo apt install maven       # Debian / Ubuntu
sudo dnf install maven       # Fedora
```

### Verify

```bash
mvn -v
```

Expected (your numbers will differ):

```text
Apache Maven 3.9.9
Maven home: C:\Program Files\apache-maven-3.9.9
Java version: 21.0.5, vendor: Eclipse Adoptium
Default locale: en_US, platform encoding: UTF-8
```

If Java version is < 17, install a newer JDK (see repo `SETUP.md`). Maven itself can run on Java 8+, but Spring Boot 3 needs Java 17+ and we target 21 in this module.

---

## Standard Maven project layout

Every Maven project looks like this:

```text
hello-maven/
├── pom.xml                                <- the build description
└── src/
    ├── main/
    │   ├── java/                          <- production .java files
    │   │   └── com/example/App.java
    │   └── resources/                     <- non-code files bundled into the jar
    │       └── application.properties
    └── test/
        ├── java/                          <- test .java files
        │   └── com/example/AppTest.java
        └── resources/                     <- test-only resources
            └── test-data.json
```

After you build, Maven creates one more directory:

```text
hello-maven/
├── target/                                <- generated; .gitignore'd
│   ├── classes/                           <- compiled main code
│   ├── test-classes/                      <- compiled test code
│   ├── hello-maven-1.0.0.jar              <- packaged result
│   └── surefire-reports/                  <- test output
```

Two things to internalize:

1. **`src/main/java/` and `src/test/java/` are not configurable lightly.** You *can* override them in `pom.xml`, but you almost never should. Every Maven developer alive expects code at those paths.
2. **`target/` is generated.** It's in `.gitignore`. Delete it any time with `mvn clean`.

---

## The smallest pom.xml

Here is the smallest `pom.xml` that is a valid Maven build. The example in `CODE_EXAMPLES/02-maven-setup-and-layout/hello-maven/` uses this exact file.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>hello-maven</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

</project>
```

There are no dependencies, no plugins explicitly declared. Maven picks defaults for everything. Topic 03 dissects each of those tags. For now, treat this file as ceremony — it says "this is a Java 21 project named `hello-maven` version 1.0.0."

---

## How it works

Walk through what happens when you run `mvn package` in `hello-maven/`:

1. Maven reads `pom.xml`. Sees `<packaging>jar</packaging>`.
2. Maven looks at `src/main/java/`. Finds `com/example/App.java`.
3. Maven runs `javac` with `--release 21` (because `maven.compiler.source/target = 21`), outputting `.class` files to `target/classes/`.
4. Maven looks at `src/test/java/`. Empty in this project, so test compile is a no-op.
5. Maven packages `target/classes/` into `target/hello-maven-1.0.0.jar`.
6. Done.

You can run the jar manually:

```bash
java -cp target/hello-maven-1.0.0.jar com.example.App
```

You can also let Maven run the class with the `exec-maven-plugin` once configured — we'll see that pattern later.

---

## Code example

The `CODE_EXAMPLES/02-maven-setup-and-layout/hello-maven/` directory contains:

```text
hello-maven/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── App.java
```

`App.java` prints one line. The lesson is the **shape** of the project, not the code.

### To run it yourself

```bash
cd 05-maven-and-gradle/CODE_EXAMPLES/02-maven-setup-and-layout/hello-maven
mvn -q package
java -cp target/hello-maven-1.0.0.jar com.example.App
```

Expected output:

```text
hello from a real Maven project
```

> `mvn -q` is quiet mode; without it Maven prints ~40 lines of `[INFO]` chatter that's useful only when something fails.

---

## When the layout is wrong

Common ways beginners break the layout, and what happens:

| What you did                                          | What Maven does                                                |
|-------------------------------------------------------|----------------------------------------------------------------|
| Put `App.java` directly under `src/`                  | Build succeeds but produces nothing. App.java is invisible.    |
| Put `App.java` under `src/main/` (skipped `java/`)    | Same — invisible.                                              |
| No `package com.example;` declaration in `App.java`   | Compiles, but jar contains `App.class` at root, not under `com/example/`. Running `java -cp ... App` works but `com.example.App` does not. |
| `src/main/java/com/example/` exists but file is named `app.java` (lowercase) on Linux | Compile fails: filename must match the public class name `App`. |

Most "Maven isn't building my code" issues from beginners are a layout problem, not a Maven problem.

---

## Common pitfalls

- **Wrong Java version in `pom.xml` vs. installed JDK.** If `pom.xml` says `<maven.compiler.source>21</maven.compiler.source>` and your JDK is Java 17, Maven errors clearly: `release version 21 not supported`. Match the version to your installed JDK or install a newer one.
- **Forgetting `<modelVersion>4.0.0</modelVersion>`.** Maven errors with a `non-readable POM` message. The 4.0.0 is the POM schema version, not your project version. Every POM has it.
- **Editing files in `target/` by hand.** They get deleted on the next `mvn clean`. Whatever you change there is lost. Generated output goes in `target/`; sources live under `src/`.

---

## Try this yourself

1. In `hello-maven/`, run `mvn clean`. Look in `target/` — it's gone. Run `mvn package`. It's back. Internalize: `target/` is disposable.
2. Add a second file `src/main/java/com/example/Goodbye.java` with a `main` method. Re-run `mvn -q package`. Inspect the jar with `jar tf target/hello-maven-1.0.0.jar` — both classes are in it.
3. Open `target/classes/com/example/App.class` in a binary viewer. Notice it's not text. This is the JVM-readable form of your code; the jar is just a zip of these.

## Self-check

1. Where exactly does Maven expect your production `.java` files to live? (Path from the project root.)
2. What does `<packaging>jar</packaging>` tell Maven to do?
3. If `mvn package` succeeds but `target/hello-maven-1.0.0.jar` is empty, what's the most likely cause?
