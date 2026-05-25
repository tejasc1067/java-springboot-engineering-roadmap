# hello-maven

The smallest valid Maven project. Topic 02 walks through every line of this `pom.xml`.

## Run it

```bash
mvn -q package
java -cp target/hello-maven-1.0.0.jar com.example.App
```

Expected output:

```text
hello from a real Maven project
```

## What's here

- `pom.xml` — declares this as a `com.example:hello-maven:1.0.0` jar targeting Java 21. No dependencies.
- `src/main/java/com/example/App.java` — one class, one `main` method. The shape of the project (not the code) is the lesson.

## Without Maven

You can also compile and run this without Maven, just to confirm Maven isn't doing anything magical:

```bash
javac -d out src/main/java/com/example/App.java
java -cp out com.example.App
```

Same output. Maven's value isn't in compiling one file; it's in managing dependencies (topic 04+) and standardizing the build across projects.
