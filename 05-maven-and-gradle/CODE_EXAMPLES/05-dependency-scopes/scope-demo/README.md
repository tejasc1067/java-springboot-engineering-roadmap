# scope-demo

One dependency per common scope, side by side. Demonstrates the compile-time and runtime classpath rules of each.

## Run it

```bash
mvn -q test
```

Expected: 1 test passes, asserting that SLF4J picks up the simple binding at runtime.

```bash
mvn -q package
jar tf target/scope-demo-1.0.0.jar
```

The bundled jar contains your `.class` files but **not** JUnit or the servlet API.

## What's here

- `pom.xml` — four dependencies, one per scope: SLF4J API (compile), SLF4J Simple (runtime), Jakarta Servlet API (provided), JUnit Jupiter (test).
- `src/main/java/com/example/ScopeDemo.java` — uses compile + provided scopes. A commented `import org.junit.jupiter.api.Test;` shows what test-scope blocks.
- `src/test/java/com/example/ScopeDemoTest.java` — uses test scope. Asserts the SLF4J binding at runtime.

## The point

The scope is the small text that controls what's on the classpath at each phase. Get it right and your deployed jar is lean, your test code can't bleed into production, and your production code can't reach into vendor classes it shouldn't.
