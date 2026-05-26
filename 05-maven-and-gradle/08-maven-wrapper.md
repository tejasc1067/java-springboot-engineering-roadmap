# 08 — The Maven wrapper

The **wrapper** is a small set of files checked into your project that lets anyone build it with a specific Maven version — without having Maven installed. It's the difference between "go install Maven 3.9.x, then run `mvn package`" and "run `./mvnw package` and you're done."

Every Spring Initializr project ships with the wrapper. Every modern open-source Java project ships with the wrapper. By the end of this topic you'll know how it works, when to use it, and how to add it to your own project.

---

## The problem this solves

Maven is a separate program. Your teammate installed Maven 3.8.4. You installed 3.9.6. Your CI uses 3.9.8. Some Maven plugin version is incompatible with one of those. The build behaves differently on different machines. Annoying.

The Maven wrapper pins one Maven version per project. When you run `./mvnw package`, the script:

1. Checks if `~/.m2/wrapper/dists/apache-maven-X.Y.Z/...` exists.
2. If not, downloads that exact Maven version from the URL configured in `.mvn/wrapper/maven-wrapper.properties`.
3. Hands off the command to that Maven.

Result: every developer and CI agent uses the same Maven version. New developers don't have to install Maven at all.

---

## The four files

A project with the wrapper has these files committed:

```text
project-root/
├── mvnw                                         <- bash/zsh launcher (executable)
├── mvnw.cmd                                     <- Windows CMD launcher
└── .mvn/
    └── wrapper/
        └── maven-wrapper.properties             <- pins Maven version + download URL
```

That's it. There's a fourth file historically — `maven-wrapper.jar` — but modern wrappers (Maven 3.8.5+ generated via `mvn wrapper:wrapper`) **do not check that jar into the repo**. The launcher scripts download it lazily on first run. This matches what Spring Initializr produces in 2026 and is recommended over committing a binary jar.

### `maven-wrapper.properties`

```properties
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
wrapperVersion=3.3.2
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
```

- `distributionUrl` — the exact Maven distribution this project uses. Pin a specific version.
- `wrapperVersion` / `wrapperUrl` — which version of the wrapper-jar itself to use.

Changing the Maven version is a one-line edit. Everyone who pulls the next commit gets that version automatically.

---

## How `./mvnw` differs from `mvn`

For the developer, almost nothing. You type one extra character:

```bash
mvn package          # uses whatever Maven is on PATH
./mvnw package       # uses the Maven version pinned in this project
```

Inside, the launcher script:

1. Reads `.mvn/wrapper/maven-wrapper.properties`.
2. Checks `~/.m2/wrapper/dists/apache-maven-3.9.9/...` for the cached distribution.
3. If missing, downloads the zip and unpacks it.
4. Runs that Maven with the arguments you passed.

The first run on a new machine downloads ~10 MB. After that it's instant.

The wrapper does not change any command syntax. All the goals and flags from topic 06 work the same.

---

## On Windows

```cmd
mvnw.cmd package
```

or in PowerShell:

```powershell
.\mvnw.cmd package
```

or, if you're using Git Bash (which is how this repo's setup recommends running things on Windows):

```bash
./mvnw package
```

(The bash version of `mvnw` works inside Git Bash.)

---

## Adding the wrapper to a fresh project

If you have Maven installed and want to add the wrapper to an existing project:

```bash
cd your-project/
mvn wrapper:wrapper -Dmaven=3.9.9
```

This produces `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties` pinned to Maven 3.9.9. Commit all three. Don't commit a `.mvn/wrapper/maven-wrapper.jar` if your `wrapper:wrapper` generated one — modern wrappers don't need it; delete it before committing.

(Spring Initializr does this for you automatically when you generate a new Spring Boot project.)

---

## When to use the wrapper

Almost always. The only cases where you wouldn't:

- A throwaway one-day script.
- A project that's literally just a teaching example (this module's earlier code examples don't have wrappers because the topic doesn't need them).
- A CI environment where Maven is provisioned by other means and your team mandates the install path.

For any real project shared across multiple machines, the wrapper is the default.

---

## Code example: `wrapped-project`

`CODE_EXAMPLES/08-maven-wrapper/wrapped-project/` is a tiny project (one Java file) that ships with `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties` so you can see the layout.

### Run it

On macOS/Linux/Git Bash:

```bash
cd 05-maven-and-gradle/CODE_EXAMPLES/08-maven-wrapper/wrapped-project
./mvnw -q package
java -cp target/wrapped-project-1.0.0.jar com.example.Hello
```

On Windows CMD:

```cmd
cd 05-maven-and-gradle\CODE_EXAMPLES\08-maven-wrapper\wrapped-project
mvnw.cmd -q package
java -cp target/wrapped-project-1.0.0.jar com.example.Hello
```

The first time you run `./mvnw`, you'll see it download Maven 3.9.9. That happens once per machine, then is cached.

Expected output:

```text
hello from a wrapped project
```

You can also confirm the wrapper used the right Maven:

```bash
./mvnw -v
```

```text
Apache Maven 3.9.9
Maven home: /Users/you/.m2/wrapper/dists/apache-maven-3.9.9/...
```

The path is in `~/.m2/wrapper/dists/`, not your system Maven. Even if you have Maven 3.8.4 on `PATH`, the wrapper uses 3.9.9.

---

## What's actually inside `.mvn/`

`.mvn/` is a Maven convention directory for **project-level configuration**. The wrapper puts its properties file there. You'll see other files in real-world projects:

- `.mvn/maven.config` — pass default command-line flags to every Maven invocation. Useful for "every build in this repo uses `-Dfile.encoding=UTF-8`."
- `.mvn/jvm.config` — JVM flags for the Maven process itself. Useful for "give Maven 1 GB of heap because the build is heavy."
- `.mvn/extensions.xml` — register Maven extensions (advanced).

For now: know `.mvn/wrapper/` is where the wrapper lives, and that other things may live alongside.

---

## Common pitfalls

- **Not committing `mvnw` and `mvnw.cmd` to git.** They're shell scripts; they belong in the repo. Without them, the wrapper isn't there for teammates.
- **Committing the old `maven-wrapper.jar`.** Modern wrappers download it lazily; don't ship the binary. (Older projects you encounter will have it — fine to leave; it works either way.)
- **Forgetting `chmod +x mvnw` on macOS/Linux.** Cloning on a Unix machine sometimes loses the execute bit. Fix: `chmod +x mvnw` then commit. Git stores the executable bit; once committed once, it stays.
- **Mixing `./mvnw` and `mvn` in the same project.** Pick one. If the project has a wrapper, always use it — including in CI. Drift between system Maven and wrapper Maven is exactly the problem the wrapper solves.

---

## Try this yourself

1. In `wrapped-project/`, run `./mvnw -v`. Note the Maven home path — it's in your home directory's `.m2/wrapper/dists`. Now run `mvn -v` (system Maven, if installed). Different home, possibly different version.
2. Edit `.mvn/wrapper/maven-wrapper.properties` to pin Maven 3.9.8 instead of 3.9.9. Run `./mvnw -v`. Watch it download the new version. Revert.
3. Try `./mvnw clean package` then `./mvnw -o package` (offline mode). The second one runs entirely from cache — no network needed.

## Self-check

1. What problem does the Maven wrapper solve that just-having-Maven-installed does not?
2. Which files of a wrapper-equipped project must you commit, and which should you not commit?
3. CI runs `mvn package` but the project has a wrapper. Is that wrong? Why or why not?
