# embedded-tomcat-demo

`spring-boot-starter-web` + one `@RestController` + the embedded Tomcat that auto-configuration brings in.

## Run

```bash
mvn -q test                  # boots Tomcat on a random port, asserts GET /hello returns "Hello, World!"
mvn -q spring-boot:run       # boots Tomcat on 8080
curl http://localhost:8080/hello
```

## What to notice

- The pom has `spring-boot-starter-web`, not the bare `spring-boot-starter`. That single change pulls in Tomcat, MVC, and Jackson.
- `App.java` has zero changes from earlier topics. `@SpringBootApplication` does the same job; auto-configuration notices the new classpath and starts a server.
- The startup log now includes `Tomcat started on port 8080 (http) with context path '/'`. That line is your proof the server bound.
- The test uses `RANDOM_PORT` so it doesn't fight a dev server already on 8080.
