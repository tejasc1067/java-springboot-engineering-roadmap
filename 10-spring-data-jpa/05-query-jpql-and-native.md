# `@Query` — JPQL and native SQL

Derived query methods cover the easy 80% of querying. The other 20% — grouped OR conditions, projections into DTOs, subqueries, function calls, dialect-specific SQL — needs `@Query`. This topic is about when to reach for it, the two query languages (JPQL and native SQL), parameter binding, projections, and the `@Modifying` flag that updates and deletes require.

---

## The problem this solves

You hit one of the cases topic 04 ended on: the method name doesn't fit.

- **Grouped conditions:** "books published between two years AND by either author A or author B." Derived methods are left-to-right; you cannot put parentheses around the OR group.
- **A projection:** "title, author, year — that's it, don't load the full row." Derived methods return entities; projecting to a DTO needs more.
- **A SQL feature JPQL doesn't have:** window functions, common table expressions, vendor functions. Drop to native SQL.
- **Bulk updates or deletes:** changing 10,000 rows by predicate. Loading 10,000 entities into the persistence context just to change one field would be wasteful; a single UPDATE statement is what you want.

`@Query` is the lever for all four.

---

## How it works

`@Query` accepts a string. By default, the string is **JPQL** (Java Persistence Query Language) — looks like SQL, but operates on **entity classes and field names** rather than tables and columns:

```java
@Query("SELECT b FROM Book b WHERE b.author = :author")
List<Book> findByAuthorNamed(@Param("author") String author);
```

`Book` is the entity class, `b.author` is the Java field. Hibernate at startup parses the JPQL, validates the field names against the entity model (typos fail at startup, not at query time), and translates the query into SQL for whichever dialect your `DataSource` points at.

Set `nativeQuery = true` and the string becomes the SQL Hibernate sends directly to the database:

```java
@Query(value = "SELECT * FROM books WHERE LENGTH(title) > ?1 ORDER BY LENGTH(title) DESC",
       nativeQuery = true)
List<Book> findLongTitlesNative(int minLength);
```

Now `books` is the actual table name, and the SQL runs as-is. You give up portability (a query that works on H2 may not work on Postgres) and you give up JPQL's validation (typos fail at runtime).

### JPQL vs native — a side-by-side

```java
// JPQL: entity-flavored. Hibernate translates to dialect SQL.
@Query("SELECT b FROM Book b WHERE b.publishedYear > :year")
List<Book> recentJpql(@Param("year") int year);

// Native: SQL exactly as you write it.
@Query(value = "SELECT * FROM books WHERE published_year > :year",
       nativeQuery = true)
List<Book> recentNative(@Param("year") int year);
```

Same result. Note the differences:

| JPQL | Native |
|---|---|
| `FROM Book b` — class name | `FROM books` — table name |
| `b.publishedYear` — field name (camelCase) | `published_year` — column name (snake_case) |
| Validated at startup | Validated at runtime |
| Database-agnostic | Database-specific |

---

## Parameter binding — positional and named

Both styles work; pick one and be consistent.

**Positional (`?1`, `?2`, …):**

```java
@Query("SELECT b FROM Book b WHERE b.author = ?1")
List<Book> findByAuthorPositional(String author);
```

The number matches the argument position. No `@Param` annotation needed.

**Named (`:author`):**

```java
@Query("SELECT b FROM Book b WHERE b.author = :author")
List<Book> findByAuthorNamed(@Param("author") String author);
```

Named parameters are clearer in long queries — `?1`, `?2`, `?3`, `?4` becomes unreadable fast. Use named for anything with more than two parameters.

**Reusing a parameter:**

```java
@Query("""
        SELECT b FROM Book b
        WHERE b.title LIKE CONCAT('%', :term, '%')
           OR b.author LIKE CONCAT('%', :term, '%')
        """)
List<Book> searchByTerm(@Param("term") String term);
```

A single `:term` parameter referenced twice. With positional parameters you'd have to pass the same value twice.

### Never concatenate strings into a query

If you ever find yourself writing `"SELECT b FROM Book b WHERE b.author = '" + name + "'"`, stop. That's SQL injection — the same problem module 04 covered. Use `?1` or `:name` and let the JPA layer bind the value safely.

---

## Projections — returning DTOs, not entities

Sometimes you don't want a full `Book`. You want title + author for a summary view. Two ways to project.

### Constructor expression (JPQL `SELECT new ...`)

```java
@Query("SELECT new com.example.BookSummary(b.title, b.author, b.publishedYear) FROM Book b WHERE b.author = :author")
List<BookSummary> findSummariesByAuthor(@Param("author") String author);
```

With `BookSummary` declared as:

```java
public record BookSummary(String title, String author, Integer publishedYear) {}
```

The JPQL `new com.example.BookSummary(...)` constructs each row as a `BookSummary`. The SQL selects only those three columns — no overhead from loading the whole row.

This works with records (Java 14+) and with regular classes that have a matching constructor. The fully-qualified class name is required in JPQL.

### Interface-based projection

```java
public interface TitleAndAuthor {
    String getTitle();
    String getAuthor();
}
```

```java
@Query("SELECT b.title AS title, b.author AS author FROM Book b WHERE b.publishedYear >= :year")
List<TitleAndAuthor> findTitleAndAuthorByYear(@Param("year") int year);
```

Spring Data builds a proxy that implements the interface at runtime, sourcing each getter from the `AS title` / `AS author` aliases in the query. No DTO class to write.

Choose between the two:

- **Record/class projection** when you want a real object (immutable, equals/hashCode for free, debuggable in the IDE).
- **Interface projection** when you want minimum boilerplate and don't need a real object.

Both run the same trimmed-down SQL.

---

## `@Modifying` — UPDATE and DELETE need a flag

`@Query` defaults to SELECT mode. An UPDATE or DELETE statement requires `@Modifying`:

```java
@Modifying
@Query("UPDATE Book b SET b.pageCount = :newCount WHERE b.id = :id")
int updatePageCount(@Param("id") Long id, @Param("newCount") int newCount);

@Modifying
@Query("DELETE FROM Book b WHERE b.publishedYear < :year")
int deletePublishedBefore(@Param("year") int year);
```

The return type is `int` (or `void`) — the count of affected rows.

**Two things to know:**

1. **A `@Modifying` query needs a transaction.** Either annotate the calling service method with `@Transactional`, or call from a context that already is.

2. **The persistence context does not get synced.** If you loaded a `Book` and then bulk-update its row via `@Modifying`, the in-memory `Book` is stale — its `pageCount` field still shows the old value. Hibernate's first-level cache does not know the row changed. Add `clearAutomatically = true` (Spring Data) to evict the cache after the update, or `flushAutomatically = true` to flush pending changes before:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE Book b SET b.pageCount = :newCount WHERE b.id = :id")
int updatePageCount(@Param("id") Long id, @Param("newCount") int newCount);
```

The cost is that all managed entities get evicted from the context. For a service method that does one bulk update and returns, that's fine. For a method that loads, updates in bulk, then continues to use other entities — be deliberate.

---

## When to use JPQL, when to use native

| Use JPQL when | Use native SQL when |
|---|---|
| The query is portable across databases. | You need a vendor function (`JSON_EXTRACT`, `ARRAY_AGG`, `RANK() OVER (...)`). |
| You want startup-time validation of field names. | The JPQL grammar doesn't cover what you need (window functions, CTEs, `MERGE`). |
| The query returns entities or DTOs you've declared. | You're optimizing a specific query and need exact control over the SQL plan. |
| You're learning JPA (default). | You're already a SQL expert and JPQL feels like a re-translation tax. |

A common pattern in real codebases: most queries are derived methods, the rest are JPQL, and a small handful of performance-critical or vendor-specific queries are native. There is no shame in mixing all three in the same repository.

---

## Common pitfalls

- **Typo in JPQL field name.** Fails at *startup* with `org.hibernate.query.SemanticException` — that's a feature, not a bug. Always start the app (or run tests) after changing a query.
- **Typo in native SQL column name.** Fails at *runtime* the first time the query runs. Integration tests catch it; reading-the-SQL doesn't, because the typo just looks like SQL.
- **Forgetting `@Modifying` on an UPDATE/DELETE query.** Spring will try to interpret it as a SELECT and fail with a confusing parser error. Fix: add `@Modifying`.
- **Forgetting the transaction on a `@Modifying` method.** Throws `TransactionRequiredException`. Either annotate the calling service method or annotate the repository method itself with `@Transactional`.
- **Calling `@Modifying` updates and not refreshing managed entities.** The DB row changed, the in-memory entity didn't. Either add `clearAutomatically = true` or be deliberate about not touching the affected entity afterwards.
- **Constructing JPQL by string concatenation with user input.** SQL injection. Always use `?1` or `:name`.
- **Constructor expression in JPQL with mismatched argument types.** `new BookSummary(b.title, b.publishedYear)` when `BookSummary` expects `(String, String, Integer)` fails at startup with a constructor-not-found error. Match the JPQL select list to the constructor signature exactly.

---

## Try this yourself

1. Convert one of topic 04's derived methods (`findByTitleContainingIgnoreCase`) into a JPQL `@Query`. Compare the generated SQL.
2. Add a new query `@Query("SELECT b FROM Book b WHERE b.publishedYear IN :years")` and call it with a `List<Integer>`. Read the SQL — it should be `... IN (?, ?)` with the right number of bind variables.
3. Write an interface projection `BookTitleOnly` with a single `getTitle()` method, and a `@Query` that selects only the title. Look at the SQL: it should be `select b.title from books b ...` — no other columns fetched.
4. Add a `@Modifying` UPDATE without `@Transactional` on the test method. Read the exception message — it tells you exactly what's missing.

## Self-check

1. Give two cases where a derived method won't work and you need `@Query`.
2. What's the operational difference between JPQL and native SQL? Name one tradeoff each way.
3. Why does an UPDATE in `@Query` need both `@Modifying` and a transaction?
4. After a bulk `@Modifying` update, why might a previously-loaded entity show stale data, and what flag fixes it?
