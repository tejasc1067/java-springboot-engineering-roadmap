# External Configuration: @Value and @PropertySource

Topic 03's `PriceCalculator` had a tax rate hardcoded as `new BigDecimal("0.10")`. That works until the tax rate changes, until you need a different rate for staging vs production, until ops asks you "where is that value set?" External configuration solves all three: put values in a properties file (or environment variable), tell Spring to load them, and inject them into your beans with `@Value`.

By the end of this topic you can read a value from `application.properties` into a bean, supply a default for missing values, and explain which sources override which.

---

## The problem this solves

Three lines from earlier topics:

```java
return new PriceCalculator(0.10);                           // wired in topic 02
private final BigDecimal taxRate = new BigDecimal("0.10");  // hardcoded in topic 03
```

Both compile a constant into your code. Changing the rate means recompiling and redeploying. Now imagine the rate is different in production (10%) vs staging (5%) vs your developer laptop (0%). You either ship three different jars or thread the value through `main` from command-line args. Both work; neither scales.

External configuration is the standard fix: keep the rate in a file outside the code, load it at startup, inject it into the bean.

## How it works

Three pieces.

**1. The properties file.** Plain `key=value`. Lives on the classpath — for a Maven project that means `src/main/resources/application.properties`.

```properties
pricing.tax.rate=0.10
pricing.unit.price=10.00
pricing.app.name=PricingApp
```

**2. Tell Spring where to load it.** Add `@PropertySource` to your `@Configuration` class:

```java
@Configuration
@ComponentScan(basePackages = "com.example")
@PropertySource("classpath:application.properties")
public class AppConfig { }
```

`classpath:` means "look in the classpath" — Maven copies `src/main/resources` to the classpath at build time, so `application.properties` ends up there. `@PropertySource("classpath:other.properties")` works just as well; the filename is arbitrary.

**3. Inject the values.** Use `@Value` on a constructor parameter (or field, but topic 04's advice still applies — prefer constructor):

```java
@Component
public class PricingPolicy {

    private final BigDecimal taxRate;
    private final BigDecimal unitPrice;
    private final String appName;

    public PricingPolicy(
            @Value("${pricing.tax.rate}") BigDecimal taxRate,
            @Value("${pricing.unit.price}") BigDecimal unitPrice,
            @Value("${pricing.app.name}") String appName) {
        this.taxRate = taxRate;
        this.unitPrice = unitPrice;
        this.appName = appName;
    }
}
```

`${pricing.tax.rate}` is a **property placeholder**. Spring looks up the key `pricing.tax.rate` in its known property sources, converts the string `"0.10"` into a `BigDecimal`, and supplies it as the constructor argument. Conversion is automatic for common types — `BigDecimal`, `int`, `long`, `boolean`, `Duration`, `List` (comma-separated), and so on. If the property is missing and has no default, the container fails to start with a clear message naming the key.

## Defaults

A common pattern: a property *might* not be set, and you have a sensible fallback. Spell it after a colon inside the placeholder:

```java
@Value("${pricing.max.items.per.order:100}") int maxItems
@Value("${pricing.app.name:UnnamedApp}") String appName
```

`100` is the default for `pricing.max.items.per.order` if no source provides one. The default is itself a string; Spring will convert it to the declared type. Use defaults for *every* property that isn't strictly required — the alternative is a startup failure whenever ops forgets a value.

## Where Spring looks for properties

`@PropertySource` is one source. By default Spring also reads from two others, which take **precedence** over `@PropertySource` files:

1. **JVM system properties** — anything set with `-Dpricing.tax.rate=0.15` on the `java` command line, or via `System.setProperty(...)`.
2. **OS environment variables** — also normalized: `PRICING_TAX_RATE` matches the key `pricing.tax.rate`.
3. **`@PropertySource` files** — lower priority than the two above.

Order matters when the same key is set in two places. System property wins over env var, env var wins over `@PropertySource`. That order is what lets ops *override* a value at runtime without recompiling — set `-Dpricing.tax.rate=0.15` on the JVM, leave the file alone, the override holds.

(Spring Boot adds a richer set of sources — `application.yml`, profile-specific overrides, `spring.config.import`, command-line `--key=value` args. Module 08 covers those.)

## Conversion: what types `@Value` handles

Out of the box, Spring's `ConversionService` will turn a property's string value into:

| Target type | Example property | Notes |
|--|--|--|
| `String` | `pricing.app.name=PricingApp` | trivially |
| `int`, `long`, `double`, `BigDecimal` | `pricing.tax.rate=0.10` | parse error -> startup failure |
| `boolean` | `feature.x.enabled=true` | accepts `true`/`false` |
| `Duration` | `cache.ttl=PT5M` (or `5m`) | ISO-8601 or short form |
| `LocalDate` / `LocalDateTime` | ISO-8601 | |
| `List<String>` | `cors.allowed-origins=a,b,c` | comma-separated |
| `Map<String,String>` (with SpEL) | trickier; see SpEL below | |

For anything more exotic, you can register a custom `Converter` on the application context. Rare in practice.

## Property placeholder vs SpEL

You will see two different syntaxes in `@Value` strings:

```java
@Value("${pricing.tax.rate}")     // PROPERTY placeholder -- look up a key
@Value("#{T(java.math.BigDecimal).valueOf(0.10)}")   // SpEL -- evaluate an expression
```

`${...}` is dumb lookup; `#{...}` is the Spring Expression Language, a small expression evaluator that can reach into other beans, do arithmetic, call static methods, and so on:

```java
@Value("#{pricingPolicy.taxRate}")           // get a property off another bean
@Value("#{systemProperties['user.name']}")   // read a system property explicitly
```

You almost always want `${...}`. SpEL exists; you'll occasionally encounter it; you can mostly ignore it for backend work. If you find yourself needing real logic inside an `@Value`, that logic probably belongs in a `@Bean` method instead.

## Common pitfalls

- **Missing property, no default, startup failure.** `@Value("${pricing.tax.rate}")` with no file value, no system property, and no default crashes the context. Either add a default or guarantee the property is set in every environment.
- **`@PropertySource` file not on the classpath.** Spring tells you `application.properties` cannot be found. Check it's in `src/main/resources` (Maven) and that the build copied it to `target/classes` — `mvn process-resources` will do that.
- **Using `@Value` with a primitive default that needs parsing.** `@Value("${cache.ttl:PT5M}") Duration ttl` works because Spring parses `PT5M`. `@Value("${cache.ttl:5}") Duration ttl` will fail — `5` is not a `Duration`. Use the parseable form, or change the type.
- **Reaching into the environment with `System.getenv` from inside a bean.** Bypasses Spring's environment, defeats `@Value`'s overrides, breaks tests. Inject what you need with `@Value`.
- **Logging the values in plain text.** `@Value`-injected secrets (DB passwords, API keys) printed by an `@PostConstruct` end up in log aggregators. Treat sensitive properties separately — mask, don't print.
- **Field injection with `@Value`.** All of topic 04's reasons still apply -- the test cannot construct the bean without setting the field via reflection. Put `@Value` on a constructor parameter.

---

## Try this yourself

1. Open `properties-demo`. Run `mvn -q compile exec:java`. The output reports the rate, unit price, and app name -- all from `application.properties`. Now run with an override: `mvn -q exec:java -Dexec.args=ignored -Dpricing.app.name=Overridden`. Notice that the JVM system property wins.
2. Edit `application.properties` and remove the `pricing.tax.rate` line entirely. Re-run the test. Spring fails to start and the message names the missing key. Add the line back.
3. Add a new property `pricing.shipping.fee.cents` to the file, inject it as an `int` into `PricingPolicy`, and write a test asserting the value. Notice how short the path was: file -> annotation -> field, no plumbing in `main`.

## Self-check

1. Three property sources contribute to `@Value` resolution by default. What are they, and which wins when the same key is set in two of them?
2. Why is `@Value("${pricing.tax.rate:0.10}")` better than `@Value("${pricing.tax.rate}")` in a typical case? When would you specifically *not* want a default?
3. The difference between `${...}` and `#{...}` in an `@Value` string is what?

---

## Code examples

In reading order:

- `properties-demo/src/main/resources/application.properties` -- the file Spring loads.
- `.../PricingPolicy.java` -- `@Value` on four constructor parameters; three from the file, one with a default.
- `.../AppConfig.java` -- `@PropertySource("classpath:application.properties")`.
- `.../Main.java` -- prints the loaded values; try the override exercise above.
- `src/test/java/com/example/PricingPolicyTest.java` -- asserts file values, the default behavior, and the system-property override.

Run with `mvn -q test` and `mvn -q compile exec:java`.
