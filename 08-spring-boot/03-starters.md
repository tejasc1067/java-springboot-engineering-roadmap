# Starters — Curated Dependency Bundles

In topic 02 you added two dependencies (`spring-boot-starter` and `spring-boot-starter-test`) and got a Spring container, YAML loading, Logback, JUnit 5, AssertJ, and Mockito — all at versions you didn't pick. That is the starter mechanism. This topic explains what a starter actually is, why "I didn't pick the version" is the headline benefit, and which starters you'll meet in the rest of the roadmap.

By the end of this topic you can read a `pom.xml`, predict what each starter pulls in, and run `mvn dependency:tree` to confirm.

---

## The problem this solves

A typical plain-Spring web app needs roughly these dependencies:

```xml
<dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>6.2.18</version></dependency>
<dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId><version>6.2.18</version></dependency>
<dependency><groupId>org.springframework</groupId><artifactId>spring-aop</artifactId><version>6.2.18</version></dependency>
<dependency><groupId>org.aspectj</groupId><artifactId>aspectjweaver</artifactId><version>1.9.25.1</version></dependency>
<dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.18.1</version></dependency>
<dependency><groupId>org.apache.tomcat.embed</groupId><artifactId>tomcat-embed-core</artifactId><version>10.1.30</version></dependency>
<dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.0.0</version></dependency>
<dependency><groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId><version>3.0.2</version></dependency>
<dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>8.0.1.Final</version></dependency>
<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.16</version></dependency>
<dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.5.12</version></dependency>
```

Eleven libraries, eleven versions, and the eleven you see are only the *direct* dependencies — each pulls in more transitively. Pick the wrong combination and you get one of three failure modes:

- **NoClassDefFoundError at runtime.** A class moved between packages between major versions; one of your direct deps was written against the old location, another against the new one.
- **AbstractMethodError at runtime.** An interface gained a default method in version N+1, but one of your transitive deps was built against version N and didn't see it.
- **Silent behavior change.** Two of your transitive deps both publish a class with the same fully-qualified name; the classpath order picks one, and which one depends on Maven's resolution order.

These failures are not bugs in the libraries. They are bugs in *the set you assembled*. Some version of every library works together; you just haven't found it.

The Spring Boot team's job is to find it for you. A **starter** is the answer they hand back.

---

## What a starter actually is

A starter is a Maven artifact whose only job is to pull in other Maven artifacts. It contains no Java code. If you look at the `spring-boot-starter` jar on disk, it has a `META-INF` directory and not a single `.class` file.

```xml
<!-- spring-boot-starter's own pom.xml, abbreviated -->
<dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-autoconfigure</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-logging</artifactId></dependency>
    <dependency><groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId></dependency>
    <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId></dependency>
    <dependency><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId></dependency>
</dependencies>
```

The versions are deliberately missing. Maven's rule when no version is specified on a dependency: look up the chain to a parent POM that *does* specify a version. That parent is what `spring-boot-starter-parent` is for.

---

## How the parent BOM works

When your project's `pom.xml` declared `<parent>spring-boot-starter-parent</parent>`, two things happened:

1. **Inheritance.** Your project inherited `<properties>`, `<build>` configuration, and (importantly) a giant `<dependencyManagement>` block from the parent.
2. **Version resolution.** Maven now consults that `<dependencyManagement>` block whenever a dependency in your project (or in a starter you pulled in) omits its `<version>`. The block pins versions for hundreds of artifacts — every Spring artifact, every starter, every transitive that Boot needs, plus common companions like Jackson, Logback, Hibernate, Lombok, Caffeine, Mockito.

A peek at what the parent pins (small slice of about 800 entries):

```xml
<!-- spring-boot-dependencies (the BOM that the parent uses), abbreviated -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>${spring-framework.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>${mockito.version}</version>
        </dependency>
        <!-- ...hundreds more... -->
    </dependencies>
</dependencyManagement>
```

The version properties (`spring-framework.version`, `jackson.version`, `mockito.version`) are also defined in the parent, all together, version-aligned and tested as a set on Spring Boot's CI.

**The user-visible effect:** you declare `<dependency>org.mockito:mockito-core</dependency>` with no version, and you get whatever Mockito version the Boot team tested with 3.5.14. If next month you bump the parent to 3.5.15, every transitive moves together — no opportunity for the eleven-library version graph to drift.

---

## The starters you'll meet first

Spring Boot ships dozens of starters. Three matter for module 08 and module 09:

### `spring-boot-starter` — the core

Brings in:

- `spring-boot` itself (the `SpringApplication` class and friends)
- `spring-boot-autoconfigure` (topic 04's machinery)
- `spring-core`, `spring-context`, `spring-beans` (the IoC container from module 07)
- The logging stack (`spring-boot-starter-logging` → SLF4J API + Logback Classic, with Log4j and JUL bridges)
- `snakeyaml` (so `application.yml` works without you doing anything)
- `jakarta.annotation-api` (so `@PostConstruct` and `@PreDestroy` work)

That's the minimum every Boot app pulls in.

### `spring-boot-starter-test` — the test stack

What `hello-demo`'s `pom.xml` has had since topic 02:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

That one line pulls in:

- **JUnit Jupiter** (`junit-jupiter`, `junit-platform`) — the test runner and `@Test`, `@BeforeEach`, etc.
- **AssertJ** (`assertj-core`) — `assertThat(...).isEqualTo(...)`.
- **Mockito** (`mockito-core`, `mockito-junit-jupiter`) — `mock(...)`, `when(...).thenReturn(...)`.
- **Hamcrest** (`hamcrest`) — `is`, `equalTo`, used by some Spring assertions.
- **JsonPath** (`json-path`) — `$.field.subfield` queries against JSON responses.
- **JSONassert** (`jsonassert`) — structural JSON comparison.
- **Spring Test** (`spring-test`) — `@SpringBootTest`, `MockMvc`, `TestRestTemplate`.
- **XMLUnit** (`xmlunit-core`) — XML comparison if you ever need it.

Eight test libraries, all version-aligned to whatever Boot tested against 3.5.14. Your test class can use any of them with no extra pom entries. The `starters-demo` for this topic exercises three of them in a single test file to make that visceral.

### `spring-boot-starter-web` — preview for topic 09

You don't use this yet, but it's the third starter you'll meet most often:

- `spring-webmvc` (`@RestController`, `@RequestMapping`)
- `spring-web` (the lower-level HTTP plumbing)
- Embedded Tomcat (`tomcat-embed-core`, `tomcat-embed-el`, `tomcat-embed-websocket`)
- `jackson-databind` (so JSON serialization just works)
- `jakarta.validation-api` (so `@Valid` annotations compile; the implementation comes in via `spring-boot-starter-validation`)

Adding this one starter to a Boot app is what turns it into an HTTP service. Topic 09.

---

## What else exists (the starter catalogue)

You don't need to memorize this list. You need to recognize the pattern: there is a starter for almost every common library Spring integrates with.

| Starter                                | What it brings in                                                  | Module |
|----------------------------------------|--------------------------------------------------------------------|--------|
| `spring-boot-starter`                  | core context, logging, YAML                                        | 08     |
| `spring-boot-starter-test`             | full test stack                                                    | 08, 09 |
| `spring-boot-starter-web`              | embedded Tomcat + Spring MVC + Jackson                             | 09     |
| `spring-boot-starter-validation`       | jakarta.validation + Hibernate Validator                           | 09     |
| `spring-boot-starter-actuator`         | health, info, metrics endpoints                                    | 12     |
| `spring-boot-starter-data-jpa`         | Hibernate, Spring Data JPA, JDBC pool                              | 10     |
| `spring-boot-starter-security`         | Spring Security, login, password encoding                          | 12     |
| `spring-boot-starter-jdbc`             | JDBC template + HikariCP (without JPA)                             | 10     |
| `spring-boot-starter-amqp`             | RabbitMQ client + Spring AMQP                                      | 17     |
| `spring-boot-starter-cache`            | Spring's `@Cacheable` plumbing                                     | future |
| `spring-boot-starter-mail`             | JavaMail integration                                               | future |

Whatever third-party library you need, search Maven Central for `spring-boot-starter-<thing>`. If it exists, use it — that's the curated set.

---

## A custom starter, briefly

Companies often publish their own starters internally to bundle "the libraries every service at this company should have" — observability agents, common security config, an internal logger format. Building one is a topic for module 19 (or later); for now, the recognition is enough: a starter is just a `pom.xml` with `<dependencies>` and zero Java code, optionally paired with an autoconfigure module that ships `@Configuration` classes.

---

## Reading `mvn dependency:tree`

The `starters-demo` for this topic has the smallest possible pom — two starters. Run `mvn dependency:tree` and you see roughly thirty entries:

```text
[INFO] com.example:starters-demo:jar:1.0.0
[INFO] +- org.springframework.boot:spring-boot-starter:jar:3.5.14:compile
[INFO] |  +- org.springframework.boot:spring-boot:jar:3.5.14:compile
[INFO] |  |  \- org.springframework:spring-context:jar:6.2.x:compile
[INFO] |  |     +- org.springframework:spring-aop:jar:6.2.x:compile
[INFO] |  |     +- org.springframework:spring-beans:jar:6.2.x:compile
[INFO] |  |     \- org.springframework:spring-expression:jar:6.2.x:compile
[INFO] |  +- org.springframework.boot:spring-boot-autoconfigure:jar:3.5.14:compile
[INFO] |  +- org.springframework.boot:spring-boot-starter-logging:jar:3.5.14:compile
[INFO] |  |  +- ch.qos.logback:logback-classic:jar:1.5.x:compile
[INFO] |  |  +- org.apache.logging.log4j:log4j-to-slf4j:jar:2.x:compile
[INFO] |  |  \- org.slf4j:jul-to-slf4j:jar:2.0.x:compile
[INFO] |  +- jakarta.annotation:jakarta.annotation-api:jar:2.1.1:compile
[INFO] |  +- org.springframework:spring-core:jar:6.2.x:compile
[INFO] |  \- org.yaml:snakeyaml:jar:2.x:compile
[INFO] \- org.springframework.boot:spring-boot-starter-test:jar:3.5.14:test
[INFO]    +- org.springframework.boot:spring-boot-test:jar:3.5.14:test
[INFO]    +- org.springframework.boot:spring-boot-test-autoconfigure:jar:3.5.14:test
[INFO]    +- com.jayway.jsonpath:json-path:jar:2.x:test
[INFO]    +- jakarta.xml.bind:jakarta.xml.bind-api:jar:4.x:test
[INFO]    +- net.minidev:json-smart:jar:2.x:test
[INFO]    +- org.assertj:assertj-core:jar:3.27.x:test
[INFO]    +- org.junit.jupiter:junit-jupiter:jar:5.12.x:test
[INFO]    +- org.mockito:mockito-core:jar:5.x:test
[INFO]    +- org.mockito:mockito-junit-jupiter:jar:5.x:test
[INFO]    \- org.springframework:spring-test:jar:6.2.x:test
```

Three things to notice:

1. The tree is *flat for each starter*. You didn't have to walk an arbitrary dependency graph yourself.
2. Every version is the one the parent pinned. Your `pom.xml` lists `<version>` zero times for these.
3. Every artifact's scope is correct. `spring-boot-starter` is `compile` (in your main code); `spring-boot-starter-test` is `test` (test code only). The starter knew which scope to set.

If you ever wonder "what did this one starter pull in," `dependency:tree` is the answer. Pipe it through `grep` for a specific artifact (`grep jackson`) to find where it came from.

---

## When you *do* need to override a version

The parent's pinned versions are usually right. Three cases when you legitimately want to override:

1. **CVE in a transitive dep, fix not yet in Boot's release.** A new Jackson patch fixes a security issue; Boot 3.5.14 still pins the old one. You override by setting the version property in your `pom.xml`:
    ```xml
    <properties>
        <jackson.version>2.18.3</jackson.version>
    </properties>
    ```
    The parent uses that property to set the version, so overriding the property bumps every Jackson artifact together. Don't directly pin individual Jackson artifacts — you'll split the family.

2. **You need a feature in a newer version of a library Boot uses.** Same approach: override the version property. Read the Spring Boot release notes when upgrading the parent so you can drop the override once Boot catches up.

3. **You need a *different* library entirely.** Want Jetty instead of Tomcat? Exclude `spring-boot-starter-tomcat` from `spring-boot-starter-web` and add `spring-boot-starter-jetty`. The Boot docs document each swap.

Outside these three cases, override versions reluctantly. The whole point of starters is "let the Boot team pick." Overriding gives back the version-juggling problem you were paying the parent to solve.

---

## Common pitfalls

- **Pinning a transitive dependency directly when it's also managed by the parent.** Your direct pin wins, but only for that one artifact — its siblings still take the parent's version. The result is a split family (`jackson-core 2.18.3`, `jackson-databind 2.18.1`), which is exactly the failure mode starters exist to prevent. Use the version property, not a direct pin.
- **Adding `spring-context` as a direct dependency.** The starter already brings it. Adding it again with a different version creates conflict resolution issues. Use the starter; let the parent decide.
- **Confusing `spring-boot-starter-test` with `spring-test`.** `spring-test` is the lower-level library — Spring Framework's test support (`@SpringJUnitConfig`, `MockMvc`), no Boot involved. `spring-boot-starter-test` is the Boot starter that pulls `spring-test` *and* JUnit, AssertJ, Mockito, JsonPath, and the rest of the testing stack. Module 06 used neither (plain JUnit). Module 07 used neither either — it bootstrapped contexts directly with `AnnotationConfigApplicationContext`. Module 08 onward uses `spring-boot-starter-test` for everything.
- **Adding `<scope>test</scope>` to `spring-boot-starter` instead of `spring-boot-starter-test`.** A copy-paste mistake. The core starter at test scope means your runtime is missing Spring's context — every test that boots a context fails with `ClassNotFoundException`.
- **Excluding things from a starter without understanding what they were doing.** "I don't think I need Logback, exclude `logback-classic`" — and now SLF4J has no implementation on the classpath at runtime. Read what the exclusion removes before you write it.

---

## Try this yourself

1. Open the `starters-demo` and run `mvn dependency:tree`. Count the artifacts. Then `grep` for `mockito`, `assertj`, and `junit` — observe that all three came from `spring-boot-starter-test` and you wrote `<version>` zero times.
2. Add a fourth library to the test stack — say, `org.junit.jupiter:junit-jupiter-params` (for parameterized tests) — without specifying a version. Re-run `mvn dependency:tree`. It resolves to a version, because the parent pins it too. Now try `org.example:nonsense-library` (which doesn't exist in the BOM) without a version. Build fails — `'dependencies.dependency.version' for org.example:nonsense-library:jar is missing`. The parent only fills in versions for artifacts it knows.
3. Open `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.14/` in a browser, click the `.pom` file, and search for `<mockito.version>`. You're looking at the line that decided what Mockito version your test got.

---

## Self-check

1. A starter is a Maven artifact with what unusual property? (Hint: count its `.class` files.)
2. Your `pom.xml` says `<dependency>org.mockito:mockito-core</dependency>` with no version. Trace, step by step, where the version comes from at build time.
3. You discover a CVE in `jackson-databind` and need to bump it to a version Boot hasn't released yet. What's the wrong way (the one that creates a split family) and what's the right way?

---

## Code examples

In reading order:

- `starters-demo/pom.xml` — same parent as topic 02, same two starters. Compare it side-by-side with topic 02's pom; they're nearly identical because the lesson is what the starters give you, not what you write.
- `starters-demo/src/main/java/com/example/ExceptionClassifier.java` — a small interface so the test has something to mock.
- `starters-demo/src/main/java/com/example/RetryPolicy.java` — plain class (not a Spring bean), takes the classifier and a max-attempts count.
- `starters-demo/src/main/java/com/example/App.java` — `@SpringBootApplication` so the project is shaped like every other module 08 demo, even though this topic's test never boots the context.
- `starters-demo/src/test/java/com/example/RetryPolicyTest.java` — uses JUnit's `@Test`, AssertJ's `assertThat`, *and* Mockito's `mock` + `when` + `any`. Three libraries, no `<version>` blocks.

Run with:

```bash
mvn -q test                        # passes
mvn -q dependency:tree             # shows what the two starters brought in
mvn -q dependency:tree | grep -E "(mockito|assertj|junit)"   # the three libraries used by the test
```
