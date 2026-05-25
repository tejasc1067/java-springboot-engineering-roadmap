# 04 — Dependencies and Maven Central

This topic is about the day-to-day act of **adding a library** to your project. You'll learn where libraries come from (Maven Central), how Maven names them (coordinates), where downloaded jars actually live on your disk, and what to do when you need a library that's not on Central.

The accompanying code example (`deps-demo`) adds Jackson and SLF4J to a project and writes real JSON to the console.

---

## The problem this solves

You need to parse JSON in your service. The JDK doesn't have a JSON parser. So you reach for Jackson. Without a build tool, that meant downloading three jars by hand, putting them in `lib/`, and hoping. With Maven, you write four lines in `pom.xml` and run the build. Where do the jars come from? Maven Central, the public repository every Java developer relies on.

---

## Maven Central — what it is

**Maven Central** (<https://search.maven.org> or <https://central.sonatype.com>) is the default public repository for Java libraries. It contains millions of artifacts (jars + their POMs + sources + javadocs) published by library authors. Almost every open-source Java library you've heard of is there: Jackson, SLF4J, Spring Framework, Hibernate, Apache Commons, you name it.

When you add a `<dependency>` to your `pom.xml` and run a Maven command, the order is:

1. Maven checks `~/.m2/repository/` (your local cache).
2. If the jar isn't there, Maven downloads it from Maven Central.
3. The downloaded jar is cached in `~/.m2/repository/` forever.

So the first build of a fresh project on a new machine downloads things. Every build after that, on the same machine, downloads nothing.

### The path inside the cache

The cache is just a folder of files arranged by coordinates:

```text
~/.m2/repository/
└── com/
    └── fasterxml/
        └── jackson/
            └── core/
                └── jackson-databind/
                    └── 2.16.1/
                        ├── jackson-databind-2.16.1.jar
                        ├── jackson-databind-2.16.1.pom        <- Jackson's own POM, used to find its dependencies
                        └── _remote.repositories               <- bookkeeping
```

The pattern is `groupId / artifactId / version / artifactId-version.jar`. If you ever want to inspect a jar Maven downloaded, the path is fully predictable from the coordinates.

---

## Coordinates (GAV) again, this time for a library

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.16.1</version>
</dependency>
```

- **groupId**: who published it. `com.fasterxml.jackson.core` for Jackson.
- **artifactId**: which specific library. Jackson publishes many; `jackson-databind` is the data-binding one.
- **version**: which release. `2.16.1` is a specific point release.

There's an optional fourth coordinate, **classifier**, used for variants of the same artifact (`jdk8`, `sources`, `javadoc`). You won't write it often; you'll see it occasionally.

### Where to find coordinates for a library

When you want to use a new library, search for it at <https://central.sonatype.com> (the modern UI for Maven Central). The site shows a copy-pasteable `<dependency>` block for the latest version. Most libraries also paste it into their README.

You almost never type coordinates from scratch.

---

## How it works: from `pom.xml` to compiled code

Walk through what happens when you add Jackson and run `mvn compile`:

1. Maven reads `pom.xml`, sees the Jackson dependency.
2. Checks `~/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.16.1/`. If missing, downloads jar + POM from Maven Central.
3. Reads `jackson-databind-2.16.1.pom`. That POM says Jackson needs `jackson-core` and `jackson-annotations`, same version.
4. Downloads those too (recursively).
5. Builds the **compile classpath** = your `src/main/java` + every direct + transitive dependency at compile scope.
6. Runs `javac` with that classpath.

You wrote 4 lines. Maven put 3 jars on the classpath. You can import `com.fasterxml.jackson.databind.ObjectMapper` and the code compiles.

---

## What's on the classpath, actually?

After running `mvn compile` you can inspect:

```bash
mvn dependency:list
```

Output looks like (trimmed):

```text
[INFO]    com.fasterxml.jackson.core:jackson-annotations:jar:2.16.1:compile
[INFO]    com.fasterxml.jackson.core:jackson-core:jar:2.16.1:compile
[INFO]    com.fasterxml.jackson.core:jackson-databind:jar:2.16.1:compile
[INFO]    org.slf4j:slf4j-api:jar:2.0.13:compile
[INFO]    org.slf4j:slf4j-simple:jar:2.0.13:runtime
```

Five jars. You declared two (jackson-databind and slf4j-simple). The other three came in transitively.

Or get the dependency *tree* (showing who pulled in what):

```bash
mvn dependency:tree
```

```text
[INFO] com.example:deps-demo:jar:1.0.0
[INFO] +- com.fasterxml.jackson.core:jackson-databind:jar:2.16.1:compile
[INFO] |  +- com.fasterxml.jackson.core:jackson-annotations:jar:2.16.1:compile
[INFO] |  \- com.fasterxml.jackson.core:jackson-core:jar:2.16.1:compile
[INFO] \- org.slf4j:slf4j-simple:jar:2.0.13:runtime
[INFO]    \- org.slf4j:slf4j-api:jar:2.0.13:runtime
```

These two commands — `dependency:list` and `dependency:tree` — are your friends. Topic 09 uses them to resolve real conflicts.

---

## Version selection

```xml
<version>2.16.1</version>
```

A specific version. Maven downloads exactly this. You get reproducible builds.

```xml
<version>[2.16,3.0)</version>
```

A version range. Maven picks the highest available in the range. **Avoid this in real projects.** Ranges give "newer builds suddenly fail" surprises when a library publishes a breaking change inside the range. Spring Boot, every Apache project, and almost every reasonable team uses fixed versions and updates them deliberately.

```xml
<version>2.16.1-SNAPSHOT</version>
```

A SNAPSHOT version. Means "in-development." Maven re-checks the remote repository for newer SNAPSHOT builds (by default, daily) and downloads if so. You produce SNAPSHOTs from your own dev branches; you almost never depend on someone else's.

---

## When the library isn't on Maven Central

Most of the time it is. But occasionally you'll meet:

- **Internal company artifacts** — a private Maven repository (Sonatype Nexus, JFrog Artifactory). Configured in `~/.m2/settings.xml` or in the project's `<repositories>` block.
- **Spring milestones / snapshots** — Spring sometimes publishes pre-release builds to their own repo (`https://repo.spring.io/milestone`).
- **A vendor-specific jar** — Oracle JDBC driver historically wasn't on Maven Central; you had to manually install it (`mvn install:install-file`) or use a corporate repo.

To add an extra repository:

```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

The default — Maven Central — is always implied; you don't need to declare it.

---

## Code example: `deps-demo`

`CODE_EXAMPLES/04-dependencies-and-maven-central/deps-demo/` is a real Maven project that:

- Adds `jackson-databind` (compile scope).
- Adds `slf4j-simple` (runtime scope — picked up here as a preview; full scope coverage in topic 05).
- Has one Java file that serializes a `Map` to JSON and logs the result.

### `pom.xml` (the part that matters)

```xml
<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.16.1</version>
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <version>2.0.13</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### `JsonReadWrite.java`

```java
package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class JsonReadWrite {
    private static final Logger log = LoggerFactory.getLogger(JsonReadWrite.class);

    public static void main(String[] args) throws Exception {
        var mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(Map.of("user", "alice", "age", 30));
        log.info("serialized: {}", json);

        Map<?, ?> parsed = mapper.readValue(json, Map.class);
        log.info("parsed: {}", parsed);
    }
}
```

Two dependencies, ten lines of code, and you have a JSON-serializing-and-parsing program. Without Maven you'd be juggling 5 jars by hand.

### Run it

```bash
cd 05-maven-and-gradle/CODE_EXAMPLES/04-dependencies-and-maven-central/deps-demo
mvn -q package
java -cp target/deps-demo-1.0.0.jar;target/dependency/*  com.example.JsonReadWrite
```

(On macOS/Linux, replace `;` with `:` in the classpath.)

Even simpler — let Maven invoke it for us using `exec-maven-plugin` (already wired in the POM):

```bash
mvn -q compile exec:java
```

Expected output (the SLF4J timestamps will vary):

```text
[main] INFO com.example.JsonReadWrite - serialized: {"user":"alice","age":30}
[main] INFO com.example.JsonReadWrite - parsed: {user=alice, age=30}
```

---

## Common pitfalls

- **Typo in `groupId` or `artifactId`.** Maven errors with `Could not find artifact ... in central`. Copy-paste from the Central search page; don't retype.
- **Forgetting to refresh the IDE after editing `pom.xml`.** IntelliJ usually picks up changes automatically; if it doesn't, right-click `pom.xml` -> Maven -> Reload Project.
- **"It builds locally but fails in CI."** Usually means you have an artifact in your local `~/.m2/cache` that isn't on a public repo. Run `mvn -U` (force update) and on a clean directory to reproduce CI's view.
- **Adding `compile`-scope dependencies that should be `runtime`.** A JDBC driver, for example, is loaded reflectively at runtime — your code never imports `org.h2.Driver`. Use `<scope>runtime</scope>`. Topic 05.

---

## Try this yourself

1. Add a third dependency to `deps-demo`: `org.apache.commons:commons-lang3:3.14.0`. Use one method from it (e.g. `StringUtils.reverse("hello")`). Re-run. The new jar should download once, then stay cached.
2. Run `mvn dependency:tree`. Identify which dependencies are direct and which are transitive.
3. Open `~/.m2/repository/com/fasterxml/jackson/` in your file explorer. Confirm the directory structure matches what this topic described.

## Self-check

1. What three values are required to identify any Maven dependency, and where does each piece live in the cache path?
2. What's the difference between adding a `<repository>` to your POM and the default Maven Central?
3. If `mvn package` works on your laptop but fails on a teammate's laptop with `Could not find artifact com.acme:secret-sauce:1.0`, what is the most likely cause?
