# 05 — Dependency scopes

A scope tells Maven **when** a dependency is on the classpath: during compile, during test, at runtime, or never (provided by something else at deploy time). Picking the wrong scope is a top-three real-world Maven mistake — it works on your laptop, it breaks in production.

The accompanying `scope-demo` project has one dependency in each common scope so you can see them side by side.

---

## The problem this solves

Three concrete examples of "right scope matters":

1. **JUnit** — you only need it to *compile and run tests*, not in the deployed jar. Wrong scope: ships JUnit inside your production artifact. Right scope: `test`. Saves megabytes and avoids leaking test code paths.
2. **MySQL JDBC driver** — your `main` code never imports `com.mysql.cj.jdbc.Driver`; you just use `DriverManager.getConnection("jdbc:mysql://...")`. The driver class loads reflectively at runtime. Wrong scope: `compile` makes every dev's IDE show MySQL APIs they shouldn't use directly. Right scope: `runtime`.
3. **Servlet API** — when deployed to Tomcat, Tomcat provides the servlet classes. If your jar also includes them, you get two copies on the classpath and a `LinkageError`. Right scope: `provided` ("the container will provide it").

The scope is small text in `pom.xml` (`<scope>test</scope>`) but it shapes what ends up in your built artifact.

---

## The six scopes

Maven has six scopes. In real projects you use four. Here they all are.

| Scope         | Available at compile? | Available at test? | Available at runtime? | Bundled in the jar? | Inherited by transitively dependent projects? |
|---------------|-----------------------|--------------------|-----------------------|---------------------|----------------------------------------------|
| **compile** (default) | Yes               | Yes                | Yes                   | Yes                 | Yes                                          |
| **test**      | No                    | Yes                | No                    | No                  | No                                           |
| **provided**  | Yes                   | Yes                | No (container provides) | No               | No                                           |
| **runtime**   | No (no compile-time imports allowed) | Yes  | Yes                   | Yes                 | Yes                                          |
| **system**    | (deprecated; avoid)    | -                  | -                     | -                   | -                                            |
| **import**    | (special: only in `<dependencyManagement>` to import BOMs) | - | - | - | - |

Read the table top-to-bottom for any single scope to know what to expect.

---

## compile — the default, the workhorse

If you don't write `<scope>`, you get `compile`. This is what you want for libraries your code directly imports.

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.16.1</version>
</dependency>
```

Jackson is on the classpath during compile (so `import com.fasterxml.jackson.databind.ObjectMapper;` works), during test (so tests can use it), and at runtime (so the deployed app can actually call it). It's bundled in the fat-jar if you build one.

---

## test — JUnit, Mockito, AssertJ, Testcontainers

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```

Available only when compiling `src/test/java/` and running tests. Not on the production classpath, not in the packaged jar. Your `src/main/java/` cannot `import org.junit.jupiter.api.Test;` — that's a compile error, and that's the point.

This is the scope you'll use **most often** after `compile`.

---

## provided — servlet API, Lombok, JSR annotations

```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>
```

Available at compile and test time. **Not** packaged in the jar. Not on the runtime classpath unless something else puts it there.

Mental model: "someone else will provide this when my code actually runs."

- Servlet API -> the servlet container (Tomcat, Jetty) provides it.
- Lombok -> only needed during compile to expand annotations; runtime is fine without it.
- JSR-305 (`@Nullable` etc.) -> annotations are only meaningful to the compiler/IDE.

If your build is a Spring Boot fat-jar (which embeds Tomcat), `provided` rarely applies — you *are* the container. `provided` matters for traditional WAR deployments.

---

## runtime — JDBC drivers, SLF4J bindings

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.4.0</version>
    <scope>runtime</scope>
</dependency>
```

**Not** available at compile time. Available at test time and runtime. Bundled in the jar.

"My code doesn't import this directly, but something — `DriverManager`, `LoggerFactory`, the JVM service loader — loads it at runtime."

Two textbook uses:

- **JDBC drivers.** Code uses `DriverManager.getConnection("jdbc:mysql://...")`. The MySQL driver class loads reflectively when the JVM sees the URL. Marking it `runtime` enforces "no direct imports of MySQL classes in production code."
- **SLF4J bindings** (`slf4j-simple`, `logback-classic`). Your code imports the SLF4J **API** (`org.slf4j.Logger`) at compile scope. The **binding** (which decides where the logs actually go) is `runtime` — discovered by SLF4J via the service loader.

If you accidentally write `import com.mysql.cj.jdbc.Driver;` while the driver is `runtime`, the compile fails. That's the safety net working.

---

## system — don't use it

```xml
<scope>system</scope>
<systemPath>/abs/path/to/some.jar</systemPath>
```

Points to a jar on the local filesystem. Effectively deprecated. Don't use it. If you need a jar that isn't on a public repo, `mvn install:install-file` puts it into your local cache properly, or set up a private repository.

---

## import — for BOMs only

`import` scope is special: it's only valid inside `<dependencyManagement>` and lets you "import" the `<dependencyManagement>` section of another POM (a BOM).

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson</groupId>
            <artifactId>jackson-bom</artifactId>
            <version>2.16.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

After this, any direct or transitive Jackson artifact in the project uses 2.16.1 with no version on the individual `<dependency>`. Heavy use in real Spring Boot setups; topic 09 demonstrates it for conflict resolution.

---

## Wrong / right side by side

### Wrong: JUnit at compile scope

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
</dependency>
```

What goes wrong:

- JUnit is bundled in your deployed jar. Production users download it for nothing.
- Production code can `import org.junit.jupiter.api.Test;` and even leave annotations like `@Test` in business code. The class still loads at runtime because JUnit is there — but now your "test" annotation runs in production.

### Right

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```

Production code can't import JUnit. The deployed jar doesn't contain it.

### Wrong: JDBC driver at compile scope

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.4.0</version>
</dependency>
```

What goes wrong:

- `import com.mysql.cj.jdbc.Driver;` compiles. Now your code is tied to MySQL classes, defeating JDBC's "swap the driver" promise.
- IDE autocomplete suggests MySQL internals to developers who shouldn't be using them directly.

### Right

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.4.0</version>
    <scope>runtime</scope>
</dependency>
```

Production code talks to `java.sql.DriverManager` only. The MySQL classes are still on the runtime classpath, so the driver registers itself, so `getConnection("jdbc:mysql://...")` works. The compiler enforces "use the JDBC abstraction, not the vendor."

---

## Code example: `scope-demo`

`CODE_EXAMPLES/05-dependency-scopes/scope-demo/` has one dependency per common scope:

```xml
<!-- compile (default) -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.13</version>
</dependency>

<!-- runtime -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.13</version>
    <scope>runtime</scope>
</dependency>

<!-- provided -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>

<!-- test -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```

Two source files demonstrate the scope rules:

- `ScopeDemo.java` (in `src/main/java/`) imports SLF4J (compile) and `HttpServletRequest` (provided). It does **not** import JUnit (would fail to compile) or SLF4J Simple (would also fail — runtime scope only).
- `ScopeDemoTest.java` (in `src/test/java/`) imports JUnit. The test asserts the SLF4J binding picks up `slf4j-simple` at runtime.

Read the POM and the import lines together. The scopes line up with what each side can and can't see.

### Run it

```bash
cd 05-maven-and-gradle/CODE_EXAMPLES/05-dependency-scopes/scope-demo
mvn -q test
```

Expected (test count, timestamps will vary):

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

You can also produce the jar and inspect what scopes ended up bundled:

```bash
mvn -q package
jar tf target/scope-demo-1.0.0.jar
```

You'll see `com/example/ScopeDemo.class` but **not** any JUnit classes — `test` scope, correctly excluded.

---

## Common pitfalls

- **`scope-demo`-style mismatches.** Production fails with `NoClassDefFoundError: org.slf4j.simple.SimpleLogger` because slf4j-simple was at `test` scope and the deployed jar didn't bundle it. Right answer: `runtime`.
- **`provided` instead of `compile` for a library you actually need to ship.** Works in development (the IDE puts the jar on the classpath at run time anyway); fails on the deployed server.
- **`compile` for everything because "it builds."** Yes, it builds, but the deployed jar is 30 MB bigger than it needed to be, and production code can reach into testing classes it shouldn't touch.

---

## Try this yourself

1. In `scope-demo`, temporarily change `slf4j-simple` from `runtime` to `test`. Re-run `mvn -q package` then try to run the main class manually with `java -cp target/scope-demo-1.0.0.jar:target/dependency/*  com.example.ScopeDemo` (after generating the dependency dir with `mvn -q dependency:copy-dependencies`). Observe the SLF4J "no providers" warning — the binding is missing in the production run.
2. Revert it to `runtime`. The warning disappears.
3. Try adding `import org.junit.jupiter.api.Test;` to `ScopeDemo.java` (the main file). Run `mvn -q compile`. Compile fails. Comment out the import. Compile passes. This is the test-scope guardrail working.

## Self-check

1. You have a logging API (slf4j-api), a logging binding (logback-classic), JUnit, and an embedded servlet container (in a Spring Boot world). Which scope does each one get and why?
2. Why is `runtime` the correct scope for a JDBC driver, given that the code obviously needs to talk to the database?
3. What's the practical difference between `provided` and `compile` for the deployed artifact?
