# 09 — Dependency conflicts and resolution

Two libraries you depend on each want a different version of SLF4J. Only one can win. The wrong one wins. Production breaks with a `NoSuchMethodError` at 2 AM. This topic is about diagnosing that and steering Maven to pick the right version.

The companion code example (`conflict-demo`) sets up a real conflict and shows three ways to resolve it.

---

## The problem this solves

Real backend projects have 50+ jars on the classpath, most of them indirect. When two of those transitives disagree on a version, Maven picks one — quietly. The code might compile. Tests might pass. The bug shows up only when production code hits an API that exists in one version and was removed in the other:

```text
Exception in thread "main" java.lang.NoSuchMethodError:
  'java.lang.String com.fasterxml.jackson.databind.ObjectMapper.writeValueAsString(java.lang.Object)'
```

The fix is almost always "force a specific version." This topic teaches you to find which version Maven picked, decide which version you actually want, and force it.

---

## How Maven picks a winner

When the same `groupId:artifactId` appears multiple times in the dependency graph at different versions, Maven applies these rules in order:

1. **Nearest wins.** The version closest to your project (fewer "hops" away in the dependency tree) wins. Direct dependencies always beat transitive ones.
2. **First-declared wins** when nearest-distance ties. If two paths are the same length, the one declared first in your `pom.xml` wins.
3. **`<dependencyManagement>` overrides everything.** If a version is pinned in `<dependencyManagement>`, that version wins regardless of distance.

These rules are intentionally simple, which makes them predictable but also easy to break. A small change in the `pom.xml` ordering can swap which version wins.

---

## Step 1: see the conflict

`mvn dependency:tree` shows the full graph. With verbose output it also shows what got *omitted*:

```bash
mvn dependency:tree -Dverbose
```

Example output (trimmed):

```text
[INFO] com.example:conflict-demo:jar:1.0.0
[INFO] +- libA:libA-core:jar:1.0:compile
[INFO] |  \- org.slf4j:slf4j-api:jar:1.7.36:compile
[INFO] \- libB:libB-core:jar:2.0:compile
[INFO]    \- (org.slf4j:slf4j-api:jar:2.0.13:compile - omitted for conflict with 1.7.36)
```

That `omitted for conflict` line is gold. It tells you:

- Two paths brought in `slf4j-api`.
- The version from libA (`1.7.36`) won.
- The version from libB (`2.0.13`) was discarded.

If libB calls SLF4J 2.x methods that didn't exist in 1.7.x, it'll explode at runtime.

A simpler version is to grep for the artifact directly:

```bash
mvn dependency:tree -Dincludes=org.slf4j:slf4j-api
```

---

## Step 2: pick the right version

Three common strategies, in order of preference:

### Strategy A: declare it explicitly at the top level

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.13</version>
</dependency>
```

Adding `slf4j-api` directly to your `<dependencies>` makes it "distance 1" — closer than any transitive — so the nearest-wins rule picks it. Clean and clear.

Use this when **you** depend on the API directly anyway (almost always true for logging).

### Strategy B: use `<dependencyManagement>` to pin the version

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.13</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

This says "if anyone (direct or transitive) pulls in slf4j-api, use 2.0.13." You don't add it to `<dependencies>` — you just pin the version. Maven applies it everywhere.

Use this when you don't directly use the library but need to control its version. Common pattern for shared parent POMs in multi-module projects (topic 10).

### Strategy C: exclude the unwanted transitive

```xml
<dependency>
    <groupId>libA</groupId>
    <artifactId>libA-core</artifactId>
    <version>1.0</version>
    <exclusions>
        <exclusion>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

This removes `slf4j-api` from libA's transitive set entirely. Now only libB's path provides it, so libB's version wins by being the only one.

Use this when:

- One specific transitive is causing problems (often an old `commons-logging` or an old `log4j` you want to fully evict).
- You're sure the dependency you're excluding isn't actually needed at runtime by the parent library.

`<exclusions>` is a surgical tool. Overusing it leads to "I excluded that and now nothing logs" surprises.

---

## A real example: BOM imports for a curated set

Spring Boot pins versions for ~200 libraries through its `spring-boot-dependencies` BOM (imported via `spring-boot-starter-parent`). When you depend on Spring Boot, you get all those versions pinned at once. No more "which version of Jackson is compatible with which version of Hibernate" pain.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.3.4</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

In module 08 you'll usually inherit this via `<parent>` and not write it directly. But knowing the mechanism — `import` scope, `type=pom`, in `<dependencyManagement>` — pays off when you need to pin Spring Cloud, Quarkus, or a custom platform BOM.

---

## How to diagnose in the wild

A typical chain:

1. Production logs `NoSuchMethodError` for a class in `org.slf4j.Logger`.
2. You run `mvn dependency:tree -Dincludes=org.slf4j:slf4j-api` on the deployed project.
3. You see `slf4j-api:1.7.36` won via the nearest-wins rule.
4. But your code (or a library you upgraded) calls a method introduced in `slf4j-api:2.0`. That method doesn't exist in 1.7.36, hence the error.
5. You add `slf4j-api:2.0.13` directly to `<dependencies>` (Strategy A).
6. Re-run `mvn dependency:tree`. Confirm 2.0.13 wins. Redeploy.

This happens often enough to be the most common "build is fine, production crashes" cause in real Java teams. Half the value of this topic is recognizing the symptom and going straight to `dependency:tree`.

---

## When two versions actually need to coexist

Sometimes Strategy A through C don't fix it because the two libraries truly require **different incompatible versions**. This is where the OSGi or JPMS module systems try to help — and the practical 99% answer is:

- Upgrade one of the conflicting libraries to a version that's compatible with the other.
- Or replace one of the libraries.

If you find yourself reaching for shading (renaming packages inside a jar to avoid collision), step back — it's a sign of a deeper problem. Most pure-Java projects never need it.

---

## Code example: `conflict-demo`

`CODE_EXAMPLES/09-dependency-conflicts/conflict-demo/` deliberately creates a conflict and then resolves it.

It depends on two real libraries that each pull in `slf4j-api` at different versions:

- `com.zaxxer:HikariCP:6.0.0` (pulls `slf4j-api` ~2.0.x).
- `org.apache.activemq:activemq-client:5.18.3` (historically pulls older SLF4J transitives).

(The specific versions used in the POM are pinned so you see the same output every time.)

The POM has three commented blocks marked **Strategy A / B / C**. Uncomment one at a time and rerun `mvn dependency:tree -Dverbose` to see how each strategy changes the winner.

### `Demo.java`

```java
package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Demo {
    public static void main(String[] args) {
        Logger log = LoggerFactory.getLogger(Demo.class);
        log.info("slf4j-api version effective at runtime: {}", Logger.class.getPackage().getImplementationVersion());
    }
}
```

### Run it

```bash
cd 05-maven-and-gradle/CODE_EXAMPLES/09-dependency-conflicts/conflict-demo
mvn -q dependency:tree -Dverbose -Dincludes=org.slf4j:slf4j-api
```

You'll see two paths pulling slf4j-api at different versions, with one "omitted for conflict." Now apply one of the three strategies in `pom.xml` and re-run. The winning version changes.

To see runtime behavior:

```bash
mvn -q dependency:copy-dependencies -DoutputDirectory=target/lib
mvn -q compile
java -cp "target/classes;target/lib/*" com.example.Demo
```

(On macOS/Linux replace `;` with `:`.)

Expected: a log line whose timestamp prefix comes from whatever SLF4J binding ends up on the classpath.

---

## Common pitfalls

- **"It builds, so it must be right."** Wrong. Maven's resolution always picks *some* version. The compile-time API surface of SLF4J 1.7.x and 2.0.x is partly overlapping. Code can compile against the loser, then `NoSuchMethodError` at runtime.
- **Excluding the wrong transitive.** `<exclusions>` removes a transitive entirely. If you exclude something the library actually needs, you'll get `NoClassDefFoundError` at runtime. Use `mvn dependency:tree` to confirm the library doesn't need what you're excluding.
- **Pinning a version that's older than what a transitive needs.** If libB requires `slf4j-api >= 2.0` and you pin `1.7.36` via `<dependencyManagement>`, libB's calls to 2.x methods explode at runtime. Pin the *higher* compatible version.
- **Re-running an old WAR/jar.** Some teams `mvn install` once, ship that artifact, then change the POM later. The deployed artifact still embeds the old transitive choice. Always reproduce: `mvn clean dependency:tree` matches what the *current* `pom.xml` produces.

---

## Try this yourself

1. In `conflict-demo`, run `mvn -q dependency:tree -Dverbose`. Identify the conflicted artifact and the loser version.
2. Uncomment **Strategy A** in `pom.xml` (direct dependency on slf4j-api at a chosen version). Rerun. Note that the chosen version now wins by distance-1.
3. Comment Strategy A, uncomment **Strategy B** (`<dependencyManagement>`). Rerun. Note the winner changes again — and there's no direct `<dependency>` for it in `<dependencies>`.
4. Try Strategy C (`<exclusions>`) on the heavier of the two libraries. Confirm via the tree that one path no longer provides slf4j-api.

## Self-check

1. State Maven's three resolution rules in order.
2. Given a tree where libA pulls Jackson 2.13 and libB pulls Jackson 2.16, which one wins by default? How would you switch to the other?
3. What's the difference between excluding a transitive and overriding its version?
