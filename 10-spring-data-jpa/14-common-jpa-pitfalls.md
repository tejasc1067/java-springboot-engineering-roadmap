# Common JPA pitfalls and performance

Every JPA-using team rediscovers the same handful of bugs. This topic catalogues the worst of them — what they look like, why they happen, and how to avoid them. Most have already appeared in passing in earlier topics; this one puts them in one place with runnable demonstrations.

---

## Pitfall 1: `LazyInitializationException`

Hibernate's most famous exception. You load an entity in a transaction, the transaction ends, then you access a lazy field on it.

The demo `lazyCollectionOutsideTransaction_throws`:

```java
// AuthorService.findAuthor uses @Transactional(readOnly = true)
// — the transaction closes when this method returns.
Author detached = authorService.findAuthor(authorId).orElseThrow();

assertThatThrownBy(() -> detached.getBooks().size())
        .isInstanceOf(LazyInitializationException.class);
```

By the time the caller touches `getBooks()`, the Hibernate session is gone. The lazy proxy has no way to load the rows. Boom.

**Why it happens:** lazy loading is *deferred SQL*. The proxy holds a session reference; when the session closes, the proxy can't run its query. Open Session In View (OSIV) used to paper this over by keeping the session open until the HTTP response finished — Spring Boot disables it by default (`spring.jpa.open-in-view: false` is recommended; the default is currently `true` and the startup log warns).

**Fixes:**

1. **Load it eagerly in the query.** Use `JOIN FETCH` (topic 07) or `@EntityGraph` (topic 07) for the specific call site.
2. **Convert to a DTO inside the transaction.** Map `Author` → `AuthorDto(name, books.size())` while the session is still open. The DTO escape never has lazy fields.
3. **Move the access into the transaction.** Push the work down into the service method that's still inside `@Transactional`.

What **not** to do: flip the relationship to EAGER. It "fixes" this caller but punishes every other call site (topic 07).

---

## Pitfall 2: modifying a detached entity has no effect

A managed entity inside a transaction gets dirty-checking for free. A detached one (post-transaction, post-`em.clear()`, or originating from outside the EntityManager) does not.

The demo `modifyingDetachedEntity_doesNotPersist`:

```java
Book existingBook = bookRepository.findById(id).orElseThrow();
// Outside a transaction now — book is detached.
existingBook.setTitle("MUTATED");

// No save call.
Book reloaded = bookRepository.findById(existingBook.getId()).orElseThrow();
assertThat(reloaded.getTitle()).isEqualTo("Effective Java");   // unchanged
```

The title change vanished. No exception. No log. Just silence.

**Why it happens:** dirty checking is a property of the persistence context — Hibernate compares each managed entity against a snapshot at flush time. A detached entity is not in any persistence context, so no snapshot, no compare.

**Fix:** call `repository.save(detached)` — `save` translates to a `merge` for entities with an id, which copies the field values into a managed entity and lets dirty checking do its job. The demo's `modifyingDetached_thenSaveMerges_persistsTheChange` shows it:

```java
existingBook.setTitle("CORRECTED");
bookRepository.save(existingBook);    // merge under the hood

Book reloaded = bookRepository.findById(existingBook.getId()).orElseThrow();
assertThat(reloaded.getTitle()).isEqualTo("CORRECTED");
```

**Mental model:** dirty checking only works *inside the transaction that loaded the entity*. Step outside, lose the magic.

---

## Pitfall 3: equals and hashCode on entities

This is a Java-flavored JPA bug that doesn't fail loudly but causes weird behavior with `HashSet`, `Map` keys, and `@OneToMany Set<Book>`.

The naïve mistake:

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Book b)) return false;
    return Objects.equals(id, b.id);          // equals based on id only
}
@Override
public int hashCode() { return Objects.hashCode(id); }
```

What goes wrong: for a *new* entity, `id` is null. You add the entity to a `HashSet<Book>`. Hibernate assigns the id on flush. Now `hashCode()` returns a different value than when it was added — and `set.contains(book)` returns false even though the book is in there.

**Two acceptable approaches:**

1. **Use a stable business key.** ISBN for a book, email for a user. Something that's known before persist, never changes, never collides.

   ```java
   @Override public boolean equals(Object o) {
       if (this == o) return true;
       if (!(o instanceof Book b)) return false;
       return Objects.equals(isbn, b.isbn);
   }
   @Override public int hashCode() { return Objects.hashCode(isbn); }
   ```

2. **Don't override equals/hashCode.** Use reference equality (the default). Java sets and maps work; cross-session comparisons of "same row" don't. Often fine.

What **not** to do: equals on id only, with no other safeguards. The hash-changing-after-insert bug is real and ugly.

---

## Pitfall 4: batch inserts without `hibernate.jdbc.batch_size`

By default, Hibernate sends each INSERT as its own JDBC statement, even if your `saveAll` has 10,000 entities. That's 10,000 round trips.

**Fix:** set `hibernate.jdbc.batch_size` in `application.yml`:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true
```

Hibernate now groups consecutive INSERTs into batches of 20. `order_inserts` reorders pending inserts so same-type inserts batch together (otherwise an interleaved INSERT for a different table breaks the batch). 50x throughput on bulk insert workloads.

**Gotcha:** `GenerationType.IDENTITY` *disables* batching, because Hibernate has to send each INSERT alone to get the auto-assigned id back. For real bulk loads, switch to `GenerationType.SEQUENCE` (or do the bulk insert with raw JDBC outside JPA).

---

## Pitfall 5: `findAll()` with too many rows

Easy mistake: `bookRepository.findAll()` on a table with a million rows. The result is a million `Book` objects in memory, plus their managed-entity overhead. The JVM heap fills, the request times out.

**Fix:** add a `Pageable` (topic 09), even when you "just want all of them." If you truly need to process every row, use `Stream<Book>`:

```java
@Transactional(readOnly = true)
public void processAllBooks() {
    try (Stream<Book> stream = repository.findAll().stream()) {  // wrong shape — see below
        stream.forEach(this::process);
    }
}
```

Spring Data JPA provides a `Stream<Book>` overload:

```java
@Query("SELECT b FROM Book b")
Stream<Book> streamAll();
```

…which Hibernate implements with a JDBC `ResultSet` cursor. Use inside a `@Transactional`, in a try-with-resources block, and `em.detach(book)` periodically if you want to keep the persistence context small.

---

## Pitfall 6: `toString` on entities

A common reflex: generate `toString` with the IDE, including every field. For entities with relationships, that includes the lazy collections:

```java
@Override public String toString() {
    return "Author{id=" + id + ", name='" + name + "', books=" + books + "}";
}
```

Calling `toString` outside a transaction → `LazyInitializationException`. Or, inside a transaction, calling `toString` triggers a SELECT for every relationship — including transitive ones if those entities also have full `toString`s.

**Fix:** exclude relationship fields from `toString`. Only include the primitives and small value types:

```java
@Override public String toString() {
    return "Author{id=" + id + ", name='" + name + "'}";
}
```

Lombok's `@ToString(exclude = "books")` is the convenient way if you use Lombok.

---

## Pitfall 7: leaving `spring.jpa.open-in-view: true`

OSIV (Open Session In View) keeps the Hibernate session open for the entire HTTP request, including the JSON serialization step. It papers over `LazyInitializationException`s — lazy access in the controller works because the session is still open.

The problem: OSIV holds a database connection from the connection pool for the *entire* request, including time spent rendering the response. Under load, you exhaust the pool with idle connections holding sessions open.

Spring Boot warns about it in the startup log:

```
spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning.
```

**Fix:** add `spring.jpa.open-in-view: false` to your `application.yml`. Now you get `LazyInitializationException` when you should — at the boundary where you forgot to fetch — and the fix is one of the three correct ones (eager fetch, DTO, push into service).

---

## Pitfall 8: `cascade = CascadeType.REMOVE` deleting more than expected

Cascade is convenience right up until it's a footgun. `CascadeType.REMOVE` on `User.orders` means "delete the user, also delete their orders." Then someone writes a "delete inactive users" admin job and the order history vanishes. Auditing is gone; downstream services that referenced order ids by foreign key are now broken.

**Fix:** be deliberate. Use `CascadeType.PERSIST` and `CascadeType.MERGE` liberally — they make `saveAll` work as expected on object graphs. Use `CascadeType.REMOVE` only when the child truly has no meaning without the parent (a `Comment` on a deleted `Post`, perhaps). Default to **no cascade** on remove.

---

## Common pitfalls (summary)

- **`LazyInitializationException`.** Eager-fetch with `JOIN FETCH` / `@EntityGraph`, or convert to DTOs inside the transaction.
- **Detached entity not persisting changes.** Either work inside a transaction, or `repository.save(detached)` to merge.
- **`equals`/`hashCode` on auto-generated id.** Use a business key, or don't override.
- **No batch size for bulk inserts.** Set `hibernate.jdbc.batch_size: 20+` and `order_inserts: true`. Switch from IDENTITY to SEQUENCE generation.
- **`findAll()` on huge tables.** Paginate or stream.
- **`toString` walking relationships.** Exclude relationships.
- **OSIV.** Disable it (`spring.jpa.open-in-view: false`) and fix the lazy-init reports it surfaces.
- **`CascadeType.REMOVE`.** Use only when the child has no independent meaning.

---

## A performance checklist

Before deploying a JPA-backed service to production, check:

- [ ] `spring.jpa.open-in-view: false`.
- [ ] `ddl-auto: validate` (and Flyway owns the schema).
- [ ] Lazy by default on `@ManyToOne` and `@OneToOne` (override the JPA default).
- [ ] `JOIN FETCH` / `@EntityGraph` on every endpoint that returns a list with relationships.
- [ ] `Pageable` on every list endpoint with a `findAll`.
- [ ] `hibernate.jdbc.batch_size` set for any code path that bulk-inserts.
- [ ] No `@OneToMany(fetch = EAGER)`.
- [ ] `show-sql` is off in production (use a structured SQL logger instead, e.g. `logging.level.org.hibernate.SQL=DEBUG` only when investigating).
- [ ] An index on every column you `findBy*` against.
- [ ] A monitoring dashboard for query count per request (Datadog, New Relic, Micrometer + Prometheus).

---

## Try this yourself

1. Run `lazyCollectionOutsideTransaction_throws`. Then change `AuthorService.findAuthor` to eagerly load books (`@EntityGraph(attributePaths = "books")` on a repository method, or `JOIN FETCH`). The test's lazy-init exception goes away — but the test should still pass because the lazy access now succeeds. Adjust the assertion or write a new test.
2. Run `modifyingDetachedEntity_doesNotPersist`. Now wrap the modification in a `@Transactional` test method (without saving). It should persist via dirty checking. Compare the two paths.
3. Add `hibernate.jdbc.batch_size: 5` and `order_inserts: true`. Write a test that inserts 20 books in one `saveAll` and look at the SQL log — you should see 4 grouped batches. Remove the property; the same test shows 20 individual INSERTs.
4. Disable OSIV (`spring.jpa.open-in-view: false`) in `application.yml`. Re-run the lazy-init test — the error message changes slightly but the behavior is the same.

## Self-check

1. What is `LazyInitializationException` and what's the cleanest fix for it in a typical service method?
2. Why does setting a field on an entity outside a transaction silently do nothing, and what do you call instead?
3. What's wrong with auto-generated `equals`/`hashCode` based on the database id?
4. Why does `GenerationType.IDENTITY` disable Hibernate's JDBC batching, and what's the workaround for bulk inserts?
