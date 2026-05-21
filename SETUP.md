# Setup

One-time setup to run the code examples in this repository.

## Required

| Tool | Version | Why |
|------|---------|-----|
| JDK | 21 (LTS) | Java version targeted by all examples. Java 17 also works for most. |
| IntelliJ IDEA Community | Latest | Recommended editor. Eclipse and VS Code work too. |
| Git | Any recent | To clone the repo. |

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

## Verifying your setup works

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

## Troubleshooting

**`Error: Could not find or load main class`**
You're probably using the old `java ClassName` form. With single-file launch you need the `.java` extension: `java HelloWorld.java`.

**`error: cannot find symbol: class Connection`**
The H2 jar isn't on the classpath. Double-check the path in `--class-path` matches where you saved the jar.

**`UnsupportedClassVersionError`**
Your JDK is older than the bytecode in the file. Install JDK 21.

**Windows path issues**
Use double quotes around the `--class-path` value. If your repo lives at a path with spaces (e.g. `C:\Personal Project\...`), the quotes are mandatory.
