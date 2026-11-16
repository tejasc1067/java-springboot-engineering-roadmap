# Testcontainers

Your repository tests have run against H2 since module 10. That's fast and needs no setup, but production runs PostgreSQL, and **H2 is not PostgreSQL**. A query can pass every test you have and fail the first time it meets the real database. **Testcontainers** is a Java library that starts a real database (or broker, or cache) in a Docker container for the duration of a test, then removes it. Your tests run against the same engine as production, from `mvn test`, with nothing installed but Docker.

---

## The problem this solves

H2 is an in-memory database written in Java. It understands most standard SQL, plus its own extras, and it doesn't match any production database exactly. The gaps show up as:

- **Functions and syntax one database has and the other doesn't.** `IFNULL`, `DATEADD`, and `SELECT TOP 1` all work in H2; PostgreSQL has none of them. PostgreSQL's `INSERT ... ON CONFLICT DO NOTHING` is a syntax error in H2.
- **Type rules.** Implicit conversions, boolean handling, and timestamp and time-zone behaviour differ.
- **Features H2 doesn't have.** PostgreSQL's `jsonb` type and its operators, and its exact locking and isolation behaviour.
- **Migrations.** Your Flyway scripts (module 10 topic 12) are written for PostgreSQL. Run them against H2 and they may not even parse.

Each of these is a test that is green on your laptop and an error in production. The tests didn't fail because they never touched the database that matters.

---

## Broken: the query that passes on H2

`testcontainers-demo` has a product catalogue. "In stock" means `stock > 0`, with a never-counted (`null`) stock treated as 0. A developer who learned SQL on MySQL writes:

```java
@Query(value = "select * from product where ifnull(stock, 0) > 0", nativeQuery = true)
List<Product> findInStockBroken();
```

The standard `@DataJpaTest` (module 10 topic 13) swaps in an embedded H2 database, and the test passes:

```java
@DataJpaTest
class H2ProductRepositoryTest {
    @Test
    void brokenQuery_passesOnH2() {
        products.save(new Product("Keyboard", 5));
        products.save(new Product("Old mouse", 0));
        products.save(new Product("New monitor", null));

        assertThat(products.findInStockBroken()).extracting(Product::getName).containsExactly("Keyboard");
    }
}
```

"H2 has a PostgreSQL compatibility mode" is the usual next suggestion. `H2PostgresModeTest` runs the same query with `jdbc:h2:mem:pgmode;MODE=PostgreSQL`, first checking that the database really is in that mode, and the query **still passes**. The compatibility mode adjusts some syntax and behaviour, but it's still H2, with H2's functions.

On PostgreSQL the query fails:

```text
ERROR: function ifnull(integer, integer) does not exist
```

PostgreSQL has no `IFNULL`. The standard function is `COALESCE`.

---

## How it works

Three test dependencies, all with versions managed by Spring Boot's parent:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

The same repository, tested against a real PostgreSQL 17:

```java
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Testcontainers
class PostgresProductRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test
    void sameBrokenQuery_failsOnRealPostgres() {
        assertThatThrownBy(() -> products.findInStockBroken())
                .isInstanceOf(InvalidDataAccessResourceUsageException.class)
                .rootCause().hasMessageContaining("function ifnull(integer, integer) does not exist");
    }

    @Test
    void portableQuery_passesOnRealPostgres() {
        assertThat(products.findInStock()).extracting(Product::getName).containsExactly("Keyboard");
    }
}
```

What each piece does:

- **`@Testcontainers` + `@Container`**: JUnit starts the container before the tests and stops it after. Testcontainers pulls `postgres:17` if needed, starts it on a **random free host port** so it never clashes with anything on your machine, and waits until PostgreSQL is accepting connections.
- **`static`**: one container for the whole test class. Without `static`, a new PostgreSQL would start for every test method.
- **`@ServiceConnection`**: Spring Boot reads the container's JDBC URL, username, and password and configures the `DataSource` from them. You never write a `spring.datasource.url` for tests, and the random port doesn't matter.
- **No `@AutoConfigureTestDatabase(replace = NONE)`.** `@DataJpaTest` normally swaps your database for an embedded H2. Since Spring Boot 3.4 it leaves alone a database that the *test itself* provides (`@ServiceConnection`, or the older `@DynamicPropertySource`). Many tutorials written for older versions still add the annotation. It's harmless, but no longer needed here.
- **`ddl-auto=create-drop`**: Spring Boot only creates the schema automatically for embedded databases. Against a real PostgreSQL the default is `none`, and the first insert fails with `relation "product_seq" does not exist`. In a real project you'd let Flyway create the schema instead, which also tests your migrations.

The fix to the query is one word:

```java
@Query(value = "select * from product where coalesce(stock, 0) > 0", nativeQuery = true)
List<Product> findInStock();
```

`COALESCE` is standard SQL, and H2, PostgreSQL, MySQL, Oracle and SQL Server all have it. The PostgreSQL test proves it rather than assuming it.

Results (`mvn -q test`, PostgreSQL image already pulled):

```text
Tests run: 1  H2PostgresModeTest            (broken query passes)
Tests run: 1  H2ProductRepositoryTest       (broken query passes)
Tests run: 2  PostgresProductRepositoryTest (broken query fails, portable query passes)
```

The whole suite took about 5 seconds, PostgreSQL startup included.

### Who cleans up?

Testcontainers starts a small helper container, `testcontainers/ryuk`, alongside your containers. If the JVM is killed halfway through a test run, Ryuk notices the connection drop and removes every container the run created, so crashed test runs don't leave databases running on your machine.

### Sharing one container across test classes

`static` gives one container per test **class**. With twenty repository test classes, that's twenty PostgreSQL startups. The common pattern is a base class (or a `@TestConfiguration` with a `@ServiceConnection` bean) that starts one container for the whole test run:

```java
abstract class PostgresTestBase {
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    static { POSTGRES.start(); }     // started once per JVM; Ryuk removes it at the end
}
```

Test classes extend it. Spring's test context caching (module 08 topic 11) then reuses the same application context too, as long as the configuration is identical.

### Not just databases

Testcontainers has modules for Kafka and RabbitMQ (module 16), Redis (module 15), MongoDB (module 10 topic 16 used flapdoodle; a container is the production-like option), Elasticsearch, and any image through `GenericContainer`. Module 13 topic 20 stubbed HTTP services you *don't own* with WireMock. Testcontainers is for the infrastructure you *do* own: your database, your broker, your cache.

---

## When to use it / when not to

| Use Testcontainers for | Keep using |
|---|---|
| Repository tests with native queries, database-specific features, or migrations | Plain unit tests with mocks (module 06) for business logic: no database at all |
| A few end-to-end-ish integration tests of the whole service with its real database and broker | H2 for quick checks of plain JPA mapping only if speed matters more than fidelity, knowing the risk above |
| Reproducing a production bug that depends on the database's behaviour | |

**The costs:** Docker must be running on every developer machine and CI agent; the first run downloads images (`postgres:17` is about 700 MB on disk); and each container takes a few seconds to start. Keep the number of container-backed tests modest, and share containers.

---

## Common pitfalls

- **A database URL from ordinary properties in a `@DataJpaTest`.** If the URL comes from `application.yml` or the test's `properties = "spring.datasource.url=..."`, `@DataJpaTest` quietly replaces it with a plain H2 and your tests "pass" on the wrong database. `H2PostgresModeTest` needs `replace = NONE` for exactly this reason: without it, its check that the database is in PostgreSQL mode fails. Provide test databases through `@ServiceConnection`, or add `@AutoConfigureTestDatabase(replace = NONE)`.
- **No schema.** Against a non-embedded database Boot's `ddl-auto` defaults to `none`, so inserts fail with `relation ... does not exist`. Run your Flyway migrations, or set `ddl-auto` for the test.
- **A non-static `@Container` field.** A new database starts for every test method, and the suite becomes minutes long. Make it `static`, or share one container across classes.
- **Using `latest` for the image.** `postgres:latest` changes under you. Use the major version you run in production (`postgres:17`).

---

## Code examples

`CODE_EXAMPLES/11-testcontainers/testcontainers-demo/`. Run `mvn -q test` with Docker running. Read in this order:

1. `src/main/java/com/example/products/Product.java` — the entity, with a nullable `stock`.
2. `src/main/java/com/example/products/ProductRepository.java` — `findInStockBroken` (`IFNULL`) and `findInStock` (`COALESCE`).
3. `src/test/java/com/example/products/H2ProductRepositoryTest.java` — the broken query passes on H2.
4. `src/test/java/com/example/products/H2PostgresModeTest.java` — and still passes in H2's PostgreSQL mode.
5. `src/test/java/com/example/products/PostgresProductRepositoryTest.java` — real PostgreSQL 17: the broken query fails, the portable one passes.

---

## Try this yourself

1. Remove `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` from `H2PostgresModeTest` and run it. Which assertion fails, and what does that tell you about which database the test really used? Why doesn't `PostgresProductRepositoryTest` need the same annotation?
2. Add a native query using `limit 5 offset 10` and another using `fetch first 5 rows only`. Test both on H2 and PostgreSQL. Which ones are portable?
3. Move the container into a `PostgresTestBase` class as shown above, make a second test class extend it, and check in `docker ps` (while the tests run) that only one PostgreSQL container starts.

## Self-check

1. Name two kinds of SQL that pass on H2 and fail on PostgreSQL. Why doesn't H2's `MODE=PostgreSQL` close the gap?
2. What do `@Container`, `static`, and `@ServiceConnection` each do in `PostgresProductRepositoryTest`?
3. A `@DataJpaTest` sets `spring.datasource.url` to a real database in its `properties`, and every test passes, even one using a PostgreSQL-only function. What happened, and what are two ways to fix it?
4. When would you still use a plain unit test with a mocked repository instead of Testcontainers?
