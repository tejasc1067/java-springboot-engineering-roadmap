# 01 — Why build tools exist

In module 04 you downloaded one jar (`h2-2.2.224.jar`), put it in `lib/`, and ran your Java files with `java --class-path "lib/h2-2.2.224.jar" Hello.java`. That worked. This topic explains why that approach stops working the moment your project grows past one jar — and what build tools do instead.

---

## The problem this solves

Imagine you want to build a simple JSON-reading service in plain Java. You need:

- Jackson (for parsing JSON)
- SLF4J + Logback (for logging)
- HikariCP (for the connection pool)
- The H2 driver

That's already 4 direct dependencies. Now look at what those 4 actually pull in:

```text
jackson-databind         needs jackson-core and jackson-annotations
logback-classic          needs logback-core and slf4j-api
HikariCP                 needs slf4j-api (but a different version than logback wants)
H2                       standalone
```

You now need to:

1. Find every jar — including the indirect ones — on the internet.
2. Pick a version of each.
3. Check that the versions are compatible. (Jackson 2.16 + Jackson 2.13 in the same classpath = silent breakage at runtime.)
4. Put every jar on the classpath, every time.
5. Update them all when one has a security bug.
6. Do it the same way on a teammate's laptop and on CI.

This is the problem. With one jar (`h2.jar` in module 04) you can shrug. With twelve jars and indirect dependencies, you can't.

### What a build tool gives you

A build tool is a program that, given a small file describing what your project needs, will:

- **Download** the right jars (and the jars they depend on) from a central repository.
- **Resolve** versions when two libraries disagree.
- **Compile** your code with those jars on the classpath.
- **Run** your tests.
- **Package** the result into a distributable artifact (a jar, a war, a fat-jar).
- **Reproduce** the same build on any machine — your laptop, your teammate's, CI — with one command.

For Java the two build tools you'll meet are **Maven** and **Gradle**. This module teaches Maven first (it dominates Spring Boot), then a single topic on Gradle for when you switch projects.

---

## A concrete picture: the same project, both ways

### Without a build tool (the module 04 style)

```text
my-project/
├── lib/
│   ├── h2-2.2.224.jar
│   ├── jackson-databind-2.16.1.jar
│   ├── jackson-core-2.16.1.jar
│   ├── jackson-annotations-2.16.1.jar
│   ├── logback-classic-1.4.14.jar
│   ├── logback-core-1.4.14.jar
│   ├── slf4j-api-2.0.9.jar
│   └── HikariCP-5.1.0.jar
└── src/
    └── App.java
```

Running it:

```bash
javac -cp "lib/*" -d out src/App.java
java -cp "out;lib/*" App
```

Sharing it: zip the whole thing (8 jars, several MB) and send to a teammate. They need to trust your jar versions.

Updating Jackson: download three new jars by hand, delete the old ones, hope you got the right ones.

### With Maven (what this module teaches)

```text
my-project/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── App.java
```

That's it. The `pom.xml` lists what you need:

```xml
<dependencies>
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.16.1</version>
  </dependency>
  <dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
  </dependency>
  <!-- ...etc -->
</dependencies>
```

Running it:

```bash
mvn package
java -jar target/my-project-1.0.jar
```

Sharing it: commit `pom.xml` and `src/`. Your teammate runs `mvn package` and gets the same result on their machine. They don't trust your jars; they trust Maven Central.

Updating Jackson: change `2.16.1` to `2.17.0` in one place.

The 6-line `pom.xml` does the work that 8 manual jar downloads did before — plus version resolution, plus reproducibility.

---

## What "managing dependencies" actually means

People often hand-wave "the build tool manages dependencies." Concretely, here's what that means:

1. **Discovery.** Your `pom.xml` says you need `jackson-databind:2.16.1`. The build tool goes to Maven Central, finds that artifact, downloads it.
2. **Transitive resolution.** Jackson's own POM (published alongside the jar) says it needs `jackson-core:2.16.1` and `jackson-annotations:2.16.1`. The build tool reads that POM and downloads those too. You never typed those names.
3. **Conflict resolution.** Library A wants `slf4j-api:1.7.36`. Library B wants `slf4j-api:2.0.9`. Both end up on your classpath — but only one copy can win. Maven's rule: nearest-wins (closest to your project in the tree). Topic 09 covers this in detail.
4. **Caching.** Downloads go to `~/.m2/repository/` once. Next time you build, nothing downloads. Your teammate builds for the first time, theirs downloads. CI builds clean and downloads each time (or caches the repo).

---

## What about the other things a build tool does?

Dependency management gets the most attention because it's the most painful problem to solve by hand. But the build tool also runs the full build lifecycle:

- **Compile** your `.java` files into `.class` files. (`mvn compile`)
- **Run tests** with the test framework (JUnit, TestNG). (`mvn test`)
- **Package** your compiled classes into a jar. (`mvn package`)
- **Install** that jar into your local Maven cache so other local projects can depend on it. (`mvn install`)

You'll see these in topic 06. The idea is: one tool, one command per phase, every project on the team builds the same way.

---

## When you might *not* need a build tool

Build tools are not free. They add files (`pom.xml`, `target/`), a learning curve, and a small startup tax (Maven warming up takes a few seconds). For:

- A 50-line script that uses only the JDK -> `java MyFile.java` is faster.
- A one-off file with one jar (module 04 H2 examples) -> manual classpath is fine. That's why module 04 used it.

Past that — past one jar, past one file, past you alone — a build tool always wins. Real backend apps cross that threshold on day one.

---

## Common pitfalls

- **"I'll just commit the `lib/` folder."** People try this. It bloats the repo, hides version info in jar filenames, breaks when a CVE drops because nobody knows which version to update. Don't.
- **Confusing the build tool with the language.** Maven is not part of Java. The JDK ships with `java` and `javac`; Maven is a separate program. Switching from Maven to Gradle does not change your Java code.
- **Confusing the build tool with the IDE.** IntelliJ has a "Build" button. Under the hood, on a Maven project, it's running Maven. The IDE is the front-end; the build tool is the engine. Always make sure your project builds from the command line — if it doesn't, the IDE is hiding a problem.

---

## Try this yourself

1. Open a Spring Boot project on GitHub (any will do — `spring-projects/spring-petclinic` is a classic). Find its `pom.xml`. Count the `<dependency>` blocks. Note how few there are — and how many jars actually end up on the classpath when Maven resolves them.
2. Look in your home directory for `~/.m2/repository/` (or `%USERPROFILE%\.m2\repository\` on Windows). If you've ever opened a Maven project in IntelliJ, this folder exists and is full of cached jars. Each jar lives at a path that matches its coordinates: `com/h2database/h2/2.2.224/h2-2.2.224.jar`.
3. (No code change needed.) Re-run the module 04 H2 example and notice the `--class-path "lib/h2-2.2.224.jar"` argument. In topic 02, the same kind of project will run with no `lib/` directory at all.

## Self-check

1. Name three specific problems that a build tool solves that you cannot easily solve by hand.
2. What is a "transitive dependency" and who decides which version of it ends up on your classpath?
3. If your project has zero external dependencies and one Java file, do you need a build tool? Justify the answer.
