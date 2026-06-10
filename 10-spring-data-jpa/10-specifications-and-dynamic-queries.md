# Specifications and dynamic queries

Derived methods cover named filters. `@Query` covers complex but fixed queries. What covers a search screen where every filter is *optional* — author, title fragment, year range, status, tag — and the user might pick any subset? Writing a `findByXOrFindByY...` cross-product is hopeless. The Spring Data answer is `Specification<T>` — a composable, type-safe wrapper around JPA's Criteria API.

---

## The problem this solves

Imagine an admin search page with four filters: author, title fragment, min year, max year. Any combination may be set. The user might pick:

- Just `author = "Bloch"`.
- `author = "Bloch"` and `minYear = 2010`.
- Just `titleFragment = "java"`.
- All four.
- None of them (return everything).

That's 16 combinations. Writing 16 derived methods, or one `@Query` with hand-rolled null checks, both get ugly fast. The query needs to *grow* based on which filters are present.

The Criteria API (part of JPA) was made for this. `Specification<T>` is Spring Data's thin wrapper that makes it usable.

---

## How it works

Two pieces:

1. **`JpaSpecificationExecutor<T>`** — an interface you mix into your repository. It adds `findAll(Specification)`, `findAll(Specification, Pageable)`, `count(Specification)`, etc.
2. **`Specification<T>`** — a function from `(Root, CriteriaQuery, CriteriaBuilder)` to a `Predicate`. It represents one WHERE-clause fragment.

The repository:

```java
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {
}
```

That's it — by extending `JpaSpecificationExecutor<Book>`, the repository now accepts specifications.

A single specification:

```java
public final class BookSpecs {

    public static Specification<Book> hasAuthor(String author) {
        return (root, query, cb) -> cb.equal(root.get("author"), author);
    }

    public static Specification<Book> titleContains(String fragment) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + fragment.toLowerCase() + "%");
    }

    public static Specification<Book> publishedYearAtLeast(int year) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("publishedYear"), year);
    }
}
```

Each method returns a `Specification<Book>` that knows how to add one predicate to a WHERE clause.

`Specification<T>` is a functional interface: `(Root<T>, CriteriaQuery<?>, CriteriaBuilder) -> Predicate`. The three arguments give you everything Criteria needs:

- **`Root<T>`** — represents the FROM table. `root.get("author")` references the column.
- **`CriteriaQuery<?>`** — the full query being built. Rarely used in simple specs; needed for subqueries.
- **`CriteriaBuilder`** — the factory for predicates. `cb.equal`, `cb.like`, `cb.greaterThan`, `cb.and`, `cb.or`, ...

---

## Composition with `and` and `or`

A `Specification<T>` has default methods to combine with others:

```java
Specification<Book> spec = BookSpecs.hasAuthor("Robert Martin")
        .and(BookSpecs.publishedYearAtLeast(2010));

List<Book> result = repository.findAll(spec);
```

Generated SQL:

```sql
select b1_0.id, b1_0.author, b1_0.published_year, b1_0.title
from books b1_0
where b1_0.author = ?
  and b1_0.published_year >= ?
```

You can chain `.and(...)` arbitrarily and mix in `.or(...)`. `Specification.unrestricted()` (Spring Data 3.4+; previously `Specification.where(null)`) returns an "always true" spec that's useful as a starting point.

### Conditional composition — the actual win

The service in the demo composes the spec based on which criteria fields are non-null:

```java
private Specification<Book> buildSpec(BookSearchCriteria c) {
    Specification<Book> spec = Specification.unrestricted();

    if (c.author() != null)        spec = spec.and(BookSpecs.hasAuthor(c.author()));
    if (c.titleFragment() != null) spec = spec.and(BookSpecs.titleContains(c.titleFragment()));
    if (c.minYear() != null)       spec = spec.and(BookSpecs.publishedYearAtLeast(c.minYear()));
    if (c.maxYear() != null)       spec = spec.and(BookSpecs.publishedYearAtMost(c.maxYear()));

    return spec;
}
```

This is the kind of code derived methods cannot express. The criteria object decides at *runtime* which clauses appear in the WHERE — and the SQL only includes what was requested. Empty criteria → no WHERE clause. Author only → `WHERE author = ?`. Author + year range → `WHERE author = ? AND year >= ? AND year <= ?`.

The demo's `withPagination` test calls this with three of four filters set, and it just works:

```java
Page<Book> page = service.search(
        new BookSearchCriteria(null, null, 2000, null),
        PageRequest.of(0, 3, Sort.by("publishedYear").ascending()));
```

---

## With pagination

`JpaSpecificationExecutor` has an overload that takes a `Pageable`:

```java
Page<Book> findAll(Specification<Book> spec, Pageable pageable);
```

Same semantics as topic 09 — content plus metadata, two SQL queries (page + count). The count query reuses the specification's WHERE, so the total reflects the same filter set.

---

## When to use specifications

| Use specifications when | Use `@Query` when |
|---|---|
| Filters are *optional* and decided at runtime. | The query is fixed. |
| You're building a search/admin page. | You need a complex query that Criteria doesn't express well (CTEs, vendor functions). |
| The same filter atoms get reused across multiple queries (you can keep `hasAuthor`, `titleContains`, etc. as a library). | The query is one-off. |
| You need to add filters incrementally as the application grows. | You want SQL-level readability. |

| Use derived methods when |
|---|
| The filter set is fixed and small (1–2 fields). |
| Beginners need to read the code without learning Criteria. |

The cost of specifications is verbosity at the call site and a slight learning curve for the Criteria API. The benefit is dynamic composition without hand-stitched JPQL.

---

## A note on `Specification.where(...)`

You'll see older code (and older tutorials) use:

```java
Specification<Book> spec = Specification.where(BookSpecs.hasAuthor("Bloch"));
```

`Specification.where(...)` returns its argument unchanged (or `null` if you pass `null`). It exists mostly for readability — `where(...).and(...)` reads better than starting from an unrestricted spec. In Spring Data 3.4+, `Specification.unrestricted()` is preferred as the explicit "no filter yet" sentinel.

Both still work; pick one and be consistent.

---

## Common pitfalls

- **Using a String column name that doesn't exist.** `root.get("publishedYr")` (typo) fails at *runtime*. No compile check. Fix: use the JPA Metamodel (auto-generated `Book_` class with `Book_.publishedYear` constants) if you can stomach the build setup; otherwise lean on tests.
- **N+1 if the spec joins a collection.** Specifications can join (`root.join("books")`), and that brings the same fetching semantics as topic 07. If you need the joined collection populated, fetch it explicitly with `root.fetch("books")` and remember the `MultipleBagFetchException` rule.
- **Specifications with stateful side effects.** A specification is a function. Side effects on `Root`/`CriteriaBuilder` (like calling `query.distinct(true)` only sometimes) can interact badly with the count query Spring Data issues for `Page<T>`. Keep specs pure.
- **Spec composition with `or`.** Mixing `and` and `or` without parentheses gets confusing. `a.and(b).or(c)` is `(a AND b) OR c`, *not* `a AND (b OR c)`. Group with parentheses by composing sub-specifications: `a.and(b.or(c))`.
- **Building specs in a controller.** Same DTO discipline as elsewhere: accept a typed criteria object at the controller, build the spec in a service. Keeps test seams clean.
- **Trying to use a spec where derived methods would do.** If your filter set is genuinely fixed, the derived method is shorter and easier to read. Reach for specs only when the dynamism is real.

---

## Try this yourself

1. Add a `Specification<Book> publishedBetween(int low, int high)` helper that returns `cb.between(root.get("publishedYear"), low, high)`. Use it in the demo and observe the SQL.
2. Build a spec that searches `(titleContains("clean") AND author = "Robert Martin") OR author = "Andy Hunt"`. Pay attention to the parenthesization with `or`/`and`.
3. Add `countMatching(criteria)` to the service that returns `long`. It should reuse the same `buildSpec(...)` and call `repository.count(spec)`.
4. Replace one of the criteria's `Integer minYear` / `Integer maxYear` pair with an `Optional<Integer>`. Update the builder. Notice how spec composition tolerates either shape.

## Self-check

1. What does `JpaSpecificationExecutor<T>` add to a repository, and what does `Specification<T>` represent?
2. Give one situation where derived methods can't solve the problem and `Specification<T>` can.
3. Show the difference in the WHERE clause between `a.and(b).or(c)` and `a.and(b.or(c))`.
4. Why is composing the spec inside a service (rather than in a controller) the cleaner pattern?
