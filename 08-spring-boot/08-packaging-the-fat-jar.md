# Packaging the Fat Jar

The deliverable an operations team usually receives from a Spring Boot project is a single jar that runs with `java -jar app.jar`. That jar contains your code, your resources, *and every dependency you pulled in* — Spring, Jackson, Tomcat, the lot. This topic explains how `spring-boot-maven-plugin` builds that jar and how the layout inside it works.

By the end of this topic you can run `mvn package`, hand the resulting jar to someone with only a JDK installed, and watch their app start.

---

## The problem this solves

Module 05 topic 07 showed how plain Maven packages your code:

```bash
mvn package
ls target/
# myapp-1.0.0.jar      <-- your .class files only; tiny
```

That jar has no Spring inside it. Running `java -jar myapp.jar` fails immediately with `ClassNotFoundException: org.springframework.boot.SpringApplication`. To produce a self-contained runnable artifact you'd reach for `maven-shade-plugin` (module 05 topic 07) — but Shade has problems with resources that exist in multiple jars (e.g. `META-INF/services` files), and the produced jar mixes everyone's classes in one flat namespace.

Spring Boot ships its own packager: `spring-boot-maven-plugin`. It produces a jar that is bigger (it contains every dependency) but better-organized: each dependency lives intact in its own nested jar under `BOOT-INF/lib/`. A small launcher class in the manifest knows how to load those nested jars at runtime. Resource collisions don't happen; classpath order is deterministic; the jar is one file you can hand to anyone.

---

## How it works: the plugin and the goal

Every Boot project's `pom.xml` since topic 02 has had this block:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

The plugin binds a goal called `repackage` to Maven's `package` phase. So `mvn package` now does two things:

1. **Maven's normal `package` step** produces `target/myapp-1.0.0.jar` — the plain jar, just your classes.
2. **The plugin's `repackage` goal** renames that to `myapp-1.0.0.jar.original` and produces a new `myapp-1.0.0.jar` — the executable fat jar with every dependency embedded.

The original is kept (with the `.original` suffix) in case you ever need it for, say, publishing to a Maven repository as a library. The new jar is what you deploy.

---

## The layout inside the jar

Unzip a Boot fat jar and you'll see:

```text
myapp-1.0.0.jar
├── META-INF/
│   └── MANIFEST.MF              # Main-Class: org.springframework.boot.loader.launch.JarLauncher
├── BOOT-INF/
│   ├── classes/                 # your compiled .class files + your resources
│   │   ├── com/example/App.class
│   │   ├── application.yml
│   │   └── ...
│   ├── lib/                     # every dependency as a nested jar
│   │   ├── spring-context-6.2.18.jar
│   │   ├── spring-core-6.2.18.jar
│   │   ├── jackson-databind-2.18.x.jar
│   │   └── ...                  # 60+ jars for a typical app
│   └── layers.idx               # layered jar metadata for Docker
└── org/springframework/boot/loader/
    ├── launch/JarLauncher.class # the bootstrap that runs first
    └── ...                      # the loader's own machinery
```

Three sections:

- **`META-INF/MANIFEST.MF`** points to Boot's `JarLauncher`, *not* your `App.class`. When the JVM runs `java -jar myapp.jar`, it sees `Main-Class: org.springframework.boot.loader.launch.JarLauncher` and runs that.
- **`org/springframework/boot/loader/`** is Boot's launcher code. It knows how to load nested jars from `BOOT-INF/lib/` and how to build a classloader that can see all of them. Then it locates your `Start-Class` (set automatically to your `@SpringBootApplication` class) and invokes its `main`.
- **`BOOT-INF/`** is your code (`classes/`) and dependencies (`lib/`). Each dependency stays as its own jar — no class mixing.

This means the loader handles classpath order deterministically, and resources that exist in multiple jars (`META-INF/spring.handlers`, `META-INF/services/jakarta.servlet.ServletContainerInitializer`, etc.) don't collide.

---

## Running the jar

```bash
mvn -q package
java -jar target/packaging-demo-1.0.0.jar
```

What you see is the same Spring banner and "Started App in X seconds" log you saw from `mvn spring-boot:run`, just outside Maven. The jar is self-contained — pass it to a machine with only a JDK installed and it runs.

You can also override properties on the command line:

```bash
java -jar target/packaging-demo-1.0.0.jar --app.message="Hello from prod"
java -Dapp.message="Hello from prod" -jar target/packaging-demo-1.0.0.jar
```

Both work; both follow the precedence rules from topic 05.

---

## `mvn spring-boot:run` vs `mvn package` + `java -jar`

| Use                              | When                                                             |
|----------------------------------|------------------------------------------------------------------|
| `mvn spring-boot:run`            | Inner-loop development — fast feedback, no jar built             |
| `mvn package` + `java -jar`      | Verifying the deliverable, running outside Maven, deploying      |

The two behave identically at runtime. The only practical difference: `spring-boot:run` reads `pom.xml` to assemble a classpath; the jar's classpath is fixed once it's built.

---

## What about WAR files?

Older Spring tutorials produce a WAR file deployed to an external Tomcat. Spring Boot supports that mode (set `<packaging>war</packaging>` and extend `SpringBootServletInitializer`), but it's rare in 2026. The default is to ship a self-contained executable jar with embedded Tomcat — simpler to deploy, simpler to test locally, simpler to containerize.

Stick to `<packaging>jar</packaging>` (the default) unless you have a deployment platform that genuinely requires WARs.

---

## Layered jars and Docker (brief)

By default the plugin produces a "layered" jar — `BOOT-INF/layers.idx` describes which lib files change rarely vs. often. A Dockerfile can use that to split the jar into multiple image layers, so a code-only rebuild doesn't re-push 50MB of Spring jars to your registry.

You unpack a layered jar with:

```bash
java -Djarmode=tools -jar myapp.jar extract --layers
```

The resulting directory has `dependencies/`, `spring-boot-loader/`, `snapshot-dependencies/`, and `application/`. Each is a Docker `COPY` target. Module 14 (Docker / DevOps) covers the Dockerfile pattern; for now, knowing the layers exist is enough.

---

## Common pitfalls

- **Running `java -jar target/myapp-1.0.0.jar.original`.** The `.original` artifact is the *plain* jar — no nested dependencies, no `JarLauncher`. It fails with `ClassNotFoundException`. Always run the un-suffixed jar.
- **Editing files inside `BOOT-INF/classes/` of a packaged jar.** Tempting for a quick config change in production — and broken on most JVMs because the jar is opened read-only. Override via CLI args, env vars, or an external `application.yml` (topic 05), not by patching the jar.
- **Putting `spring-boot-maven-plugin` in the wrong section.** It goes in `<build><plugins>`, not `<build><pluginManagement>`. The parent BOM already declares it in `<pluginManagement>`; your project just references it (with no version, since the parent pins it).
- **Forgetting `mvn clean` after a refactor that renamed your app's main class.** The plugin sets `Start-Class` based on what `@SpringBootApplication` it finds; a stale `target/` can hold a jar that points at the old name. `mvn clean package` rebuilds.
- **Trying to use the executable jar as a library.** Other Maven projects depending on a fat jar will pull in conflicting copies of every Spring class. If you need a library, configure the plugin's `classifier`: `<classifier>exec</classifier>` makes the fat jar a side artifact, leaving the plain one for library consumers.

---

## Try this yourself

1. From `packaging-demo/`, run `mvn -q clean package`. Look at `target/` — confirm both `packaging-demo-1.0.0.jar` and `packaging-demo-1.0.0.jar.original` exist. Note the size difference (the original is tiny, the fat jar is ~20MB).
2. Run `java -jar target/packaging-demo-1.0.0.jar`. The app starts and exits, same as `mvn spring-boot:run` would.
3. Unzip the fat jar (`jar -tf target/packaging-demo-1.0.0.jar | head -40`). Find `BOOT-INF/classes/com/example/App.class`. Find one library jar under `BOOT-INF/lib/`. Read `META-INF/MANIFEST.MF` — note `Main-Class` is the launcher, not your `App`.
4. Try running the original by mistake: `java -jar target/packaging-demo-1.0.0.jar.original`. Read the error. That's why the un-suffixed file is the deliverable.

---

## Self-check

1. Why does `META-INF/MANIFEST.MF` in a Boot fat jar say `Main-Class: org.springframework.boot.loader.launch.JarLauncher` instead of your `App` class?
2. What is the difference between `myapp-1.0.0.jar` and `myapp-1.0.0.jar.original` in the `target/` directory after `mvn package`? When would anyone need the `.original`?
3. You override an `application.yml` value at the command line: `java -jar myapp.jar --app.message="X"`. Trace where that override lands in the precedence list from topic 05. Why is this safer than patching the YAML inside the jar?

---

## Code examples

In reading order:

- `packaging-demo/pom.xml` — identical shape to previous topics; the magic is in the plugin section, which has been there since topic 02.
- `.../App.java` — `@SpringBootApplication`, prints a startup message, exits.
- `.../GreetingPrinter.java` — a small `@Component` whose `@PostConstruct` proves the context wired correctly inside the jar.
- `src/main/resources/application.yml` — base config.
- `src/test/java/com/example/AppTest.java` — `@SpringBootTest` confirms the context loads (this runs without packaging).

Run with:

```bash
mvn -q test                                       # context loads in-place
mvn -q clean package                              # builds the fat jar
java -jar target/packaging-demo-1.0.0.jar         # runs the fat jar standalone
java -jar target/packaging-demo-1.0.0.jar --app.message="From CLI"
```
