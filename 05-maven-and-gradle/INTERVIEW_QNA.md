# Interview Q&A — Module 05 (Build Tools)

Questions a backend interviewer might ask about Maven and Gradle. Each answer is the kind of response that shows actual understanding, not a one-line definition.

---

## Q1 — What is a build tool, and what problem does it solve that you can't solve with `javac` alone?

A build tool is a program that turns source code plus a small declarative description of the project into a deployable artifact. It does four things `javac` alone doesn't:

1. **Resolves dependencies.** Most real projects need third-party libraries. The build tool reads a list of those libraries from your project descriptor (`pom.xml` / `build.gradle`), downloads them and their transitive dependencies from a repository (Maven Central by default), and puts them on the classpath. With `javac` alone you'd be hand-managing every jar.
2. **Reproduces the build.** Same source + same `pom.xml` on any machine produces the same jar. With raw `javac`, the build depends on whatever you happen to have in `lib/`.
3. **Standardizes the lifecycle.** Compile, test, package, install — every project on the team builds the same way with the same commands. Onboarding is "clone, run `./mvnw package`."
4. **Wires plugins.** Code coverage, fat-jar packaging, code generation, static analysis — all of these are plugins that bind into a known lifecycle. You don't write glue code; you declare and configure.

For a 50-line script, `javac` is fine. Past that — past one external jar, past one developer — a build tool is the difference between an ad-hoc setup and a maintainable project.

---

## Q2 — Walk me through what happens when you run `mvn package` on a typical project.

`mvn package` runs the **default lifecycle** up to and including the `package` phase. Specifically:

1. `validate` — sanity-check the POM (well-formed XML, required fields present).
2. `compile` — `maven-compiler-plugin:compile` runs `javac` against `src/main/java/`. Output: `target/classes/`.
3. `test-compile` — compiles `src/test/java/` into `target/test-classes/`.
4. `test` — `maven-surefire-plugin:test` runs JUnit/TestNG tests. If any fail, the build stops here (unless you used `-DskipTests`).
5. `package` — `maven-jar-plugin:jar` (for `<packaging>jar</packaging>`) zips `target/classes/` into `target/<artifactId>-<version>.jar`.

Phases between these (`initialize`, `generate-sources`, etc.) also run; for a typical project they're empty unless a plugin binds to them.

**Two important nuances:**

- **`mvn package` runs tests** — most beginners don't realize that. Use `-DskipTests` to compile-but-not-run tests, or `-Dmaven.test.skip` to skip test compile entirely (rarely correct).
- **Maven uses incremental compilation.** `mvn package` doesn't recompile classes whose source hasn't changed. To force a clean build, prepend `clean`: `mvn clean package`.

---

## Q3 — Explain the four common dependency scopes. When do you use each?

- **`compile`** (default) — available at compile, test, and runtime. Bundled in the jar. Used for libraries the code directly imports and needs at runtime: Jackson, Apache Commons, your domain library, the Spring framework.
- **`test`** — only available when compiling and running tests. Not in the deployed jar. Used for JUnit, Mockito, AssertJ, Testcontainers, anything that exists to help you test.
- **`provided`** — available at compile and test time, not packaged in the jar, expected to be provided by the container at runtime. Classic use: the servlet API in a WAR that deploys to Tomcat — Tomcat provides it. Also used for Lombok (compile-time only) and the JSR annotation jars.
- **`runtime`** — not available at compile time, but on the test and runtime classpath. Bundled in the jar. Used when the code uses an abstraction (`DriverManager`, `LoggerFactory`) and the concrete impl is discovered reflectively or via the service loader. Classic uses: JDBC drivers (`mysql-connector-j`), SLF4J bindings (`logback-classic`, `slf4j-simple`).

The scope choice has two consequences: what's on the classpath at each phase (which controls what you can `import`), and what's bundled in the deployable artifact (which controls size and what's available at runtime). Wrong scope = a `NoSuchMethodError` or `ClassNotFoundException` somewhere downstream of `mvn package`.

---

## Q4 — Two of your dependencies want different versions of slf4j-api. How do you figure out which one Maven picks, and how do you control it?

**Figuring out which won:**

```bash
mvn dependency:tree -Dverbose -Dincludes=org.slf4j:slf4j-api
```

The `-Dverbose` flag adds `(omitted for conflict with X.Y.Z)` annotations next to the loser. The non-omitted version is what's on the classpath.

**Maven's resolution rules**, in order:

1. **Nearest wins** — the version closer to your project in the dependency tree wins. Direct deps always beat transitives.
2. **First-declared wins** when nearest-distance ties — order matters in your `<dependencies>` block.
3. **`<dependencyManagement>` overrides all the above.**

**Three ways to force the version you want:**

- **Direct declaration** — add `slf4j-api` to your `<dependencies>` at the version you want. It's now distance-1; it wins.
- **`<dependencyManagement>`** — pin the version. Applies everywhere, regardless of distance. You don't need it in `<dependencies>` at all.
- **`<exclusions>`** — surgically remove the offending transitive from a specific dependency.

The first two are the day-to-day moves. `<exclusions>` is more invasive — use it when one library is actively pulling in something dangerous (an old `commons-logging`, a banned `log4j 1.x`).

---

## Q5 — What's a `<dependencyManagement>` section and how is it different from `<dependencies>`?

`<dependencies>` is the list of dependencies you actually want on your classpath. Maven downloads each one (plus its transitives) and puts them on the classpath at the appropriate phase.

`<dependencyManagement>` is **declarative version control**, not a dependency list. Entries in it don't add anything to the classpath. They say: "If any of my dependencies (direct or transitive) pulls in artifact X, force version Y."

Three concrete reasons to use it:

1. **Conflict resolution.** Force a specific version of a transitive that's appearing at multiple incompatible versions.
2. **BOM imports.** With `<type>pom</type><scope>import</scope>`, you can import the `<dependencyManagement>` section of another POM in bulk — that's how Spring Boot pins ~200 library versions for you at once.
3. **Multi-module projects.** A parent POM declares pinned versions once; every child uses them by referencing the dependency without a `<version>`.

A short way to put it: `<dependencies>` is "use these," `<dependencyManagement>` is "if you use these, use this version."

---

## Q6 — Why does Spring Boot ship its own Maven plugin (`spring-boot-maven-plugin`) instead of using `maven-shade-plugin` like everyone else?

Both produce executable single-file jars, but Spring Boot's needs are different.

A shaded fat-jar **flattens** every dependency's classes into one jar. That works for most apps, but it breaks anything that depends on `META-INF` files staying in their original locations, or on jar identity (Spring's classpath scanning, signed jars, JPMS modules — all sensitive to this).

The Spring Boot plugin produces an "executable jar" with a **nested layout**:

```text
spring-boot-app.jar
├── BOOT-INF/classes/                       <- your app classes
├── BOOT-INF/lib/                           <- dependency JARS as-is
├── org/springframework/boot/loader/...    <- a custom classloader
└── META-INF/MANIFEST.MF                    <- Main-Class = org.springframework.boot.loader.JarLauncher
```

When you `java -jar` the result, the custom classloader unpacks `BOOT-INF/lib/` virtually and loads classes from there. Each dependency jar stays a jar, with its `META-INF` intact. Spring's classpath scanning, autoconfiguration, and signed jars all work correctly.

Shade is fine for command-line tools and simple services that don't depend on per-jar metadata. Spring Boot apps need the more nuanced packaging.

---

## Q7 — A junior developer says "I added a dependency but it's not on my classpath." What do you check first?

A short, ordered debug checklist:

1. **Did the POM change actually save?** Cmd+S looks trivial but it's the number-one cause.
2. **Did the IDE re-import?** IntelliJ usually re-syncs `pom.xml` automatically; sometimes you need Right-Click -> Maven -> Reload Project. Don't trust the IDE green check until after the reload.
3. **Run `mvn dependency:tree` and grep for the artifact.** If it's not there, the POM is wrong. If it's there with the wrong scope, the scope is the bug.
4. **Check the scope.** A `test`-scope dep is invisible to `src/main/java`. A `runtime`-scope dep is invisible to the compiler.
5. **Did a transitive override your version?** `mvn dependency:tree -Dverbose -Dincludes=group:artifact` — look for "omitted for conflict."
6. **Is the project actually using the POM the IDE thinks it is?** In multi-module projects, IDEs sometimes pick the wrong POM as "the one." `mvn -pl module-name help:effective-pom` shows what Maven sees.

After step 3, 90% of cases are solved. Step 4 is the next 5%.

---

## Q8 — What does `<packaging>pom</packaging>` mean? When do you use it?

`<packaging>pom</packaging>` means "this project does not produce a jar. It exists only as a POM file." Two uses:

1. **Aggregator / parent in a multi-module project.** The root `pom.xml` declares `<modules>core</module><module>app</module></modules>`. Running `mvn package` there walks into each child and builds it. The parent itself produces nothing.
2. **A standalone parent POM published for downstream consumers** — `spring-boot-starter-parent` is the canonical example. You consume it via `<parent>` in your project. It carries pinned versions, plugin configuration, and a Java target. Nothing else.

Both cases share the same property: no `src/main/java/`, no jar to produce. With the default `<packaging>jar</packaging>` Maven would try to compile from `src/main/java/`, find nothing, and fail or produce an empty jar.

---

## Q9 — A teammate's PR removes the version from a `<plugin>` declaration ("Maven picks one anyway"). What do you say?

You'd push back, politely. Here's why:

Maven without a pinned plugin version uses its **super POM defaults**, which are stamped into the Maven distribution itself. Different Maven versions ship with different super POMs.

- Developer A is on Maven 3.9.6 -> compiler plugin 3.11.0 (whatever's in 3.9.6's super POM).
- Developer B is on Maven 3.9.9 -> compiler plugin 3.13.0.
- CI is on Maven 3.9.4 -> something else.

If two of those versions handle some flag differently (e.g. `<release>` interacts subtly with the JDK version), you get different build output depending on whose Maven runs. The "works on my machine" bug is invisible until someone hits it.

The fix is one line: keep the `<version>` on every plugin. `mvn versions:display-plugin-updates` shows you what's available so you can pin a sensible recent version.

The same reasoning is why the Maven wrapper (topic 08) exists for Maven itself — same problem, one layer up.

---

## Q10 — What's the practical difference between Maven and Gradle? When would you pick one over the other in 2026?

The major differences:

- **Language.** Maven is XML; Gradle is Groovy or Kotlin. Maven is verbose and declarative; Gradle is concise and (because it's a real language) potentially imperative — for good and for ill.
- **Performance.** Gradle's build cache and configuration cache make incremental builds significantly faster on large projects. Maven's incremental support exists but is shallower.
- **Lifecycle model.** Maven has a fixed set of phases. Gradle has a directed task graph; you can wire arbitrary tasks together.
- **Ecosystem.** Maven dominates Spring Boot (Initializr defaults to Maven). Gradle dominates Android (where it's mandatory) and large monorepos.

**In 2026, I'd pick:**

- **Maven** for: new Spring Boot projects, standard backend services, teams new to JVM build tools, anywhere the build is "compile, test, package." It's predictable and well-documented; you can read any Maven build by knowing the lifecycle.
- **Gradle** for: anywhere already on Gradle (don't migrate without a reason), Android (no choice), monorepos with custom build logic, projects where build performance is a measurable bottleneck.

I'd avoid migrating just for the language preference. The real cost of build tools is the team's familiarity; switching tools resets that.

---

## Q11 — Your team builds a fat-jar for production. After a Spring Boot upgrade, the deployed jar suddenly fails to start with `NoClassDefFoundError`. Where do you look?

Two likely causes:

1. **A dependency you used to pull in transitively isn't transitive anymore.** Spring Boot major upgrades sometimes drop transitives or move them to optional. The compiled code references a class that was on the classpath in the old version but isn't in the new one. Diagnosis: `mvn dependency:tree` before and after; look for what disappeared. Fix: declare the missing dependency explicitly.
2. **A conflict resolved differently after the upgrade.** A library that was at `1.2.0` transitively now resolves to `2.0.0` because Spring Boot 3.x pinned the newer version in its BOM. Your code uses a class that exists in 1.2 but not in 2.0. Diagnosis: `mvn dependency:tree -Dincludes=...`, compare old/new. Fix: pin the version you need explicitly, or update your code to the new API.

For Spring Boot projects, **always read the upgrade notes**. They document removed transitives and version bumps. Skim them; the bugs are listed.

The general lesson: dependency upgrades are not free. The build can be green while runtime breaks. Test deploy + smoke test in a staging environment before pushing a Spring Boot major upgrade to prod.

---

## Q12 — `mvn dependency:analyze` shows "Used undeclared dependencies." What does that mean, and is it a bug to fix?

It means your code imports a class from a library that you do **not** declare in `<dependencies>`. The library is on the classpath only because it's a transitive of something else you declared.

It's not strictly a bug — the build works. But it's fragile. If the path that brings in the transitive changes (you remove or upgrade the parent dependency), your direct usage suddenly breaks. The dependency on the library is invisible in the POM, so a code reviewer can't see that you rely on it.

The fix is to **add it as a direct dependency** at the version you're using. Now the POM tells the truth: "I need jackson-core, I use it directly, here's what version." Removing `jackson-databind` later won't bite you.

This is one of the most useful Maven hygiene checks. Run `dependency:analyze` periodically; clean up the "used undeclared" list. The opposite list — "declared but unused" — is also informative (you can prune dead dependencies).

---

## Q13 — In Gradle, what's the difference between `implementation` and `api`, and why does it matter?

Both put a dependency on your compile classpath at compile time. The difference is **what consumers of your library see**.

- **`implementation`** — the dependency is internal to your library. Consumers' compile classpath does **not** include it. They can't accidentally `import` your dependency's classes.
- **`api`** — the dependency is part of your library's public API. Consumers' compile classpath **does** include it. They can use it freely.

The practical question is: "Does my library's public API surface this dependency?" If your library's method signatures return `Optional<JsonNode>`, then `jackson-databind` is on your API — declare `api`. If your library only uses `JsonNode` internally and returns plain strings, declare `implementation`.

**Why it matters:**

- **Compile speed.** Changing an `implementation` dep doesn't trigger downstream consumer recompilation. Changing an `api` dep does. Big monorepos save real time using `implementation` aggressively.
- **Encapsulation.** Consumers can't accidentally couple to transitive details they shouldn't see.

When in doubt, use `implementation`. Upgrade to `api` only when the dependency actually leaks through your public API.

This distinction has no clean Maven equivalent — Maven's `compile` scope leaks transitively to consumers' compile classpath by default. Gradle's split is genuinely useful at scale.
