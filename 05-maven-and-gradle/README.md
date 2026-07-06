# 05 — Build Tools: Maven and Gradle

How professional Java projects manage dependencies, compile code, run tests, and package an app — without you running `javac` by hand.

This module exists because of one painful moment in module 04: you had to download `h2-2.2.224.jar` manually, drop it in `lib/`, and remember to put it on the classpath every time. That works for one jar. It does not work for a real Spring Boot app that pulls in 80+ jars. Build tools solve that.

By the end of this module you'll be able to read a `pom.xml`, add a dependency, understand where it came from, and know what `mvn package` actually does to your source code.

## Who this is for

- Beginners who finished module 04 and felt the "one jar is fine, what about ten?" pain.
- Career switchers who've used `npm`, `pip`, or `cargo` and want the Java equivalent vocabulary.

## What you'll be able to do at the end

- [ ] Explain in one sentence what a build tool is and the specific problem it solves.
- [ ] Read a `pom.xml` and identify the project coordinates, dependencies, and plugins.
- [ ] Add a dependency from Maven Central to a project and use it from code.
- [ ] Tell the difference between `compile`, `test`, `provided`, and `runtime` scope and pick the right one.
- [ ] Run a Maven build through its lifecycle phases (`validate` -> `compile` -> `test` -> `package` -> `install`) and explain what each phase does.
- [ ] Use a plugin (the Shade plugin) to produce an executable fat-jar.
- [ ] Run a project that uses `./mvnw` without installing Maven yourself.
- [ ] Diagnose a dependency conflict with `mvn dependency:tree` and resolve it with `<exclusions>` or `<dependencyManagement>`.
- [ ] Lay out a multi-module project with a parent POM and child modules.
- [ ] Read a `build.gradle` (Groovy) and a `build.gradle.kts` (Kotlin) and recognize the same concepts.

If you can do all 10 at the end, the module worked.

## Prerequisites

- Module 04 done. You should remember the pain of `java --class-path "lib/h2-2.2.224.jar" SomeFile.java`.
- A JDK installed (Java 17 or newer; we target Java 21 in examples — the Spring Boot 3.x LTS standard).
- Comfort opening a terminal and running commands.

## How this module is organized

Topics 01–03 set up *why* and *what* — the pain, the install, the file format. No deep dives yet.

Topics 04–07 are the working parts you'll use daily: dependencies, scopes, the lifecycle, and plugins.

Topics 08–10 are the things you'll meet on real teams: the wrapper that pins a Maven version, dependency conflicts that bite in production, and multi-module project layouts.

Topic 11 is a one-sitting tour of Gradle so you can read a `build.gradle` without panic.

```text
01-why-build-tools                  <- the no-build-tool pain from module 04
02-maven-setup-and-layout           <- install, standard directory layout, hello-maven
03-pom-anatomy                      <- every section of a pom.xml, explained
04-dependencies-and-maven-central   <- adding libraries that aren't on your disk
05-dependency-scopes                <- compile / test / provided / runtime
06-maven-lifecycle                  <- phases, goals, what `mvn package` really does
07-plugins                          <- compiler, surefire, shade (fat-jar)
08-maven-wrapper                    <- mvnw / mvnw.cmd, version pinning
09-dependency-conflicts             <- two libs want different versions of slf4j
10-multi-module-projects            <- parent POM + child modules
11-gradle-comparison                <- the same concepts in Gradle (Groovy + Kotlin DSL)
```

```text
COMMON_MISTAKES.md       <- real Maven/Gradle bugs with code and fix
INTERVIEW_QNA.md         <- interview-grade questions with deep answers
```

## Checkpoint

You're done with this module when you can:

1. Open any open-source Spring Boot repo on GitHub, find its `pom.xml`, and explain what each top-level section is for.
2. Tell a teammate "your build is failing because the `mysql-connector` is in `compile` scope but should be `runtime`" and explain why.
3. Run `./mvnw clean package` on a new laptop where Maven isn't installed and have it work.
4. Read a `mvn dependency:tree` output and say "this transitive `slf4j-api:1.7.36` is being overridden by our direct `2.0.x` declaration — that's why the warning went away."

If those four feel comfortable, move to module 06 (Unit Testing) where you'll start using JUnit 5 inside a real Maven build.

## Why Maven first, Gradle second

In 2026, the Java backend ecosystem is roughly 70% Maven, 30% Gradle (Spring projects skew Maven; Android skews Gradle). Spring Initializr defaults to Maven. You will spend more time reading `pom.xml` than `build.gradle`. The 11 topics reflect that: 10 Maven topics + 1 Gradle tour. Once you understand the Maven model (coordinates, scopes, lifecycle, plugins), Gradle is the same ideas with different syntax.

## A note on what's not here

- No Spring Boot Maven plugin yet — that lives in module 08.
- No CI/CD configuration. That belongs in module 14 (Docker & DevOps), later in the roadmap.
- No deep Gradle (custom tasks, Kotlin build logic, composite builds). One topic is enough to read other people's builds; deeper Gradle is its own roadmap.
