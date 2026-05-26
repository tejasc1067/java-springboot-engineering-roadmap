# multi-mod-demo

A two-module project: `core` (a tiny library) and `app` (depends on `core`). One parent POM at the root aggregates them.

## Layout

```text
multi-mod-demo/
├── pom.xml                                 <- parent, packaging=pom
├── core/
│   ├── pom.xml
│   └── src/main/java/com/example/core/Greeter.java
└── app/
    ├── pom.xml                             <- depends on core
    └── src/main/java/com/example/app/App.java
```

## Build everything

From this directory:

```bash
mvn -q package
```

Maven's reactor sees that `app` depends on `core` and builds them in order.

## Run the app

```bash
java -cp app/target/app-1.0.0.jar:core/target/core-1.0.0.jar com.example.app.App
```

(Windows: replace `:` with `;`.)

Expected output:

```text
hello, multi-module Maven
```

## Build only one module

```bash
mvn -q package -pl app -am       # app + everything it needs (so core too)
mvn -q package -pl core          # just core
```

## The point

The parent POM (`<packaging>pom</packaging>`) doesn't produce a jar — it aggregates children and carries shared config. Each child is its own jar with its own classpath. Inter-module deps are declared with normal `<dependency>` blocks.
