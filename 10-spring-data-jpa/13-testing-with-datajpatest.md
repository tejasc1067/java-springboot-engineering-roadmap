# Testing repositories with `@DataJpaTest`

Every repository test in this module so far used `@SpringBootTest` — boot the entire Spring application context, then run the test. That works, but it's heavy: the web layer starts, every service bean instantiates, every `@Configuration` runs. For testing a repository, none of that is needed. `@DataJpaTest` is the focused alternative: it boots only the JPA slice, swaps in an embedded database, and wraps each test in a transaction that rolls back at the end.

---

## The problem this solves

Module 09 topic 11 covered the same idea on the web side: `@WebMvcTest` is to `@SpringBootTest` what a slice test is to a full-context test. `@DataJpaTest` is the persistence-side version.

A `@SpringBootTest` of a 100-class application starts 100 beans. That takes seconds. Multiply by hundreds of tests across the codebase and the test suite is now too slow to run on every push.

`@DataJpaTest` starts only the JPA-related beans (entities, repositories, transaction manager, `EntityManagerFactory`). Service beans, controllers, security, the web server — none of them load. The startup is measured in milliseconds, not seconds.

---

## How it works

The annotation does five things:

1. **Auto-configures only the JPA slice.** No `@Service` beans, no `@Controller` beans, no auto-configured web stack. The application context contains entities, repositories, the `DataSource`, the `EntityManagerFactory`, the `TransactionManager`, and `TestEntityManager`.
2. **Swaps in an embedded H2 database** (default). If you've configured Postgres or MySQL in `application.yml`, `@DataJpaTest` overrides it. To keep your real database, use `@AutoConfigureTestDatabase(replace = NONE)`.
3. **Wraps each test in a transaction.** When the test method returns, the transaction rolls back — so the next test starts with empty tables.
4. **Sets `spring.jpa.hibernate.ddl-auto: create-drop`** for the test context. The schema is built from your entities at startup.
5. **Provides `TestEntityManager`** — a Spring wrapper around `EntityManager` with friendlier method names (`persistAndFlush`, `clear`, `find`).

The demo test class:

```java
@DataJpaTest
class BookRepositorySliceTest {

    @Autowired private TestEntityManager em;
    @Autowired private BookRepository repository;

    @Test
    void findByAuthor() {
        em.persist(new Book("Effective Java", "Joshua Bloch", 2017));
        em.persist(new Book("Java Puzzlers", "Joshua Bloch", 2005));
        em.persist(new Book("Clean Code", "Robert Martin", 2008));
        em.flush();

        List<Book> byBloch = repository.findByAuthor("Joshua Bloch");

        assertThat(byBloch).hasSize(2);
    }

    @Test
    void rolledBackBetweenTests_previousDataIsGone() {
        assertThat(repository.count()).isZero();
    }
}
```

The last test demonstrates the rollback: even though the previous test inserted three books, this one starts with an empty database.

---

## `TestEntityManager` — the test-friendly wrapper

The methods you'll use most:

| Method | Equivalent on `EntityManager` |
|---|---|
| `persistAndFlush(entity)` | `persist(entity); flush()` — returns the entity with id assigned |
| `persistFlushFind(entity)` | persist + flush + clear + find — useful when you want to be sure you're reading from the DB, not the cache |
| `find(Class, id)` | `find(Class, id)` |
| `flush()` | `flush()` |
| `clear()` | `clear()` |
| `merge(entity)` | `merge(entity)` |
| `remove(entity)` | `remove(entity)` |
| `getEntityManager()` | returns the underlying `EntityManager` if you need it |

The `persistAndFlush` and `persistFlushFind` shortcuts cover 90% of test setup needs. You rarely need raw `EntityManager`.

---

## When to pick `@DataJpaTest` vs `@SpringBootTest`

| Choose `@DataJpaTest` when | Choose `@SpringBootTest` when |
|---|---|
| Testing a repository in isolation. | Testing the integration of repository + service + controller. |
| You want fast tests (seconds matter when you have many). | You need the whole context (cross-layer concerns). |
| You don't need services or controllers. | You need to call a service method that uses multiple repositories. |
| You're verifying derived methods, `@Query`, specifications, fetch behavior. | You're verifying end-to-end behavior. |

The two are not in competition. A healthy test suite has many fast `@DataJpaTest` slices and a smaller number of `@SpringBootTest` integration tests. Module 09 topic 11 made the same argument on the web side.

---

## What `@DataJpaTest` does *not* do

- **Does not start the embedded web server.** If you `@Autowire MockMvc` into a `@DataJpaTest`, the autowiring fails. That's `@WebMvcTest`/`@SpringBootTest` territory.
- **Does not auto-detect your services.** If `BookService` uses `BookRepository`, a `@DataJpaTest` can autowire the repository, but `BookService` is not in the context — autowiring it fails. To test the service, use `@SpringBootTest` or define a `@TestConfiguration` that provides the service explicitly.
- **Does not commit.** The rollback is a feature for test isolation, but it surprises people the first time: "I ran the test, the data isn't in the DB after." Right — that's by design.
- **Does not run Flyway by default in older Boot versions.** Spring Boot 3.0+ runs Flyway in `@DataJpaTest` by default; older code may need `@AutoConfigureTestDatabase(replace = NONE)` plus extra setup.

---

## Overriding the database

If you want `@DataJpaTest` to use the database your `application.yml` configures (Postgres, MySQL, whatever) instead of swapping in H2:

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookRepositoryAgainstRealDbTest { ... }
```

When to do this: when your queries use vendor-specific SQL (JSON columns in Postgres, full-text search functions, vendor-specific dialect quirks) that H2 doesn't faithfully replicate. The cost is needing a real database available in CI.

A middle path: **Testcontainers**. A library that spins up a real Postgres / MySQL in a Docker container for the test run. You get the real dialect *and* isolation. Outside the scope of this module, but worth knowing about — every serious Java backend team uses it.

---

## Common pitfalls

- **Trying to `@Autowire` a `@Service` in a `@DataJpaTest`.** Fails: the service isn't in the slice's context. Fix: use `@SpringBootTest` for cross-layer testing, or import the service explicitly with `@Import(BookService.class)`.
- **Asserting against a `@MockitoBean` in a `@DataJpaTest`.** The `@MockitoBean`/`@MockBean` mechanism is web-slice territory. In `@DataJpaTest`, you have the real repository against an embedded DB — no mocking needed.
- **Forgetting that the transaction rolls back.** The test passes; you check the database afterwards manually and see no rows. That's correct — the rollback fires when the test method returns.
- **Calling `repository.save(...)` and expecting the SQL to run immediately.** It still doesn't — `save` is followed by a flush at the end of the test transaction (or never, if you don't call it explicitly). For "watch the SQL," use `em.persistAndFlush(...)` or `em.flush()`.
- **Expecting `@DataJpaTest` to be faster than `@SpringBootTest` in a one-test class.** The startup difference dominates only when running many test classes. For a single isolated run, the gap is small.
- **Mixing `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = NONE)` and then running against a shared dev database.** Multiple tests can race. Either use a per-test schema or Testcontainers.

---

## Try this yourself

1. Add a `BookService` that uses `BookRepository`. Try to autowire it into the `@DataJpaTest` — fails. Add `@Import(BookService.class)` — now the autowiring works. Read the import semantics in the IDE.
2. Remove `em.flush()` from a test. Notice the test still passes — but the SQL log no longer shows the INSERTs until commit time. Reason about *when* the flush happens.
3. Add `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` to a test and watch the startup log. With H2 already configured, nothing changes — but it's important to know the dial exists.
4. Annotate one test method with `@Rollback(false)` and re-run. Now the previous data persists into the next test, which fails the `rolledBackBetweenTests` assertion. Reflect on why per-test isolation matters.

## Self-check

1. List three things `@DataJpaTest` does that `@SpringBootTest` doesn't.
2. Why is testing a `@Service` inside `@DataJpaTest` not directly supported, and what's the workaround?
3. What does `TestEntityManager.persistAndFlush(book)` do that `repository.save(book)` doesn't?
4. When would you set `@AutoConfigureTestDatabase(replace = NONE)`?
