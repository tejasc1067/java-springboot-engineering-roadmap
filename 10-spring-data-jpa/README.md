# 10 — Spring Data JPA

Module 04 taught you how to talk to a database with raw JDBC — `Connection`, `PreparedStatement`, `ResultSet`, and a lot of try-with-resources. That works, but writing it for every query gets old fast, and every line is a place to leak a connection or paste SQL injection into your codebase.

This module replaces that ceremony with **JPA** (Jakarta Persistence API), **Hibernate** (the dominant JPA implementation), and **Spring Data JPA** (the Spring layer that gives you `JpaRepository<Book, Long>` and writes most of your query code for you). By the end you will be able to model a domain with `@Entity`, query it with derived methods or `@Query`, navigate relationships without falling into the N+1 trap, and test the persistence layer in isolation with `@DataJpaTest`.

## Who this is for

- Anyone who finished modules 04 (JDBC) and 09 (REST APIs) and is ready to wire a real database behind the controllers.
- Developers who have seen `@Entity` and `JpaRepository` in someone else's code but cannot say what JPA *is*, what Hibernate is, or what Spring Data JPA adds on top.
- Anyone who has been bitten by `LazyInitializationException` or an N+1 query in production and wants the model that explains why.

## What you'll be able to do at the end

- [ ] Explain the three-layer stack — JPA spec, Hibernate implementation, Spring Data JPA — and what each layer is responsible for.
- [ ] Map a Java class to a database table with `@Entity`, `@Id`, `@GeneratedValue`, `@Column`, and the basic relationship annotations.
- [ ] Choose between extending `CrudRepository`, `PagingAndSortingRepository`, or `JpaRepository` and defend the choice.
- [ ] Write derived query methods (`findByTitleContaining`, `countByAuthor_Name`, `existsByIsbn`) and know when the method name has outgrown the convention.
- [ ] Drop to `@Query` (JPQL or native SQL) when derived methods stop fitting, with `?1` / `:name` parameter binding and projection into a DTO.
- [ ] Model a one-to-many relationship correctly — owning side, `mappedBy`, cascade choices, why bidirectional helpers exist.
- [ ] Spot an N+1 query in the SQL log and fix it with `JOIN FETCH` or `@EntityGraph`.
- [ ] Use `@Transactional` in the right place (service layer, usually), and understand why it does *not* roll back on checked exceptions by default.
- [ ] Use `Pageable`, `Page<T>`, and `Sort` to do pagination the Spring Data way — and connect it back to the hand-rolled version from module 09 topic 12.
- [ ] Write a `Specification<T>` for a dynamic, multi-filter search and combine specs with `and`/`or`.
- [ ] Turn on JPA auditing so `created_at` / `updated_at` fill in without you wiring them by hand.
- [ ] Replace `ddl-auto: create-drop` with Flyway and explain why every production app does this.
- [ ] Write a `@DataJpaTest` that boots only the persistence slice and uses `TestEntityManager` to set up data.
- [ ] List the top JPA pitfalls (lazy loading after the transaction ends, dirty-checking surprises, batch insert without `hibernate.jdbc.batch_size`) and the fix for each.

If you can do all fourteen, the module worked.

## Prerequisites

- Module 04 done. You should be comfortable with what a `Connection`, a `PreparedStatement`, and a transaction are at the JDBC level. JPA hides all of this; knowing it is underneath is what stops JPA from feeling like magic.
- Modules 08 and 09 done. You should be at home with `@SpringBootApplication`, `application.yml`, dependency injection of services into controllers, and writing `MockMvc` tests.
- A JDK installed (Java 17 or newer; examples target Java 21 — Spring Boot 3.x's baseline is Java 17).
- No external database needed. Every demo uses H2 in-memory, the same database module 04 introduced.

## How this module is organized

Topic 01 is the only markdown-only topic. It names the three layers (JPA / Hibernate / Spring Data JPA) and what each one is for. Without that mental model the rest of the module is just annotations.

Topics 02–03 are the **shape** of an entity and the **shape** of a repository — the two halves of every JPA-backed app.

Topics 04–05 are **querying**: derived method names for the easy 80%, `@Query` for the rest.

Topics 06–07 are **relationships and fetch strategy** — the area where most production JPA bugs live. Topic 07 in particular is the one that explains N+1.

Topic 08 is `@Transactional` — what it does, where to put it, and the checked-exception rollback gotcha.

Topic 09 is pagination/sorting the Spring Data way. Topic 10 is `Specification<T>` for dynamic queries that derived methods can't express.

Topic 11 is auditing. Topic 12 is Flyway, which is the production answer to "should Hibernate manage my schema?" (no).

Topic 13 is testing with `@DataJpaTest` — a focused slice that boots only JPA, not the whole web stack.

Topic 14 is a catalog of the bugs JPA users hit most often, with the cause and the fix for each.

```text
01-what-jpa-hibernate-spring-data-are       <- three-layer mental model; markdown-only
02-entities-and-id-generation               <- @Entity, @Id, @GeneratedValue, @Column, @Table
03-jparepository-and-the-hierarchy          <- Repository -> CrudRepository -> JpaRepository
04-derived-query-methods                    <- findByX, countByX, existsByX, return types
05-query-jpql-and-native                    <- @Query, parameter binding, projections
06-relationships-onetomany-manytoone        <- owning side, mappedBy, cascade, bidirectional
07-fetch-types-and-n-plus-one               <- LAZY vs EAGER, N+1 demo, JOIN FETCH, @EntityGraph
08-transactional                            <- propagation, rollback rules, where to put it
09-pagination-and-sorting                   <- Pageable, Page, Slice, Sort
10-specifications-and-dynamic-queries       <- JpaSpecificationExecutor, Criteria API
11-auditing                                 <- @CreatedDate, @LastModifiedDate, @EnableJpaAuditing
12-flyway-migrations                        <- V1__init.sql, ddl-auto debate
13-testing-with-datajpatest                 <- slice test, TestEntityManager, embedded H2
14-common-jpa-pitfalls                      <- LazyInitializationException, batch inserts, flush
```

```text
COMMON_MISTAKES.md       <- real JPA bugs with code and the fix
INTERVIEW_QNA.md         <- interview-grade questions on entities, repos, fetch, transactions, testing
```

## The toolkit (what we add to the pom)

Module 10 stays on the same Spring Boot version as module 08/09 so projects move between modules without surprises. The big change is the starter: instead of `-web`, you depend on `-data-jpa`, which transitively pulls Hibernate, the JDBC starter, HikariCP, and Spring Data JPA.

| Library | Coordinates | What it does | First used in |
|---------|-------------|--------------|---------------|
| Spring Boot Starter Parent | `org.springframework.boot:spring-boot-starter-parent:3.5.14` | parent POM; locks versions | every topic |
| Spring Boot Starter Data JPA | `org.springframework.boot:spring-boot-starter-data-jpa` | Hibernate + Spring Data JPA + HikariCP + JDBC | every topic |
| H2 Database | `com.h2database:h2:2.2.224` | in-memory database for demos | every topic |
| Spring Boot Starter Test | `org.springframework.boot:spring-boot-starter-test` | JUnit 5, AssertJ, Mockito, Spring test | every topic |
| Flyway Core | `org.flywaydb:flyway-core` (Boot-managed version) | SQL migrations | topic 12 |

What's **not** here: `spring-boot-starter-web`. Module 10 demos run as `main` methods or as tests against `@DataJpaTest` — they do not stand up an HTTP server. Wiring controllers on top is the cross-module exercise at the end of topic 13. There is also no `spring-boot-starter-security` (module 12), no second datasource, and no R2DBC (reactive persistence is outside the scope of this roadmap).

## How to run the examples

Each topic is a real Maven project under `CODE_EXAMPLES/NN-topic-name/topic-demo/`. To see a demo:

```bash
cd 10-spring-data-jpa/CODE_EXAMPLES/02-entities-and-id-generation/entity-demo
mvn -q test                       # runs the assertions
mvn -q spring-boot:run            # boots the app and runs the CommandLineRunner
```

Most topics print the SQL Hibernate generated. Look for it in the console — `show-sql: true` is set in every `application.yml`. Reading the generated SQL is half the point of learning JPA; if you can't see what query Hibernate is sending, you can't reason about performance.

## Checkpoint

You're done with this module when you can:

1. Sketch a `Book` and `Author` entity with the right relationship annotations on each side, without copy-paste.
2. Be shown a controller method that returns 50 books and triggers 51 SQL queries in the log — diagnose it as N+1 and rewrite the repository method to fix it.
3. Take a derived query method that has grown to `findByTitleContainingIgnoreCaseAndAuthor_NameStartingWithOrderByPublishedDateDesc` and replace it with an equivalent `@Query`, defending why.
4. Be given an entity with no `@Transactional` anywhere and asked "where should it go?" — answer "on the service method, not the repository, and not the controller," with the reason.
5. Pin a `flyway-core` dependency to a Boot project, add `V1__init.sql`, flip `spring.jpa.hibernate.ddl-auto` to `validate`, and explain why this is the production setup.
6. Write a `@DataJpaTest` that inserts two `Author`s and three `Book`s via `TestEntityManager`, then asserts a repository method returns the right subset.

If those six feel comfortable, move to module 11, where the entities you have here meet `@RestController` again — with proper exception mapping (`EntityNotFoundException` → 404) and request-level validation that responds with a useful error body.

## A note on what's *not* here

- **No exception handling for repository errors.** When `findById` returns `Optional.empty()`, the demos let the call site decide. Mapping that to a 404 with a `@ControllerAdvice` is module 11.
- **No security.** Row-level filtering, ownership checks, `@PreAuthorize` on repository methods — all module 12.
- **No multi-datasource, no read replicas, no sharding.** One H2 database per demo. Real production setups (write-master + read-replica) are an architectural topic for module 15.
- **No reactive persistence.** No R2DBC, no `ReactiveCrudRepository`. The roadmap is servlet-stack throughout.
- **No JOOQ, no MyBatis, no plain `JdbcTemplate`.** They are valid choices in real systems; the module sticks to JPA because that is the dominant Spring Boot persistence layer.
- **No deep Flyway operations.** Topic 12 shows enough to get migrations running and explains the `ddl-auto` debate. Real migration ops (rollback strategy, baselining a brownfield database, repair) are outside the module.
