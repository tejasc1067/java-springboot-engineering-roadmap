# Common Mistakes — Module 05 (Build Tools)

Real bugs people hit with Maven (and Gradle). Each entry: what someone wrote, what went wrong, why, and how to fix it.

---

## 1. Wrong scope on a JDBC driver

**What someone wrote:**

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.4.0</version>
</dependency>
```

**What went wrong:** A developer wrote `import com.mysql.cj.jdbc.Driver;` in business code because the IDE happily auto-imported it. Six months later, the team tried to move the service to PostgreSQL. The build broke in 14 places.

**Why:** With no `<scope>`, the dependency is `compile`. Every MySQL class is on the compile classpath, so the import was allowed. The JDBC abstraction (`DriverManager`, `Connection`) exists precisely so business code never imports vendor classes.

**Fix:** `<scope>runtime</scope>`. The driver still loads at runtime (via `DriverManager`), but business code can't import vendor classes anymore. The compile fails on the bad import; you fix it once.

---

## 2. JUnit shipped to production

**What someone wrote:**

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
</dependency>
```

**What went wrong:** The deployed fat-jar was 4 MB bigger than it should have been. Worse, an intern accidentally left an `@Test` annotation on a `cleanup()` method in business code. The annotation was a no-op in production... until someone added a test runner that scanned the runtime classpath. Suddenly an unintended method ran on app startup.

**Why:** No `<scope>test</scope>` means JUnit is on every classpath, including production. The annotation became loadable, and downstream tools picked it up.

**Fix:** `<scope>test</scope>`. JUnit becomes invisible to `src/main/java`, so the bad annotation wouldn't even compile.

---

## 3. Version range surprise

**What someone wrote:**

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>[2.16,)</version>
</dependency>
```

**What went wrong:** Build was green on Monday. On Wednesday, Jackson 2.17 was released. The team's `mvn clean package` on Thursday morning produced a different jar than Monday's. A subtle behavior change in Jackson's date handling broke a downstream consumer that had pinned an old date format.

**Why:** Version ranges (`[2.16,)`) tell Maven "pick the newest you can find." That means your build is no longer reproducible — same source, same `pom.xml`, different output depending on what's been published in the meantime.

**Fix:** Always use a fixed version (`<version>2.16.1</version>`). Upgrade deliberately, in a commit that's reviewable.

---

## 4. The "fat-jar with two SLF4J bindings" runtime warning

**What someone wrote:**

```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.6</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.13</version>
    <scope>runtime</scope>
</dependency>
```

**What went wrong:** Application boot logs:

```text
SLF4J: Class path contains multiple SLF4J providers.
SLF4J: Found provider [org.slf4j.simple.SimpleServiceProvider@...]
SLF4J: Found provider [ch.qos.logback.classic.spi.LogbackServiceProvider@...]
SLF4J: See https://www.slf4j.org/codes.html#multiple_bindings ...
```

Two bindings, only one will actually serve logs, and which one is undefined behavior.

**Why:** Both jars register themselves as SLF4J providers via `META-INF/services/`. SLF4J picks the first one it sees on the classpath, which depends on classpath ordering — flaky.

**Fix:** Pick one binding. Most teams use `logback-classic` (more configurable) and remove `slf4j-simple`. If a transitive pulls in a second binding, exclude it:

```xml
<dependency>
    <groupId>some-lib</groupId>
    <artifactId>some-artifact</artifactId>
    <version>...</version>
    <exclusions>
        <exclusion>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## 5. Forgetting to skip non-jar packaging in a multi-module build

**What someone wrote:** A parent POM in a multi-module project:

```xml
<project>
    <groupId>com.example</groupId>
    <artifactId>my-parent</artifactId>
    <version>1.0.0</version>
    <!-- no <packaging> declared -->

    <modules>
        <module>core</module>
        <module>app</module>
    </modules>
</project>
```

**What went wrong:** `mvn package` fails with `Error packaging project for parent: target/classes not found.` Maven tried to compile and jar the parent project itself.

**Why:** With no `<packaging>` declared, the default is `jar`. Maven tried to build a jar from `src/main/java/`, which doesn't exist for the parent.

**Fix:** Always set `<packaging>pom</packaging>` on aggregator/parent POMs.

---

## 6. Plugin without a pinned version

**What someone wrote:**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <!-- no <version> -->
    <configuration>
        <release>21</release>
    </configuration>
</plugin>
```

**What went wrong:** Developer A's build uses compiler plugin 3.13.0 (the one on her PC). Developer B's uses 3.11.0. CI uses 3.12.1. Edge-case behavior of `<release>` interacted differently with the JDK versions on each box. "Works on my machine" debugging followed.

**Why:** Maven auto-picks a plugin version when none is given, and the chosen version depends on the local Maven install's "super POM." Different installs, different defaults.

**Fix:** Always pin plugin versions. `mvn versions:display-plugin-updates` shows you the latest available for each.

---

## 7. `-DskipTests` in CI

**What someone wrote (in CI config):**

```bash
mvn -DskipTests=true package
```

**What went wrong:** A regression test for a bug fixed last sprint started failing locally but kept passing CI. "CI passed, ship it." The bug returned to production.

**Why:** `-DskipTests` tells Surefire not to *run* the tests; they still compile, but they never execute. CI builds the artifact and uploads it; nobody verifies the tests pass.

**Fix:** Remove `-DskipTests` from CI. If the build is slow, optimize the slow tests, run them in parallel, or split into a fast/slow tier — don't disable them entirely. `-DskipTests` belongs in "emergency local build" scripts, not CI.

---

## 8. Cyclic module dependency

**What someone wrote:** In a multi-module project, `core` depends on `web` (because `core` needed a utility that lived in `web`), and `web` depends on `core` (the original sensible direction).

**What went wrong:** `mvn package` fails with `The projects in the reactor contain a cyclic reference: ...`.

**Why:** Maven's reactor needs a DAG to compute build order. Cycle = no order.

**Fix:** Extract the shared utility into a third module (`common`) that both `core` and `web` depend on. Cycle broken.

---

## 9. Wrong `mvn install` mental model

**What someone wrote:** Every time they want to run their app, the developer types:

```bash
mvn install
java -jar target/their-app.jar
```

**What went wrong:** The `install` step copies the jar into `~/.m2/repository/`, which on this developer's laptop now contains 300+ snapshot versions of every internal module. Disk fills up. Maven also took ~30 s longer per iteration than necessary.

**Why:** `install` is "make this jar available to *other local Maven projects*." If no other local project depends on the artifact, `install` is doing nothing useful — but it still happens.

**Fix:** Use `mvn package` for "I want a jar." Reserve `mvn install` for the case where another local project depends on this one. Use `mvn -rf :module-name` to resume builds.

---

## 10. The wrapper is committed but `chmod +x` was lost

**What someone wrote:** Cloned the repo on macOS, ran `./mvnw package`, got `bash: ./mvnw: Permission denied`.

**Why:** On macOS/Linux a file needs the executable bit. The teammate who created the wrapper was on Windows, where the bit doesn't apply, so git didn't store it.

**Fix:** Set the bit and commit it:

```bash
git update-index --chmod=+x mvnw
git commit -m "make mvnw executable"
git push
```

Now anyone cloning gets the executable bit.

---

## 11. Adding a transitive directly without the explicit version

**What someone wrote:** A teammate found that `jackson-core` was already on the classpath (via `jackson-databind`) and decided to use a Jackson class directly. They didn't add a direct dependency.

**What went wrong:** Six months later, the team upgraded to a different JSON library and removed `jackson-databind`. Compile errors all over the place — they were using `jackson-core` classes they never declared.

**Why:** `mvn dependency:analyze` calls this "Used undeclared dependency." Relying on a transitive that might be removed in the next upgrade is fragile.

**Fix:** If your code imports a class from a library, **declare the library as a direct dependency**. Even if it would also come in transitively. The explicit declaration documents intent and survives transitive changes.

---

## 12. Gradle: `compile` configuration in modern Gradle

**What someone wrote (in `build.gradle`):**

```groovy
dependencies {
    compile 'com.fasterxml.jackson.core:jackson-databind:2.16.1'
}
```

**What went wrong:** Gradle 7+ errors:

```text
Configuration 'compile' was removed.
Use 'implementation' or 'api' instead.
```

**Why:** Gradle deprecated `compile` because it conflated two concepts: "I need this to compile my code" and "I want my consumers to also see this on their compile classpath." The new configurations (`implementation`, `api`) separate them.

**Fix:** Use `implementation 'com.fasterxml.jackson.core:jackson-databind:2.16.1'` (no leaking to consumers, almost always what you want) or `api '...'` (when the dependency appears in your public method signatures).

---

## 13. Gradle: JUnit 5 tests "silently passing" with zero tests run

**What someone wrote (in `build.gradle`):**

```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}
```

**What went wrong:** `gradle test` reports `0 tests passed, 0 failed`. Developer ships untested code.

**Why:** Gradle's `test` task defaults to JUnit 4's test runner. JUnit 5 tests look invisible.

**Fix:**

```groovy
tasks.named('test') {
    useJUnitPlatform()
}
```

(Or in Kotlin DSL: `tasks.named<Test>("test") { useJUnitPlatform() }`.)

A common gotcha for Maven->Gradle migrators since Maven Surefire 3.x figures this out automatically.

---

## 14. Editing `target/` files by hand

**What someone wrote:** A developer found `target/classes/com/example/Config.class`, opened it in a hex editor, and "patched" a hard-coded URL.

**What went wrong:** Next `mvn compile`, the patch was gone. They tried again. Same result. They concluded "Maven is broken."

**Why:** `target/` is generated. `mvn clean`, `mvn package`, `mvn compile` — every build rewrites it from source.

**Fix:** Edit `src/main/java/...` (or `src/main/resources/application.properties` for config) and let the build produce a new `target/`. If a value needs to differ per environment, externalize it (env var, properties file, command-line flag), don't bake it into `.class` files.

---

## 15. Treating the IDE's build as authoritative

**What someone wrote:** "My code runs in IntelliJ but fails when I run `mvn package`."

**What went wrong:** IntelliJ has its own internal classpath calculator. It often picks up classes from sibling modules even when the POM doesn't declare a dependency on them. Code that "runs" in the IDE refers to classes that won't be on the classpath at `mvn` time.

**Why:** The IDE is a convenience. The build tool is the truth. If `mvn clean package` doesn't work, the project doesn't work, no matter what IntelliJ shows.

**Fix:** Before pushing, always run `mvn clean package` from the terminal. If it fails, fix the POM; don't fight the IDE.
