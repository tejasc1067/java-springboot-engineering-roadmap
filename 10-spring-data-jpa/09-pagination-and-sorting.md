# Pagination and sorting

Module 09 topic 12 had you write `?page=0&size=10&sort=name,asc` by hand against an in-memory `List` — splitting the list into pages, applying the sort, building a `PagedResponse<T>` shape. This topic replaces all of that with three Spring Data types: `Pageable`, `Page<T>`, and `Sort`. The mechanics are the same; the boilerplate is gone.

---

## The problem this solves

Returning the whole table is wrong for two reasons. The first you already know: clients don't want 10,000 books at once. The second is operational: building a 10,000-row JSON response holds memory, holds a database connection, and times out under load.

Pagination splits the response into pages. Each request asks for one page of N items at offset M. The repository runs a `SELECT ... LIMIT N OFFSET M` query and (usually) a separate `SELECT COUNT(*)` so the client knows how many pages there are total. Hand-rolling this is tedious; Spring Data does it for you.

---

## The three core types

| Type | What it is | Use it for |
|---|---|---|
| `Pageable` | A request: which page, how big, sorted how. | Pass into a repository method to say "I want page X of size Y." |
| `Page<T>` | A response: the content for one page, plus `totalElements`, `totalPages`, `hasNext`, etc. | Receive from a repository method when you need metadata. |
| `Slice<T>` | A lighter response: content + `hasNext` only. No total count. | Receive when you only need "is there more?", not the exact total. Cheaper because no `COUNT(*)`. |
| `Sort` | An ordering: which fields, ascending or descending. | Pass on its own (`findAll(Sort)`) or embed in a `Pageable`. |

---

## How it works

The repository:

```java
public interface BookRepository extends JpaRepository<Book, Long> {
    Page<Book>  findByAuthor(String author, Pageable pageable);
    Slice<Book> findSliceByAuthor(String author, Pageable pageable);
}
```

Adding a `Pageable` parameter to a derived method is the entire wiring. Spring sees it, includes `LIMIT`/`OFFSET` in the generated SQL, and (for `Page<T>`) issues a second `SELECT COUNT(*)` query.

A caller:

```java
Pageable request = PageRequest.of(0, 2, Sort.by("publishedYear").ascending());
Page<Book> page = repository.findByAuthor("Robert Martin", request);

page.getContent();       // List<Book>, max size 2
page.getTotalElements(); // long: total rows matching the WHERE (across all pages)
page.getTotalPages();    // int: ceil(totalElements / pageSize)
page.getNumber();        // int: current page index (0-based)
page.isFirst();          // boolean
page.isLast();           // boolean
page.hasNext();          // boolean
page.hasPrevious();      // boolean
```

The SQL generated:

```sql
-- Page 0 of size 2, sorted by published_year asc
select b1_0.id, b1_0.author, b1_0.published_year, b1_0.title
from books b1_0
where b1_0.author = ?
order by b1_0.published_year asc
offset ? rows fetch first ? rows only

-- And a second query for the count:
select count(b1_0.id) from books b1_0 where b1_0.author = ?
```

Two queries. One for the page content, one for the total. That's what `Page<T>` costs.

---

## `Page<T>` vs `Slice<T>` — the count query is not free

For tables with millions of rows, the `COUNT(*)` query is often the slowest part of the response. If you don't *need* the total — for example, an infinite-scroll UI that only cares whether there's a next page — switching to `Slice<T>` halves the database work:

```java
Slice<Book> findSliceByAuthor(String author, Pageable pageable);
```

Spring Data implements this by asking for `pageSize + 1` rows internally. If `pageSize + 1` rows come back, `hasNext()` is true; otherwise it's false. The "+1" row is discarded from the visible content.

When to use each:

| You need… | Pick |
|---|---|
| Total page count (UI shows "Page 1 of 47") | `Page<T>` |
| Total result count (UI shows "382 matches") | `Page<T>` |
| Just "is there more?" (infinite scroll, next/prev navigation) | `Slice<T>` |
| Maximum cheapness (count is the slow part of your query) | `Slice<T>` |

---

## `Sort` — order by

`Sort` is constructed via static factories:

```java
Sort byYear        = Sort.by("publishedYear");                                    // default: ASC
Sort byYearDesc    = Sort.by("publishedYear").descending();
Sort byYearThenTitle = Sort.by(Sort.Order.asc("publishedYear"), Sort.Order.asc("title"));
```

You can pass `Sort` directly to `JpaRepository.findAll(Sort)`:

```java
List<Book> books = repository.findAll(Sort.by("publishedYear").descending());
```

…or embed it inside a `PageRequest`:

```java
PageRequest.of(0, 10, Sort.by("publishedYear").descending());
```

The field name (`"publishedYear"`) is the **entity field name**, not the column name. Spring Data turns it into the right column at query time.

If you sort on a field that doesn't exist, Spring throws `InvalidDataAccessApiUsageException` at runtime — the safety net is integration tests, not the compiler.

---

## `PageRequest.of` — what the three arguments mean

```java
PageRequest.of(page, size)
PageRequest.of(page, size, sort)
PageRequest.of(page, size, direction, properties...)  // shortcut
```

| Argument | Meaning |
|---|---|
| `page` | Zero-indexed page number. `page=0` is the first page. `page=1` is the second. (Spring Data is zero-indexed; some APIs ship as 1-indexed and translate at the controller boundary — module 11 has the pattern.) |
| `size` | How many rows per page. Cap this at the controller boundary (e.g. max 100) so a client can't ask for `size=1000000` and crash you. |
| `sort` | Optional `Sort` for ordering. |

For `Slice<T>` you'd use the same `PageRequest`; the only difference is the return type.

---

## Connecting back to module 09 topic 12

You hand-rolled the same shape in module 09 topic 12:

```java
// What we wrote in module 09
@GetMapping("/api/books")
public PagedResponse<Book> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "name,asc") String sort) {

    Comparator<Book> cmp = parseSort(sort);
    List<Book> sorted = store.values().stream().sorted(cmp).toList();
    int from = Math.min(page * size, sorted.size());
    int to = Math.min(from + size, sorted.size());
    return new PagedResponse<>(sorted.subList(from, to), page, size, sorted.size());
}
```

The Spring Data equivalent, with JPA backing:

```java
@GetMapping("/api/books")
public Page<Book> list(Pageable pageable) {     // Spring binds ?page=&size=&sort= automatically
    return repository.findAll(pageable);
}
```

Two lines instead of fifteen. And it works for any repository method that takes a `Pageable`.

For the controller path: Spring Boot includes a `PageableHandlerMethodArgumentResolver` that turns `?page=0&size=10&sort=publishedYear,desc` into a `Pageable`, with the same multi-sort syntax (`?sort=publishedYear,desc&sort=title,asc`) you hand-parsed in module 09.

---

## `Page<T>.map(...)` — transform without losing the metadata

A common pattern: load entities, but return DTOs. `Page<T>` has a `map` method that applies a function to each element and returns a new `Page<U>` with the same metadata:

```java
Page<Book> entityPage = repository.findByAuthor(author, pageable);
Page<BookDto> dtoPage = entityPage.map(BookDto::from);

return dtoPage;   // Jackson serializes the DTO content + the page metadata
```

The total counts and `hasNext` etc. carry over. No second query.

---

## The pagination + `JOIN FETCH` warning (again)

Topic 07 mentioned this: combining `Pageable` with `JOIN FETCH` on a collection makes Hibernate do the pagination **in memory**, loading everything into the JVM first. The log will warn:

```
HHH000104: firstResult/maxResults specified with collection fetch; applying in memory
```

Fix options:

- Use `@EntityGraph` instead of `JOIN FETCH` for collection associations alongside paging.
- Page first, then load relationships in a separate query.
- Use `@ManyToOne` JOIN FETCH (single-row relationships are fine; the row count doesn't multiply).

---

## Common pitfalls

- **Returning `Page<Book>` (the entity) directly from a controller.** Same DTO discipline as module 09 — convert with `.map(...)` to a DTO so internal field changes don't break the API.
- **Off-by-one on page indexing.** Spring Data pages are zero-indexed. If your API contract is 1-indexed, translate at the controller boundary, not in business code.
- **Caps on page size.** Spring Boot's default cap is 2000. A malicious client can send `?size=2000` and crash you on a large dataset. Set `spring.data.web.pageable.max-page-size: 100` in `application.yml`.
- **Counting unnecessarily.** Using `Page<T>` when the caller only needs `hasNext()`. Switch to `Slice<T>`.
- **Sorting on a derived field that isn't a column.** Spring Data only knows entity fields. If you need to sort on `LENGTH(title)`, you need `@Query` with an explicit `ORDER BY`.
- **Pagination over an unstable order.** If you `ORDER BY publishedYear` and many rows share a year, the order *within* a year is undefined — and may shift between pages, so the same row appears twice or never. Always add a tiebreaker: `ORDER BY publishedYear, id`.
- **Pagination + `JOIN FETCH` on a collection.** Hibernate paginates in memory, which means it loads everything. Warning in the log. Fix: `@EntityGraph` instead.

---

## Try this yourself

1. Add `Page<Book> findAll(Pageable)` is already inherited. Call it with `PageRequest.of(0, 3, Sort.by("title"))` and inspect the SQL.
2. Replace the `Page<Book>` with `Slice<Book>` in `findByAuthor`. Re-run the tests — the count assertions fail (Slice doesn't expose total). Read what works on `Slice` (`hasNext`, `getContent`, `getNumber`).
3. Add a tie-breaker sort: `Sort.by(Sort.Order.asc("publishedYear"), Sort.Order.asc("id"))`. Compare the SQL — the second `ORDER BY` clause appears.
4. Add `spring.data.web.pageable.max-page-size: 5` to `application.yml`. Try to request `size=100` from a real controller — Spring caps it at 5.

## Self-check

1. What two SQL queries does a `Page<T>` repository method issue, and what does each return?
2. When is `Slice<T>` preferable to `Page<T>`?
3. Pageable's `page` argument — is it zero-indexed or one-indexed? Where would you translate if your API contract differs?
4. Why is sorting on a non-unique column without a tie-breaker dangerous when paginating?
