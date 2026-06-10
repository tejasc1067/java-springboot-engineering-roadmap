# Interview questions — Spring Data JPA

Each entry is a real question from a real interview, answered with depth. If you can read the question out loud and reconstruct the answer in your own words, you're ready.

---

## 1. What's the difference between JPA, Hibernate, and Spring Data JPA?

Three layers stacked on top of JDBC.

- **JPA** (Jakarta Persistence API) is a *specification*. It defines annotations (`@Entity`, `@Id`, `@OneToMany`, ...), interfaces (`EntityManager`, `EntityManagerFactory`), and a query language (JPQL). It contains no working code.
- **Hibernate** is the most popular *implementation* of JPA. It actually reads the annotations, builds an entity model, generates SQL, manages the persistence context, and runs the queries through JDBC.
- **Spring Data JPA** is a *repository abstraction* on top of JPA. It lets you declare `interface BookRepository extends JpaRepository<Book, Long>` and generates the implementation at runtime — including parsing derived method names like `findByAuthor` into JPQL.

If your code uses `JpaRepository`, you're using Spring Data JPA. The query runs through Hibernate. The annotations on your entity are JPA. Knowing which layer is responsible for which behavior is the entire point — when you see `LazyInitializationException`, that's a Hibernate-specific exception (it's not in the JPA spec), and the fix is at the Hibernate level.

---

## 2. Why must every `@Entity` class have a no-arg constructor?

JPA needs to instantiate entities via reflection — specifically when loading rows from the database. Reflection-based instantiation calls the no-arg constructor. Without one, Hibernate throws `InstantiationException: No default constructor for entity` at runtime.

Convention: make it `protected` (not `public`). That way application code is pushed toward the meaningful constructor (with required arguments) and you don't accidentally create half-built objects. JPA can still reach the protected constructor via reflection.

---

## 3. Why declare an entity's id as `Long`, not `long`?

Two reasons:

1. **The "is this entity new?" check.** Hibernate uses `id == null` to decide between `INSERT` and `UPDATE`. A primitive `long` defaults to `0`, which is ambiguous — Hibernate sometimes treats a brand-new entity as if it had id 0 already, leading to confusing `merge`-vs-`persist` behavior.
2. **Domain modeling.** An unpersisted entity logically has no id yet. `null` represents that cleanly. `0` is a value that means "the row with id 0."

Same reasoning applies to any other nullable column — prefer `Integer publishedYear` over `int publishedYear` if the column is nullable.

---

## 4. What does `@GeneratedValue(strategy = IDENTITY)` actually do, and what's the difference from `SEQUENCE`?

**IDENTITY**: the database assigns the id via an auto-increment column. Hibernate sends the INSERT, the database picks the next value, and the assigned id is returned to Hibernate in the response. *Side effect:* JDBC batching is disabled — Hibernate has to send each INSERT alone to get its id back. Fine for typical CRUD, bad for bulk inserts.

**SEQUENCE**: Hibernate asks the database for the next sequence value *before* INSERT, then includes that id in the INSERT statement. Hibernate can request a block of N ids at once (the `allocationSize`), which makes batched inserts efficient. Postgres and Oracle support sequences natively; MySQL does not.

**AUTO**: lets Hibernate pick. On most databases this resolves to SEQUENCE and may create a hidden `hibernate_sequence` table. Pick a concrete strategy explicitly.

**UUID** (Spring Data extension): string ids, useful when you need globally-unique ids across services. Larger key (16 bytes vs 8) costs index size and join performance.

---

## 5. Walk me through how Spring Data JPA generates an implementation for an interface like `BookRepository`.

At application startup, Spring scans for interfaces that extend `Repository` (transitively — `JpaRepository<T, ID>` extends `Repository`). For each one, Spring:

1. Generates a proxy class implementing the interface.
2. Wires inherited methods (`save`, `findById`, `findAll`, etc.) to a backing `SimpleJpaRepository<T, ID>` that uses the configured `EntityManager`.
3. For each custom method, parses the method name against a grammar (`findBy`, `countBy`, `existsBy`, etc.) and resolves field names against the entity model. The parsed query is compiled into JPQL.
4. Validates the JPQL against the entity model. **A typo in a field name fails at startup**, not at first call — that's a feature.
5. Registers the proxy as a Spring bean keyed on the interface.

You then `@Autowire BookRepository repository;` and call methods. Every call goes through the proxy, which manages transactions and dispatches to either the inherited CRUD machinery or the parsed-query machinery.

---

## 6. When would you choose `@Query` over a derived method?

Three signals:

1. **The method name has outgrown the convention.** `findByTitleContainingIgnoreCaseAndAuthor_NameStartingWithIgnoreCaseAndPublishedYearBetween` parses, but is unreadable.
2. **You need a JPQL feature the convention doesn't expose.** Subqueries, `CASE WHEN`, `GROUP BY`, `HAVING`, function calls.
3. **You need grouped OR conditions** — `(A OR B) AND C`. Derived methods are flat left-to-right and cannot express parenthesized groupings.

Use native SQL (`@Query(value = "...", nativeQuery = true)`) when JPQL itself is too limited: window functions, CTEs, vendor-specific functions (Postgres `JSONB`, MySQL `JSON_EXTRACT`).

A healthy repository often has all three: derived methods for the easy 80%, `@Query` JPQL for the harder 15%, native SQL for the last 5%.

---

## 7. Explain the N+1 problem with a concrete example, and two ways to fix it.

You load a list of authors:

```java
List<Author> authors = authorRepository.findAll();      // SELECT * FROM authors — 1 query
for (Author a : authors) {
    System.out.println(a.getBooks().size());            // SELECT * FROM books WHERE author_id = ?
                                                        // — one per author, N queries
}
```

Total: 1 + N queries. On 1,000 authors, 1,001 round trips. A page that's fast in dev with 3 rows becomes a 30-second timeout in production.

**Fix 1: JPQL `JOIN FETCH`.**

```java
@Query("SELECT DISTINCT a FROM Author a JOIN FETCH a.books")
List<Author> findAllWithBooks();
```

One query. `DISTINCT` deduplicates the author rows that the JOIN multiplied. Hibernate optimizes `DISTINCT` in JPQL — the dedup happens in memory, not via SQL `DISTINCT`.

**Fix 2: `@EntityGraph`.**

```java
@EntityGraph(attributePaths = "books")
@Query("SELECT a FROM Author a")
List<Author> findAllWithBooks();
```

Same one-query result via `LEFT OUTER JOIN`. Cleaner for derived methods (no JPQL to embed `JOIN FETCH` into) and for multi-path fetches (`attributePaths = {"books", "publisher"}`).

What **not** to do: change `@OneToMany` to `fetch = EAGER`. Solves N+1 here, breaks every other caller who didn't need the books.

---

## 8. What's the difference between `CascadeType.REMOVE` and `orphanRemoval = true`?

`CascadeType.REMOVE`: when the parent is *deleted*, the children are also deleted.
`orphanRemoval = true`: when a child is *removed from the parent's collection* (and not re-attached to another parent), the child is deleted.

Example:

```java
@OneToMany(mappedBy = "author", cascade = CascadeType.REMOVE, orphanRemoval = true)
private List<Book> books;
```

- `authorRepository.delete(bloch)` → CascadeType.REMOVE fires → all of Bloch's books are also deleted.
- `bloch.getBooks().remove(secondBook); authorRepository.save(bloch);` → orphanRemoval fires → `secondBook` is deleted (it has no author anymore).

`CascadeType.REMOVE` is about deleting parents. `orphanRemoval` is about reassigning children. Most parent-child relationships want both; aggregate-style relationships (`Post.comments`) need both.

---

## 9. Where should `@Transactional` go — controller, service, or repository?

**Service.** That's the unit of work — one user-facing operation, one transaction.

- **Controller:** no. Transactions opening before request validation means a database connection is held while Jackson deserializes the body, while Bean Validation runs, while security filters check authorization. Couples web shape to persistence shape.
- **Service:** yes. The method represents one business operation; that operation is one atomic unit.
- **Repository:** Spring Data JPA already annotates the standard methods (`save`/`delete` writable, finders read-only). You don't add `@Transactional` on top.

A common shape:

```java
@Service
@Transactional(readOnly = true)               // default: read-only
public class BookService {
    public Book find(Long id) { ... }         // inherits readOnly
    @Transactional                            // overrides to writable
    public void rename(Long id, String t) { ... }
}
```

---

## 10. By default, on what kind of exception does `@Transactional` roll back?

**Only `RuntimeException` and `Error`.** Checked exceptions (any `Exception` that doesn't extend `RuntimeException`) cause the method to throw, but the transaction **commits**.

This is the most-asked Spring trivia question because it surprises everyone. The rationale is historical (EJB 1.0 set the rule in 1998); Spring preserved it.

Fixes:

- `@Transactional(rollbackFor = MyException.class)` to roll back on a specific checked exception.
- `@Transactional(rollbackFor = Exception.class)` to roll back on any exception.
- Use unchecked exceptions for domain errors — derive from `RuntimeException`.

Most production codebases either standardize on unchecked exceptions or set `rollbackFor = Exception.class` as the project-wide default.

---

## 11. What is the self-invocation problem with `@Transactional`?

`@Transactional` works via a Spring AOP proxy. When you call a `@Transactional` method *through the proxy* (via dependency injection), Spring opens a transaction. When you call it *via `this.`* from another method on the same bean, the call bypasses the proxy and `@Transactional` does nothing.

```java
public void outer() {
    this.inner();        // bypasses the proxy
}
@Transactional
public void inner() { ... }
```

`inner()` here runs with no transaction. Dirty checking doesn't fire, writes get rolled back, things "don't save" silently.

Fixes:
- Move one of the methods to a different bean — now `this.inner()` becomes `otherBean.inner()` and goes through the proxy.
- Inject `this` (`@Autowired private BookService self;`) and call `self.inner(...)`.
- `((BookService) AopContext.currentProxy()).inner(...)`.

The first is least bad. The second is a smell but works. The third is tightly coupled to AOP internals.

---

## 12. What does `@Transactional(readOnly = true)` do?

Three things:

1. Hints the JDBC driver and connection pool that the transaction won't write — useful for read-replica routing.
2. Disables Hibernate's dirty-checking snapshot for the session. Modifications to managed entities are silently ignored at flush time.
3. Acts as a safety net against accidental writes.

The third point has a sharp edge: if you accidentally set `readOnly = true` on a method that does write, the write **silently fails** — no exception, just no UPDATE. Pair `readOnly` with code review.

A common pattern: `@Transactional(readOnly = true)` at the service-class level as the default, with `@Transactional` (writable) overriding on specific write methods.

---

## 13. `JpaRepository.save()` vs `EntityManager.persist()` — what's the difference?

`EntityManager.persist(entity)` makes a brand-new entity managed. It throws if the entity already has an id and is detached.

`EntityManager.merge(entity)` copies a detached entity's state into a managed copy. Returns the managed copy (`entity` itself stays detached).

`JpaRepository.save(entity)` is "do the right thing":
- If the entity is new (id is null, or the id matches no row), call `persist`.
- Otherwise, call `merge`.

`save` is the recommended default for repository callers. Use `persist` directly if you want to enforce "this must be new" semantics, `merge` if you want to enforce "this must update an existing row."

---

## 14. `findById(id)` vs `getReferenceById(id)` — when to use each?

`findById(id)` → returns `Optional<T>`. Executes a SELECT immediately to confirm the row exists. The returned entity is fully loaded.

`getReferenceById(id)` → returns `T`. Returns a *proxy* that does not execute any SQL until you touch a field. Throws `EntityNotFoundException` later, on first field access, if the row doesn't exist.

Use `getReferenceById` when you only need the id to set a foreign-key relationship:

```java
Author author = authorRepository.getReferenceById(42L);  // no SELECT
book.setAuthor(author);
bookRepository.save(book);                                // INSERT with author_id = 42
```

Use `findById` when you actually need the entity's data, or when "missing row" should be a normal outcome (Optional pattern).

The footgun: touching `getReferenceById` result outside a transaction throws `LazyInitializationException`. Default to `findById` unless you have a measured reason.

---

## 15. How does Spring Data JPA's `Page<T>` work, and how is it different from `Slice<T>`?

`Page<T>` returns content + metadata: `getContent()`, `getTotalElements()`, `getTotalPages()`, `getNumber()`, `isFirst()`, `isLast()`, `hasNext()`, `hasPrevious()`. Implementing it costs **two SQL queries**: one with `LIMIT`/`OFFSET` for the page content, and one `SELECT COUNT(*)` for the total.

`Slice<T>` returns content + only `hasNext()`. No total. Implemented with a single query that asks for `pageSize + 1` rows internally — if `pageSize + 1` rows come back, hasNext is true.

When to use which:
- Need total page count or total result count (UI shows "Page 3 of 47") → `Page<T>`.
- Just need infinite-scroll or next-button navigation → `Slice<T>`. Halves the query work.

On a million-row table, the `COUNT(*)` may be the slow part of the response. `Slice<T>` is a free perf win there.

---

## 16. What is `Specification<T>` and when would you use it over `@Query`?

`Specification<T>` is Spring Data's thin wrapper around JPA's Criteria API. It represents a WHERE-clause fragment built programmatically. The repository extends `JpaSpecificationExecutor<T>` to accept them.

Use it when **filters are optional and decided at runtime** — an admin search page with five filters where any combination may be set. You write small static factory methods (`hasAuthor(String)`, `titleContains(String)`, `publishedAfter(int)`) and compose them with `.and(...)` / `.or(...)`.

Use `@Query` when the query is fixed and complex. Use derived methods when the filter set is small and fixed.

The win for specifications: composability. The cost: verbosity at the call site, and the Criteria API itself has a learning curve.

---

## 17. What does `@EnableJpaAuditing` do, and how do `@CreatedDate` / `@LastModifiedDate` get filled in?

`@EnableJpaAuditing` activates the `AuditingEntityListener` infrastructure. Then, for each entity with `@EntityListeners(AuditingEntityListener.class)` and `@CreatedDate` / `@LastModifiedDate` / `@CreatedBy` / `@LastModifiedBy` fields, Spring Data JPA listens for `@PrePersist` and `@PreUpdate` lifecycle events and fills the fields automatically.

`@CreatedDate` and `@LastModifiedDate` are filled with the current `Instant` (or `LocalDateTime`, etc. — pick `Instant` for sanity).

`@CreatedBy` and `@LastModifiedBy` are filled from a bean of type `AuditorAware<T>` that you provide. In a real app, that bean reads the current user from Spring Security's `SecurityContextHolder`. Without an `AuditorAware`, the "by" fields stay null.

Easy bug: forget `@EnableJpaAuditing`, see `created_at: null` in the DB, no error message anywhere.

---

## 18. Why does production code use Flyway instead of `ddl-auto: update`?

`ddl-auto: update` is convenient — Hibernate keeps the schema in sync with your entities. The problems:

1. **No rollback path.** If a deploy goes wrong, you can't revert the schema with the code.
2. **Cannot rename, drop, or alter type.** `update` only adds.
3. **Cannot add a NOT NULL column with existing rows** — there's no default-value mechanism in entity annotations that handles backfill.
4. **The schema is a side effect of Java code review,** not its own reviewable artifact.
5. **No record of what's been applied.** No history table; no way to know what state the database is in.

Flyway addresses all five. SQL migrations are committed to the repo, run in order, recorded in `flyway_schema_history`, never re-run, and never edited. JPA runs in `ddl-auto: validate` mode and just checks the entity model matches.

The trade: you write more SQL. The benefit: every schema change is reviewed, versioned, repeatable, and reversible (with a follow-up migration).

---

## 19. What is `LazyInitializationException` and what are the right ways to fix it?

Hibernate's exception: you tried to load a lazy field (a `@OneToMany` collection, a `@ManyToOne` proxy) outside the session that originally loaded the parent. The lazy proxy has no session to issue its query against.

Three correct fixes:

1. **Eager-load with `JOIN FETCH` or `@EntityGraph`** — fetch the relationship in the same SQL statement that loaded the parent. Per-query, not per-entity.
2. **Convert to a DTO inside the transaction** — map `Author` to `AuthorDto(name, books.size())` while the session is still open. The DTO has no lazy fields, so it can travel anywhere safely.
3. **Push the work down into the transaction** — perform the collection access inside the `@Transactional` service method, not in the controller.

Wrong fix: switch the entity-level fetch to EAGER. That fixes this caller but punishes every other call site with unwanted loads.

Open Session In View (`spring.jpa.open-in-view: true`) papers over the bug by keeping the session open for the entire HTTP request — but it holds a database connection for the entire response rendering time. Default to `false` and fix the lazy-init errors properly.

---

## 20. Why is `equals`/`hashCode` based on the database id a bad pattern?

For a new entity, the id is null until INSERT. If you add the entity to a `HashSet` or use it as a `Map` key before persistence, the `hashCode()` is whatever `Objects.hashCode(null)` returns. After Hibernate assigns the id at flush, `hashCode()` returns a different value. The set's internal bucketing is now wrong — `set.contains(entity)` returns false even though the entity is "in" the set.

Two acceptable patterns:

1. **Use a stable business key** for equality — ISBN, email, slug, anything that exists before persist and never changes.
2. **Don't override `equals`/`hashCode`** — rely on reference equality. Java's default. Works fine for entities that live within a single persistence context; breaks down across sessions, but for many apps that's acceptable.

What absolutely doesn't work: equals on auto-generated id.

---

## 21. `spring.jpa.open-in-view` — what is it and why disable it?

Open Session In View is a servlet filter that keeps the Hibernate session open for the *entire* HTTP request, including JSON serialization. Lazy field access in the controller (and inside Jackson, during rendering) works because the session is still open.

That's the upside: you don't see `LazyInitializationException` as often.

The downsides:

1. **Holds a database connection for the full request.** Under load, the connection pool fills with idle connections still owning sessions. New requests wait for connections that aren't doing any work.
2. **Hides architectural problems.** If your controller needs `book.getAuthor().getName()`, you should have fetched the author in the query. OSIV makes the lazy SELECT happen during JSON rendering — silent, slow, and hard to spot in profiling.
3. **Breaks under reactive or async patterns.** The "view" assumption is servlet-stack synchronous request-response. Modern patterns don't honor it.

Best practice: `spring.jpa.open-in-view: false` and treat every `LazyInitializationException` as a real bug to fix with eager fetching or DTOs.

---

## 22. Why does `GenerationType.IDENTITY` defeat batch inserts, and what's the workaround?

JDBC batching groups multiple INSERTs into one network round-trip — huge throughput win for bulk loads. Hibernate enables batching automatically when `hibernate.jdbc.batch_size` is set.

With `IDENTITY`, the database assigns the id during INSERT. Hibernate has to send each INSERT alone to read back the assigned id (so it can populate the in-memory entity). It cannot defer "what id do I get?" until the batch flushes, so it disables batching for IDENTITY-generated entities.

With `SEQUENCE`, Hibernate asks the database for a block of ids up front (`allocationSize = 50` requests 50 ids per round trip), assigns them to entities in memory, and then can batch-INSERT freely with the ids already known.

Workarounds for bulk inserts on IDENTITY entities:

- Switch the entity to `SEQUENCE` if your database supports sequences.
- Use raw JDBC for the bulk-insert path, sidestepping JPA entirely.
- Accept the cost — for small bulks (a few hundred), it's fine.

---

## 23. What is `@DataJpaTest` and how does it differ from `@SpringBootTest`?

`@DataJpaTest` is the JPA-slice test annotation. It:

1. Boots only the JPA layer (entities, repositories, `EntityManager`, `TransactionManager`). No services, no controllers, no web stack.
2. Auto-configures an embedded H2 database (override with `@AutoConfigureTestDatabase(replace = NONE)`).
3. Wraps each test in a transaction that rolls back at the end — clean state per test.
4. Provides a `TestEntityManager` (Spring's wrapper around `EntityManager` with `persistAndFlush`, `persistFlushFind`, etc.).

`@SpringBootTest` boots the entire application context — every bean, every auto-configuration, the whole web stack if applicable.

Pick `@DataJpaTest` for repository tests: faster startup, smaller context, automatic rollback. Pick `@SpringBootTest` for cross-layer integration tests (controller + service + repo).

A healthy test suite has many of both, with the slow integration tests in the minority.

---

## 24. How would you detect N+1 in a running application?

Three approaches at three levels of formality:

1. **Read the SQL logs.** `spring.jpa.show-sql: true` plus `org.hibernate.SQL` at DEBUG. If you see the same SELECT shape repeated with different parameter values for one request, that's N+1.
2. **Use Hibernate's `Statistics` API** in tests. `entityManagerFactory.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount()` gives a query count per code block. Useful in regression tests that pin the query count.
3. **Use a profiler or APM tool.** Datadog, New Relic, Spring Boot Actuator + Micrometer give per-endpoint query counts and traces. A spike in query count or a "10× normal" signature against a specific endpoint flags N+1 in production.

Cheap heuristic: **if an endpoint runs more queries than there are rows in the response, N+1 is happening somewhere.**

---

## 25. Walk me through the lifecycle of an entity from `new Book(...)` to a row in the database.

1. **Transient.** `Book b = new Book("Effective Java", ...)`. Not managed, not in any persistence context, no row in the DB.
2. **Persistent.** Inside a `@Transactional` method, call `em.persist(b)` or `repository.save(b)`. Hibernate adds the entity to its persistence context, generates an id (for IDENTITY: by issuing the INSERT immediately; for SEQUENCE: by reserving from a pool). The entity is now *managed* — dirty checking applies.
3. **Flushed.** At transaction commit (or at explicit `em.flush()`), Hibernate compares each managed entity against its snapshot and emits UPDATEs for the dirty ones. INSERTs for the new ones (already done for IDENTITY) and DELETEs for those marked `em.remove(...)`.
4. **Committed.** Transaction commits. The session may or may not stay open — depends on transaction scope and OSIV.
5. **Detached.** Transaction closes, session ends, or `em.clear()` is called. The entity is no longer managed. Dirty checking no longer applies. Modifications to its fields do not persist.
6. **Removed.** Calling `em.remove(b)` on a managed entity transitions it to "removed" — Hibernate emits a DELETE at flush.

Knowing which state an entity is in answers most "why didn't my change save?" questions.

---

## 26. What does `cascade = CascadeType.ALL` actually do, and is it usually a good idea?

`CascadeType.ALL` is shorthand for all of:

- `PERSIST` — saving the parent also persists the children
- `MERGE` — merging the parent also merges the children
- `REMOVE` — deleting the parent also deletes the children
- `REFRESH` — refreshing the parent also refreshes the children
- `DETACH` — detaching the parent also detaches the children

For aggregates where children don't exist outside the parent (`Post.comments`, `Order.lineItems`, `Author.books` in a books-only model), `CascadeType.ALL` is fine.

For relationships where children have independent meaning (`User.orders` — orders outlive users for compliance reasons; `Author.books` if authors and books are managed separately), `CascadeType.REMOVE` is dangerous. Deleting a user shouldn't silently delete their orders.

Default to opting in to specific cascades (`PERSIST`, `MERGE`) and never `REMOVE` unless the relationship is genuinely a parent-child aggregate.

---

## 27. How do you write a JPA query with conditional WHERE clauses based on runtime flags?

Two answers depending on shape.

**Specifications** (recommended for genuinely dynamic queries):

```java
Specification<Book> spec = Specification.unrestricted();
if (criteria.author() != null) spec = spec.and(BookSpecs.hasAuthor(criteria.author()));
if (criteria.minYear() != null) spec = spec.and(BookSpecs.publishedYearAtLeast(criteria.minYear()));
return repository.findAll(spec, pageable);
```

The repository extends `JpaSpecificationExecutor<Book>`. Composes cleanly, supports paging, gives type safety.

**Dynamic JPQL via the `EntityManager` and Criteria API directly** — same idea but more verbose. Useful when you need full control.

**Native dynamic SQL** — last resort. Build the SQL string with `StringBuilder` and bind parameters. The price: lose type safety, fight injection bugs.

What **not** to do: write five `@Query` methods for five filter combinations. The combinatorial explosion gets out of hand fast.
