# 07 — Plugins

Maven itself does almost nothing. Plugins do the work. Compiling? A plugin. Running tests? A plugin. Producing the jar? A plugin. This topic introduces the three plugins you'll touch most (compiler, Surefire, Shade) and ends with `fat-jar-demo` — a project that produces a single runnable jar that includes its dependencies.

---

## The problem this solves

In topic 06 we said "the `compile` phase runs `maven-compiler-plugin:compile`." But what *is* that plugin? Where does it come from? Can you configure it? What happens when you need to do something it doesn't do (e.g. bundle dependencies into the jar)? This topic answers all three by walking the plugins you actually use.

The fat-jar demo is the concrete payoff: by the end you'll know exactly what makes `java -jar your-app.jar` work and why a regular `mvn package` jar doesn't run on its own.

---

## What a plugin is

A Maven plugin is a published artifact (just like Jackson or JUnit) that provides **goals** Maven can invoke. Goals do specific work: compile Java, run tests, copy files, sign artifacts.

Plugin coordinates look like dependency coordinates:

```text
org.apache.maven.plugins:maven-compiler-plugin:3.13.0
```

Maven applies a short-name convention so you can call them without typing the full group/artifact:

```bash
mvn compiler:compile         # full: org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile
mvn dependency:tree          # full: org.apache.maven.plugins:maven-dependency-plugin:3.7.1:tree
mvn versions:display-dependency-updates    # third-party: org.codehaus.mojo:versions-maven-plugin
```

Plugins under `org.apache.maven.plugins` get the privilege of the short prefix (`compiler:`, `surefire:`, etc.). Third-party plugins under `org.codehaus.mojo` (also widely trusted) get a `groupId`-derived prefix.

---

## How plugins bind to the lifecycle

There are two ways a plugin gets invoked:

### Implicitly, via the default lifecycle for your packaging

A `<packaging>jar</packaging>` project comes with a default plugin binding:

```text
process-resources  -> maven-resources-plugin:resources
compile            -> maven-compiler-plugin:compile
process-test-resources -> maven-resources-plugin:testResources
test-compile       -> maven-compiler-plugin:testCompile
test               -> maven-surefire-plugin:test
package            -> maven-jar-plugin:jar
install            -> maven-install-plugin:install
deploy             -> maven-deploy-plugin:deploy
```

You inherit these for free. You don't have to declare any plugin to get a basic Java jar to build.

### Explicitly, via `<build><plugins>`

When you need to configure a default plugin (pick a version, tweak its options) or add a new one, you declare it in `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <release>21</release>
            </configuration>
        </plugin>
    </plugins>
</build>
```

This pins the compiler plugin version (so the build is reproducible across Maven versions) and tells it to use the `--release 21` flag.

---

## The three plugins to know

### 1. `maven-compiler-plugin` — compiles Java

The compiler plugin runs `javac` against your source. The most common configuration:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <release>21</release>
    </configuration>
</plugin>
```

`<release>21</release>` is shorthand for "compile *and* link against the Java 21 API." It's better than the older `<source>21</source><target>21</target>` pair because it also prevents accidental use of newer-than-21 APIs.

Alternative: set the `maven.compiler.source/target` properties (we did this in topic 02). Both ways work; in larger projects, pin the plugin version and use `<release>`.

### 2. `maven-surefire-plugin` — runs unit tests

Surefire is bound to the `test` phase by default. It looks for test classes by naming pattern (`*Test.java`, `*Tests.java`) and runs them with JUnit or TestNG.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <argLine>-Xmx512m</argLine>
    </configuration>
</plugin>
```

In modern Surefire (3.x) the JUnit 5 engine is detected automatically — just add `junit-jupiter` at `test` scope and tests run. (See `scope-demo` from topic 05.)

Module 06 in this roadmap dives deeper into unit testing. For now, recognize: Surefire = unit tests, runs at `test` phase.

(Cousin: `maven-failsafe-plugin` is the same idea but for integration tests, bound to `integration-test` and `verify` phases. It tolerates failures during `integration-test` to give the `post-integration-test` phase a chance to clean up, then asserts at `verify`.)

### 3. `maven-shade-plugin` — bundles dependencies into a fat-jar

By default `mvn package` produces a "thin" jar containing only your compiled classes. To run it you still need every dependency jar on the classpath:

```bash
java -cp target/your-app.jar:target/dependency/* com.example.Main
```

That's clumsy. For self-contained executables (CLIs, simple services) we want a **fat-jar** (also called **uber-jar**): one jar that contains your classes *plus* every dependency, runnable with `java -jar`.

Shade plugin does that:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>com.example.Main</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

After `mvn package`, `target/fat-jar-demo-1.0.0.jar` contains every class from every compile/runtime dependency, with `Main-Class: com.example.Main` set in the manifest. You can run it anywhere with a JDK installed:

```bash
java -jar target/fat-jar-demo-1.0.0.jar
```

That's it. No classpath, no `lib/` folder, one file.

Two important details:

- **`ManifestResourceTransformer`** writes the `Main-Class` line so `java -jar` knows what to run.
- **`ServicesResourceTransformer`** (not shown above but often needed) merges `META-INF/services/` files across jars. If you skip it and your project uses any `ServiceLoader`-based mechanism (SLF4J bindings, JDBC drivers), one jar's services file overwrites another's and things break at runtime. The `fat-jar-demo` POM uses this transformer.

> **Heads up:** Spring Boot projects don't use Shade. They use the `spring-boot-maven-plugin` to produce a special "executable jar" with a custom layout. We'll see that in module 08. The concept is the same — bundle everything for `java -jar` — the mechanism is Spring's.

---

## Other plugins you'll meet

A short tour of what shows up in real projects. You don't need these now; recognize them so you know what to read up on later.

| Plugin                              | Purpose                                                               |
|-------------------------------------|-----------------------------------------------------------------------|
| `spring-boot-maven-plugin`          | Spring Boot's special packaging + the `mvn spring-boot:run` goal.     |
| `maven-failsafe-plugin`             | Integration tests (bound to `verify`).                                |
| `jacoco-maven-plugin`               | Code coverage instrumentation. Generates HTML reports.                |
| `maven-dependency-plugin`           | The `tree`, `list`, `copy-dependencies` goals.                        |
| `versions-maven-plugin`             | Find/upgrade dependency versions.                                     |
| `maven-enforcer-plugin`             | Ban specific dependencies, enforce Java version, ban `RELEASE` strings. |
| `maven-source-plugin` / `maven-javadoc-plugin` | Produce sources jar / javadoc jar (needed when publishing to Maven Central). |

---

## Code example: `fat-jar-demo`

`CODE_EXAMPLES/07-plugins/fat-jar-demo/` is a tiny project that pulls in a real dependency (Jackson again) and uses Shade to produce a runnable single-file jar.

### `Main.java`

```java
package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        var json = new ObjectMapper().writeValueAsString(Map.of("status", "ok"));
        System.out.println(json);
    }
}
```

### Run it

```bash
cd 05-maven-and-gradle/CODE_EXAMPLES/07-plugins/fat-jar-demo
mvn -q package
java -jar target/fat-jar-demo-1.0.0.jar
```

Expected output:

```text
{"status":"ok"}
```

Now inspect what's inside the jar:

```bash
jar tf target/fat-jar-demo-1.0.0.jar | head -20
```

You should see your own `Main.class` plus a lot of Jackson classes:

```text
META-INF/MANIFEST.MF
com/example/Main.class
com/fasterxml/jackson/databind/ObjectMapper.class
com/fasterxml/jackson/core/JsonGenerator.class
...
```

That's the "fat" in fat-jar. Hundreds of classes from your dependencies are now physically inside your one jar.

You'll also see a smaller `original-fat-jar-demo-1.0.0.jar` — the regular thin jar Shade saves alongside the shaded one. Both are produced by the `package` phase.

---

## Common pitfalls

- **Forgetting `ServicesResourceTransformer` in the shade config.** Symptom: SLF4J prints "Failed to load class org.slf4j.impl.StaticLoggerBinder" or the JDBC driver doesn't register. Fix: add the transformer.
- **Not pinning plugin versions.** Without `<version>` on a plugin, Maven picks one and your build's output may differ across Maven installs. Always pin plugin versions in real projects.
- **Two plugins fighting over `package`.** If you accidentally configure both `maven-shade-plugin` and `spring-boot-maven-plugin` to run at `package`, you get two jar variants and confusion. Pick one packaging strategy per project.
- **A 200 MB fat-jar surprising you.** Shading is dumb: it bundles every compile-scope class. Mark dependencies that the deployment environment already supplies as `provided` to keep the jar lean.

---

## Try this yourself

1. In `fat-jar-demo`, comment out the `<transformer>` (the one that sets `Main-Class`). Re-run `mvn -q package` and `java -jar target/fat-jar-demo-1.0.0.jar`. You'll get `no main manifest attribute`. The transformer is the line that makes `java -jar` work.
2. Re-enable the transformer. Add an SLF4J dependency (api + simple at runtime, as in topic 04) and log instead of printing. Run the fat-jar. Confirm the binding still works — only because the shade plugin has `ServicesResourceTransformer` configured. Remove that transformer and the log writes nothing.
3. Compare file sizes: `ls -lh target/*.jar`. The shaded one is much bigger than the original.

## Self-check

1. What does the `maven-shade-plugin` do that `maven-jar-plugin` does not?
2. Why does Spring Boot use its own `spring-boot-maven-plugin` instead of shade?
3. You add a new plugin without a `<phase>` element. When does Maven run it?
