# fat-jar-demo

Uses `maven-shade-plugin` to bundle Jackson's classes into the project's own jar so the result runs with a plain `java -jar`.

## Run it

```bash
mvn -q package
java -jar target/fat-jar-demo-1.0.0.jar
```

Expected output:

```text
{"status":"ok"}
```

## Inspect the jar

```bash
jar tf target/fat-jar-demo-1.0.0.jar | head
```

You'll see your `com/example/Main.class` plus a lot of `com/fasterxml/jackson/...` classes — the dependency is physically inside the jar.

Also notice `target/original-fat-jar-demo-1.0.0.jar` — Shade keeps the thin jar around alongside the shaded one for reference.

## What's here

- `pom.xml` — declares Jackson, configures `maven-shade-plugin` at the `package` phase with two transformers: one for the `Main-Class` manifest entry, one for merging `META-INF/services/` files.
- `src/main/java/com/example/Main.java` — serializes a one-key map to JSON, prints it.

## The point

Spring Boot will package fat-jars for you in module 08. Shade is the equivalent for non-Spring projects. Either way, the win is the same: one file, runs anywhere.
