# 11-gradle-comparison

Two minimal Gradle projects, side by side, building the same `Hello.java`.

- `hello-gradle-groovy/` — Groovy DSL (`build.gradle`, `settings.gradle`).
- `hello-gradle-kotlin/` — Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`).

## Run either one

If you have Gradle installed:

```bash
cd hello-gradle-groovy        # or hello-gradle-kotlin
gradle -q run
```

Expected output:

```text
hello from a Gradle project
```

## No Gradle installed?

Generate the wrapper from a machine that has Gradle:

```bash
gradle wrapper
```

That creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/`. Commit those and future runs use `./gradlew run` with no Gradle install needed.

These examples are intentionally **not** shipped with the wrapper — the lesson is the two DSL syntaxes, not the bootstrap files.

## The point

Same project model — group, version, deps, plugins, mainClass — two slightly different syntaxes. Once you spot the mapping (Maven `<scope>compile</scope>` -> Gradle `implementation`, etc.) reading a Gradle build feels like reading a `pom.xml`.
