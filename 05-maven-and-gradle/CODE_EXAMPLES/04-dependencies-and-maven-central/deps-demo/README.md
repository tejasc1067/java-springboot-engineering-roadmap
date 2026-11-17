# deps-demo

Adds real dependencies (Jackson, plus the SLF4J API and a simple SLF4J implementation) and uses them. Demonstrates how Maven Central + transitive resolution work in practice.

## Run it

```bash
mvn -q compile exec:java
```

Expected output (timestamps will vary):

```text
[main] INFO com.example.JsonReadWrite - serialized: {"user":"alice","age":30}
[main] INFO com.example.JsonReadWrite - parsed: {user=alice, age=30}
```

## Inspect the dependencies

```bash
mvn dependency:tree
```

Shows the full graph — including the transitive `jackson-core` and `jackson-annotations` jars you never declared.

## What's here

- `pom.xml` — declares 3 direct dependencies. `slf4j-api` must be declared even though `slf4j-simple` depends on it: anything a `runtime`-scope dependency brings in is runtime-only, so without it the code doesn't compile. The `exec-maven-plugin` lets us run the main class with `mvn exec:java`.
- `src/main/java/com/example/JsonReadWrite.java` — serializes a Map to JSON, parses it back, logs both. Imports from `com.fasterxml.jackson` and `org.slf4j` — both jars Maven fetched for us.

## The point

Without Maven you'd be hunting down jackson-databind, jackson-core, jackson-annotations, slf4j-api, slf4j-simple — five jars — by hand. With Maven you declared three and got five on the classpath.
