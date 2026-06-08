package com.example;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final List<Book> CATALOG = List.of(
            new Book(1L, "Effective Java",                 "Bloch",  2017),
            new Book(2L, "Java Concurrency in Practice",   "Goetz",  2006),
            new Book(3L, "Clean Code",                     "Martin", 2008),
            new Book(4L, "Concurrency Patterns",           "Bloch",  2006),
            new Book(5L, "The Pragmatic Programmer",       "Hunt",   1999),
            new Book(6L, "Refactoring",                    "Fowler", 2018),
            new Book(7L, "Domain-Driven Design",           "Evans",  2003));

    // Whitelist of sortable field names -> comparator. Arbitrary client input
    // must never be passed straight through to a field/index lookup; that is the
    // common shape of a slow-query or info-leak bug.
    private static final Map<String, Comparator<Book>> SORT_FIELDS = Map.of(
            "title",  Comparator.comparing(Book::title),
            "author", Comparator.comparing(Book::author),
            "year",   Comparator.comparingInt(Book::year));

    @GetMapping
    public ResponseEntity<Page<Book>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) String author,
            HttpServletRequest request) {

        // Read 'sort' off the raw request. With @RequestParam List<String> sort,
        // Spring's StringToCollectionConverter splits "year,asc" into
        // ["year", "asc"], breaking the Spring Data style. Spring Data uses a
        // dedicated argument resolver; here we just read the unconverted values.
        String[] sort = request.getParameterValues("sort");

        int effectivePage = Math.max(0, page);
        int effectiveSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        // 1) filter
        List<Book> filtered = CATALOG.stream()
                .filter(b -> author == null || b.author().equalsIgnoreCase(author))
                .toList();

        // 2) sort
        Comparator<Book> comparator = null;
        if (sort != null) {
            for (String spec : sort) {
                String[] parts = spec.split(",");
                String field = parts[0].trim();
                boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
                Comparator<Book> next = SORT_FIELDS.get(field);
                if (next == null) {
                    return ResponseEntity.badRequest().build();
                }
                if (desc) next = next.reversed();
                comparator = (comparator == null) ? next : comparator.thenComparing(next);
            }
        }
        List<Book> sorted = comparator == null
                ? filtered
                : filtered.stream().sorted(comparator).toList();

        // 3) page
        return ResponseEntity.ok(Page.of(sorted, effectivePage, effectiveSize));
    }
}
