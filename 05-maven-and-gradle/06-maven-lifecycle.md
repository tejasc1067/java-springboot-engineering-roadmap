# 06 — Maven lifecycle and goals

When you type `mvn package`, what actually runs? This topic answers that. You'll come out knowing what every common Maven command does, what order things execute in, and how to skip phases when you need to move fast.

There's no separate code example — the explanations apply to **any** Maven project, including `hello-maven` from topic 02 and `deps-demo` from topic 04.

---

## The problem this solves

`mvn compile`, `mvn test`, `mvn package`, `mvn install` — beginners often type one of these by superstition. They work, but the developer doesn't know what's happening or what runs in between. That makes Maven feel like magic. Understanding the lifecycle replaces magic with a predictable sequence.

---

## Three lifecycles, but you only need one

Maven defines three built-in lifecycles:

1. **default** — compile, test, package, install, deploy. This is the one you use every day.
2. **clean** — deletes `target/`. A single phase: `clean`.
3. **site** — generates HTML reports about the project. Rarely used.

The rest of this topic focuses on `default`.

---

## The default lifecycle: phases

A **lifecycle** is an ordered list of **phases**. Running a phase runs all the phases before it, in order. There are 23 phases total; here are the ones you'll meet:

```text
validate          <- check that the project is sound (POM well-formed, etc.)
   |
compile           <- compile main code (src/main/java -> target/classes)
   |
test-compile      <- compile test code (src/test/java -> target/test-classes)
   |
test              <- run unit tests (Surefire plugin)
   |
package           <- build the jar (target/x-y.jar)
   |
verify            <- run integration tests, quality checks
   |
install           <- copy the jar to ~/.m2/repository so OTHER local projects can depend on it
   |
deploy            <- copy the jar to a remote repository (a company Nexus, Maven Central, ...)
```

When you run `mvn install`, Maven runs **everything from `validate` through `install`** in order. There's no "just install, skip the rest" — phases compose.

### What `mvn package` actually does

```bash
mvn package
```

Under the hood:

1. `validate` — sanity-check the POM.
2. (also runs: `initialize`, `generate-sources`, etc. — usually empty for typical projects.)
3. `compile` — Maven binds the compiler plugin's `compile` goal to this phase. `javac` runs.
4. `test-compile` — compiler plugin again, this time for tests.
5. `test` — Surefire plugin's `test` goal runs JUnit/TestNG tests.
6. `package` — Jar plugin's `jar` goal zips `target/classes/` into `target/x-y.jar`.

That's why `mvn package` produces a jar **and** runs tests as a side effect. Tests are a prerequisite of packaging by design.

### What `mvn install` adds

Same as above, then:

7. `verify` — usually empty for simple projects; Failsafe plugin runs here for integration tests.
8. `install` — copies your jar into `~/.m2/repository/com/example/your-app/1.0.0/` so other local projects can use it as a dependency.

### What `mvn deploy` adds

Same as install, then:

9. `deploy` — uploads the jar to a remote repository (Nexus, Artifactory, Maven Central). You almost never run this from your laptop; CI does.

---

## Phases vs. goals

**Phase** is a step in the lifecycle ("compile", "test"). **Goal** is a specific task a plugin can do ("`maven-compiler-plugin:compile`", "`maven-surefire-plugin:test`"). Phases get goals bound to them.

The full identifier of a goal is `groupId:artifactId:goal` or its short form. So:

```bash
mvn compile
```

runs whatever goals are bound to the `compile` phase (by default, `maven-compiler-plugin:compile`).

And:

```bash
mvn dependency:tree
```

invokes the `tree` goal of the `maven-dependency-plugin` directly, without running a phase at all. That's why `dependency:tree` runs instantly even though `compile` is slow — you're skipping the lifecycle entirely.

A common mistake is thinking `compile` and `package` are commands. They're phases. `dependency:tree` and `exec:java` are goals. You can mix them on one line:

```bash
mvn clean compile dependency:tree
```

This runs the `clean` lifecycle, then the `compile` phase (which also runs everything before it), then invokes the `dependency:tree` goal.

---

## The cheat sheet

The commands you'll actually run, day to day:

```bash
mvn clean                  # delete target/
mvn compile                # compile src/main/java
mvn test                   # compile main + tests + run tests
mvn package                # everything above + produce target/x.jar
mvn install                # everything above + copy x.jar into ~/.m2/repository
mvn verify                 # everything except install; common in CI
mvn clean package          # most common combo: fresh build + jar
mvn clean install          # most common when other local projects depend on this one
```

Plugin goals you'll run often:

```bash
mvn dependency:tree                    # show dependency graph
mvn dependency:list                    # show flat dependency list
mvn dependency:analyze                 # find declared-but-unused, used-but-undeclared
mvn help:effective-pom                 # show the POM after inheritance + property substitution
mvn versions:display-dependency-updates  # which of your deps have newer versions?  (versions-maven-plugin)
mvn exec:java -Dexec.mainClass=com.example.App   # run a main class
```

The `dependency:*` family is the most useful for diagnosing weird builds. Get used to them early.

---

## Skipping things — when you need to move fast

Sometimes you want a jar without running tests (debugging, an emergency). The flags:

```bash
mvn package -DskipTests       # compile tests but don't run them
mvn package -Dmaven.test.skip # don't even compile tests (faster but less safe)
```

Use `-DskipTests` 95% of the time. `-Dmaven.test.skip` only when test compilation itself is the problem.

```bash
mvn package -o                # offline mode; don't contact any repository
mvn package -U                # force-update SNAPSHOT dependencies
mvn package -X                # debug output (very verbose; for diagnosing failures)
mvn package -q                # quiet mode; only WARN and above
mvn package --fail-fast       # stop at first module failure in a multi-module project
mvn package --fail-at-end     # finish what you can, report failures at the end
```

`-q` is one of the most-loved-and-least-used flags. Use it once and you won't go back.

---

## What does "phase X is bound to goal Y" mean, concretely?

Inside `pom.xml`, a plugin declaration can pin itself to a phase:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>           <!-- when to run -->
            <goals>
                <goal>shade</goal>            <!-- what to do -->
            </goals>
        </execution>
    </executions>
</plugin>
```

Whenever you run any phase at or after `package` (e.g. `mvn install`), Maven invokes the `shade` goal. The default `package` step (regular jar) also happens. Plugins add to the lifecycle; they don't replace it (unless they're configured to).

This is how `<packaging>jar</packaging>` decides what gets bound to each phase. There's a default mapping for each packaging type. `pom` packaging (used by parent POMs) binds almost nothing — that's why `mvn install` on a `pom`-packaged project is fast.

---

## A worked example

You're in `deps-demo/` from topic 04. You run:

```bash
mvn -q clean package
```

What happens:

1. `clean` lifecycle: delete `target/`.
2. `default` lifecycle, starting at `validate`, ending at `package`.
3. `validate` — POM looks fine.
4. `compile` — `maven-compiler-plugin:compile` runs `javac` against `src/main/java/`. Output: `target/classes/com/example/JsonReadWrite.class`.
5. `test-compile` — `src/test/java/` is empty, so this is a no-op.
6. `test` — `maven-surefire-plugin:test` looks for tests; finds none; passes trivially.
7. `package` — `maven-jar-plugin:jar` zips `target/classes/` into `target/deps-demo-1.0.0.jar`.
8. End.

Maven stops at `package` because that's the phase you requested. It did not run `install`.

Now you run:

```bash
mvn -q dependency:tree
```

What happens:

1. No phase is named, so no lifecycle runs.
2. `dependency:tree` goal of `maven-dependency-plugin` runs directly.
3. It prints the dependency graph.
4. End. No compilation, no jar, no test execution.

This is much faster (~0.5s) than `mvn package` (~5s).

---

## When the lifecycle isn't what's slow

If your build feels slow, the lifecycle isn't the usual culprit — plugin work is. The compiler is fast on a small project. The slow parts are typically:

- Surefire running hundreds of tests.
- `mvn install` to a large remote SNAPSHOT repository.
- A custom plugin (e.g. JaCoCo coverage instrumentation) doing extra work.

`mvn -q clean install -X` prints timestamps per phase; you can see which phase ate the time. On a Spring Boot project the build-fat-jar phase often dominates.

---

## Common pitfalls

- **Running `mvn install` when you don't need to.** `install` copies into `~/.m2/repository/`, which is only useful when other local projects depend on this one. For routine "I want a jar," use `mvn package`. It's faster and cleaner.
- **Believing `mvn test` skips compile.** It doesn't. Phases compose. `mvn test` runs `compile` and `test-compile` first.
- **Assuming `clean` is part of every command.** It isn't. If you change a Java file and run `mvn package`, Maven uses incremental compilation — only changed files recompile. That's by design and fast. Use `mvn clean package` only when you suspect stale `target/` content.
- **`mvn -Dmaven.test.skip` then puzzling over test failures.** `-Dmaven.test.skip=true` skips compiling *and* running tests. If you change a test and the change "doesn't take effect," check whether you're skipping the test compile.

---

## Try this yourself

1. In `hello-maven/` from topic 02, run `mvn -X package 2>&1 | head -60`. Watch the lifecycle phases scroll past: `validate`, `initialize`, `generate-sources`, ... `compile`, `test`, `package`. You'll see each plugin "execution" log line.
2. Run `mvn help:effective-pom > effective.xml`. Open `effective.xml`. This is the POM as Maven sees it after inheritance and defaults — usually 200+ lines for a 30-line `pom.xml`. You'll see the default plugin bindings that produce the jar.
3. Run `mvn dependency:analyze` on `deps-demo` from topic 04. Note: it may report nothing useful here (the project's dependencies are all actually used). Real projects often surface "Used undeclared dependencies" — transitives that your code imports directly and should be declared explicitly.

## Self-check

1. What's the difference between a phase and a goal? Give one example of each.
2. If you run `mvn install`, list every phase that runs (in order) before `install` actually executes.
3. Your CI runs `mvn -DskipTests=true package`. A developer says "tests passed in CI." What did CI actually verify, and what didn't it verify?
