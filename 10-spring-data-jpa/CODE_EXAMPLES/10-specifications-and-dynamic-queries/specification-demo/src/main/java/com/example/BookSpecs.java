package com.example;

import org.springframework.data.jpa.domain.Specification;

public final class BookSpecs {

    private BookSpecs() {}

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

    public static Specification<Book> publishedYearAtMost(int year) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("publishedYear"), year);
    }
}
