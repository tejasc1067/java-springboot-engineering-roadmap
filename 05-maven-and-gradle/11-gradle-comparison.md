# 11 — Gradle in one sitting

Gradle is the other major Java build tool. In 2026, it's used by Android, by some Spring teams, and by performance-sensitive projects that want incremental builds and a faster dev loop. This topic is a one-sitting tour so you can read a `build.gradle` (or `build.gradle.kts`) and recognize the same concepts you learned for Maven.

Two code examples come with this topic — one Groovy DSL, one Kotlin DSL — both compiling the same Hello-world.

---

## The problem this solves

You finish onboarding to a Java team. The repo's build file is `build.gradle.kts`. You learned Maven. Nothing on the screen looks like a `pom.xml`. Without a map, you're stuck. This topic provides the map.

---

## Mapping the concepts

| Maven concept                             | Gradle equivalent                                          |
|-------------------------------------------|------------------------------------------------------------|
| `pom.xml`                                 | `build.gradle` (Groovy DSL) or `build.gradle.kts` (Kotlin DSL) |
| `groupId` / `artifactId` / `version`      | `group` / `name` (from `settings.gradle`) / `version`     |
| `<packaging>jar</packaging>`              | `plugins { id 'java' }`                                    |
| `<dependencies>`                          | `dependencies { ... }`                                     |
| Scope `compile`                           | Configuration `implementation`                             |
| Scope `test`                              | Configuration `testImplementation`                         |
| Scope `runtime`                           | Configuration `runtimeOnly`                                |
| Scope `provided`                          | Configuration `compileOnly` (+ optional `testCompileOnly`) |
| `mvn package`                             | `gradle build` (or `gradle assemble` for no-test build)    |
| `mvn test`                                | `gradle test`                                              |
| `mvn dependency:tree`                     | `gradle dependencies`                                      |
| `~/.m2/repository/`                       | `~/.gradle/caches/modules-2/files-2.1/`                    |
| Lifecycle phases                          | Tasks (with a task graph)                                  |
| `mvn wrapper:wrapper` -> `./mvnw`          | `gradle wrapper` -> `./gradlew`                            |
| `<plugin>` in `<build>`                   | `plugins { id 'something' version 'X' }` block             |
| Multi-module: `<modules>`                 | `settings.gradle` with `include 'core', 'app'`             |

Print this table, paste it next to your monitor when you first switch projects.

---

## Two DSLs: Groovy and Kotlin

Gradle has two scripting languages:

- **Groovy DSL** — `build.gradle`. The original. Looser syntax, more "scripty," historically the default.
- **Kotlin DSL** — `build.gradle.kts`. Type-safe, IDE auto-completes everything, the **Spring Initializr 2026 default for new Gradle projects**.

Both compile to the same Gradle model. You'll see both in real repos. The differences are mostly syntactic: Groovy uses single-quotes and optional parentheses; Kotlin uses double-quotes, mandatory parens, and `val`/`var` for properties.

### Groovy DSL example (`build.gradle`)

```groovy
plugins {
    id 'java'
    id 'application'
}

group = 'com.example'
version = '1.0.0'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.16.1'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = 'com.example.Hello'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

### Kotlin DSL example (`build.gradle.kts`)

```kotlin
plugins {
    java
    application
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.example.Hello"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
```

Same project, two syntaxes. Note:

- **`group` / `version`** — top-level. (The `name` comes from `settings.gradle` — Gradle separates "what to call this build" from "what it contains.")
- **`plugins { ... }`** — replaces Maven's `<build><plugins>`. Plugins add tasks and configuration.
- **`dependencies { ... }`** — single block with **configurations** (`implementation`, `testImplementation`, `runtimeOnly`, `compileOnly`) instead of `<scope>`.
- **`tasks.named('test')`** — Gradle's whole build is a graph of tasks. You configure them by name.

---

## Configurations vs. scopes — the one mental shift

The Maven->Gradle mental shift is from "scope on a single dependency" to "configuration that the dependency goes into."

```groovy
implementation 'org.slf4j:slf4j-api:2.0.13'         // compile + runtime, NOT exposed to consumers' compile classpath
api 'org.slf4j:slf4j-api:2.0.13'                    // compile + runtime, IS exposed to consumers' compile classpath
testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'   // compile/run tests only
runtimeOnly 'com.mysql:mysql-connector-j:8.4.0'     // not on compile classpath; on runtime
compileOnly 'jakarta.servlet:jakarta.servlet-api:6.0.0'  // compile only; not bundled
```

The big new one is `implementation` vs. `api`. In Maven, every `compile` dependency leaks to your consumers' compile classpath. Gradle separates these:

- **`implementation`** — your code uses this internally; consumers don't need to see it on their compile classpath. (Most things.)
- **`api`** — your code's public API exposes this; consumers must see it. (Anything that appears in your public method signatures.)

This means Gradle can sometimes compile downstream modules faster: changing an `implementation` dep doesn't recompile consumers.

For a beginner, the rule of thumb is "use `implementation` for everything unless a teammate tells you otherwise." Picking the wrong one rarely breaks anything immediately; the cost shows up later.

---

## Tasks instead of lifecycle phases

Maven has phases (`compile`, `test`, `package`) bound to plugin goals. Gradle has **tasks**, and the build is a graph. Common tasks the `java` plugin adds:

```bash
gradle build              # compile + test + jar + check (everything)
gradle assemble           # compile + jar, skip tests
gradle test               # compile + run tests
gradle compileJava        # just compile main
gradle clean              # delete build/
gradle dependencies       # show dependency graph
gradle tasks              # list all available tasks
gradle wrapper            # generate ./gradlew + gradle/wrapper/...
```

The output goes to `build/` (not `target/` like Maven).

---

## The wrapper, again

Gradle has its own wrapper, the same idea as Maven's:

```text
project/
├── gradlew                              <- POSIX launcher
├── gradlew.bat                          <- Windows launcher
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.properties    <- pins Gradle version
│       └── gradle-wrapper.jar           <- here it IS committed (Gradle convention)
└── build.gradle.kts
```

Run `./gradlew build` instead of `gradle build`. Same idea as `./mvnw`.

One small difference: Gradle's wrapper **does** check `gradle-wrapper.jar` into the repo (it's small, ~60 KB). Maven's modern wrapper doesn't. Both are correct for their respective tools.

---

## When you'd pick Gradle over Maven

Honest summary in 2026:

- **Maven** if you're new, you're doing Spring Boot, you want the most-documented stack, and your build is "compile, test, run." That's most backend jobs.
- **Gradle** if you're on Android (mandatory), if you want incremental builds and Build Cache (real wins on giant codebases), or if you're already in a Gradle shop.

For learning, Maven first is the right call. After this topic you'll be able to read Gradle when you encounter it. Going deeper on Gradle (custom tasks, plugin development, Kotlin build logic, build cache, configuration cache) is a separate journey.

---

## Code examples: `hello-gradle-groovy` and `hello-gradle-kotlin`

Two minimal projects, one per DSL, both producing the same Hello jar.

### `hello-gradle-groovy/`

```text
hello-gradle-groovy/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/Hello.java
```

### `hello-gradle-kotlin/`

```text
hello-gradle-kotlin/
├── settings.gradle.kts
├── build.gradle.kts
└── src/main/java/com/example/Hello.java
```

### Run them

If you have Gradle installed:

```bash
cd hello-gradle-groovy        # or hello-gradle-kotlin
gradle -q run
```

Expected output:

```text
hello from a Gradle project
```

If you don't have Gradle installed, you can generate the wrapper from a machine that does (`gradle wrapper`) and commit `gradlew` + `gradlew.bat` + `gradle/wrapper/`. Subsequent runs use `./gradlew run`.

> These examples do **not** ship with the wrapper, intentionally — adding ~50 KB of Gradle bootstrap files to a teaching repo would distract from the lesson. In a real project you'd always ship the wrapper.

---

## Common pitfalls

- **Mixing Groovy and Kotlin DSL in one project.** You can, but don't. Pick one and stick.
- **Using `compile` configuration.** It's deprecated since Gradle 7. Use `implementation` (or `api` when needed). If you see `compile 'org.slf4j...'` in an older repo, that's why.
- **Forgetting `useJUnitPlatform()`.** Gradle defaults to JUnit 4. Without that line, JUnit 5 tests don't run and the build silently passes with "0 tests."
- **Looking for `target/`.** Gradle's output dir is `build/`. Different convention from Maven.
- **`mvn` in a Gradle project, `gradle` in a Maven project.** They don't translate. Two different tools, two different files.

---

## Try this yourself

1. In `hello-gradle-groovy`, swap `implementation` for `api` on a dependency. Run `gradle dependencies` and compare the configurations. (You'll see `apiElements` and `compileClasspath` listed; `api` deps show up in both.)
2. Open `spring-projects/spring-petclinic` on GitHub. It has both a Maven and a Gradle branch. Compare `pom.xml` to `build.gradle`. Same project, same dependencies, two build files.
3. Read the Gradle Build Lifecycle docs (`https://docs.gradle.org/current/userguide/build_lifecycle.html`) once. The "Initialization, Configuration, Execution" phases are a separate concept from tasks and matter when you debug a slow build.

## Self-check

1. What's the Gradle equivalent of Maven's `<scope>test</scope>` and `<scope>runtime</scope>`?
2. When would you use `api` instead of `implementation`?
3. Your team is on Spring Boot 3.x and has no Android code. Maven or Gradle, and why?
