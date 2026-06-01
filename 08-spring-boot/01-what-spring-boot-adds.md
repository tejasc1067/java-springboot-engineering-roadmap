# What Spring Boot Adds on Top of Spring Core

Module 07 ended with a working Spring container: `AnnotationConfigApplicationContext`, a handful of `@Bean` methods, profiles, events, AOP. That container is what most people mean when they say "Spring." It is small, fast, and self-contained — exactly what module 07 set out to teach.

Plain Spring is not, however, what most people *ship*. The moment you try to turn module 07's demo into a service a deployment team can run, six new chores show up — none of them about objects or wiring. Spring Boot is the layer that does those six chores for you. This topic names each chore, names what Boot does about it, and points at the topic in this module where it gets unpacked.

This topic has no code project. The plain-Spring snippets below are illustrative — you've already seen their equivalents in module 07 — and the Boot snippets are previews of topics 02–12. From topic 02 onward, every topic is a real Maven project.

---

## The problem this solves

Imagine you finished module 07 yesterday. Today's task: take that `OrderService` and turn it into an HTTP service another team can call. Not a tutorial — an actual deliverable. Here is what plain Spring leaves you to do:

1. **Add an embedded server.** Plain Spring doesn't ship with one. The `spring-context` module is a DI container, nothing more. To serve HTTP you'd add `spring-webmvc`, pick an embedded Tomcat/Jetty/Undertow, register a `DispatcherServlet`, wire `ServletContext` into the application context, and configure thread pools and connectors. Forty lines of code you would write the same way in every project.

2. **Pin every Spring artifact yourself.** `spring-context:6.2.18`, `spring-webmvc:6.2.18`, `spring-aop:6.2.18`, `spring-jdbc:6.2.18`, `aspectjweaver:1.9.25.1`, `jakarta.annotation-api:2.1.1`. Forget to keep them aligned and a transitively-pulled mismatch surfaces as `NoClassDefFoundError` at runtime. Every new project repeats the dance.

3. **Read configuration by hand.** Plain Spring's `@PropertySource("classpath:application.properties")` works, but only loads one file. Profile-specific files (`application-prod.properties`), YAML, command-line overrides, environment variables — each needs its own bean. You either build a custom `Environment` setup per project, or copy-paste one between them.

4. **Package the fat jar.** `mvn package` on a plain-Spring project produces a jar with *your* classes only; `java -jar your.jar` fails with `ClassNotFoundException` on the first Spring class. You'd add `maven-shade-plugin` (module 05 topic 07), configure a `Main-Class` manifest, then deal with nested-jar resource collisions that Shade can't fix cleanly.

5. **Add health checks and metrics.** Every operations team wants `/health` for the load balancer and `/metrics` for monitoring. In plain Spring, you write the controllers, the JSON shape, the database-pinging logic, and the metrics registry, by hand.

6. **Bootstrap every test.** Every test in module 07 wrote `try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) { ... }`. Acceptable for a teaching module; ten lines of boilerplate per test class for a real codebase. Web-layer slice tests, mock beans, transactional rollback — all manual.

That is six chores, all repeated in every project, none of them about what your code *does*. Spring Boot is the answer to "stop doing these six things by hand."

---

## What Boot actually adds: three big ideas

Everything in module 08 fits under three additions Spring Boot makes to plain Spring. Once you have the three, the six chores above collapse.

### 1. Starters — curated dependency bundles

A *starter* is a Maven dependency that pulls in a group of related libraries at versions known to work together. Instead of pinning eight Spring artifacts plus their transitive friends, you add one line:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

That pulls in `spring-webmvc`, `spring-web`, an embedded Tomcat, Jackson for JSON, validation, logging — at versions the Boot team has tested together. You don't specify a version because `spring-boot-starter-parent` (a parent POM you set in your project) locks them centrally. Chore #2 — manual version juggling — gone. Topic 03 unpacks this.

### 2. Auto-configuration — beans the framework picks for you

Add `spring-boot-starter-web` to a project and *without writing any `@Configuration`*, Boot will create a `DispatcherServlet`, an embedded Tomcat on port 8080, a Jackson `ObjectMapper`, an error-page handler, and several other beans you'd otherwise wire by hand. It does this by checking — at startup, for each candidate bean — three things:

- "Is this class on the classpath?" (e.g. `Tomcat.class` is, because the starter brought it.)
- "Has the user already defined a bean of this type?" (If yes, Boot's auto-configured one steps aside.)
- "Are the required properties set?" (Some auto-configurations only activate when you opt in.)

That conditional logic is the whole magic. Chores #1 (no embedded server), #3 (config loading), #5 (no health or metrics endpoints) collapse into "starters bring the right libraries, auto-config creates the right beans." Topic 04 is the deep dive.

### 3. `SpringApplication` and `@SpringBootApplication` — the bootstrap

In plain Spring, `main` constructs an `AnnotationConfigApplicationContext`, registers a config class, retrieves a bean, and closes the context. In Boot:

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

`@SpringBootApplication` expands to three module-07 annotations stacked: `@Configuration` (this class can declare beans), `@ComponentScan` (find `@Component` classes from this package downward), and `@EnableAutoConfiguration` (turn on the auto-config machinery). `SpringApplication.run` boots a context just like module 07 did, then — if the classpath contains a servlet container — also starts the embedded web server, binds the port, and parks the JVM on a non-daemon thread so the server keeps serving. Topic 02 expands each piece.

---

## The layering

It is easy to read the above and think "Boot replaces plain Spring." It does not. Boot is a *layer* that sits on top of plain Spring and does the chores. Your `@Service`, `@Component`, `@Autowired`, profiles, AOP — all the module 07 material — works identically inside a Boot app, because Boot still uses the same `ApplicationContext` underneath.

```text
   +-------------------------------------------------+
   |   Your application code                         |
   |   (@RestController, @Service, @Repository)      |
   +-------------------------------------------------+
   |   Spring Boot                                   |
   |   - starters (curated dependencies)             |
   |   - auto-configuration (conditional beans)      |
   |   - SpringApplication (embedded server boot)    |
   |   - application.yml / profiles / Actuator       |
   +-------------------------------------------------+
   |   Spring Framework  (module 07)                 |
   |   - IoC container, ApplicationContext           |
   |   - @Bean, @Component, @Autowired               |
   |   - @Profile, events, AOP                       |
   +-------------------------------------------------+
   |   Java / JDK                                    |
   +-------------------------------------------------+
```

When something breaks, the layer the error came from matters. `NoSuchBeanDefinitionException`? That's the Spring Framework layer — module 07 tells you why. `Cannot determine embedded database driver class`? That's an auto-configuration failure — module 08 territory. Knowing which layer is talking saves an hour of guessing.

---

## A concrete before-and-after

Plain Spring (the kind of `pom.xml` and `main` you wrote in module 07):

```xml
<dependencies>
    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>6.2.18</version></dependency>
    <dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId><version>6.2.18</version></dependency>
    <dependency><groupId>org.springframework</groupId><artifactId>spring-aop</artifactId><version>6.2.18</version></dependency>
    <dependency><groupId>org.aspectj</groupId><artifactId>aspectjweaver</artifactId><version>1.9.25.1</version></dependency>
    <dependency><groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId><version>2.1.1</version></dependency>
    <dependency><groupId>org.apache.tomcat.embed</groupId><artifactId>tomcat-embed-core</artifactId><version>10.1.30</version></dependency>
    <!-- ...plus the Jackson, validation, slf4j/logback artifacts you also have to pin -->
</dependencies>
```

```java
public class Main {
    public static void main(String[] args) {
        AnnotationConfigWebApplicationContext ctx = new AnnotationConfigWebApplicationContext();
        ctx.register(AppConfig.class);

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);
        Context tomcatCtx = tomcat.addContext("", null);
        Tomcat.addServlet(tomcatCtx, "dispatcher", new DispatcherServlet(ctx)).setLoadOnStartup(1);
        tomcatCtx.addServletMappingDecoded("/*", "dispatcher");
        tomcat.start();
        tomcat.getServer().await();
    }
}
```

The equivalent in Spring Boot:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.4</version>
</parent>
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

Same observable result: an HTTP server on port 8080 with a fully wired Spring context. Different number of lines you had to write and own. The middle layer in the diagram above is where the missing lines went.

---

## Pain point -> Boot feature -> where it's covered

| Pain in plain Spring                 | Boot's answer                              | Topic |
|--------------------------------------|--------------------------------------------|-------|
| No embedded server                   | starter-web + auto-configured Tomcat       | 09    |
| Version juggling across artifacts    | `spring-boot-starter-parent` BOM           | 03    |
| Beans you'd wire the same every time | Auto-configuration with `@ConditionalOn...`| 04    |
| Loading `application.properties`     | Property sources + precedence rules        | 05    |
| Typed config blocks                  | `@ConfigurationProperties`                 | 06    |
| Profile-specific config files        | `application-{profile}.yml` convention     | 07    |
| Building a runnable jar              | `spring-boot-maven-plugin` fat jar         | 08    |
| Default logging setup                | Logback defaults + `logging.level.*`       | 10    |
| Test boilerplate                     | `@SpringBootTest`, slice tests, `@MockitoBean` | 11 |
| Health / metrics endpoints           | Spring Boot Actuator                       | 12    |

Topic 01 (this one) is just the map. Topics 02–12 walk through it.

---

## When *not* to reach for Spring Boot

Boot is the right choice for almost every Spring application written in 2026. Three honest exceptions:

- **A library, not an application.** If you are publishing a library that other Spring apps will depend on, depend on the bare `spring-context` and friends — not on `spring-boot-starter-*`. Otherwise your library forces a version of Boot on every consumer and your release cadence has to follow theirs.
- **A short-lived JVM job.** A one-shot data migration or a CLI does not need an embedded Tomcat or auto-configured beans for thirty services. Plain `AnnotationConfigApplicationContext` from module 07 is leaner. You *can* still use Boot in non-web mode (`SpringApplication.setWebApplicationType(WebApplicationType.NONE)`) — many teams do — but it's a judgment call.
- **You need to know every bean exists.** In high-assurance code (regulated finance, embedded systems), some teams refuse auto-configuration because the bean graph must be explicit and audit-traceable. They use plain Spring and accept the cost.

Outside these cases: use Boot.

---

## Common misconceptions

- **"Boot is its own framework."** No. Boot is a layered project on top of Spring Framework. Every Boot app is a Spring app. `@Service`, `@Autowired`, `@Profile` mean the same thing in both. The annotations were not redefined.
- **"Auto-configuration is magic that watches my code."** No — it's a fixed set of `@Configuration` classes shipped inside Boot's jars, each annotated with `@Conditional...` rules that decide at startup whether to apply. Topic 04 shows you the report and lets you read it.
- **"`@SpringBootApplication` is a brand-new bigger annotation."** It is three module-07 annotations bundled with one new one (`@EnableAutoConfiguration`). Topic 02 unpacks it.
- **"Boot makes the app slower."** Startup is slightly slower because auto-configuration runs (a few hundred milliseconds at most for typical apps). Steady-state runtime is identical — once the context is built, you're running the same Spring container as plain Spring.
- **"I should learn plain Spring deeply before touching Boot."** You just did, in module 07. That's the right order. The reverse misconception — "Boot replaces plain Spring, why bother with module 07" — is more common and more painful: people who skip module 07 hit `NoUniqueBeanDefinitionException` in a Boot app and have no mental model for what just happened.

---

## Try this yourself

This topic has no code project, but you have three concrete things to try before topic 02:

1. Open any of module 07's demos (say `events-demo`). Look at its `pom.xml`. Count the `<dependency>` blocks and how many versions are pinned. Then open `start.spring.io`, generate a project with "Web" selected, and look at *its* `pom.xml`. The difference in line count is the value of starters.
2. Pick any *one* pain point from the table above. Skim ahead to the topic that solves it — don't try to understand the code yet, just notice that the chore *is* solved and how short the answer looks.
3. Write down, in your own words, what you think `@SpringBootApplication` does. We will compare it against reality in topic 02.

---

## Self-check

1. Plain Spring (module 07) already gives you DI, profiles, events, and AOP. Name two concrete things you would *still* have to build by hand to deploy a plain-Spring web service, and which Boot feature removes each.
2. `@SpringBootApplication` is composed of how many other annotations? Which one is unique to Boot (i.e. does not exist in module 07)?
3. Boot "sits on top of" plain Spring. When you see `NoUniqueBeanDefinitionException` at startup of a Boot app, which layer is talking — and where in this roadmap did you learn how to fix it?

---

This topic is markdown-only. There is no `CODE_EXAMPLES/01-what-spring-boot-adds/` directory. From topic 02 onward, every topic ships a real Maven project under `CODE_EXAMPLES/`.
