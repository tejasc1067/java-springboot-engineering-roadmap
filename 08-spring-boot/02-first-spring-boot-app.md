# Your First Spring Boot App

Topic 01 named the chores plain Spring leaves you to do. This topic does two of them — picks the right library versions and bootstraps an application context — in three lines of Java and a six-line `pom.xml`. Nothing here is conceptually new beyond module 07; the trick is that `@SpringBootApplication` and `SpringApplication.run` collapse the boilerplate.

By the end of this topic you can boot a Spring Boot context, retrieve a bean, and explain in your own words what `@SpringBootApplication` actually expands to.

---

## The problem this solves

Module 07's `main` looked like this:

```java
public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService = context.getBean(OrderService.class);
            Order placed = orderService.placeOrder("ada@example.com", List.of("BOOK-A"));
            System.out.println("Placed " + placed.id());
        }
    }
}
```

Two things were noisy. First, you had to name a `@Configuration` class explicitly (`AppConfig.class`) — every new project needs one. Second, the `pom.xml` listed every Spring artifact by hand with a pinned version (`spring-context`, `spring-aop`, `aspectjweaver`, `jakarta.annotation-api`). Both chores recur on every project; both are easy to get wrong.

Spring Boot replaces them with one annotation, one method call, and one parent POM. That is all this topic teaches — the rest of module 08 unpacks what those three things actually *do*.

---

## How it works: the `pom.xml`

The single difference at build-tool level is a `<parent>` block:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.14</version>
    <relativePath/>
</parent>
```

`spring-boot-starter-parent` is a parent POM that does two jobs:

- **Locks versions** in a `<dependencyManagement>` block for every library Spring Boot has tested together — Spring Framework, Jackson, Logback, JUnit, AssertJ, Mockito, Tomcat, everything. Your `<dependencies>` block lists artifacts by name only; the parent supplies the version.
- **Configures plugins** — `maven-compiler-plugin` with the right release flag, `maven-surefire-plugin` for tests, the layered-jar packaging, sane defaults for resource filtering.

With the parent in place, your dependencies look like this:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

No `<version>` on either one — the parent fills it in. Two starters give you the Spring container, the YAML loader, the logging stack (Logback + SLF4J), JUnit 5, AssertJ, and Mockito. Topic 03 unpacks starters in depth.

The `<java.version>` property tells the parent which Java release to compile for:

```xml
<properties>
    <java.version>21</java.version>
</properties>
```

That's the whole build setup. Compare to module 07 where you pinned six artifacts by hand and configured the compiler plugin yourself.

---

## How it works: the application class

A complete first Boot app:

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);

        Greeter greeter = context.getBean(Greeter.class);
        System.out.println(greeter.greet("Spring Boot"));
    }
}
```

And the bean Spring will find for you:

```java
package com.example;

import org.springframework.stereotype.Component;

@Component
public class Greeter {
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
```

Three things happen when you run `App.main`:

1. **`SpringApplication.run(App.class, args)`** boots a Spring container — exactly the same `ApplicationContext` you used in module 07 — but with auto-configuration enabled.
2. **`@ComponentScan` (part of `@SpringBootApplication`)** scans the package of `App` and every sub-package, finds `Greeter` because it's annotated `@Component`, and registers it as a bean.
3. **The container returns** a `ConfigurableApplicationContext` that you can `getBean` from, just like in module 07.

No `AppConfig.java`. No `register(...)`. No version pinning. The smallest interesting Boot app is one annotation, one `main`, one `@Component`.

---

## What `@SpringBootApplication` actually is

This annotation is the headline of every Boot app. It is also slightly misleading: it looks like one new thing, but it is three module-07 annotations bundled with one new one.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration             // a flavor of @Configuration
@EnableAutoConfiguration             // the one new idea
@ComponentScan(...)                  // module 07, topic 03
public @interface SpringBootApplication { ... }
```

Three of those are familiar:

- **`@SpringBootConfiguration`** is just a marker variant of `@Configuration`. Your `App` class is itself a configuration class — you can put `@Bean` methods on it if you want, the same way `AppConfig` did in module 07.
- **`@ComponentScan`** tells Spring to scan for `@Component`, `@Service`, `@Repository`, `@RestController`, and `@Configuration` classes. Without arguments, it scans the package the annotated class lives in (and all sub-packages). That is why `Greeter` in `com.example` is discovered automatically: `App` is in `com.example` too.
- **`@EnableAutoConfiguration`** is the only annotation that is genuinely new in module 08. It is what turns on the conditional-bean machinery from topic 01. Topic 04 unpacks it; for now, treat it as "look at the classpath, create reasonable beans."

Equivalent form, if you ever see it written out:

```java
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public class App { ... }
```

Same thing. `@SpringBootApplication` is just shorter.

---

## What `SpringApplication.run` actually does

Two arguments: a "primary source" (a class — usually `App.class` — that Spring will treat as the root configuration) and the command-line `args`. The method:

1. Prints the Spring banner (the ASCII art Spring logo).
2. Creates an `ApplicationContext` of the right type. With no web server on the classpath you get a `AnnotationConfigApplicationContext`. With `spring-boot-starter-web` on the classpath you get a `AnnotationConfigServletWebServerApplicationContext` that also starts embedded Tomcat. Topic 09 is where the second variant shows up.
3. Loads `application.properties` / `application.yml` from `src/main/resources`, plus profile-specific files and command-line overrides (topics 05 and 07).
4. Registers all auto-configured beans whose conditions match.
5. Runs `@ComponentScan` from the primary source's package down.
6. Calls any `CommandLineRunner` or `ApplicationRunner` beans (a Boot convention for "do something once the context is ready"). The `hello-demo` doesn't use these — it just calls `getBean` from `main`.
7. Returns the running context. The JVM stays alive as long as a non-daemon thread keeps running — in a web app, the embedded server thread does that. In a non-web app like `hello-demo`, `main` exits, the JVM exits, Boot's shutdown hook closes the context.

Module 07 wrote `try-with-resources` to close the context. Boot registers a JVM shutdown hook automatically (`SpringApplication.setRegisterShutdownHook(true)` is the default), so the explicit `try-with-resources` is no longer required. You can still write it if you prefer the lifecycle to be obvious in code — it is harmless and arguably clearer for short-lived programs like this demo.

---

## What you see when you run it

```text
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::               (v3.5.14)

2026-06-01T10:23:11.421+05:30  INFO 12345 --- [           main] com.example.App                          : Starting App using Java 21 with PID 12345
2026-06-01T10:23:11.423+05:30  INFO 12345 --- [           main] com.example.App                          : No active profile set, falling back to 1 default profile: "default"
2026-06-01T10:23:11.811+05:30  INFO 12345 --- [           main] com.example.App                          : Started App in 0.612 seconds (process running for 0.812)
Hello, Spring Boot!
```

Four log lines and a banner. The lines tell you:

- which Java the app is using (sanity check during version upgrades),
- which profile is active (no profile yet — topic 07),
- how long the startup took (auto-configuration runs here),
- that the context is ready.

Then your code's `println` runs.

---

## The test

`spring-boot-starter-test` brings in `spring-test` and a Boot-specific annotation: `@SpringBootTest`.

```java
package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AppTest {

    @Autowired
    Greeter greeter;

    @Test
    void contextLoadsAndGreeterIsWired() {
        assertThat(greeter).isNotNull();
        assertThat(greeter.greet("Boot")).isEqualTo("Hello, Boot!");
    }
}
```

`@SpringBootTest` boots the same context your `main` would, then autowires fields on the test class. The bare-minimum useful Boot test is "the context loads and my bean is there" — which is what this asserts. Topic 11 unpacks `@SpringBootTest`, slice tests, and `@MockitoBean`; for now, treat it as "module 06's JUnit plus an automatic Spring context."

Compare to the module 07 testing pattern:

```java
// module 07 style — explicit context, manual close
try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
    Greeter g = ctx.getBean(Greeter.class);
    assertThat(g.greet("Boot")).isEqualTo("Hello, Boot!");
}
```

Same idea, fewer lines, no per-test `new AnnotationConfigApplicationContext`.

---

## Running the demo

Three useful Maven commands from inside the `hello-demo/` directory:

```bash
mvn -q test                  # runs the @SpringBootTest assertions
mvn -q spring-boot:run       # boots the app, runs main, prints "Hello, Spring Boot!", exits
mvn -q package               # produces target/hello-demo-1.0.0.jar (fat jar — topic 08)
java -jar target/hello-demo-1.0.0.jar
```

`mvn spring-boot:run` is the day-to-day inner-loop command — same JVM, no separate compile step, full classpath. The fat-jar form is what you'd actually deploy; topic 08 explains the packaging.

---

## Common pitfalls

- **Putting `App` in `com.example.app` and `Greeter` in `com.example.domain`.** Both still in `com.example`'s sub-tree, both get scanned, fine. But if `Greeter` lives in `com.other.greeter`, `@ComponentScan` will not find it — the scan starts at the package of the `@SpringBootApplication` class. Either move the bean to a sub-package or pass `@SpringBootApplication(scanBasePackages = "com.other")`. Most beginners put `App` at the root of the company package on purpose for this reason.
- **Calling `SpringApplication.run` twice in the same JVM.** The second call boots a second, independent context. Almost always a mistake — you wanted one app. `getBean` against `context2` returns different bean instances than `context1`.
- **Returning `void` from `main` and forgetting the `args` parameter.** `SpringApplication.run(App.class)` (no args) compiles, but you lose command-line property overrides (`--server.port=9090`). Always pass `args`.
- **Annotating `App` with `@Configuration` *and* `@SpringBootApplication`.** Redundant — `@SpringBootApplication` already includes a `@Configuration` flavor (`@SpringBootConfiguration`). Some IDE refactorings add the duplicate; remove it.
- **Forgetting `<relativePath/>` on the `<parent>` block.** Without it, Maven looks for a parent POM in the parent *directory* (`../pom.xml`), not in the repository. Confusing error message: "Could not find artifact in local repo." Add the empty `<relativePath/>` to tell Maven "resolve from the repository, not from the filesystem."

---

## Try this yourself

1. Delete the `@Component` annotation from `Greeter`. Run `mvn test` again. Read the error message — it points at the missing bean. Put `@Component` back.
2. Rename the `App` class to `BootApp` (and the file). Run again. Everything still works — the name is not special; `@SpringBootApplication` is. Now move it to a sub-package `com.example.boot`. Notice `Greeter` (in `com.example`) is no longer in the scan tree; the test fails. Move `Greeter` to `com.example.boot.greeter` and watch the test pass again. That is `@ComponentScan` doing its job from the `BootApp` package down.
3. Run `mvn -q spring-boot:run -- --debug`. Skim the very long auto-configuration report that prints. Notice the `Positive matches` and `Negative matches` sections. Topic 04 lives entirely inside that report.

---

## Self-check

1. `@SpringBootApplication` is composed of three other annotations. Name each, and say what each one does on its own. Which is the only one that didn't exist in module 07?
2. `SpringApplication.run(App.class, args)` returns a value. What is its type, what is it for, and why don't most Boot tutorials show you using the return value?
3. You move `Greeter` from `com.example` to `com.elsewhere`, leaving `App` in `com.example`. The test fails with `NoSuchBeanDefinitionException`. In one sentence, why? In one more sentence, what's the smallest change that fixes it?

---

## Code examples

In reading order:

- `hello-demo/pom.xml` — the parent POM, the two starters, the spring-boot-maven-plugin.
- `hello-demo/src/main/java/com/example/Greeter.java` — a plain `@Component` with one method.
- `hello-demo/src/main/java/com/example/App.java` — `@SpringBootApplication` + `main` + one `getBean` call.
- `hello-demo/src/main/resources/application.yml` — empty except for the application name (more in topic 05).
- `hello-demo/src/test/java/com/example/AppTest.java` — a `@SpringBootTest` that asserts the context loads and `Greeter` is wired.

Run with:

```bash
mvn -q test                  # runs AppTest
mvn -q spring-boot:run       # runs App.main
```
