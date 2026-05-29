# properties-demo

Four `@Value`-injected properties on a single bean. Three come from `application.properties`; one has only a default, demonstrating fallback. The test class also overrides a value via `System.setProperty` to show source precedence.

Run:

```bash
mvn -q test
mvn -q compile exec:java
# override at the JVM level:
mvn -q compile exec:java "-Dpricing.app.name=Overridden"
```

Read in this order: `application.properties`, `PricingPolicy`, `AppConfig`, then the test.
