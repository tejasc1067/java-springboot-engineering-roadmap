# Common mistakes — Spring Data JPA

Real bugs, with code that demonstrates them and the fix. Skim these before you start a new entity; revisit when persistence behaves unexpectedly.

---

## 1. `javax.persistence` instead of `jakarta.persistence`

```java
import javax.persistence.Entity;       // wrong for Spring Boot 3.x
@Entity
public class Book { ... }
```

**What goes wrong:** Spring Boot 3 moved to Jakarta EE. The `javax.persistence` package is gone. Either compilation fails, or — worse, if you have a stale `javax` jar somewhere — the entity is silently ignored at startup and your repository methods return empty lists.

**Fix:** every persistence import is `jakarta.persistence.*`. Search-and-replace once when upgrading from Spring Boot 2.

---

## 2. Forgetting the no-arg constructor on `@Entity`

```java
@Entity
public class Book {
    @Id @GeneratedValue private Long id;
    public Book(String title) { this.title = title; }     // only constructor
}
```

**What goes wrong:** `org.hibernate.InstantiationException: No default constructor for entity: Book` at runtime when something tries to load a Book.

**Fix:** add `protected Book() {}` so JPA can instantiate via reflection. Protected (not public) discourages callers from creating half-built objects.

---

## 3. Primitive `long` for an `@Id`

```java
@Id @GeneratedValue private long id;       // primitive
```

**What goes wrong:** Hibernate uses `id == null` to detect "is this a new entity?" Primitive `long` defaults to `0`, which is ambiguous. New entities sometimes get treated as existing ones (or vice versa) and you get strange `merge`-vs-`persist` behavior.

**Fix:** use `Long`. Same reasoning for `Integer` over `int` on nullable numeric columns.

---

## 4. `@GeneratedValue(strategy = AUTO)` and the surprise sequence table

```java
@Id @GeneratedValue(strategy = GenerationType.AUTO) private Long id;
```

**What goes wrong:** on Hibernate 6 against most databases this resolves to `SEQUENCE`, and Hibernate creates a hidden `hibernate_sequence` table you didn't ask for. Schema diff between dev and prod gets confusing.

**Fix:** pick `IDENTITY` or `SEQUENCE` explicitly. Document the choice.

---

## 5. Forgetting `mappedBy` on the inverse `@OneToMany`

```java
@Entity class Author {
    @OneToMany private List<Book> books;           // no mappedBy
}
@Entity class Book {
    @ManyToOne private Author author;
}
```

**What goes wrong:** Hibernate thinks both sides own the relationship and creates a *second* join table (`authors_books`) you didn't expect. The `author_id` foreign key on `books` exists too. You now have two ways to represent the same data and they will drift.

**Fix:** add `mappedBy = "author"` on the `@OneToMany` so it's marked as the inverse side.

---

## 6. Updating only the inverse side of a bidirectional relationship

```java
Author bloch = new Author("Joshua Bloch");
Book ej = new Book("Effective Java");
bloch.getBooks().add(ej);                          // only the inverse side
// ej.setAuthor(bloch) is NOT called
authorRepository.save(bloch);
```

**What goes wrong:** the owning side (`Book.author`) is null, so the FK column doesn't get set. Either no INSERT for the book, or the INSERT fails on the NOT NULL constraint.

**Fix:** use a bidirectional helper that updates both sides:

```java
public void addBook(Book book) {
    books.add(book);
    book.setAuthor(this);
}
```

---

## 7. `CascadeType.ALL` (especially `REMOVE`) on every relationship

```java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
private List<Order> orders;
```

**What goes wrong:** an admin runs "delete inactive users" and silently wipes out years of order history. Downstream systems that referenced order ids break.

**Fix:** be deliberate. `CascadeType.PERSIST` and `CascadeType.MERGE` are usually safe. `CascadeType.REMOVE` belongs only on parent-child relationships where the child has no independent meaning (e.g. `Post.comments`).

---

## 8. `@ManyToOne` defaulting to EAGER

```java
@ManyToOne
private Author author;        // EAGER by default — JPA's choice, not a good one
```

**What goes wrong:** every time you load a Book, the Author is loaded with it. If Author has its own EAGER associations, the cascade multiplies. You see "select ... from authors" queries when you didn't ask for the author at all.

**Fix:** write `@ManyToOne(fetch = FetchType.LAZY)` on every `@ManyToOne`. There's no good reason to ever use the default.

---

## 9. Fixing N+1 by switching to EAGER

You see N+1 in the logs. You change `@OneToMany` to `fetch = FetchType.EAGER`. The N+1 goes away. Every other endpoint slows down because *they* now also eagerly load books they didn't need.

**Fix:** keep LAZY; use `JOIN FETCH` or `@EntityGraph` **per-method** for the specific endpoints that need the relationship. Topic 07.

---

## 10. JPQL `JOIN FETCH` without `DISTINCT`

```java
@Query("SELECT a FROM Author a JOIN FETCH a.books")
List<Author> findAllWithBooks();
```

**What goes wrong:** authors appear once per book. An author with 3 books gives 3 references to the same `Author` in the result list.

**Fix:** add `DISTINCT`. Hibernate optimizes JPQL `DISTINCT` to dedupe in memory without affecting the SQL.

```java
@Query("SELECT DISTINCT a FROM Author a JOIN FETCH a.books")
```

---

## 11. `MultipleBagFetchException` joining two collections

```java
@Query("SELECT DISTINCT a FROM Author a JOIN FETCH a.books JOIN FETCH a.reviews")
List<Author> findAll();
```

**What goes wrong:** `org.hibernate.loader.MultipleBagFetchException`. Hibernate cannot reconstruct two unordered collections from a single result set without combinatorial duplication.

**Fix:** change one collection to `Set` (Hibernate can dedupe a `Set` cleanly), or split into two queries, or use `@EntityGraph` for one and `JOIN FETCH` for the other.

---

## 12. `JOIN FETCH` + `Pageable` = in-memory pagination

```java
@Query("SELECT a FROM Author a JOIN FETCH a.books")
Page<Author> findAll(Pageable pageable);
```

**What goes wrong:** the log shows `HHH000104: firstResult/maxResults specified with collection fetch; applying in memory`. Hibernate loads **the whole table** into memory, joins, dedupes, then slices in Java.

**Fix:** use `@EntityGraph(attributePaths = "books")` instead of `JOIN FETCH` for the paginated query; or page first and load the relationship in a follow-up.

---

## 13. Self-invocation bypassing `@Transactional`

```java
public void outer() {
    this.inner();        // bypasses the proxy — inner's @Transactional is ignored
}
@Transactional
public void inner() { ... }
```

**What goes wrong:** `inner()` runs without a transaction. Dirty checking doesn't fire, `EntityManager` writes get rolled back at the implicit transaction boundary, things "don't save" with no error.

**Fix:** move one of the methods to a different bean. The call across beans goes through the proxy. (`AopContext.currentProxy()` works too but is ugly.)

---

## 14. `@Transactional` on a `private` method

```java
@Transactional
private void doWork() { ... }     // ignored
```

**What goes wrong:** Spring's AOP proxies only intercept public methods. The annotation does nothing. Same silent-no-transaction problem as above.

**Fix:** make the method public, or split it into a separate bean.

---

## 15. `@Transactional` not rolling back on a checked exception

```java
@Transactional
public void renameBook(Long id, String title) throws OutOfPagesException {
    book.setTitle(title);
    throw new OutOfPagesException();      // NOT a RuntimeException
}
```

**What goes wrong:** the title change commits. Spring rolls back only on `RuntimeException` / `Error` by default. The exception propagates to the caller, but the database has the new title.

**Fix:** add `rollbackFor = OutOfPagesException.class` (or `Exception.class`), or convert the exception to unchecked.

---

## 16. `@Transactional` on the controller

```java
@RestController
public class BookController {
    @Transactional
    @PostMapping("/api/books") public Book create(@RequestBody BookDto dto) { ... }
}
```

**What goes wrong:** the transaction opens before request validation, holds a DB connection while Jackson deserializes the body, and couples HTTP shape to persistence boundaries.

**Fix:** `@Transactional` belongs on the service method. The controller calls a service that has its own `@Transactional`.

---

## 17. `@Transactional(readOnly = true)` silently ignoring writes

```java
@Transactional(readOnly = true)
public void renameBook(Long id, String title) {
    Book book = repo.findById(id).orElseThrow();
    book.setTitle(title);                // dirty checking disabled in readOnly
}
```

**What goes wrong:** Hibernate disables dirty checking on read-only sessions. The `setTitle` call modifies the in-memory object but no UPDATE is emitted. No exception. The change vanishes.

**Fix:** check that any method that writes is not annotated `readOnly = true`. A common shape is `@Transactional(readOnly = true)` at the class level + `@Transactional` (writable) on the specific write methods.

---

## 18. `LazyInitializationException` from accessing relations outside the transaction

```java
Author author = service.findAuthor(id);
author.getBooks().size();                // boom
```

**What goes wrong:** the service's `@Transactional` ended when it returned. The `books` proxy can't load anything outside the session.

**Fix (pick one):**
- Eagerly load with `JOIN FETCH` or `@EntityGraph` in the query that fetched the author.
- Convert to a DTO inside the transaction.
- Access the collection inside the transaction (push the work down).

What **not** to do: leave `spring.jpa.open-in-view: true`. That hides the bug at the cost of holding a DB connection for the entire HTTP request.

---

## 19. Modifying a detached entity and expecting it to persist

```java
Book book = repository.findById(id).orElseThrow();
// outside any transaction here
book.setTitle("New title");
// no save() call
```

**What goes wrong:** dirty checking only works on managed entities inside a transaction. The detached `book` has no persistence context watching it. The title change vanishes.

**Fix:** either call `repository.save(book)` (which does a merge), or perform the modification inside a `@Transactional` service method where `book` is managed.

---

## 20. `equals` / `hashCode` based on auto-generated id

```java
@Override public boolean equals(Object o) { /* compare ids */ }
@Override public int hashCode() { return Objects.hashCode(id); }
```

**What goes wrong:** for a new entity, `id` is null. You add it to a `HashSet`. Hibernate assigns the id at flush. Now `hashCode()` returns a different value than when the entity was bucketed, and `set.contains(book)` returns false even though the book is in the set.

**Fix:** use a stable business key (ISBN, email, slug) for equality, or don't override `equals`/`hashCode` at all and rely on reference equality.

---

## 21. `findAll()` on a huge table

```java
List<Book> all = repository.findAll();    // 5 million rows
```

**What goes wrong:** OOM, request timeout, GC pressure.

**Fix:** add a `Pageable`. If you genuinely need to process every row, switch to a `Stream<Book>` repository method inside a `@Transactional` and `em.detach` periodically.

---

## 22. Forgetting `@Modifying` on a `@Query` UPDATE/DELETE

```java
@Query("UPDATE Book b SET b.pageCount = :p WHERE b.id = :id")
int updatePageCount(...);                 // no @Modifying
```

**What goes wrong:** Spring Data interprets it as a SELECT, fails with a query-parser error or returns 0 rows affected.

**Fix:** add `@Modifying` (and ensure the method is called inside a transaction). Optionally `@Modifying(clearAutomatically = true)` if managed entities of the same type are in scope.

---

## 23. `ddl-auto: update` in production

```yaml
spring.jpa.hibernate.ddl-auto: update
```

**What goes wrong:** Hibernate adds columns silently based on entity changes. Cannot rename columns. Cannot add a NOT NULL column to an existing populated table. Cannot drop. Two devs deploy at the same time and the schema becomes a race condition.

**Fix:** Flyway (topic 12) owns the schema. `ddl-auto: validate` ensures the entities match.

---

## 24. Editing an already-applied Flyway migration

```sql
-- Edit V1__create_books.sql in place
ALTER TABLE books ADD ...   -- new content
```

**What goes wrong:** Flyway recomputes the checksum and refuses to start: "Migration checksum mismatch."

**Fix:** never edit applied migrations. Write a new `V{n+1}__` migration with the correction.

---

## 25. Forgetting `@EnableJpaAuditing`

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Book {
    @CreatedDate private Instant createdAt;       // stays null
}
```

**What goes wrong:** auditing fields stay null. No error, no warning. The first sign is `created_at: null` rows in the database.

**Fix:** add `@EnableJpaAuditing` to the `@SpringBootApplication` class.

---

## 26. Pagination without a tie-breaker sort

```java
Page<Book> page = repository.findAll(PageRequest.of(0, 10, Sort.by("publishedYear")));
```

**What goes wrong:** if many books share `publishedYear`, the order within a year is undefined and may shift across page boundaries. The same row appears on two pages, or no pages.

**Fix:** always add a stable tie-breaker: `Sort.by(Sort.Order.asc("publishedYear"), Sort.Order.asc("id"))`.

---

## 27. Returning an entity from a controller

```java
@GetMapping("/api/books/{id}")
public Book get(@PathVariable Long id) { return repo.findById(id).orElseThrow(); }
```

**What goes wrong:** lazy fields trigger SELECTs during JSON serialization (or `LazyInitializationException` if OSIV is off). Internal fields leak to clients. Schema changes break the API contract. The DTO discipline from module 09 topic 9 is exactly the fix.

**Fix:** return a DTO. Use a record. Map inside the transaction.

---

## 28. `spring.jpa.open-in-view: true` (the default)

Spring Boot's default keeps the Hibernate session open for the duration of the HTTP request, including the JSON-rendering step.

**What goes wrong:** holds a database connection from the pool for as long as the entire request takes. Under load, the connection pool exhausts even though most connections are idle.

**Fix:** set `spring.jpa.open-in-view: false`. Then fix every `LazyInitializationException` it surfaces with one of the three correct patterns (eager fetch, DTO, push into service).
