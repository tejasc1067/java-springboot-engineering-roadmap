# Auto-Configuration — How Boot Picks Beans for You

When you added `spring-boot-starter` in topic 02, Spring Boot quietly created a Spring context that worked without you writing a `@Configuration` class. When you add `spring-boot-starter-web` in topic 09, it will create an embedded Tomcat, a `DispatcherServlet`, a Jackson `ObjectMapper`, and a default error handler — still without a `@Configuration` of yours. That's auto-configuration. This topic explains how it actually decides what to create.

By the end of this topic you can read the `--debug` auto-configuration report, override an auto-configured bean correctly, and write a small `@Conditional` rule of your own.

---

## The problem this solves

Plain Spring (module 07) makes you spell every bean out. For a web app the boilerplate looks like:

```java
@Configuration
@EnableWebMvc
public class WebConfig {
    @Bean public DispatcherServlet dispatcherServlet() { ... }
    @Bean public TomcatServletWebServerFactory tomcatFactory() { ... }
    @Bean public ObjectMapper objectMapper() { ... }
    @Bean public MappingJackson2HttpMessageConverter jacksonConverter(ObjectMapper m) { ... }
    @Bean public DefaultErrorAttributes errorAttributes() { ... }
    @Bean public DefaultErrorPageRegistrarBeanPostProcessor errorPageRegistrar() { ... }
    @Bean public TomcatProtocolHandlerCustomizer<?> protocolCustomizer() { ... }
    // ...about 20 more
}
```

Nothing in those 20+ beans is *opinion* — almost every Spring web app sets them up the same way. The Boot team's insight: bundle a default `@Configuration` for every common library, ship it inside the starter's jar, and have it apply automatically *unless the user already defined a bean of that type*. That bundle of always-shipped `@Configuration` classes is **auto-configuration**.

You only write `@Configuration` when you want to *differ* from the defaults. Otherwise, auto-configuration is the configuration.

---

## How it works: the three-step decision

For every bean Boot might auto-create, it asks three questions at startup:

1. **Is the relevant library on the classpath?** (`@ConditionalOnClass(Tomcat.class)` — if Tomcat isn't on the classpath, don't try to make a Tomcat bean.)
2. **Has the user already defined a bean of this type?** (`@ConditionalOnMissingBean(ObjectMapper.class)` — if the user has their own `ObjectMapper`, don't make one.)
3. **Has the user opted in (or not opted out)?** (`@ConditionalOnProperty(name = "spring.jpa.open-in-view", havingValue = "true", matchIfMissing = true)`.)

These three predicates live as annotations on the auto-config classes inside `spring-boot-autoconfigure.jar`. When the context boots, Boot walks every registered auto-config class and applies it only if all its conditions hold.

A real one, abbreviated, from inside Spring Boot's own jars:

```java
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.createXmlMapper(false).build();
    }
}
```

Read it as:

- "This whole config only activates if `ObjectMapper` is on the classpath." (It is — Jackson came with `spring-boot-starter-web`.)
- "Within this config, the `jacksonObjectMapper` bean is only registered if the user hasn't defined an `ObjectMapper` themselves."

That second annotation — `@ConditionalOnMissingBean` — is why auto-configuration is *non-intrusive*. The moment you write your own `@Bean ObjectMapper myObjectMapper()`, Boot's version steps aside and yours takes over. No flag, no opt-out, no `@DisableJacksonAutoConfig`. Just define what you want.

---

## The conditional annotations you'll meet

A handful cover ~90% of Boot's auto-config:

| Annotation                            | Activates when                                    | Common use                                                       |
|---------------------------------------|---------------------------------------------------|------------------------------------------------------------------|
| `@ConditionalOnClass(X.class)`        | class `X` is on the classpath                     | "we have a library to integrate with"                            |
| `@ConditionalOnMissingClass("X")`     | class `X` is *not* on the classpath               | "the user didn't pull in the alternative"                        |
| `@ConditionalOnBean(X.class)`         | a bean of type `X` already exists                 | "build this only if its dependency was set up by another config" |
| `@ConditionalOnMissingBean(X.class)`  | no bean of type `X` exists yet                    | "let the user override us"                                       |
| `@ConditionalOnProperty(name = "p", havingValue = "v")` | property `p` equals `v` in the Environment | feature flags, opt-ins                                  |
| `@ConditionalOnWebApplication`        | the context is a web one                          | web-only beans                                                   |
| `@ConditionalOnNotWebApplication`     | the context is not web                            | non-web variants                                                 |
| `@ConditionalOnExpression("#{ ... }")`| SpEL expression evaluates to true                 | rare; for cases the above don't cover                            |

There are more (`@ConditionalOnResource`, `@ConditionalOnSingleCandidate`, `@ConditionalOnJndi`) — read them when you need them. The eight above are the ones you'll see daily.

---

## The `--debug` auto-configuration report

The single most useful diagnostic in Spring Boot. Add `--debug` to your run command (or set `debug=true` in `application.yml`) and Boot prints a long report after startup:

```text
============================
CONDITIONS EVALUATION REPORT
============================


Positive matches:
-----------------

   JacksonAutoConfiguration matched:
      - @ConditionalOnClass found required class 'com.fasterxml.jackson.databind.ObjectMapper' (OnClassCondition)

   TaskExecutionAutoConfiguration matched:
      - @ConditionalOnClass found required class 'org.springframework.core.task.TaskExecutor' (OnClassCondition)

   ...

Negative matches:
-----------------

   DataSourceAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'javax.sql.DataSource' (OnClassCondition)

   WebMvcAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'org.springframework.web.servlet.DispatcherServlet' (OnClassCondition)

   ...

Unconditional classes:
----------------------

   org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
   ...
```

Three sections to read:

- **Positive matches** — auto-configs that activated. These created beans.
- **Negative matches** — auto-configs that *didn't* activate, with the specific predicate that failed. "Did not find class X" usually means the relevant starter isn't in your `pom.xml`.
- **Unconditional classes** — applied unconditionally, no predicates to check.

When something doesn't work in Boot ("I expected Jackson to wire automatically but it didn't"), the report tells you why. Open it, search for the auto-config name, read the predicate that failed.

---

## Overriding an auto-configured bean — three ways

### 1. Define your own bean (the default mechanism)

`@ConditionalOnMissingBean` does the work. Want a custom `ObjectMapper`? Declare one:

```java
@Configuration
public class MyJacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule());
    }
}
```

Boot's auto-configured `ObjectMapper` no longer fires. Yours is the bean.

### 2. Set a property

Some auto-configs are gated on properties. `spring-boot-starter-web` ships an embedded Tomcat by default; switch to Jetty by excluding Tomcat from the starter and adding Jetty:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```

Boot's `@ConditionalOnClass(Tomcat.class)` no longer holds. The Jetty auto-config now does.

### 3. Disable an auto-config explicitly

If you want auto-configuration on for everything *except* one class:

```java
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class App { ... }
```

Or via property:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

Use this rarely. The usual case where it's appropriate: a library on your classpath that an auto-config thinks it should integrate with, but you do not want the integration in this app. The first mechanism (define your own bean) is preferable when it's available.

---

## The mechanism: how Boot finds the auto-configs

Inside `spring-boot-autoconfigure.jar` there is a single file:

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

It is a plain-text list — one fully-qualified class name per line — of every `@AutoConfiguration` class Boot ships. When `@EnableAutoConfiguration` runs, it reads that file, loads every class listed, and tries to apply each. Auto-configs from third-party libraries (Micrometer, Spring Cloud, your company's internal starter) each ship their own copy of that file in their own jar; Boot reads all of them and unions the lists.

You don't usually need to know this. You do need to know it exists when you want to publish your own auto-config in a custom starter (a tiny exercise at the end of this topic). The file format used to be `META-INF/spring.factories` — if you see that name in older code or tutorials, it's the same idea, deprecated since Boot 2.7.

---

## The demo for this topic

`autoconfig-demo` does not write a real auto-config (that requires the `.imports` file and a separate module). Instead it shows the same `@Conditional` machinery in a single project so you can run it and see the bean selection change:

- `Greeter` is an interface.
- `PlainGreeter` and `FancyGreeter` are two implementations.
- `GreeterConfig` is a `@Configuration` with:
  - `@Bean @ConditionalOnProperty(name = "greeter.style", havingValue = "fancy") FancyGreeter fancyGreeter()`
  - `@Bean @ConditionalOnMissingBean(Greeter.class) PlainGreeter plainGreeter()`
- Without setting `greeter.style`, the plain bean wins (the fancy one's condition fails, then "no `Greeter` bean exists yet" holds, so plain registers).
- With `greeter.style=fancy`, the fancy bean registers first, the missing-bean condition fails on the plain one, plain is skipped.

This is exactly the dance Boot's own auto-configs do — just with one bean and one property instead of hundreds.

---

## Common pitfalls

- **Defining `@Bean ObjectMapper` twice.** If two `@Configuration` classes both define the same type without `@ConditionalOnMissingBean`, Boot's auto-config is bypassed (good) but your two beans collide (bad — `NoUniqueBeanDefinitionException`). When overriding an auto-config, your bean replaces Boot's; don't also keep an old override.
- **Reading the `--debug` report and seeing your config in "Negative matches" without realizing why.** A condition fired and you didn't read which one. The report names the exact predicate. Read it character by character.
- **Adding `@ConditionalOnMissingBean` to a `@Bean` and expecting it to win against a different *config class* on the classpath.** `@ConditionalOnMissingBean` works against beans, not against other config classes. If you want to disable a config wholesale, use `exclude` on `@SpringBootApplication`.
- **Putting `@AutoConfiguration` on a class in your own application code.** It is intended for code shipped in a library, registered via the `.imports` file. In application code, just use `@Configuration` — auto-configuration is for *libraries* that want to register defaults for *users*.
- **Expecting auto-configuration to react to a class you added to the classpath at runtime.** Auto-config decisions happen at context startup. Adding a jar later via `URLClassLoader` won't retro-activate the auto-config — you'd have to restart.

---

## Try this yourself

1. Run `mvn -q spring-boot:run` against `autoconfig-demo`. Read the printed greeting — it comes from the plain bean.
2. Run `mvn -q spring-boot:run -Dspring-boot.run.arguments=--greeter.style=fancy`. Read the printed greeting now — it comes from the fancy bean. Without changing a line of Java.
3. Run `mvn -q spring-boot:run -Dspring-boot.run.arguments=--debug` against `hello-demo` from topic 02. Scroll the report. Pick three auto-configs that *didn't* match and read why each was skipped. (Hint: most will say "did not find class X" because we don't have web on the classpath yet.)

---

## Self-check

1. List the three questions an auto-config asks before it registers a bean. Give the annotation name for each.
2. You add `spring-boot-starter-data-jpa` to your project. A `DataSource` bean appears in the context without you writing one. Which condition on `DataSourceAutoConfiguration` was responsible, and how would you confirm it via the debug report?
3. You want to replace Boot's auto-configured `ObjectMapper` with a custom one that pretty-prints. Two valid mechanisms exist. Name both, and explain which one you'd reach for first and why.

---

## Code examples

In reading order:

- `autoconfig-demo/pom.xml` — same two starters as topic 03 (no `-web` yet).
- `.../Greeter.java` — interface.
- `.../PlainGreeter.java` and `.../FancyGreeter.java` — two impls.
- `.../GreeterConfig.java` — `@Configuration` with `@ConditionalOnProperty` + `@ConditionalOnMissingBean`. Read this twice.
- `.../App.java` — `@SpringBootApplication`, retrieves the `Greeter` bean and prints its greeting.
- `src/test/java/com/example/DefaultGreeterTest.java` — `@SpringBootTest` with no property set; asserts `PlainGreeter` is the wired bean.
- `.../FancyGreeterTest.java` — `@SpringBootTest` with `@TestPropertySource(properties = "greeter.style=fancy")`; asserts `FancyGreeter` is the wired bean.

Run with:

```bash
mvn -q test                                                  # both tests pass
mvn -q spring-boot:run                                       # prints the plain greeting
mvn -q spring-boot:run -Dspring-boot.run.arguments=--greeter.style=fancy   # prints the fancy greeting
```
