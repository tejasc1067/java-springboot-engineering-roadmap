# 10 — Multi-module projects

Big Java codebases split themselves into modules: `core`, `web`, `cli`, `db`. Each module is its own jar with its own `pom.xml`, but they share a common parent POM that pins versions and plugin config. This topic explains the layout, the parent-child relationship, and the build order — and ends with `multi-mod-demo`, a parent + two modules.

---

## The problem this solves

By the time a project has ~20 packages and a couple of distinct concerns (a domain library, a CLI front-end, a web layer), keeping it as one giant module hurts:

- Build time grows even when you only changed one corner.
- Cyclic imports creep in because nothing physically separates the layers.
- You can't reuse the domain library from a separate sibling project without copy-paste.

Splitting into modules — `orders-core` (domain), `orders-cli` (a console runner), `orders-web` (a Spring Boot app) — keeps each concern in its own jar with its own classpath. Refactor `orders-core`, rebuild only the modules that depend on it.

---

## The layout

A multi-module Maven project has one parent POM and several child modules:

```text
my-project/
├── pom.xml                          <- parent POM, <packaging>pom</packaging>
├── core/
│   ├── pom.xml                      <- child, depends on nothing in this project
│   └── src/main/java/...
└── app/
    ├── pom.xml                      <- child, depends on core
    └── src/main/java/...
```

The parent POM declares `<modules>` listing each child. Each child POM points back at the parent with `<parent>`.

### Parent POM (minimal)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>multi-mod-demo</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>          <!-- The big difference -->

    <modules>
        <module>core</module>
        <module>app</module>
    </modules>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <slf4j.version>2.0.13</slf4j.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
                <version>${slf4j.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

Two things to notice:

1. `<packaging>pom</packaging>` — this POM **does not produce a jar**. It's an aggregator. It carries config for its children.
2. `<modules>` lists relative directory names. Maven recursively descends into each.

### Child POM (`core/pom.xml`, minimal)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>multi-mod-demo</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>core</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <!-- no <version> needed: parent's dependencyManagement pins it -->
        </dependency>
    </dependencies>
</project>
```

Notice the child:

- Inherits `groupId` and `version` from the parent (so it doesn't repeat them).
- Inherits compile config and pinned versions from the parent's `<dependencyManagement>`.
- Sets its own `artifactId` and packaging.

### Sibling depending on sibling (`app/pom.xml`)

```xml
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>core</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

`app` depends on its sibling `core` exactly like it would depend on any other Maven artifact. Maven sees that `core` is also being built in this reactor and uses the just-built jar instead of fetching from a repository.

---

## The reactor

When you run `mvn package` in the parent directory, Maven:

1. Reads the parent POM. Sees `<modules>core</module><module>app</module>`.
2. Computes a build order using the inter-module `<dependency>` graph. `core` is built before `app`.
3. For each module, runs the requested lifecycle phase: `core/` builds, `app/` builds (using the jar `core` just produced).

This dependency-aware ordering is called the **reactor**.

The output of `mvn -q package` from the parent:

```text
[INFO] Reactor Summary for multi-mod-demo 1.0.0:
[INFO]
[INFO] multi-mod-demo ..................................... SUCCESS [  0.001 s]
[INFO] core ............................................... SUCCESS [  1.234 s]
[INFO] app ................................................ SUCCESS [  0.987 s]
```

### Building only one module

```bash
mvn -q package -pl app -am
```

- `-pl app` -> the project list: only `app`.
- `-am` -> "also make": include any modules that `app` depends on (so `core` is also built).

Use this when you change only `app` and don't want to rebuild `core` from scratch (though Maven is incremental about it anyway).

---

## When to split a project into modules

A useful rule of thumb: **split when a layer is reusable by two or more consumers, or when a layer's classpath should not be polluted by another layer's deps.**

Concrete cases for splitting:

- The same domain logic powers a CLI tool and a web API. -> `core` (domain), `cli`, `web`.
- The web layer pulls in Spring Boot + 80 transitives; the domain doesn't need any of that. -> separate modules so the domain stays clean and testable in isolation.
- Your project produces both a runtime library *and* a CI tool. -> one module each.

Cases for not splitting yet:

- It's a 5-class project. Splitting adds overhead with no payoff.
- You haven't felt the pain of mixed concerns. Premature splitting creates ceremony.

Wait until you wish for the boundary, then add it.

---

## Inherited vs. aggregated vs. both

Two concepts often confused:

- **Inheritance**: child POM has `<parent>...</parent>` -> picks up properties, deps, plugin config from the parent.
- **Aggregation**: parent POM has `<modules>...</modules>` -> Maven knows to descend.

A POM can be a parent without aggregating (a "stand-alone parent" you publish for downstream consumers — Spring Boot's `spring-boot-starter-parent` is this). A POM can be an aggregator without being a parent (less common, possible if children declare a different parent). Usually they go together.

---

## Code example: `multi-mod-demo`

`CODE_EXAMPLES/10-multi-module-projects/multi-mod-demo/`:

```text
multi-mod-demo/
├── pom.xml                                <- parent, packaging=pom, modules: core, app
├── core/
│   ├── pom.xml
│   └── src/main/java/com/example/core/Greeter.java
└── app/
    ├── pom.xml
    └── src/main/java/com/example/app/App.java
```

`core` has one class:

```java
package com.example.core;

public class Greeter {
    public String greet(String name) {
        return "hello, " + name;
    }
}
```

`app` depends on `core` and uses it:

```java
package com.example.app;

import com.example.core.Greeter;

public class App {
    public static void main(String[] args) {
        System.out.println(new Greeter().greet("multi-module Maven"));
    }
}
```

### Run it

From the parent directory:

```bash
cd 05-maven-and-gradle/CODE_EXAMPLES/10-multi-module-projects/multi-mod-demo
mvn -q package
java -cp app/target/app-1.0.0.jar;core/target/core-1.0.0.jar com.example.app.App
```

(macOS/Linux: replace `;` with `:`.)

Expected output:

```text
hello, multi-module Maven
```

Run only `app` (and its dependency `core`):

```bash
mvn -q package -pl app -am
```

Run only `core`:

```bash
mvn -q package -pl core
```

`core` builds, `app` is skipped. Useful when you change only the library and want the fastest possible build.

---

## What about `relativePath`?

Each child POM has:

```xml
<parent>
    <groupId>com.example</groupId>
    <artifactId>multi-mod-demo</artifactId>
    <version>1.0.0</version>
    <!-- <relativePath>../pom.xml</relativePath>  is the default -->
</parent>
```

By default, Maven looks at `../pom.xml` for the parent — which is exactly where the parent lives in a multi-module layout. So you don't have to write `relativePath` explicitly.

The `<relativePath/>` (empty self-closing) trick we saw in topic 03 (Spring Boot) means "don't look on disk, fetch from the repository." That makes sense for a standalone library inheriting from `spring-boot-starter-parent`. In a multi-module project, the local parent is what you want.

---

## Common pitfalls

- **Forgetting `<packaging>pom</packaging>` on the parent.** Maven tries to compile the parent and fails because there's no `src/main/java/`. Set the packaging.
- **Aggregating modules that don't exist on disk.** A `<module>orders-old</module>` pointing at a deleted directory blows up the reactor. Keep `<modules>` in sync with the filesystem.
- **Cyclic module dependencies.** `app` depends on `core`, `core` depends on `app` -> reactor refuses to compute a build order. Resolve by extracting the shared piece into a third module both depend on.
- **Forgetting to bump the parent version when you bump children.** If `core` jumps to `2.0.0` but the parent stays at `1.0.0`, child `<parent>` blocks still point at the old parent — usually fine, but confusing. Many teams version the parent and all children in lockstep.

---

## Try this yourself

1. In `multi-mod-demo`, add a third module `cli` that depends on `core` (use `app` as the template). Add `cli` to the parent's `<modules>` list. Run `mvn -q package`. Inspect the reactor summary; you should see three modules built in dependency order.
2. Add a deliberate cyclic dependency: make `core` depend on `app`. Run `mvn package`. Maven errors with a cycle message. Revert.
3. Open Spring Cloud or Apache Camel on GitHub — both heavily multi-module. Skim the parent POM and count the `<module>` entries. (You'll see dozens. That's normal at scale.)

## Self-check

1. What does `<packaging>pom</packaging>` mean and when do you use it?
2. In what order does Maven build a multi-module project, and how does it decide?
3. A teammate adds a new module `metrics` but the reactor build doesn't pick it up. What's the most likely fix?
