# Pagination, filtering, and sorting — by hand

Every REST API that returns a list eventually needs three things: a way to ask for a subset (filter), a way to ask for an order (sort), and a way to ask for a slice (page). This topic implements all three by hand against an in-memory list. When Spring Data arrives in module 10, you will see the exact same shape automated — the conventions you learn here translate directly.

---

## The problem this solves

A naive `GET /api/books` returns every book in the system. That works at 50 books, hurts at 50 000, and brings the page-load timeline down to "loading…" at 5 000 000. Lists need a contract: how many at once, which subset, in what order. The conventions:

```
GET /api/books?page=0&size=20            paging only
GET /api/books?author=Bloch              filter
GET /api/books?sort=title,asc            sort
GET /api/books?page=2&size=20&author=Bloch&sort=author,asc&sort=title,desc
```

These are the names Spring Data uses, and the rest of the Java ecosystem has adopted them. Use them in your API even before Spring Data is in play.

---

## How it works — three concerns, one endpoint

### Filtering

Filtering picks which records appear. Each filter is a query parameter named after the field:

```java
.filter(b -> author == null || b.author().equalsIgnoreCase(author))
.filter(b -> minYear == null || b.year() >= minYear)
```

Conventions:

- `?author=Bloch` — exact match on `author`.
- `?titleContains=java` — substring search (be explicit about the verb when the parameter name doesn't make it obvious).
- `?minYear=2000&maxYear=2020` — ranges.
- `?status=active,archived` — collection of allowed values.

Null parameter -> filter is a no-op. Always allow filters to be combined.

### Sorting

Sort accepts one or more `field,direction` pairs:

```text
?sort=title,asc
?sort=author,asc&sort=year,desc
```

Multiple sorts apply in order — primary sort first, then tie-breakers. The parser:

```java
Comparator<Book> comparator = Comparator.comparing(b -> 0L);  // start with no-op
for (String spec : sortSpecs) {
    String[] parts = spec.split(",");
    String field = parts[0];
    boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
    Comparator<Book> next = comparatorFor(field);
    comparator = comparator.thenComparing(desc ? next.reversed() : next);
}
list.sort(comparator);
```

In real code you would write a small `Comparator<Book> comparatorFor(String)` factory that maps field names to comparators. Be explicit about which fields are sortable — exposing `comparatorFor` to arbitrary input is a vector for index-less queries against your database (module 10 covers the consequences).

One subtle Spring trap to be aware of: `@RequestParam List<String> sort` splits `"year,asc"` on the comma into `["year", "asc"]` because Spring's default `StringToCollectionConverter` treats commas as separators. The demo controller reads `HttpServletRequest.getParameterValues("sort")` to get the raw, unsplit values. Spring Data hides this behind a custom `SortHandlerMethodArgumentResolver`; you will see that mechanism in module 10.

### Paging

Paging slices the result after filtering and sorting:

```java
int from = page * size;
int to = Math.min(from + size, list.size());
List<Book> slice = list.subList(from, to);
```

Always clamp:

- `page < 0` -> treat as 0 (or 400).
- `size < 1` -> treat as the default.
- `size > max` -> clamp to a maximum (e.g. 100). Clients asking for `?size=1000000` should not get a million records.

### The wrapped response

A naked array is not enough — clients also need to know "how many total" and "is there a next page." The convention:

```json
{
  "content":         [ ...slice... ],
  "page":            2,
  "size":            20,
  "totalElements":   137,
  "totalPages":      7,
  "first":           false,
  "last":            false
}
```

Spring Data's `Page<T>` produces exactly this shape. Adopting it before module 10 means no JSON contract change later.

---

## A worked example

The demo project ships a controller that filters, sorts, and pages a `List<Book>` in memory. The shape of the call:

```
GET /api/books?page=0&size=2&author=Bloch&sort=year,desc
```

Returns:

```json
{
  "content": [
    {"id": 1, "title": "Effective Java", "author": "Bloch", "year": 2017},
    {"id": 4, "title": "Concurrency",    "author": "Bloch", "year": 2006}
  ],
  "page": 0,
  "size": 2,
  "totalElements": 3,
  "totalPages": 2,
  "first": true,
  "last": false
}
```

The controller is short — about 30 lines — and exercises every concept above. Read it from top to bottom in the demo.

---

## Offset vs cursor pagination

Offset paging (`?page=2&size=20`) is what every tutorial shows. It has two flaws:

1. **Cost grows with offset.** A SQL `LIMIT 20 OFFSET 1000000` scans a million rows to discard them. Acceptable below ~10 000 rows; pathological beyond.
2. **Drift under concurrent writes.** If item 21 is inserted between page 1 and page 2, the new item appears on both pages (or skips one).

**Cursor paging** addresses both. Instead of a numeric offset, the client sends an opaque token derived from the last item of the previous page:

```
GET /api/books?limit=20&cursor=eyJpZCI6MTIzfQ
```

The server decodes the cursor, e.g. `WHERE id > 123 ORDER BY id LIMIT 20`. Cost is O(limit), not O(offset). And there is no drift — the cursor pins the position.

Cursor paging is harder to implement and harder for clients to use (you can't jump to "page 47"). Use offset paging for small datasets and admin UIs; reach for cursor when you hit the limits. This module sticks with offset because Spring Data's `Page<T>` is offset-based.

---

## Common pitfalls

- **No max page size.** A client sets `?size=1000000` and OOMs your service. Always cap.
- **Filters that allow arbitrary fields.** `?filter=passwordHash=...` will find a way in if you parse generic filters. Whitelist the filter parameter names.
- **Sorting by arbitrary fields.** Same vector — exposing `comparatorFor(field)` without a whitelist lets clients ask for sorts on fields you didn't index. Module 10 covers the SQL angle.
- **Inconsistent pagination shapes.** Half the endpoints return `{content, page, size, totalElements}` and the other half return a bare array. Pick one and apply it consistently.
- **`page` 1-based on some endpoints, 0-based on others.** Same problem. Spring Data is 0-based; stay 0-based throughout your API.
- **Returning totals when they're expensive.** `totalElements` requires a `COUNT(*)` on the underlying query. For huge tables this can be slower than the page itself. Some APIs drop the total in favour of `hasNext` only (cheaper).

---

## Try this yourself

The demo project is `CODE_EXAMPLES/12-pagination-filtering-sorting/paging-demo`.

1. Run `mvn -q spring-boot:run`, then:
   ```
   curl -s "http://localhost:8080/api/books?page=0&size=2"
   curl -s "http://localhost:8080/api/books?author=Bloch"
   curl -s "http://localhost:8080/api/books?sort=year,asc"
   curl -s "http://localhost:8080/api/books?sort=author,asc&sort=year,desc"
   curl -s "http://localhost:8080/api/books?author=Bloch&page=0&size=2&sort=year,desc"
   ```

2. Try a hostile request: `?size=1000000`. The demo clamps to 100. Note the response is still well-formed.

3. Add a new sortable field (`title`). Verify `?sort=title,desc` works. Verify `?sort=secret` returns 400 (or your chosen error) because `secret` isn't whitelisted.

## Code examples (reading order)

1. **`App.java`** — bootstrap.
2. **`Book.java`** — record with four fields so sorting has something to chew on.
3. **`Page.java`** — the wrapped-response record, matching Spring Data's `Page<T>`.
4. **`BookController.java`** — filter -> sort -> page, in 30 lines.
5. **`BookControllerTest.java`** — MockMvc exercises each combination.

## Self-check

1. Why do real APIs return a wrapped object (`content`, `totalElements`, ...) instead of a bare array?
2. What goes wrong with offset paging on a million-row table?
3. A client sends `?sort=passwordHash,desc`. Your code calls `comparatorFor("passwordHash")`. What is the security issue, and what is the simplest fix?
