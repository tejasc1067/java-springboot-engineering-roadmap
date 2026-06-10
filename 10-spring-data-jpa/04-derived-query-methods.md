# Derived query methods

Once you have a repository, the obvious next question is: how do I query things other than `findById`? Spring Data JPA's first answer is **derived query methods** — you declare a method on the interface, name it according to a strict convention, and Spring parses the name and generates the JPQL for you. No `@Query`, no SQL, no implementation. The method name *is* the query.

---

## The problem this solves

You want to fetch all books by a specific author. Without derived queries, you'd write:

```java
public List<Book> findBooksByAuthor(String author) {
    return em.createQuery("SELECT b FROM Book b WHERE b.author = :author", Book.class)
             .setParameter("author", author)
             .getResultList();
}
```

For every variation — "by author and year," "where title contains," "ordered by date" — another query string. Most of those strings are mechanical translations of the method name.

Spring Data JPA inverts the relationship: **write the method name, get the query for free.**

```java
List<Book> findByAuthor(String author);
```

Spring parses `findByAuthor`, sees that `author` is a field of `Book`, and generates `SELECT b FROM Book b WHERE b.author = ?1`. You never type SQL.

---

## How it works

At startup, for every repository interface, Spring Data JPA:

1. Looks at every method that is **not** inherited from `JpaRepository` / `CrudRepository`.
2. Parses the method name against a grammar: `<prefix>[<subject>]By<criteria>[OrderBy<sort>]`.
3. Maps the parts to entity fields. `Author` → `book.author`, `PublishedYear` → `book.publishedYear`, `Author_Name` → `book.author.name` (joins through a relationship — covered in topic 06).
4. Builds JPQL from those parts.
5. Wires the parsed query into the proxy that backs the interface.

Look at `BookRepository.java` in `CODE_EXAMPLES/04-derived-query-methods/derived-query-demo/`:

```java
public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByAuthor(String author);

    Optional<Book> findByIsbn(String isbn);

    List<Book> findByTitleContainingIgnoreCase(String fragment);

    List<Book> findByPublishedYearBetween(int low, int high);

    List<Book> findByAuthorAndPublishedYear(String author, int year);

    List<Book> findByAuthorOrderByPublishedYearDesc(String author);

    List<Book> findTop3ByOrderByPublishedYearDesc();

    long countByAuthor(String author);

    boolean existsByIsbn(String isbn);
}
```

No implementations. The tests pass. The console shows Hibernate emitting SQL like `select ... from books b where b.author = ?` and `... where lower(b.title) like ?` for each one.

---

## The grammar

| Prefix | Returns | Behaviour |
|---|---|---|
| `findBy` / `getBy` / `readBy` / `queryBy` / `searchBy` | `List<T>`, `Optional<T>`, single `T`, `Stream<T>`, `Page<T>`, `Slice<T>` | SELECT. Pick the return type based on cardinality. |
| `countBy` | `long` | SELECT COUNT(*). |
| `existsBy` | `boolean` | SELECT 1 LIMIT 1 (or COUNT-based, dialect-dependent). |
| `deleteBy` | `long` (rows deleted) or `void` | DELETE. Needs `@Transactional` at the call site. |

The `By` keyword separates the action prefix from the criteria. The criteria is a chain of **field names** joined by `And` / `Or`, each optionally followed by an **operator**.

### Operators on a field

| Suffix | SQL equivalent | Example |
|---|---|---|
| (none) | `=` | `findByAuthor(String)` → `WHERE author = ?` |
| `Containing` | `LIKE %?%` | `findByTitleContaining("Java")` |
| `StartingWith` | `LIKE ?%` | `findByTitleStartingWith("Clean")` |
| `EndingWith` | `LIKE %?` | `findByTitleEndingWith("Code")` |
| `Like` | `LIKE ?` | `findByTitleLike("Java%")` (you supply wildcards) |
| `IgnoreCase` | `LOWER(field) = LOWER(?)` | `findByAuthorIgnoreCase(...)` |
| `GreaterThan` / `LessThan` | `>` / `<` | `findByPublishedYearGreaterThan(2010)` |
| `GreaterThanEqual` / `LessThanEqual` | `>=` / `<=` | `findByPublishedYearGreaterThanEqual(2017)` |
| `Between` | `BETWEEN ? AND ?` | `findByPageCountBetween(400, 450)` |
| `In` | `IN (?, ?, ...)` | `findByPublishedYearIn(List<Integer>)` |
| `NotIn` | `NOT IN (...)` | `findByPublishedYearNotIn(...)` |
| `IsNull` / `IsNotNull` | `IS NULL` / `IS NOT NULL` | `findByPublishedYearIsNull()` |
| `True` / `False` | `= true` / `= false` | `findByActiveTrue()` |

You can combine multiple criteria with `And` / `Or`:

```java
List<Book> findByAuthorAndPublishedYear(String author, int year);
List<Book> findByAuthorOrTitle(String author, String title);
```

…and append ordering at the end with `OrderBy<Field><Asc|Desc>`:

```java
List<Book> findByAuthorOrderByPublishedYearDesc(String author);
```

And limit the result set with `First<N>` or `Top<N>`:

```java
List<Book> findFirst5ByOrderByPublishedYearDesc();
List<Book> findTop3ByOrderByPublishedYearDesc();
```

---

## Return types

Spring Data JPA accepts several return shapes; pick the one that matches the cardinality and the call-site need.

| Return type | When to use |
|---|---|
| `List<Book>` | Multiple rows expected; empty list if none. The default for collection queries. |
| `Optional<Book>` | At most one row expected, and "not found" is a normal outcome (e.g. `findByIsbn`). |
| `Book` (raw type) | Exactly one row expected. Returns `null` if none, throws `IncorrectResultSizeDataAccessException` if more than one. Avoid — `Optional<Book>` is clearer. |
| `Stream<Book>` | Many rows where you want to process row-by-row instead of loading them all into memory. Use inside a `@Transactional` and close the stream. |
| `Page<Book>` | Paginated (topic 09). |
| `Slice<Book>` | Paginated without a count query (topic 09). |
| `long` | For `countBy...` |
| `boolean` | For `existsBy...` |

`findByAuthor(String)` returning `Optional<Book>` for an author that has two books would throw. `List<Book>` is the safe default for "could be any number, including zero."

---

## The relationship-traversal trick

When `Book` has a `@ManyToOne Author author` (topic 06), you can traverse the relationship in the method name with an underscore:

```java
List<Book> findByAuthor_Name(String authorName);    // explicit
List<Book> findByAuthorName(String authorName);     // works too — Spring Data resolves the ambiguity
```

The underscore makes the intent unambiguous, especially if `Book` had both an `authorName` field *and* an `author.name` relationship — without the underscore, Spring would pick the direct field. Use the underscore in any cross-table traversal.

(Topic 06 covers the actual `@ManyToOne` setup. This is forward-reference for now.)

---

## When derived methods stop fitting

Derived methods are great until your method name reads like a sentence:

```java
List<Book> findByTitleContainingIgnoreCaseAndAuthor_NameStartingWithIgnoreCaseAndPublishedYearBetweenOrderByPublishedYearDescTitleAsc(
    String titleFragment, String authorPrefix, int low, int high);
```

That parses, but it's read-once-and-never-again code. Three signals that you've outgrown derived methods:

1. **The method name exceeds about 50 characters.** Readers can't tell what it does at a glance.
2. **You need an OR with parentheses around a group of conditions.** Derived methods are flat left-to-right; you can't express `(A OR B) AND C`.
3. **You need a JPQL feature derived methods don't expose** — `CASE WHEN`, subqueries, function calls, `GROUP BY`, `HAVING`.

At that point, switch to `@Query` (topic 05).

---

## How to read the SQL Spring generates

With `show-sql: true`, every derived-method call prints its SQL. For `findByTitleContainingIgnoreCase("clean")` you'll see something like:

```sql
select b1_0.id, b1_0.author, b1_0.isbn, b1_0.page_count, b1_0.published_year, b1_0.title
from books b1_0
where lower(b1_0.title) like ? escape '\'
```

…and the parameter binding will be `%clean%` (the `%` wildcards come from `Containing`, the `lower(...)` from `IgnoreCase`). Reading the generated SQL is how you sanity-check that the method name resolved the way you expected — especially when the method name is long.

If you see a SELECT you didn't expect (e.g. a JOIN on a relationship you didn't realize was being traversed), the method name is hiding a query that's more expensive than the name suggests. That's the moment to switch to `@Query`.

---

## Common pitfalls

- **Misspelling a field name.** `findByAuthr` won't fail at compile time — Spring Data fails at *application startup* with `PropertyReferenceException: No property 'authr' found for type 'Book'`. Catch it by running the application (or tests) before deploying.
- **Calling `deleteBy...` without a transaction.** Throws `TransactionRequiredException`. Either annotate the calling method with `@Transactional` or call from a service method that already is.
- **Returning `Book` (raw, non-Optional) and getting `IncorrectResultSizeDataAccessException`.** Happens when two rows match. Fix: return `Optional<Book>` for "at most one" or `List<Book>` for "any number."
- **Using `Containing` on a column with no index.** Generates `LIKE %x%`, which cannot use a B-tree index. On a million-row table this is slow. Fix: add a full-text index, or rethink the query.
- **Building giant `And`/`Or` chains.** They are read-once code and fragile to schema changes. Move to `@Query` once the name gets out of hand.
- **Assuming `findByAuthorIgnoreCase` is automatically indexed.** It isn't — `LOWER(author) = LOWER(?)` won't use a plain index on `author`. Either store a normalized column or add a functional index.

---

## Try this yourself

1. Add `List<Book> findByTitleContainingAndPublishedYearGreaterThanEqual(String fragment, int year)` to `BookRepository`. Write a test. Read the generated SQL in the console.
2. Add `List<Book> findByAuthrIgnoreCase(String author)` (note the typo). Run the application. Read the startup error — it tells you exactly which method and which property failed.
3. Add a `findFirstByAuthorOrderByPublishedYearDesc(String author)` returning `Optional<Book>`. Write a test that finds the most-recent book by Robert Martin.
4. Add `long deleteByAuthor(String author)` and call it from a `@Transactional` test method. Check the SQL — it's a single DELETE, not a SELECT-then-DELETE loop.

## Self-check

1. What is the parsing grammar for a derived method name? Name the four parts.
2. Why does `findByAuthr(...)` (typo) fail at *application startup* rather than at compile time?
3. Give two signals that a derived method has outgrown the convention and should become a `@Query`.
4. Why prefer `Optional<Book> findByIsbn(...)` over `Book findByIsbn(...)`?
