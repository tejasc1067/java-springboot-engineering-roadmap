# hello-demo

The smallest interesting Spring Boot application: `@SpringBootApplication`, one `@Component`, and a `@SpringBootTest` that confirms the context loads.

## Run

```bash
mvn -q test                  # runs the @SpringBootTest assertions
mvn -q spring-boot:run       # boots the context, prints "Hello, Spring Boot!", exits
```

## What to notice

- No `AppConfig.java`. `@SpringBootApplication` is the configuration class.
- `Greeter` lives in the same package tree as `App`, so `@ComponentScan` (folded into `@SpringBootApplication`) finds it without configuration.
- `pom.xml` pins no library versions. The `spring-boot-starter-parent` parent POM does that for every transitive dependency.
- The JVM exits when `main` returns because no embedded web server is on the classpath. Topic 09 adds one.
