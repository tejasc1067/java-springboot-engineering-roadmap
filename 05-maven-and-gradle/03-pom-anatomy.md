# 03 — pom.xml anatomy

The `pom.xml` ("Project Object Model") is the entire build description. Reading one is the central Maven skill. This topic walks every section, top to bottom, so you can open any open-source Spring Boot project and know what each tag means.

We don't add a code example here — the POM examples are inline, and you'll see real POMs in topics 04, 05, 07, 09, and 10.

---

## The problem this solves

A teammate hands you a repo. You open `pom.xml` and see 200 lines of XML. Some you recognize, some you don't. Which lines are "this project's identity"? Which are "what this project needs"? Which are "how this project is built"? Without a mental map, the file is a wall of text. This topic gives you the map.

---

## The skeleton

Every POM has the same outer shape:

```xml
<project>
    <modelVersion>4.0.0</modelVersion>

    <!-- 1. Coordinates: who am I? -->
    <groupId>...</groupId>
    <artifactId>...</artifactId>
    <version>...</version>
    <packaging>...</packaging>

    <!-- 2. Inheritance (optional): am I a child of another POM? -->
    <parent>...</parent>

    <!-- 3. Properties: variables I want to reuse below -->
    <properties>...</properties>

    <!-- 4. Dependencies: what jars do I need? -->
    <dependencies>...</dependencies>

    <!-- 5. Dependency management: pin versions for me and my children -->
    <dependencyManagement>...</dependencyManagement>

    <!-- 6. Build: which plugins shape compile/test/package? -->
    <build>
        <plugins>...</plugins>
    </build>

    <!-- 7. Profiles: alternate build configurations -->
    <profiles>...</profiles>
</project>
```

Treat the order as load-bearing — it matches the way Maven reads the file. Now each piece, in detail.

---

## 1. Coordinates (GAV)

```xml
<groupId>com.example</groupId>
<artifactId>orders-service</artifactId>
<version>1.4.2</version>
<packaging>jar</packaging>
```

- **groupId**: who built this. Convention: reversed domain name. `com.example`, `org.springframework`, `io.github.your-user`. Owns a "namespace" on Maven Central.
- **artifactId**: the project's short name. Becomes the jar filename prefix (`orders-service-1.4.2.jar`).
- **version**: the version. Suffix `-SNAPSHOT` means "in-development" (e.g. `1.5.0-SNAPSHOT`); anything else is "release". Topic 04 covers what that means for resolution.
- **packaging**: `jar` (default), `war` (for old-style servlet deployments), or `pom` (for parent/aggregator projects — topic 10).

These four values, together, uniquely identify your artifact. When someone *else* wants to use your code, they write your GAV in *their* `<dependencies>`.

### `modelVersion`

```xml
<modelVersion>4.0.0</modelVersion>
```

The version of the POM schema, **not** of your project. Always `4.0.0` for the current Maven generation. Don't change it.

---

## 2. Parent (optional)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
</parent>
```

A POM can inherit from a "parent" POM, like a class inherits from a superclass. Anything declared in the parent — Java version, plugin configuration, pinned dependency versions — flows into the child unless the child overrides it.

You'll meet this for real in module 08 when we use Spring Boot's parent. For now, just recognize: if a `pom.xml` starts with `<parent>`, the project is inheriting build config from that parent. Without `<parent>`, the project stands alone (like `hello-maven` in topic 02).

`<relativePath/>` (empty self-closing tag) is a Spring Boot idiom meaning "don't look on disk for this parent, fetch it from the repository." Without it, Maven looks at `../pom.xml` first, which makes sense only inside multi-module projects (topic 10).

---

## 3. Properties

```xml
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>${java.version}</maven.compiler.source>
    <maven.compiler.target>${java.version}</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <jackson.version>2.16.1</jackson.version>
</properties>
```

`<properties>` defines variables. Two kinds of properties matter:

- **Magic ones Maven knows about.** `maven.compiler.source` and `maven.compiler.target` tell the compiler plugin which Java version to target. `project.build.sourceEncoding` controls how Maven reads `.java` files (always set this to `UTF-8` — Windows defaults to Windows-1252 and breaks non-ASCII filenames otherwise).
- **Ones you define for reuse.** `<jackson.version>2.16.1</jackson.version>` is just a variable you'll reference below as `${jackson.version}` in three or four `<dependency>` blocks. When you upgrade Jackson, you change it in one place.

Properties also include implicit ones: `${project.version}` (this project's version), `${project.basedir}` (the project root path), `${user.home}` (your home directory). Useful for plugin configuration.

---

## 4. Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

The list of libraries this project needs. Each `<dependency>` is itself a GAV (plus optional `<scope>`, `<optional>`, `<exclusions>`).

- **`<scope>`** controls *when* the dependency is on the classpath (compile, test, runtime, provided). Topic 05 covers this in full.
- **No `<scope>`** = `compile`, the default. Available everywhere.

Maven resolves transitive dependencies for you — Jackson pulls in `jackson-core` and `jackson-annotations`, JUnit pulls in `opentest4j` and `apiguardian-api`. You only list what you directly need.

---

## 5. Dependency management

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

`<dependencyManagement>` does **not** add dependencies to your classpath. It says "**if** any dependency (direct or transitive) needs `jackson-core`, use version 2.16.1." It's a version-pinning mechanism.

Two big uses:

- **BOMs** (Bill of Materials): import a project like `jackson-bom` or `spring-boot-dependencies` to pin a curated set of compatible versions in one go. Most Spring Boot apps do this through the parent.
- **Resolving conflicts** in transitive dependencies. Topic 09 demonstrates this with a real conflict.

`<dependencyManagement>` is a quiet but powerful section. Beginners often skip past it; in real projects it's where 80% of dependency-version pain is fixed.

---

## 6. Build

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.6.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals><goal>shade</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

The `<build>` block customizes how Maven compiles, tests, and packages. Almost all build customization happens through **plugins** — small programs Maven runs at specific lifecycle phases. Topic 07 dives into the plugins you'll use most.

The key idea: **Maven itself does almost nothing.** Compilation is a plugin (`maven-compiler-plugin`). Testing is a plugin (`maven-surefire-plugin`). Packaging is a plugin (`maven-jar-plugin`). Even copying resources is a plugin. The lifecycle binds these plugins to phases (topic 06).

If `<build>` is omitted, Maven uses sensible defaults. The plugin you'll most commonly add by hand is `maven-shade-plugin` (for fat-jars, topic 07) or `spring-boot-maven-plugin` (in module 08).

---

## 7. Profiles (optional, advanced)

```xml
<profiles>
    <profile>
        <id>ci</id>
        <build>
            <plugins>
                <plugin>...different config for CI...</plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

Profiles let you have alternate build configurations activated by a flag (`mvn -P ci package`), an OS, a property, or a missing file. Useful but easy to overuse. You won't write profiles for a while — beginners read them more than write them. Recognize the section, skip the content unless you need it.

---

## A realistic, complete POM

Here's a fuller POM tying it all together. This is the shape of what most non-Spring-Boot Java services look like:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>orders-service</artifactId>
    <version>1.4.2</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <jackson.version>2.16.1</jackson.version>
        <junit.version>5.10.2</junit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>

</project>
```

Read top-to-bottom: who I am, what version of Java, which libraries, which plugin to use for tests.

---

## A Spring Boot teaser

You'll see this in module 08:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
</parent>
```

Inheriting from `spring-boot-starter-parent` gives you, for free:

- Pinned versions for ~200 common libraries (Jackson, SLF4J, JUnit, etc.).
- A configured `maven-compiler-plugin` set to Java 17+.
- A configured `maven-surefire-plugin` that finds JUnit 5 tests.
- The `spring-boot-maven-plugin` already configured to produce a runnable fat-jar.

That's why Spring Boot projects have surprisingly short `pom.xml` files — most of the config is inherited.

---

## Common pitfalls

- **`maven.compiler.target` higher than your installed JDK.** Setting `target=21` when you only have JDK 17 installed -> `release version 21 not supported`. Either install the right JDK or lower the target.
- **Hard-coded versions everywhere.** Re-using `<jackson.version>2.16.1</jackson.version>` across 4 `<dependency>` tags as `${jackson.version}` is fine. Writing `2.16.1` four separate times is a future maintenance bomb.
- **Putting business config in `<properties>`.** Things like a database URL don't belong in `pom.xml`. They belong in `application.properties`. `<properties>` in `pom.xml` is for build-time variables only.

---

## Try this yourself

1. Open `spring-projects/spring-petclinic` on GitHub. Locate the `pom.xml`. Identify these sections by sight: parent, properties, dependencies, build. (There may be no `<dependencyManagement>` — it's inherited from the parent.)
2. Find one `<dependency>` whose version is *not* declared. That version comes from the parent's `<dependencyManagement>`. Click through to `spring-boot-dependencies` POM on GitHub and find the pinned version.
3. Open a `pom.xml` you're less familiar with (any open-source Java repo). For each section, say aloud what it does. If you can do this in under a minute, you've got the anatomy.

## Self-check

1. What four values uniquely identify a Maven artifact? What does each one represent?
2. What is the difference between `<dependencies>` and `<dependencyManagement>`? When would you put a dependency in the latter but not the former?
3. The `<build><plugins>` section is empty in a small POM. Does Maven still compile and package the project? Why?
