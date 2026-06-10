# Fetch types and the N+1 problem

If JPA has one bug that lives in every codebase, it's the N+1 query. A list of 100 books triggers 101 database round trips. It looks fine in tests with two rows; in production with 100,000 rows, the page that took 50ms in dev takes 30 seconds. This topic is about *why* it happens, *how to see it*, and the two fixes that solve it for 95% of cases.

---

## The problem this solves

Module 06 set up `Author` ↔ `Book` with a `@OneToMany` from Author and a `@ManyToOne` from Book. By default, those relationships are **lazy**: when you load an `Author`, Hibernate does **not** load the books — it puts a proxy in `author.books`, and the SELECT for books runs only when you first touch the collection (`.size()`, `.iterator()`, `.get(0)`, …).

Lazy is the right default for one author, because most of the time you don't need their books. The problem starts when you have a *list* of authors:

```java
List<Author> authors = authorRepository.findAll();          // SELECT * FROM authors
for (Author a : authors) {
    System.out.println(a.getName() + ": " + a.getBooks().size());  // <-- one SELECT per author
}
```

That's **1 + N** queries. One for the authors, N for the per-author book lists. The "+1" is the original list query; the N is the lazy load per element.

In the demo `NPlusOneTest.naiveQueryTriggersNPlusOne`, the assertion is concrete:

```java
List<Author> authors = repository.findAllNaive();
long queriesAfterListLoad = stats.getPrepareStatementCount();    // 1

authors.forEach(a -> a.getBooks().size());                       // triggers lazy loads

long totalQueries = stats.getPrepareStatementCount();            // 4
long lazyLoads = totalQueries - queriesAfterListLoad;            // 3

assertThat(queriesAfterListLoad).isEqualTo(1);
assertThat(lazyLoads).isEqualTo(3);                              // <-- the N+1
```

Three authors → three extra queries. Replace `3` with `10,000` and you have a production incident.

---

## How it works

`@OneToMany` and `@ManyToMany` default to `FetchType.LAZY`. `@ManyToOne` and `@OneToOne` default to `FetchType.EAGER` (despite that being almost always the wrong choice — see "Common pitfalls" below).

| Annotation | Default fetch | What "lazy" means here |
|---|---|---|
| `@OneToMany` | LAZY | Collection is a proxy; SELECT runs at first access. |
| `@ManyToMany` | LAZY | Same as above. |
| `@ManyToOne` | EAGER | Loaded with the parent in the same query (JOIN). |
| `@OneToOne` | EAGER | Same as above. |

The mental model: **lazy = "I'll fetch this when you ask." Eager = "I'll fetch this every time."** Neither is good or bad in isolation; the choice depends on whether the caller needs the related data.

The naive solution most beginners reach for is "just make it EAGER":

```java
@OneToMany(mappedBy = "author", fetch = FetchType.EAGER)
private List<Book> books;
```

Now every `authorRepository.findAll()` always loads the books. That fixes the N+1 for callers who do want the books, but creates a new problem for callers who don't — they pay for the JOIN every time. And if `Book` has its own EAGER associations (publisher, reviews, tags), the cost cascades. **Don't fix N+1 by making everything EAGER.** Fix it per-query.

---

## Fix 1: `JOIN FETCH` in JPQL

`JOIN FETCH` tells Hibernate "while you're SELECTing the authors, also SELECT and attach their books — in the *same* SQL statement."

```java
@Query("SELECT DISTINCT a FROM Author a JOIN FETCH a.books")
List<Author> findAllWithBooksJoinFetch();
```

Generated SQL:

```sql
select distinct a1_0.id, a1_0.name, b1_0.id, b1_0.author_id, b1_0.title
from authors a1_0
join books b1_0 on a1_0.id = b1_0.author_id
```

One query. The author rows are duplicated for each book (one row per author-book pair), but Hibernate deduplicates them into distinct `Author` objects with their `books` list already populated.

Why `DISTINCT`? Because the JOIN multiplies rows — an author with 3 books produces 3 rows. Without `DISTINCT`, you'd get the same `Author` reference 3 times in the result list. Hibernate optimizes `SELECT DISTINCT` in JPQL — it does the deduplication in memory, not via SQL `DISTINCT`, so you don't pay the SQL sort cost.

In the demo:

```java
assertThat(totalQueries).isEqualTo(1);
assertThat(queriesAfterListLoad).isEqualTo(1);
```

One query. Books are populated. No lazy loads.

### The "MultipleBagFetchException" gotcha

`JOIN FETCH` works fine for one collection. Try to fetch *two* `List` collections at once and you get:

```
MultipleBagFetchException: cannot simultaneously fetch multiple bags
```

This is JPA's term for "I can't reconstruct two unordered collections from a single result set without duplication." The fix is one of:

- Use `Set` instead of `List` for one or both collections.
- Use two queries (load the authors with books in one, with reviews in another, and let Hibernate stitch them via the persistence context).
- Use `@EntityGraph` (next section) for one and `JOIN FETCH` for the other.

---

## Fix 2: `@EntityGraph`

`@EntityGraph` is the Spring Data way to say "load these associations eagerly *for this method only*." Cleaner than embedding `JOIN FETCH` in the JPQL, especially for derived methods (which don't take JPQL at all).

```java
@EntityGraph(attributePaths = "books")
@Query("SELECT a FROM Author a")
List<Author> findAllWithBooksEntityGraph();
```

Or on a derived method:

```java
@EntityGraph(attributePaths = "books")
List<Author> findByName(String name);
```

Hibernate translates the entity graph into a `LEFT OUTER JOIN` and pulls the books in the same query.

Use `@EntityGraph` when:

- The base query is a derived method (no JPQL to put `JOIN FETCH` into).
- You want the *option* of fetching, but the entity defaults to LAZY (preserves the lazy default for other callers).
- You need to fetch multiple paths (use `attributePaths = {"books", "publisher"}`).

---

## When to leave it lazy

Not every list iteration triggers an N+1. If you load 100 authors and never touch `author.getBooks()`, lazy is fine — those collections stay as unloaded proxies and the queries never run.

Lazy is right when:

- The relationship isn't used by the current caller.
- You're returning a DTO that doesn't include the relationship.
- You'll later need to filter or project the relationship anyway (and would do a separate query).

Eager is right when:

- The relationship is *always* used by every caller of this method.
- It's a `@ManyToOne` to a small lookup table (e.g. `Book.publisher` where there are 5 publishers).
- The cost of one extra JOIN is less than the cost of accidentally lazy-loading on a hot path.

When unsure, **stay lazy and add `@EntityGraph` per-method**. That's the configuration that's hardest to misuse.

---

## How to spot N+1 in real systems

The demo uses Hibernate's `Statistics` API:

```java
SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
sf.getStatistics().setStatisticsEnabled(true);
sf.getStatistics().clear();

// ... run your code ...

long queries = sf.getStatistics().getPrepareStatementCount();
```

In production you'd use:

- **`show-sql: true` + log review.** Crude but works. Look at the log; if you see the same SELECT shape repeated with different parameter values, that's N+1.
- **Hibernate's `org.hibernate.SQL` logger at `DEBUG`.** Same info as `show-sql`, but routed through your logging framework.
- **A monitoring tool** like Datadog, New Relic, or Spring Boot Actuator + Micrometer. Per-endpoint query counts make the spike obvious.
- **A dev-time tool** like [P6Spy](https://github.com/p6spy/p6spy) or [Hibernate Statistics on actuator endpoints](https://docs.spring.io/spring-boot/docs/current/actuator-api/htmlsingle/). They surface "you ran 432 queries during one request" in a way no log review can.

The cheap heuristic: **if your endpoint runs more queries than you have rows in the response, you have N+1 somewhere.**

---

## Common pitfalls

- **`@ManyToOne` defaults to EAGER.** Almost always wrong. Every `book.author` traversal pulls the author. Worse, if author has its own EAGER associations, the cascade is multiplicative. Fix: write `@ManyToOne(fetch = FetchType.LAZY)` explicitly on every `@ManyToOne` you add.
- **Fixing N+1 by switching to EAGER on the entity.** It "works" but pollutes every caller. Use `JOIN FETCH` or `@EntityGraph` *per method*.
- **`MultipleBagFetchException` when JOIN-fetching two `List` collections at once.** Change one to `Set`, or split into two queries.
- **Forgetting `DISTINCT` in JPQL `JOIN FETCH`.** You get duplicate `Author` references in the result. Fix: `SELECT DISTINCT a FROM Author a JOIN FETCH a.books`.
- **Touching a lazy collection outside the transaction.** You get `LazyInitializationException`. Either eager-load with `JOIN FETCH`/`@EntityGraph`, or move the access into the transaction, or convert to a DTO inside the transaction so the entity escape never happens. Topic 14 covers this in more depth.
- **Assuming pagination + `JOIN FETCH` is safe.** It isn't. `findAll(Pageable)` with `JOIN FETCH` on a collection makes Hibernate do the pagination in memory (because the JOIN multiplies rows and the SQL `LIMIT` would cut off mid-author). On a large table this loads everything into memory. Use `@EntityGraph` with paging, or do the pagination in a separate query and load the collection in a second pass.

---

## Try this yourself

1. Remove the `entityManager.clear()` from the test's `@BeforeEach`. Re-run — `naiveQueryTriggersNPlusOne` should now fail or behave differently, because the books are still cached in the persistence context. Add the `clear()` back and reflect: in production, every request gets a fresh persistence context, so the N+1 *will* happen — the test must simulate that.
2. Change `Author.books` to `fetch = FetchType.EAGER`. Re-run the naive test — it should now show 1 query instead of 1+N. But also note: every test that loads an `Author` for any reason now also loads its books. Run the whole module's tests and see what slows down.
3. Add a second collection on Author, e.g. `@OneToMany List<Review> reviews`. Try `SELECT DISTINCT a FROM Author a JOIN FETCH a.books JOIN FETCH a.reviews`. Read the `MultipleBagFetchException`. Now change one collection to `Set` and re-run.
4. Use `@EntityGraph(attributePaths = "books")` on a derived method like `findByName(String)`. Trace the generated SQL — it's `LEFT OUTER JOIN` on books in the same SELECT.

## Self-check

1. What is the N+1 problem? Where does the "+1" come from, and what is "N"?
2. Why is fixing N+1 by switching the entity to `FetchType.EAGER` usually wrong?
3. What does `DISTINCT` do in a JPQL `SELECT DISTINCT a FROM Author a JOIN FETCH a.books` query?
4. Give one tool or technique for detecting N+1 in a real running application.
